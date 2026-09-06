package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.prefs.AppPreferences
import th.ac.kmutnb.prachin.map.navigation.RouteGraph
import th.ac.kmutnb.prachin.map.navigation.RouteGraphBuilder
import th.ac.kmutnb.prachin.map.navigation.model.RouteWaypoint
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/** The routable walking network plus where each POI sits on it. */
data class RouteNetwork(
    val graph: RouteGraph,
    val paths: List<WalkPath>,
    /** POI id -> graph node. Missing entries are in [unroutablePoiIds]. */
    val poiNodes: Map<String, Int>,
    /** POI id -> metres from the nearest path. */
    val poiSnapDistances: Map<String, Double>,
    /** POIs further than the snap limit from any path, so no route can reach them. */
    val unroutablePoiIds: List<String>,
    /** How many of [paths] the user walked and recorded themselves. */
    val surveyedPathCount: Int = 0,
) {
    val isReady: Boolean get() = !graph.isEmpty

    fun waypointFor(poi: Poi): RouteWaypoint? {
        val node = poiNodes[poi.id] ?: return null
        return RouteWaypoint(id = poi.id, name = poi.label, point = poi.point, nodeId = node)
    }

    /**
     * A waypoint for a position that is not a stored POI - the user's current location, or a
     * point they tapped.
     *
     * Projects onto the nearest *segment* and takes the closer end of it. Measuring against
     * nodes instead would ask "how far is the nearest corner of the road", which is a very
     * different question from "how far is the road": over half of this network's length sits
     * on segments longer than 60 m, so standing in the middle of one and being refused a
     * route was the common case rather than an edge case.
     */
    fun waypointFor(
        id: String,
        name: String,
        point: GeoPoint,
        maxSnapMeters: Double = MAX_DYNAMIC_SNAP_METERS,
    ): RouteWaypoint? {
        val projection = graph.project(point) ?: return null
        if (projection.distanceMeters > maxSnapMeters) return null
        return RouteWaypoint(
            id = id,
            name = name,
            point = point,
            nodeId = projection.nearestNode,
        )
    }

    companion object {
        /**
         * How far a live position may sit from a road or path and still be routed from.
         * Generous because GPS under tree cover drifts, and refusing to route at all is worse
         * than starting from a point a few metres off. Measured perpendicular to the network,
         * so this really is "how far from the road am I".
         */
        const val MAX_DYNAMIC_SNAP_METERS = 60.0

        val EMPTY = RouteNetwork(RouteGraph.EMPTY, emptyList(), emptyMap(), emptyMap(), emptyList())
    }
}

/**
 * Keeps a [RouteNetwork] in sync with the stored POIs and the paths the user has walked.
 *
 * The graph has to be rebuilt whenever a POI moves or is added, because each POI owns a node
 * spliced into the path it sits beside, and whenever a path is recorded or deleted. A rebuild
 * is a linear pass over a few thousand vertices, so doing it on every change is cheaper than
 * tracking incremental edits - and it is what makes a path usable for routing the moment the
 * walker stops recording it.
 */
class RouteNetworkRepository(
    private val campusRepository: CampusRepository,
    poiRepository: PoiRepository,
    walkPathRepository: WalkPathRepository,
    preferences: AppPreferences,
    scope: CoroutineScope,
) {

    val network: Flow<RouteNetwork> = combine(
        poiRepository.pois,
        walkPathRepository.paths,
        preferences.surveyedPathsOnly,
    ) { pois, surveyed, surveyedOnly -> Triple(pois, surveyed, surveyedOnly) }
        .map { (pois, surveyed, surveyedOnly) -> build(pois, surveyed, surveyedOnly) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), RouteNetwork.EMPTY)

    suspend fun current(): RouteNetwork = network.first()

    private suspend fun build(
        pois: List<Poi>,
        surveyed: List<WalkPath>,
        surveyedOnly: Boolean,
    ): RouteNetwork {
        // Surveyed paths come last so that where a recorded path and an imported one share a
        // junction, the builder's node merging joins them into one network rather than
        // leaving the walker's work stranded beside the imported data.
        val imported = if (surveyedOnly) emptyList() else campusRepository.paths().items
        val paths = imported + surveyed
        if (paths.isEmpty()) return RouteNetwork.EMPTY

        val built = RouteGraphBuilder().build(
            paths = paths,
            snapTargets = pois.associate { it.id to it.point },
        )
        return RouteNetwork(
            graph = built.graph,
            paths = paths,
            poiNodes = built.snappedNodes,
            poiSnapDistances = built.snapDistances,
            unroutablePoiIds = built.unsnappedIds,
            surveyedPathCount = surveyed.size,
        )
    }
}
