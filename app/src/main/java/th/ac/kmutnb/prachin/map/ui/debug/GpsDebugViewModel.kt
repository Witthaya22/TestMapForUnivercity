package th.ac.kmutnb.prachin.map.ui.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo

data class GpsDebugUiState(
    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),
    /** Distance to the nearest surveyed path, or null when there is no fix or no network. */
    val distanceToPathMeters: Double? = null,
)

class GpsDebugViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(GpsDebugUiState())
    val uiState: StateFlow<GpsDebugUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // One fix a second: this screen exists to watch the receiver settle, so the
            // battery cost of the fast rate is the point rather than a problem.
            container.locationSource.updates(minIntervalMs = 1_000L).collect { location ->
                val distance = (location as? LocationState.Available)?.let { available ->
                    val graph = container.routeNetworkRepository.current().graph
                    if (graph.isEmpty) null else graph.distanceToNetworkMeters(available.fix.point)
                }
                _uiState.update {
                    it.copy(locationState = location, distanceToPathMeters = distance)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                GpsDebugViewModel(app.container)
            }
        }
    }
}
