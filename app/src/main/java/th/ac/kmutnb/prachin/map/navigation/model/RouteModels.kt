package th.ac.kmutnb.prachin.map.navigation.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

/**
 * A stop on the route. [nodeId] is the graph node the waypoint was snapped to, which is what
 * the router actually starts and ends at; [point] is the real-world position used for display
 * and for arrival detection.
 */
data class RouteWaypoint(
    val id: String,
    val name: String,
    val point: GeoPoint,
    val nodeId: Int,
)

/** The shortest path found between two consecutive waypoints. */
data class RouteLeg(
    val from: RouteWaypoint,
    val to: RouteWaypoint,
    /** Full geometry including both endpoints, in order. */
    val points: List<GeoPoint>,
    /** Real walking distance in metres - path type multipliers are NOT applied here. */
    val distanceMeters: Double,
) {
    val durationSeconds: Int get() = GeoUtils.walkingSecondsFor(distanceMeters)
}

/**
 * A complete multi-waypoint route, e.g. `หน้ามอ 1 -> หน้ามอ 3 -> หอชาย`.
 */
data class NavigationRoute(
    val waypoints: List<RouteWaypoint>,
    val legs: List<RouteLeg>,
) {
    /** Every leg joined end to end, without repeating the shared point between legs. */
    val points: List<GeoPoint> = buildList {
        legs.forEachIndexed { index, leg ->
            if (index == 0) addAll(leg.points) else addAll(leg.points.drop(1))
        }
    }

    val totalDistanceMeters: Double = legs.sumOf { it.distanceMeters }

    val totalDurationSeconds: Int get() = GeoUtils.walkingSecondsFor(totalDistanceMeters)

    /**
     * Distance from the start of the route to the start of each leg, so progress along the
     * whole route can be turned into "which leg am I on" without re-walking the geometry.
     */
    val legStartDistances: List<Double> = buildList {
        var running = 0.0
        legs.forEach { leg ->
            add(running)
            running += leg.distanceMeters
        }
    }

    val isEmpty: Boolean get() = legs.isEmpty()

    /** Cumulative distance to each point of [points], same size as [points]. */
    val cumulativeDistances: List<Double> = buildList {
        var running = 0.0
        points.forEachIndexed { index, point ->
            if (index > 0) running += GeoUtils.haversineMeters(points[index - 1], point)
            add(running)
        }
    }
}

/** Outcome of a routing request. */
sealed interface RoutePlanResult {
    data class Success(val route: NavigationRoute) : RoutePlanResult

    /** No connected path exists between two waypoints; carries which pair failed. */
    data class NoPath(val fromWaypointIndex: Int, val toWaypointIndex: Int) : RoutePlanResult

    /** Fewer than two waypoints, or the walking network has not been loaded. */
    data object NotEnoughWaypoints : RoutePlanResult
}
