package th.ac.kmutnb.prachin.map.ui.gpslog

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
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsPoint
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.survey.PointSurveySession
import th.ac.kmutnb.prachin.map.survey.SurveyedPoint
import th.ac.kmutnb.prachin.map.survey.TrackRecorder

/** What the export writer produced, so the screen can say which file succeeded. */
enum class GpsLogExportFormat { GEOJSON, CSV }

data class GpsLogUiState(
    val config: CampusConfig? = null,
    val configProblem: ConfigProblem? = null,
    val styleUri: String? = null,

    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),

    /** Where the device has walked since the screen opened; drawn, never saved. */
    val trail: List<GeoPoint> = emptyList(),
    /** Camera follows the dot until the user pans away from it. */
    val followUser: Boolean = true,

    val isCapturing: Boolean = false,
    val capturedSamples: Int = 0,
    val targetSamples: Int = PointSurveySession.DEFAULT_SAMPLE_COUNT,
    val rejectedSamples: Int = 0,
    val averageAccuracyMeters: Float = 0f,
    /** A finished measurement waiting for the surveyor to keep or discard it. */
    val pendingResult: SurveyedPoint? = null,
    /** The running label the pending measurement will be saved under. */
    val pendingCode: String = "",

    val points: List<GpsPoint> = emptyList(),
    /** The recorded point whose detail sheet is open. */
    val selectedPoint: GpsPoint? = null,
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
     * Whether a measurement may be started. Capturing without a fix would sit at 0 of 30
     * samples forever, which looks like a frozen screen rather than a weak signal.
     */
    val canCapture: Boolean get() = fix != null && !isCapturing && pendingResult == null
}

/**
 * Drives both GPS point logs: the map one and the readout one.
 *
 * The POI surveyor in [th.ac.kmutnb.prachin.map.ui.survey.SurveyorViewModel] answers a
 * different question - "where is this building's door" - and immediately turns its result
 * into a place on the campus map. This one records what the receiver observed and stops
 * there, so the exported file is a set of readings that can be checked, re-measured and
 * compared, with nothing about the campus mixed into it.
 *
 * One view model for two screens, differing only in [captureMode]. The two screens show
 * the same measurement in different ways, and splitting the logic would mean maintaining
 * the averaging, the running codes and the export twice - the classic way two screens
 * quietly stop agreeing about what a saved point contains. What the mode does change is
 * real, though: without a map there is no style to resolve and no trail to draw, so
 * neither is set up.
 *
 * The averaging itself is [PointSurveySession], shared with the POI surveyor: there is one
 * implementation of "stand still and take the median", and it is unit tested once.
 */
