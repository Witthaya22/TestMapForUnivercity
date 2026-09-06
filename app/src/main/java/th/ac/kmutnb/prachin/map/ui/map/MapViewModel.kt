package th.ac.kmutnb.prachin.map.ui.map

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.local.RouteHistoryEntity
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.data.repository.RouteNetwork
import th.ac.kmutnb.prachin.map.navigation.model.RouteWaypoint
import kotlin.math.roundToInt
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.navigation.NavigationEngine
import th.ac.kmutnb.prachin.map.navigation.NavigationEvent
import th.ac.kmutnb.prachin.map.navigation.NavigationProgress
import th.ac.kmutnb.prachin.map.navigation.RoutePlanner
import th.ac.kmutnb.prachin.map.navigation.TapValidator
import th.ac.kmutnb.prachin.map.navigation.TapVerdict
import th.ac.kmutnb.prachin.map.navigation.model.NavigationRoute
import th.ac.kmutnb.prachin.map.navigation.model.RoutePlanResult

/** A dialog waiting on the user, driven by [TapVerdict]. */
data class PendingPlacement(val verdict: TapVerdict)

data class MapUiState(
    val config: CampusConfig? = null,
    val configProblem: ConfigProblem? = null,
    val styleUri: String? = null,

    val pois: List<Poi> = emptyList(),
    val network: RouteNetwork = RouteNetwork.EMPTY,
    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),

    /** Destinations chosen so far, in visiting order. */
    val selectedWaypoints: List<Poi> = emptyList(),
    val route: NavigationRoute? = null,
    val progress: NavigationProgress? = null,
    val isNavigating: Boolean = false,
    val isOffRoute: Boolean = false,

    /** Set while the user is choosing a new position for this POI. */
    val relocatingPoi: Poi? = null,

    /** True when the route had to start at the first chosen stop, not at the user. */
    val startsAtFirstStop: Boolean = false,

    /** POI whose detail sheet is open, either tapped or just reached. */
    val detailPoi: Poi? = null,
    val detailIsArrival: Boolean = false,

    val pendingPlacement: PendingPlacement? = null,
    /** Point awaiting a name before it becomes a POI. */
    val namingPoint: GeoPoint? = null,

    val showWalkingNetwork: Boolean = false,
) {
    val currentPoint: GeoPoint?
        get() = (locationState as? LocationState.Available)?.fix?.point

    val hasLocation: Boolean get() = currentPoint != null
}

/** One-shot side effects: things that happen rather than things that are true. */
sealed interface MapEffect {
    data object VibrateArrival : MapEffect
    data class Message(@param:StringRes val messageRes: Int) : MapEffect

