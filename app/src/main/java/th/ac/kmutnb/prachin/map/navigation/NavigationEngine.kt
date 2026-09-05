package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.navigation.model.NavigationRoute
import th.ac.kmutnb.prachin.map.navigation.model.RouteWaypoint

/** Where the walker is along the route right now. */
data class NavigationProgress(
    /** The waypoint being walked towards. Null once the last one has been reached. */
    val nextWaypoint: RouteWaypoint?,
    val nextWaypointIndex: Int,
    /** Along the route, not as the crow flies. */
    val distanceToNextMeters: Double,
    val remainingMeters: Double,
    val travelledMeters: Double,
    val totalMeters: Double,
    /** Perpendicular distance from the route line; drives the off-route warning. */
    val distanceFromRouteMeters: Double,
    /** The position projected onto the route, which is what the distances are measured from. */
    val snappedPoint: GeoPoint,
    val travelledPolyline: List<GeoPoint>,
    val remainingPolyline: List<GeoPoint>,
    val isComplete: Boolean,
) {
    val progressFraction: Double
        get() = if (totalMeters <= 0.0) 0.0 else (travelledMeters / totalMeters).coerceIn(0.0, 1.0)

    val remainingSeconds: Int get() = GeoUtils.walkingSecondsFor(remainingMeters)

    val secondsToNextWaypoint: Int get() = GeoUtils.walkingSecondsFor(distanceToNextMeters)
}

/** Things worth telling the user about, emitted once each rather than on every fix. */
sealed interface NavigationEvent {
    data class Arrived(val waypointIndex: Int, val waypoint: RouteWaypoint, val isFinal: Boolean) :
        NavigationEvent

    data object WentOffRoute : NavigationEvent

    data object BackOnRoute : NavigationEvent
}

data class NavigationUpdate(
    val progress: NavigationProgress,
    val events: List<NavigationEvent>,
)

/**
 * Tracks progress along a planned route.
 *
 * Stateful on purpose: arrival and off-route are transitions, not properties of a single fix,
 * and both need hysteresis. Arrival requires two consecutive close fixes so a single noisy
 * reading cannot skip a waypoint, and going off route requires the walker to stay away for
 * a sustained period so a GPS wobble beside a building does not trigger a recalculation.
 *
 * Pure Kotlin with no Android dependency, so the whole state machine is unit tested.
 */
