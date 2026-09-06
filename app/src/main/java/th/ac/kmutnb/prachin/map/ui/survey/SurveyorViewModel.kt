package th.ac.kmutnb.prachin.map.ui.survey

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
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParser
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
import th.ac.kmutnb.prachin.map.survey.PointSurveySession
import th.ac.kmutnb.prachin.map.survey.SurveyedPoint
import th.ac.kmutnb.prachin.map.survey.TrackRecorder

enum class SurveyTab { POINT, TRACK }

data class SurveyUiState(
    val tab: SurveyTab = SurveyTab.POINT,
    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),

    val isCollectingPoint: Boolean = false,
    val collectedSamples: Int = 0,
    val targetSamples: Int = PointSurveySession.DEFAULT_SAMPLE_COUNT,
    val rejectedSamples: Int = 0,
    val averageAccuracyMeters: Float = 0f,
    val pointResult: SurveyedPoint? = null,

    val isRecordingTrack: Boolean = false,
    val trackPointCount: Int = 0,
    val trackLengthMeters: Double = 0.0,
    val simplifiedCount: Int = 0,
    /** Everything recorded so far, live from the database. */
    val recordedTracks: List<WalkPath> = emptyList(),
    /** Total metres walked while recording them. */
    val surveyedLengthMeters: Double = 0.0,
) {
    val currentAccuracy: Float?
        get() = when (val location = locationState) {
            is LocationState.Available -> location.fix.accuracyMeters
            is LocationState.Searching -> location.lastAccuracyMeters
            else -> null
        }
}

/**
 * Drives the surveyor tools that produce `pois.geojson` and `paths.geojson`.
 *
 * The whole point is that coordinates come from the same receiver that will later navigate
 * with them, so whatever offset that receiver has cancels out - see `docs/ACCURACY.md` A1.
 */
class SurveyorViewModel(
    private val application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    private var session: PointSurveySession? = null
    private var recorder: TrackRecorder? = null
    private var locationJob: Job? = null

    init {
        startLocationUpdates()
        observeRecordedTracks()
    }

    /**
     * Mirrors the stored paths into the UI state.
     *
     * Read back from the database rather than kept in a list here, so what the screen counts
     * is what the router will actually use, and so a path recorded, then deleted, then
     * recorded again cannot drift apart from the map.
     */
    private fun observeRecordedTracks() {
        viewModelScope.launch {
            container.walkPathRepository.paths.collect { paths ->
                val metres = container.walkPathRepository.totalLengthMeters()
                _uiState.update {
                    it.copy(recordedTracks = paths, surveyedLengthMeters = metres)
                }
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            // Surveying needs every fix the receiver produces, so the relaxed idle interval
            // used elsewhere is not appropriate here.
            container.locationSource.updates(minIntervalMs = 1_000L).collect(::onLocation)
        }
    }

    private fun onLocation(location: LocationState) {
        _uiState.update { it.copy(locationState = location) }
        val fix = (location as? LocationState.Available)?.fix ?: return

        session?.let { active ->
            active.offer(fix.point, fix.accuracyMeters)
            _uiState.update {
                it.copy(
                    collectedSamples = active.sampleCount,
                    rejectedSamples = active.rejectedCount,
                    averageAccuracyMeters = active.averageAccuracyMeters,
                )
            }
            if (active.isComplete) finishPointSurvey()
        }

        recorder?.let { active ->
            if (active.offer(fix.point)) {
                _uiState.update {
                    it.copy(
                        trackPointCount = active.pointCount,
                        trackLengthMeters = active.lengthMeters,
                    )
                }
            }
        }
    }

    fun selectTab(tab: SurveyTab) = _uiState.update { it.copy(tab = tab) }

    // ----------------------------------------------------------------------------------
    // Point survey
    // ----------------------------------------------------------------------------------

    fun startPointSurvey() {
        session = PointSurveySession()
        _uiState.update {
            it.copy(
                isCollectingPoint = true,
                collectedSamples = 0,
                rejectedSamples = 0,
                averageAccuracyMeters = 0f,
                pointResult = null,
            )
        }
    }

    fun stopPointSurvey() = finishPointSurvey()

    private fun finishPointSurvey() {
        val result = session?.result()
        session = null
        _uiState.update { it.copy(isCollectingPoint = false, pointResult = result) }
    }

    fun discardPointResult() = _uiState.update { it.copy(pointResult = null) }

    fun savePoint(name: String, category: PoiCategory, description: String) {
        val result = _uiState.value.pointResult ?: return
        viewModelScope.launch {
            container.poiRepository.createUserPoi(
                name = name,
                point = result.point,
                category = category,
                description = description,
                gpsAccuracy = result.averageAccuracyMeters,
            )
            _uiState.update { it.copy(pointResult = null) }
        }
    }

    // ----------------------------------------------------------------------------------
    // Track recording
    // ----------------------------------------------------------------------------------

    fun startTrackRecording() {
        recorder = TrackRecorder()
        _uiState.update {
            it.copy(
                isRecordingTrack = true,
                trackPointCount = 0,
                trackLengthMeters = 0.0,
                simplifiedCount = 0,
            )
        }
    }

    /** Returns false when the walk was too short to be worth keeping. */
    fun stopTrackRecording(name: String, type: PathType): Boolean {
        val active = recorder ?: return false
        recorder = null
        val rawCount = active.pointCount
        val simplified = active.simplified()

        if (simplified.size < 2 || active.lengthMeters < TrackRecorder.MIN_USABLE_LENGTH_M) {
            _uiState.update { it.copy(isRecordingTrack = false) }
            return false
        }

        val path = WalkPath(
            id = "surveyed_${System.currentTimeMillis()}",
            type = type,
            points = simplified,
            name = name.ifBlank { null },
        )
        // Stored immediately rather than held for export: the point of walking a path is to
        // be able to route over it, and waiting for a file round-trip and a rebuild would
        // make the survey useless until someone got back to a computer.
        viewModelScope.launch {
            container.walkPathRepository.save(
                path = path,
                lengthMeters = active.lengthMeters,
                gpsAccuracy = _uiState.value.currentAccuracy,
            )
        }
        _uiState.update {
            it.copy(
                isRecordingTrack = false,
                trackPointCount = rawCount,
                simplifiedCount = simplified.size,
            )
        }
        return true
    }

    fun cancelTrackRecording() {
        recorder = null
        _uiState.update { it.copy(isRecordingTrack = false, trackPointCount = 0) }
    }

    // ----------------------------------------------------------------------------------
    // Export
    // ----------------------------------------------------------------------------------

    fun exportPois(target: Uri, onResult: (Boolean) -> Unit) =
        writeDocument(target, onResult) { container.poiRepository.exportGeoJson() }

    fun deleteTrack(id: String) {
        viewModelScope.launch { container.walkPathRepository.delete(id) }
    }

    fun exportTracks(target: Uri, onResult: (Boolean) -> Unit) =
        writeDocument(target, onResult) { container.walkPathRepository.exportGeoJson() }

    private fun writeDocument(
        target: Uri,
        onResult: (Boolean) -> Unit,
        content: suspend () -> String,
    ) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = content()
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
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
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                SurveyorViewModel(app, app.container)
            }
        }
    }
}