    /** A message with a value in it, formatted against the string resource. */
    data class MessageWith(@param:StringRes val messageRes: Int, val arg: Any) : MapEffect
    data class CameraTo(val point: GeoPoint) : MapEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModel(
    application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private val effectChannel = Channel<MapEffect>(Channel.BUFFERED)
    val effects: Flow<MapEffect> = effectChannel.receiveAsFlow()

    private val locationSource = container.locationSource

    /**
     * Update interval, raised while navigating and relaxed otherwise. The GPS chip is the
     * biggest battery draw in the app, and one fix every three seconds is plenty for a dot
     * the user is only glancing at.
     */
    private val locationInterval = MutableStateFlow(IDLE_INTERVAL_MS)

    private var engine: NavigationEngine? = null

    init {
        loadConfig()

        viewModelScope.launch {
            container.poiRepository.pois.collect { pois ->
                _uiState.update { state ->
                    // Drop selections whose POI has been deleted.
                    val ids = pois.mapTo(HashSet()) { it.id }
                    state.copy(
                        pois = pois,
                        selectedWaypoints = state.selectedWaypoints.filter { it.id in ids },
                    )
                }
            }
        }

        viewModelScope.launch {
            container.routeNetworkRepository.network.collect { network ->
                _uiState.update { it.copy(network = network) }
            }
        }

        viewModelScope.launch {
            locationInterval
                .flatMapLatest { interval -> locationSource.updates(minIntervalMs = interval) }
                .collect(::onLocationState)
        }
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

            // Resolved on every change rather than once, so switching the tile source in
            // Settings takes effect immediately: MapScreen keys its DisposableEffect on
            // styleUri and reloads the style when this emits a different one.
            container.preferences.tileSourceMode.collect { mode ->
                val uri = container.mapStyleProvider.styleUri(mode)
                _uiState.update { it.copy(styleUri = uri) }
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // Location
    // ----------------------------------------------------------------------------------

    private fun onLocationState(state: LocationState) {
        _uiState.update { it.copy(locationState = state) }
        if (state !is LocationState.Available) return
        adjustIntervalForSpeed(state.fix.speedMps)

        val activeEngine = engine ?: return
        val update = activeEngine.update(state.fix.point, System.currentTimeMillis())
        _uiState.update { it.copy(progress = update.progress) }

        update.events.forEach { event ->
            when (event) {
                is NavigationEvent.Arrived -> onArrived(event)
                NavigationEvent.WentOffRoute -> onWentOffRoute()
                NavigationEvent.BackOnRoute -> _uiState.update { it.copy(isOffRoute = false) }
            }
        }
    }

    /**
     * Backs the GPS off while the user is standing still.
     *
     * The receiver is the largest single battery draw here, and a stationary walker gains
     * nothing from a fix every second - the dot does not move. Waiting at a crossing or
     * reading a noticeboard is the common case on a campus walk, so this covers a real part
     * of a session rather than an edge case. Any movement restores the full rate on the very
     * next fix, so the dot never lags behind someone who starts walking again.
     */
    private fun adjustIntervalForSpeed(speedMps: Float?) {
        if (!_uiState.value.isNavigating) return
        val moving = speedMps == null || speedMps >= WALKING_SPEED_THRESHOLD_MPS
        locationInterval.value = if (moving) NAVIGATING_INTERVAL_MS else STANDING_INTERVAL_MS
    }

    private fun onArrived(event: NavigationEvent.Arrived) {
        viewModelScope.launch {
            effectChannel.send(MapEffect.VibrateArrival)
            val poi = container.poiRepository.find(event.waypoint.id)
            _uiState.update { it.copy(detailPoi = poi, detailIsArrival = true) }

            if (event.isFinal) {
                recordCompletedRoute()
                stopNavigation(showMessage = false)
            }
        }
    }

    private fun onWentOffRoute() {
        _uiState.update { it.copy(isOffRoute = true) }
        viewModelScope.launch {
            if (container.preferences.autoRecalculate.first()) {
                recalculateRoute()
            } else {
                effectChannel.send(MapEffect.Message(R.string.nav_off_route))
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // Waypoint selection
    // ----------------------------------------------------------------------------------

    fun addWaypoint(poi: Poi) {
        _uiState.update { state ->
            if (state.selectedWaypoints.any { it.id == poi.id }) {
                state
            } else {
                state.copy(selectedWaypoints = state.selectedWaypoints + poi, detailPoi = null)
            }
        }
        planRoute()
    }

    fun setSingleDestination(poi: Poi) {
        _uiState.update { it.copy(selectedWaypoints = listOf(poi), detailPoi = null) }
        planRoute()
    }

    fun removeWaypoint(index: Int) {
        _uiState.update { state ->
            state.copy(
                selectedWaypoints = state.selectedWaypoints.filterIndexed { i, _ -> i != index },
            )
        }
        planRoute()
    }

    fun clearWaypoints() {
        _uiState.update { it.copy(selectedWaypoints = emptyList(), route = null, progress = null) }
        stopNavigation(showMessage = false)
    }

    // ----------------------------------------------------------------------------------
    // Routing
    // ----------------------------------------------------------------------------------

    /**
     * Recomputes the route through the chosen destinations, starting from the live position
     * when that position is actually on the campus network.
     *
     * Being somewhere the map does not cover must not make the app useless. Standing in a
     * dorm a kilometre off campus, "หอพักชาย to โรงอาหาร" is still a perfectly good question,
     * and every other map app answers it. So an unusable start is not an error: the route is
     * planned between the chosen places instead, and the UI says the line does not begin
     * where the user is. Only a single destination with no usable start has nothing to draw,
     * and that case explains itself with the real distance rather than "no route found".
     */
    private fun planRoute() {
        val state = _uiState.value
        if (state.selectedWaypoints.isEmpty()) {
            _uiState.update { it.copy(route = null, progress = null) }
            return
        }

        val network = state.network
        val destinations = state.selectedWaypoints.mapNotNull(network::waypointFor)
        if (destinations.size != state.selectedWaypoints.size) {
            viewModelScope.launch { effectChannel.send(MapEffect.Message(R.string.poi_unroutable_explain)) }
            return
        }

        val startWaypoint = state.currentPoint?.let { point ->
            network.waypointFor(id = CURRENT_LOCATION_ID, name = "", point = point)
        }

        if (startWaypoint == null) {
            if (destinations.size < 2) {
                _uiState.update { it.copy(route = null, progress = null, startsAtFirstStop = false) }
                viewModelScope.launch { effectChannel.send(offNetworkMessage(state)) }
                return
            }
            // Enough destinations to draw a route between: fall back to that rather than
            // refusing, and tell the user where the line starts.
            plan(network, destinations, startsAtFirstStop = true, wasNavigating = state.isNavigating)
            return
        }

        plan(
            network = network,
            waypoints = listOf(startWaypoint) + destinations,
            startsAtFirstStop = false,
            wasNavigating = state.isNavigating,
        )
    }

    private fun plan(
        network: RouteNetwork,
        waypoints: List<RouteWaypoint>,
        startsAtFirstStop: Boolean,
        wasNavigating: Boolean,
    ) {
        when (val result = RoutePlanner.plan(network.graph, waypoints)) {
            is RoutePlanResult.Success -> {
                _uiState.update { it.copy(route = result.route, startsAtFirstStop = startsAtFirstStop) }
                if (wasNavigating && !startsAtFirstStop) attachEngine(result.route)
            }

            is RoutePlanResult.NoPath,
            RoutePlanResult.NotEnoughWaypoints,
            -> {
                _uiState.update { it.copy(route = null, progress = null, startsAtFirstStop = false) }
                viewModelScope.launch { effectChannel.send(MapEffect.Message(R.string.nav_no_path_body)) }
            }
        }
    }

    /** Says how far off the network the user is, instead of blaming the destination. */
    private fun offNetworkMessage(state: MapUiState): MapEffect {
        val point = state.currentPoint
            ?: return MapEffect.Message(R.string.nav_need_location)
        val metres = state.network.graph.distanceToNetworkMeters(point)
        if (metres == Double.MAX_VALUE) return MapEffect.Message(R.string.nav_no_path_body)
        return MapEffect.MessageWith(R.string.nav_off_campus, metres.roundToInt())
    }

    fun startNavigation() {
        val state = _uiState.value
        if (state.selectedWaypoints.isEmpty()) {
            viewModelScope.launch { effectChannel.send(MapEffect.Message(R.string.nav_need_two_points)) }
            return
        }
        if (!state.hasLocation) {
            viewModelScope.launch { effectChannel.send(MapEffect.Message(R.string.nav_need_location)) }
            return
        }
        planRoute()
        val route = _uiState.value.route ?: return
        attachEngine(route)
        locationInterval.value = NAVIGATING_INTERVAL_MS
        _uiState.update { it.copy(isNavigating = true) }
    }

    fun stopNavigation(showMessage: Boolean = true) {
        engine = null
        locationInterval.value = IDLE_INTERVAL_MS
        _uiState.update { it.copy(isNavigating = false, progress = null, isOffRoute = false) }
        if (showMessage) {
            viewModelScope.launch { effectChannel.send(MapEffect.Message(R.string.nav_stop)) }
        }
    }

    fun recalculateRoute() {
        planRoute()
        val route = _uiState.value.route
        viewModelScope.launch {
            if (route == null) {
                effectChannel.send(MapEffect.Message(R.string.nav_recalculate_failed))
            } else {
                attachEngine(route)
                _uiState.update { it.copy(isOffRoute = false) }
                effectChannel.send(MapEffect.Message(R.string.nav_recalculated))
            }
        }
    }

    private fun attachEngine(route: NavigationRoute) {
        engine = NavigationEngine(route)
        _uiState.value.currentPoint?.let { point ->
            _uiState.update { it.copy(progress = engine?.update(point, System.currentTimeMillis())?.progress) }
        }
    }

    private suspend fun recordCompletedRoute() {
        val state = _uiState.value
        val route = state.route ?: return
        container.routeHistoryDao.insert(
            RouteHistoryEntity(
                waypointIds = state.selectedWaypoints.joinToString(",") { it.id },
                distanceMeters = route.totalDistanceMeters,
                durationSeconds = route.totalDurationSeconds,
                completedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ----------------------------------------------------------------------------------
    // Map interaction
    // ----------------------------------------------------------------------------------

    fun onPoiTapped(poiId: String) {
        val poi = _uiState.value.pois.firstOrNull { it.id == poiId } ?: return
        _uiState.update { it.copy(detailPoi = poi, detailIsArrival = false) }
    }

    /** Long press on empty map: validate the spot before letting the user name it (F9). */
    fun onMapLongPressed(point: GeoPoint) {
        // While a POI is being moved the same gesture places it, so the user is not asked to
        // learn a second one.
        val relocating = _uiState.value.relocatingPoi
        if (relocating != null) {
            relocatePoi(relocating.id, point, gpsAccuracy = null)
            return
        }
        val verdict = TapValidator.validate(point, _uiState.value.network.graph)
        when (verdict) {
            is TapVerdict.OnPath -> _uiState.update { it.copy(namingPoint = verdict.point) }
            else -> _uiState.update { it.copy(pendingPlacement = PendingPlacement(verdict)) }
        }
    }

    // ----------------------------------------------------------------------------------
    // Moving a POI
    // ----------------------------------------------------------------------------------

    fun beginRelocate(poi: Poi) =
        _uiState.update { it.copy(relocatingPoi = poi, detailPoi = null) }

    fun cancelRelocate() = _uiState.update { it.copy(relocatingPoi = null) }

    /**
     * Puts the POI where the user is standing.
     *
     * This is the accurate way to correct a point and the one docs/ACCURACY.md argues for:
     * the position comes from the same receiver that will navigate to it, so whatever offset
     * the imported data had disappears by construction. The accuracy of the fix is stored
     * with it so the detail sheet can show how much to trust it.
     */
    fun movePoiToCurrentLocation() {
        val poi = _uiState.value.relocatingPoi ?: return
        val fix = (_uiState.value.locationState as? LocationState.Available)?.fix
        if (fix == null) {
            viewModelScope.launch { effectChannel.send(MapEffect.Message(R.string.nav_need_location)) }
            return
        }
        relocatePoi(poi.id, fix.point, fix.accuracyMeters)
    }

    private fun relocatePoi(poiId: String, point: GeoPoint, gpsAccuracy: Float?) {
        viewModelScope.launch {
            container.poiRepository.updateLocation(poiId, point, gpsAccuracy)
            _uiState.update { it.copy(relocatingPoi = null) }
            effectChannel.send(MapEffect.Message(R.string.poi_moved))
            effectChannel.send(MapEffect.CameraTo(point))
            // A moved POI owns a node spliced into the network, so any route through it is
            // stale until the graph is rebuilt; planRoute picks up the new one.
            if (_uiState.value.selectedWaypoints.any { it.id == poiId }) planRoute()
        }
    }

    /** "Move it onto the path" from the warning dialog. */
    fun acceptSuggestedPlacement() {
        val point = when (val verdict = _uiState.value.pendingPlacement?.verdict) {
            is TapVerdict.NearPath -> verdict.suggestion
            is TapVerdict.FarFromPath -> verdict.suggestion
            else -> null
        } ?: return
        _uiState.update { it.copy(pendingPlacement = null, namingPoint = point) }
    }

    /** "Keep it where I tapped" from the warning dialog. */
    fun keepPlacementAnyway() {
        val point = when (val verdict = _uiState.value.pendingPlacement?.verdict) {
            is TapVerdict.NearPath -> verdict.point
            is TapVerdict.OnPath -> verdict.point
            is TapVerdict.FarFromPath -> verdict.point
            else -> null
        } ?: return
        _uiState.update { it.copy(pendingPlacement = null, namingPoint = point) }
    }

    fun dismissPlacement() = _uiState.update { it.copy(pendingPlacement = null, namingPoint = null) }

    fun createPoiAtPendingPoint(name: String, category: PoiCategory) {
        val point = _uiState.value.namingPoint ?: return
        viewModelScope.launch {
            container.poiRepository.createUserPoi(name = name, point = point, category = category)
            _uiState.update { it.copy(namingPoint = null) }
        }
    }

    fun dismissDetail() = _uiState.update { it.copy(detailPoi = null, detailIsArrival = false) }

    fun toggleWalkingNetwork() =
        _uiState.update { it.copy(showWalkingNetwork = !it.showWalkingNetwork) }

    fun recenter() {
        _uiState.value.currentPoint?.let { point ->
            viewModelScope.launch { effectChannel.send(MapEffect.CameraTo(point)) }
        }
    }

    fun saveNote(poiId: String, note: String) {
        viewModelScope.launch { container.poiRepository.updateNote(poiId, note) }
    }

    fun saveDetails(
        poiId: String,
        name: String,
        description: String,
        note: String,
        category: PoiCategory,
    ) {
        viewModelScope.launch {
            container.poiRepository.updateDetails(poiId, name, description, note, category)
            _uiState.update { state ->
                state.copy(detailPoi = state.pois.firstOrNull { it.id == poiId })
            }
        }
    }

    fun deletePoi(poiId: String) {
        viewModelScope.launch {
            container.poiRepository.delete(poiId)
            _uiState.update { it.copy(detailPoi = null) }
        }
    }

    companion object {
        /** Synthetic waypoint id for the live position, which is not a stored POI. */
        private const val CURRENT_LOCATION_ID = "__current_location__"

        private const val NAVIGATING_INTERVAL_MS = 1_000L
        private const val IDLE_INTERVAL_MS = 3_000L

        /** Navigating but not moving; see [adjustIntervalForSpeed]. */
        private const val STANDING_INTERVAL_MS = 3_000L

        /**
         * Below this the user counts as standing still. Set under a slow walk (about
         * 1.2 m/s) but above the drift a stationary receiver reports.
         */
        private const val WALKING_SPEED_THRESHOLD_MPS = 0.5f

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                MapViewModel(app, app.container)
            }
        }
    }
}
