package th.ac.kmutnb.prachin.map.ui.pathlog

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.survey.RecordedTrack
import th.ac.kmutnb.prachin.map.survey.TrackRecorder

/** Which file the export wrote, so the screen can say which one succeeded. */
enum class PathLogExportFormat { GEOJSON, CSV }

data class PathLogUiState(
    val config: CampusConfig? = null,
    val configProblem: ConfigProblem? = null,
    val styleUri: String? = null,

    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),

    val isRecording: Boolean = false,
    /** The line as it is being walked, for drawing. Empty when not recording. */
    val recordingPoints: List<GeoPoint> = emptyList(),
    val recordedLengthMeters: Double = 0.0,
    val recordedFixCount: Int = 0,
    val rejectedFixCount: Int = 0,
    val recordingAccuracyMeters: Float = 0f,
    val recordingSeconds: Int = 0,

    /** A finished walk waiting for the walker to name it or throw it away. */
    val pendingResult: RecordedTrack? = null,
    /** The running label the pending walk will be saved under. */
    val pendingCode: String = "",

    val tracks: List<TrackLog> = emptyList(),
    /** The saved track whose detail sheet is open. */
    val selectedTrack: TrackLog? = null,

    /** Camera follows the dot until the user pans away from it. */
    val followUser: Boolean = true,
) {
    val fix get() = (locationState as? LocationState.Available)?.fix

    val currentPoint: GeoPoint? get() = fix?.point

    val satellites: SatelliteInfo
        get() = when (val location = locationState) {
            is LocationState.Available -> location.satellites
            is LocationState.Searching -> location.satellites
            else -> SatelliteInfo.UNKNOWN
        }

    val currentAccuracyMeters: Float?
        get() = when (val location = locationState) {
            is LocationState.Available -> location.fix.accuracyMeters
            is LocationState.Searching -> location.lastAccuracyMeters
            else -> null
        }

    /**
     * Whether recording may start. Starting without a fix would draw nothing and count
     * nothing, which reads as a broken screen rather than as a weak signal.
     */
    val canRecord: Boolean get() = fix != null && !isRecording && pendingResult == null

    /** Whether there is enough line to be worth keeping. Mirrors [TrackRecorder.isUsable]. */
    val canFinish: Boolean
        get() = recordingPoints.size >= 2 && recordedLengthMeters >= TrackRecorder.MIN_USABLE_LENGTH_M

    val routableCount: Int get() = tracks.count { it.isUsedForRouting }

    val totalLengthMeters: Double get() = tracks.sumOf { it.lengthMeters }
}

/**
 * Drives both walked-path logs: the map one and the readout one.
 *
 * One view model for two screens, differing only in [captureMode] - the same arrangement
 * the GPS point log uses, and for the same reason. The two screens record the same thing
 * in different surroundings, and splitting the logic would mean maintaining the filtering,
 * the running codes and the export twice, which is how two screens quietly stop agreeing
 * about what a saved track contains. What the mode really changes is small: without a map
 * there is no style to resolve and no line to draw.
 *
 * The filtering and the statistics are [TrackRecorder], which is pure Kotlin and unit
 * tested, so nothing here decides what counts as a good fix.
 */
