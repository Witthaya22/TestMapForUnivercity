package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.navigation.model.NavigationRoute
import th.ac.kmutnb.prachin.map.navigation.model.RouteLeg
import th.ac.kmutnb.prachin.map.navigation.model.RoutePlanResult
import th.ac.kmutnb.prachin.map.navigation.model.RouteWaypoint

/**
 * Chains A* results into a route that visits waypoints in the given order.
 *
 * The order is taken as-is: asking for `หน้ามอ 1 -> หน้ามอ 3 -> หอชาย` walks those three in
 * that sequence. Reordering them to shorten the total is a travelling-salesman problem and
 * would surprise a user who deliberately picked the order.
 */
object RoutePlanner {

    fun plan(graph: RouteGraph, waypoints: List<RouteWaypoint>): RoutePlanResult {
        if (waypoints.size < 2 || graph.isEmpty) return RoutePlanResult.NotEnoughWaypoints

        val legs = ArrayList<RouteLeg>(waypoints.size - 1)
        for (i in 0 until waypoints.lastIndex) {
            val from = waypoints[i]
            val to = waypoints[i + 1]
            val path = AStarRouter.findPath(graph, from.nodeId, to.nodeId)
                ?: return RoutePlanResult.NoPath(i, i + 1)

            legs += RouteLeg(
                from = from,
                to = to,
                points = withRealEndpoints(path.points, from, to),
                distanceMeters = path.distanceMeters,
            )
        }

        return RoutePlanResult.Success(NavigationRoute(waypoints = waypoints, legs = legs))
    }

    /**
     * Draws the leg from the waypoint's true position rather than from the graph node it
     * snapped to, so the line visibly touches the marker. The few metres this adds are not
     * counted in the distance, which stays the on-network figure A* computed.
     */
    private fun withRealEndpoints(
        pathPoints: List<th.ac.kmutnb.prachin.map.core.geo.GeoPoint>,
        from: RouteWaypoint,
        to: RouteWaypoint,
    ) = buildList {
        if (pathPoints.firstOrNull() != from.point) add(from.point)
        addAll(pathPoints)
        if (pathPoints.lastOrNull() != to.point) add(to.point)
    }
}
