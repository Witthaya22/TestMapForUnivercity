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
import th.ac.kmutnb.prachin.map.survey.PointSurveySession
import th.ac.kmutnb.prachin.map.survey.SurveyedPoint

data class SurveyUiState(
    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),

    val isCollectingPoint: Boolean = false,
    val collectedSamples: Int = 0,
    val targetSamples: Int = PointSurveySession.DEFAULT_SAMPLE_COUNT,
    val rejectedSamples: Int = 0,
    val averageAccuracyMeters: Float = 0f,
    val pointResult: SurveyedPoint? = null,
) {
    val currentAccuracy: Float?
        get() = when (val location = locationState) {
            is LocationState.Available -> location.fix.accuracyMeters
            is LocationState.Searching -> location.lastAccuracyMeters
            else -> null
        }
}

/**
 * Drives the POI surveyor, which produces `pois.geojson`.
 *
 * The whole point is that coordinates come from the same receiver that will later navigate
 * with them, so whatever offset that receiver has cancels out - see `docs/ACCURACY.md` A1.
 *
 * Paths used to be recorded here too, on a second tab. They are not any more: a walked
 * path is a measurement, and sharing a screen with a form that asks for a building's name
 * and faculty meant a survey of somewhere with no buildings kept being asked about them.
 * It lives in `ui/pathlog/` now - see `docs/PATH_LOGGING.md`.
 */
class SurveyorViewModel(
    private val application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyUiState())
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    private var session: PointSurveySession? = null
    private var locationJob: Job? = null

    init {
        startLocationUpdates()
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
        val available = location as? LocationState.Available ?: return
        val fix = available.fix

        session?.let { active ->
            // The same session type the GPS point log uses, given everything the fix knows:
            // a POI surveyed here can then report its satellite count and height too.
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
                    collectedSamples = active.sampleCount,
                    rejectedSamples = active.rejectedCount,
                    averageAccuracyMeters = active.averageAccuracyMeters,
                )
            }
            if (active.isComplete) finishPointSurvey()
        }
    }

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
    // Export
    // ----------------------------------------------------------------------------------

    fun exportPois(target: Uri, onResult: (Boolean) -> Unit) =
        writeDocument(target, onResult) { container.poiRepository.exportGeoJson() }

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