class NavigationEngine(
    private val route: NavigationRoute,
    private val arrivalRadiusMeters: Double = DEFAULT_ARRIVAL_RADIUS_M,
    private val offRouteThresholdMeters: Double = DEFAULT_OFF_ROUTE_THRESHOLD_M,
    private val offRouteGraceMillis: Long = DEFAULT_OFF_ROUTE_GRACE_MS,
) {

    /** Cumulative along-route distance of each waypoint, so leg maths is a lookup. */
    private val waypointDistances: List<Double> = buildList {
        add(0.0)
        var running = 0.0
        route.legs.forEach { leg ->
            running += leg.distanceMeters
            add(running)
        }
    }

    /** Index into [NavigationRoute.waypoints] of the stop being walked towards. */
    var targetIndex: Int = 1
        private set

    private var consecutiveCloseFixes = 0
    private var offRouteSinceMillis: Long? = null
    private var isOffRoute = false

    val isComplete: Boolean get() = targetIndex > route.waypoints.lastIndex

    fun update(position: GeoPoint, timestampMillis: Long): NavigationUpdate {
        val events = mutableListOf<NavigationEvent>()
        val projection = projectOntoRoute(position)

        // --- arrival -------------------------------------------------------------------
        val target = route.waypoints.getOrNull(targetIndex)
        if (target != null) {
            val straightLineToTarget = GeoUtils.haversineMeters(position, target.point)
            if (straightLineToTarget <= arrivalRadiusMeters) {
                consecutiveCloseFixes++
                if (consecutiveCloseFixes >= REQUIRED_CLOSE_FIXES) {
                    val isFinal = targetIndex == route.waypoints.lastIndex
                    events += NavigationEvent.Arrived(targetIndex, target, isFinal)
                    targetIndex++
                    consecutiveCloseFixes = 0
                }
            } else {
                consecutiveCloseFixes = 0
            }
        }

        // --- off route -----------------------------------------------------------------
        if (projection.distanceFromRoute > offRouteThresholdMeters) {
            val since = offRouteSinceMillis ?: timestampMillis.also { offRouteSinceMillis = it }
            if (!isOffRoute && timestampMillis - since >= offRouteGraceMillis) {
                isOffRoute = true
                events += NavigationEvent.WentOffRoute
            }
        } else {
            offRouteSinceMillis = null
            if (isOffRoute) {
                isOffRoute = false
                events += NavigationEvent.BackOnRoute
            }
        }

        val travelled = projection.distanceAlong
        val nextWaypoint = route.waypoints.getOrNull(targetIndex)
        val distanceToNext = nextWaypoint
            ?.let { (waypointDistances.getOrElse(targetIndex) { route.totalDistanceMeters } - travelled) }
            ?.coerceAtLeast(0.0)
            ?: 0.0

        val progress = NavigationProgress(
            nextWaypoint = nextWaypoint,
            nextWaypointIndex = targetIndex,
            distanceToNextMeters = distanceToNext,
            remainingMeters = (route.totalDistanceMeters - travelled).coerceAtLeast(0.0),
            travelledMeters = travelled,
            totalMeters = route.totalDistanceMeters,
            distanceFromRouteMeters = projection.distanceFromRoute,
            snappedPoint = projection.point,
            travelledPolyline = polylineUpTo(projection),
            remainingPolyline = polylineFrom(projection),
            isComplete = isComplete,
        )

        return NavigationUpdate(progress, events)
    }

    // ----------------------------------------------------------------------------------

    private data class RouteProjection(
        val point: GeoPoint,
        val segmentIndex: Int,
        val distanceAlong: Double,
        val distanceFromRoute: Double,
    )

    /**
     * Finds the closest point on the route line and how far along it sits.
     *
     * Measuring the remainder along the line rather than straight to the destination is what
     * makes the readout drop steadily as the user walks, instead of stalling whenever the
     * path bends away from the goal.
     */
    private fun projectOntoRoute(position: GeoPoint): RouteProjection {
        val points = route.points
        if (points.isEmpty()) return RouteProjection(position, 0, 0.0, 0.0)
        if (points.size == 1) {
            return RouteProjection(points[0], 0, 0.0, GeoUtils.haversineMeters(position, points[0]))
        }

        var bestIndex = 0
        var bestPoint = points[0]
        var bestDistance = Double.MAX_VALUE
        for (i in 1 until points.size) {
            val candidate = GeoUtils.nearestPointOnSegment(position, points[i - 1], points[i])
            val distance = GeoUtils.haversineMeters(position, candidate)
            if (distance < bestDistance) {
                bestDistance = distance
                bestIndex = i - 1
                bestPoint = candidate
            }
        }

        val along = route.cumulativeDistances[bestIndex] +
            GeoUtils.haversineMeters(points[bestIndex], bestPoint)
        return RouteProjection(bestPoint, bestIndex, along, bestDistance)
    }

    private fun polylineUpTo(projection: RouteProjection): List<GeoPoint> =
        route.points.take(projection.segmentIndex + 1) + projection.point

    private fun polylineFrom(projection: RouteProjection): List<GeoPoint> =
        listOf(projection.point) + route.points.drop(projection.segmentIndex + 1)

    companion object {
        /** Close enough to count as arrived; roughly the width of a building entrance. */
        const val DEFAULT_ARRIVAL_RADIUS_M = 20.0

        /** Beyond this from the route line the walker is somewhere else. */
        const val DEFAULT_OFF_ROUTE_THRESHOLD_M = 25.0

        /** How long they must stay there before being told. */
        const val DEFAULT_OFF_ROUTE_GRACE_MS = 10_000L

        /** Two fixes, so one noisy reading cannot mark a waypoint reached. */
        const val REQUIRED_CLOSE_FIXES = 2
    }
}