class GpsLogViewModel(
    private val application: Application,
    private val container: AppContainer,
    private val captureMode: GpsCaptureMode,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GpsLogUiState())
    val uiState: StateFlow<GpsLogUiState> = _uiState.asStateFlow()

    private var session: PointSurveySession? = null

    /**
     * The breadcrumb behind the dot. Its 2 m spacing is finer than the 3 m the path
     * recorder uses, because this line is only ever looked at, never simplified into a
     * routable path, and a surveyor needs to see that they really did walk round the far
     * side of the building.
     */
    private val trail = TrackRecorder(
        minSpacingMeters = TRAIL_SPACING_M,
        // Every fix that reached here, however coarse. The recorder's accuracy gate is
        // there to keep bad fixes out of a routable line; this one is a breadcrumb, and
        // dropping the poor stretches would hide exactly where the signal was poor.
        maxAccuracyMeters = Float.MAX_VALUE,
    )

    private var locationJob: Job? = null

    /** True on the map screen; the readout screen needs no basemap and no breadcrumb. */
    private val showsMap: Boolean get() = captureMode == GpsCaptureMode.MAP

    init {
        if (showsMap) loadConfig()
        observePoints()
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

    private fun observePoints() {
        viewModelScope.launch {
            container.gpsPointRepository.points.collect { points ->
                _uiState.update { state ->
                    state.copy(
                        points = points,
                        // Drop a selection whose point has just been deleted.
                        selectedPoint = points.firstOrNull { it.id == state.selectedPoint?.id },
                    )
                }
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            // Every fix the receiver produces: this screen exists to measure, so the
            // battery cost of the fast rate is the point rather than a problem.
            container.locationSource.updates(minIntervalMs = FIX_INTERVAL_MS).collect(::onLocation)
        }
    }

    private fun onLocation(location: LocationState) {
        _uiState.update { it.copy(locationState = location) }
        val available = location as? LocationState.Available ?: return
        val fix = available.fix

        if (showsMap && trail.offer(fix.point, fix.accuracyMeters, available.satellites)) {
            _uiState.update { it.copy(trail = trail.points.toList()) }
        }

        val active = session ?: return
        active.offer(
            point = fix.point,
            accuracyMeters = fix.accuracyMeters,
            altitudeMeters = fix.altitudeMeters,
            verticalAccuracyMeters = fix.verticalAccuracyMeters,
            satellites = available.satellites,
            atMillis = fix.timeMs,
        )
        _uiState.update {
            it.copy(
                capturedSamples = active.sampleCount,
                rejectedSamples = active.rejectedCount,
                averageAccuracyMeters = active.averageAccuracyMeters,
            )
        }
        if (active.isComplete) finishCapture()
    }

    // ----------------------------------------------------------------------------------
    // Measuring
    // ----------------------------------------------------------------------------------

    fun startCapture() {
        if (!_uiState.value.canCapture) return
        session = PointSurveySession()
        viewModelScope.launch {
            val code = container.gpsPointRepository.nextCode()
            _uiState.update { it.copy(pendingCode = code) }
        }
        _uiState.update {
            it.copy(
                isCapturing = true,
                capturedSamples = 0,
                rejectedSamples = 0,
                averageAccuracyMeters = 0f,
                pendingResult = null,
            )
        }
    }

    /** Ends the measurement early, keeping whatever was collected so far. */
    fun stopCapture() = finishCapture()

    private fun finishCapture() {
        val result = session?.result()
        session = null
        _uiState.update { it.copy(isCapturing = false, pendingResult = result) }
    }

    fun cancelCapture() {
        session = null
        _uiState.update { it.copy(isCapturing = false, pendingResult = null) }
    }

    fun savePending(note: String) {
        val result = _uiState.value.pendingResult ?: return
        viewModelScope.launch {
            // Resolved again against the table rather than reusing the label shown during
            // the measurement: the preview is set when capturing starts, and only what is
            // true at the moment of writing may decide the running number.
            val code = container.gpsPointRepository.nextCode()
            container.gpsPointRepository.save(
                surveyed = result,
                code = code,
                captureMode = captureMode,
                note = note.trim(),
            )
            _uiState.update { it.copy(pendingResult = null) }
        }
    }

    fun discardPending() = _uiState.update { it.copy(pendingResult = null) }

    // ----------------------------------------------------------------------------------
    // The recorded points
    // ----------------------------------------------------------------------------------

    fun selectPoint(id: String) = _uiState.update { state ->
        state.copy(selectedPoint = state.points.firstOrNull { it.id == id })
    }

    fun dismissSelection() = _uiState.update { it.copy(selectedPoint = null) }

    fun updateNote(id: String, note: String) {
        viewModelScope.launch { container.gpsPointRepository.updateNote(id, note.trim()) }
    }

    fun deletePoint(id: String) {
        viewModelScope.launch { container.gpsPointRepository.delete(id) }
        _uiState.update { it.copy(selectedPoint = null) }
    }

    fun deleteAllPoints() {
        viewModelScope.launch { container.gpsPointRepository.deleteAll() }
        _uiState.update { it.copy(selectedPoint = null) }
    }

    // ----------------------------------------------------------------------------------
    // Camera
    // ----------------------------------------------------------------------------------

    fun setFollowUser(follow: Boolean) = _uiState.update { it.copy(followUser = follow) }

    fun clearTrail() {
        trail.reset()
        _uiState.update { it.copy(trail = emptyList()) }
    }

    // ----------------------------------------------------------------------------------
    // Export
    // ----------------------------------------------------------------------------------

    /**
     * @param filter null for every reading in the log, or one mode to write out only what
     * that screen recorded.
     */
    fun export(
        target: Uri,
        format: GpsLogExportFormat,
        filter: GpsCaptureMode?,
        onResult: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val ok = runCatching {
                val content = when (format) {
                    GpsLogExportFormat.GEOJSON ->
                        container.gpsPointRepository.exportGeoJson(filter)

                    GpsLogExportFormat.CSV -> container.gpsPointRepository.exportCsv(filter)
                }
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
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
        private const val FIX_INTERVAL_MS = 1_000L
        private const val TRAIL_SPACING_M = 2.0

        /** For the map screen. */
        val Factory: ViewModelProvider.Factory = factoryFor(GpsCaptureMode.MAP)

        /** For the readout screen. Each destination has its own store, so both can exist. */
        val ReadoutFactory: ViewModelProvider.Factory = factoryFor(GpsCaptureMode.READOUT)

        private fun factoryFor(mode: GpsCaptureMode): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as MapApplication
                    GpsLogViewModel(app, app.container, mode)
                }
            }
    }
}