class PathLogViewModel(
    private val application: Application,
    private val container: AppContainer,
    private val captureMode: TrackCaptureMode,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PathLogUiState())
    val uiState: StateFlow<PathLogUiState> = _uiState.asStateFlow()

    private var recorder: TrackRecorder? = null

    private var locationJob: Job? = null

    init {
        if (captureMode == TrackCaptureMode.MAP) loadConfig()
        observeTracks()
        startLocationUpdates()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            try {
                val config = container.campusRepository.config()
                _uiState.update { it.copy(config = config, configProblem = null) }
            } catch (e: CampusConfigException) {
                _uiState.update { it.copy(configProblem = e.problem) }
                return@launch
            }
            container.preferences.tileSourceMode.collect { mode ->
                val uri = container.mapStyleProvider.styleUri(mode)
                _uiState.update { it.copy(styleUri = uri) }
            }
        }
    }

    private fun observeTracks() {
        viewModelScope.launch {
            container.trackLogRepository.tracks.collect { tracks ->
                _uiState.update { state ->
                    state.copy(
                        tracks = tracks,
                        selectedTrack = tracks.firstOrNull { it.id == state.selectedTrack?.id },
                    )
                }
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            container.locationSource.updates(minIntervalMs = FIX_INTERVAL_MS).collect { location ->
                _uiState.update { it.copy(locationState = location) }
                if (location is LocationState.Available) {
                    onFix(location)
                }
            }
        }
    }

    /** Feeds the recorder, if one is running. Every fix, recording or not, updates the UI. */
    private fun onFix(location: LocationState.Available) {
        val active = recorder ?: return
        active.offer(
            point = location.fix.point,
            accuracyMeters = location.fix.accuracyMeters,
            satellites = location.satellites,
        )
        _uiState.update {
            it.copy(
                recordingPoints = active.points.toList(),
                recordedLengthMeters = active.lengthMeters,
                recordedFixCount = active.fixCount,
                rejectedFixCount = active.rejectedCount,
                recordingAccuracyMeters = active.averageAccuracyMeters,
                recordingSeconds = active.durationSeconds,
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // Recording
    // ----------------------------------------------------------------------------------

    fun startRecording() {
        if (!_uiState.value.canRecord) return
        recorder = TrackRecorder()
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingPoints = emptyList(),
                recordedLengthMeters = 0.0,
                recordedFixCount = 0,
                rejectedFixCount = 0,
                recordingAccuracyMeters = 0f,
                recordingSeconds = 0,
                selectedTrack = null,
            )
        }
    }

    /**
     * Stops and holds the result for naming.
     *
     * Not saved outright: the note is the only thing the walker will ever be able to use
     * to tell T007 from T008, and asking for it afterwards means asking about a walk they
     * have stopped thinking about.
     *
     * @return false when the walk was too short to keep, so the screen can say so.
     */
    fun stopRecording(): Boolean {
        val active = recorder ?: return false
        val finished = active.finish()
        recorder = null

        if (finished == null) {
            _uiState.update {
                it.copy(isRecording = false, recordingPoints = emptyList())
            }
            return false
        }

        viewModelScope.launch {
            val code = container.trackLogRepository.nextCode()
            _uiState.update {
                it.copy(isRecording = false, pendingResult = finished, pendingCode = code)
            }
        }
        return true
    }

    fun cancelRecording() {
        recorder = null
        _uiState.update {
            it.copy(
                isRecording = false,
                recordingPoints = emptyList(),
                recordedLengthMeters = 0.0,
                recordedFixCount = 0,
                rejectedFixCount = 0,
                recordingSeconds = 0,
            )
        }
    }

    fun savePending(note: String, useForRouting: Boolean) {
        val state = _uiState.value
        val pending = state.pendingResult ?: return
        viewModelScope.launch {
            container.trackLogRepository.save(
                recorded = pending,
                code = state.pendingCode,
                captureMode = captureMode,
                note = note.trim(),
                useForRouting = useForRouting,
            )
            _uiState.update {
                it.copy(pendingResult = null, pendingCode = "", recordingPoints = emptyList())
            }
        }
    }

    fun discardPending() = _uiState.update {
        it.copy(pendingResult = null, pendingCode = "", recordingPoints = emptyList())
    }

    // ----------------------------------------------------------------------------------
    // Saved tracks
    // ----------------------------------------------------------------------------------

    fun select(id: String) = _uiState.update { state ->
        state.copy(selectedTrack = state.tracks.firstOrNull { it.id == id })
    }

    fun dismissSelection() = _uiState.update { it.copy(selectedTrack = null) }

    fun setUsedForRouting(id: String, used: Boolean) {
        viewModelScope.launch { container.trackLogRepository.setUsedForRouting(id, used) }
    }

    fun updateNote(id: String, note: String) {
        viewModelScope.launch { container.trackLogRepository.updateNote(id, note.trim()) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.trackLogRepository.delete(id) }
        _uiState.update { it.copy(selectedTrack = null) }
    }

    fun deleteAll() {
        viewModelScope.launch { container.trackLogRepository.deleteAll() }
        _uiState.update { it.copy(selectedTrack = null) }
    }

    fun setFollowUser(follow: Boolean) = _uiState.update { it.copy(followUser = follow) }

    // ----------------------------------------------------------------------------------
    // Export
    // ----------------------------------------------------------------------------------

    /** @param ids exactly the tracks to write; never null, so nothing leaves by accident. */
    fun export(
        target: Uri,
        format: PathLogExportFormat,
        ids: Set<String>,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val ok = runCatching {
                val text = when (format) {
                    PathLogExportFormat.GEOJSON -> container.trackLogRepository.exportGeoJson(ids)
                    PathLogExportFormat.CSV -> container.trackLogRepository.exportCsv(ids)
                }
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("cannot open the chosen file for writing")
                }
            }.isSuccess
            onResult(ok)
        }
    }

    override fun onCleared() {
        locationJob?.cancel()
        super.onCleared()
    }

    companion object {
        /**
         * One fix a second. The recorder's own 3 m spacing filter decides what becomes a
         * vertex, so a faster stream would only add rejected duplicates and battery drain.
         */
        private const val FIX_INTERVAL_MS = 1_000L

        fun factory(captureMode: TrackCaptureMode): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                PathLogViewModel(app, app.container, captureMode)
            }
        }
    }
}
