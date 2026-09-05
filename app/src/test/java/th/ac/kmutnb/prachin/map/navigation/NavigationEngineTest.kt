package th.ac.kmutnb.prachin.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at
import th.ac.kmutnb.prachin.map.navigation.model.NavigationRoute
import th.ac.kmutnb.prachin.map.navigation.model.RouteLeg
import th.ac.kmutnb.prachin.map.navigation.model.RouteWaypoint

class NavigationEngineTest {

    /** Straight 400 m corridor south to north with a waypoint at 200 m and one at 400 m. */
    private fun straightRoute(): NavigationRoute {
        val start = RouteWaypoint("start", "หน้ามอ 1", at(0.0, 0.0), 0)
        val middle = RouteWaypoint("middle", "หน้ามอ 3", at(200.0, 0.0), 1)
        val end = RouteWaypoint("end", "หอชาย", at(400.0, 0.0), 2)
        return NavigationRoute(
            waypoints = listOf(start, middle, end),
            legs = listOf(
                RouteLeg(start, middle, listOf(at(0.0, 0.0), at(100.0, 0.0), at(200.0, 0.0)), 200.0),
                RouteLeg(middle, end, listOf(at(200.0, 0.0), at(300.0, 0.0), at(400.0, 0.0)), 200.0),
            ),
        )
    }

    private fun engine(route: NavigationRoute = straightRoute()) = NavigationEngine(route)

    @Test
    fun `route geometry joins legs without repeating the shared point`() {
        val route = straightRoute()
        assertEquals(5, route.points.size)
        assertEquals(400.0, route.totalDistanceMeters, 1.0)
    }

    @Test
    fun `remaining distance falls as the walker advances`() {
        val engine = engine()
        val atStart = engine.update(at(0.0, 0.0), 0L).progress
        val quarter = engine.update(at(100.0, 0.0), 1_000L).progress
        val threeQuarters = engine.update(at(300.0, 0.0), 2_000L).progress

        assertEquals(400.0, atStart.remainingMeters, 2.0)
        assertEquals(300.0, quarter.remainingMeters, 2.0)
        assertEquals(100.0, threeQuarters.remainingMeters, 2.0)

        assertEquals(0.0, atStart.progressFraction, 0.01)
        assertEquals(0.25, quarter.progressFraction, 0.01)
        assertEquals(0.75, threeQuarters.progressFraction, 0.01)
    }

    @Test
    fun `distance to the next waypoint is measured along the route`() {
        // An L-shaped route: the straight line to the corner shortcuts the real walk.
        val start = RouteWaypoint("s", "s", at(0.0, 0.0), 0)
        val end = RouteWaypoint("e", "e", at(100.0, 100.0), 1)
        val route = NavigationRoute(
            waypoints = listOf(start, end),
            legs = listOf(
                RouteLeg(start, end, listOf(at(0.0, 0.0), at(100.0, 0.0), at(100.0, 100.0)), 200.0),
            ),
        )
        val progress = NavigationEngine(route).update(at(0.0, 0.0), 0L).progress

        // Along the corridor it is 200 m, even though the destination is only ~141 m away.
        assertEquals(200.0, progress.distanceToNextMeters, 2.0)
    }

    @Test
    fun `arrival needs two consecutive close fixes`() {
        val engine = engine()

        val first = engine.update(at(195.0, 0.0), 0L)
        assertTrue(first.events.isEmpty())
        assertEquals(1, engine.targetIndex)

        val second = engine.update(at(197.0, 0.0), 1_000L)
        val arrival = second.events.single() as NavigationEvent.Arrived
        assertEquals(1, arrival.waypointIndex)
        assertEquals("หน้ามอ 3", arrival.waypoint.name)
        assertFalse(arrival.isFinal)
        assertEquals(2, engine.targetIndex)
    }

    @Test
    fun `a single noisy fix near a waypoint does not count as arrival`() {
        val engine = engine()
        engine.update(at(195.0, 0.0), 0L)
        // GPS jumps away again before the second reading.
        val away = engine.update(at(140.0, 0.0), 1_000L)
        assertTrue(away.events.isEmpty())
        assertEquals(1, engine.targetIndex)
    }

    @Test
    fun `the final waypoint is flagged and completes the route`() {
        val engine = engine()
        // Reach the middle.
        engine.update(at(200.0, 0.0), 0L)
        engine.update(at(200.0, 0.0), 1_000L)
        // Reach the end.
        engine.update(at(400.0, 0.0), 2_000L)
        val last = engine.update(at(400.0, 0.0), 3_000L)

        val arrival = last.events.single() as NavigationEvent.Arrived
        assertTrue(arrival.isFinal)
        assertTrue(engine.isComplete)
        assertTrue(last.progress.isComplete)
        assertNull(engine.update(at(400.0, 0.0), 4_000L).progress.nextWaypoint)
    }

    @Test
    fun `going off route only fires after the grace period`() {
        val engine = engine()
        engine.update(at(100.0, 0.0), 0L)

        // 40 m to the side, which is past the 25 m threshold.
        val immediately = engine.update(at(100.0, 40.0), 1_000L)
        assertTrue("should wait out the grace period", immediately.events.isEmpty())

        val stillAway = engine.update(at(100.0, 40.0), 5_000L)
        assertTrue(stillAway.events.isEmpty())

        val afterGrace = engine.update(at(100.0, 40.0), 11_500L)
        assertEquals(listOf(NavigationEvent.WentOffRoute), afterGrace.events)

        // Reported once, not on every subsequent fix.
        assertTrue(engine.update(at(100.0, 40.0), 13_000L).events.isEmpty())
    }

    @Test
    fun `a brief wobble beside the route never fires`() {
        val engine = engine()
        engine.update(at(100.0, 0.0), 0L)
        engine.update(at(100.0, 30.0), 2_000L)
        val backOn = engine.update(at(100.0, 2.0), 4_000L)
        assertTrue(backOn.events.isEmpty())
    }

    @Test
    fun `returning to the route is reported`() {
        val engine = engine()
        engine.update(at(100.0, 0.0), 0L)
        engine.update(at(100.0, 40.0), 1_000L)
        engine.update(at(100.0, 40.0), 12_000L)

        val back = engine.update(at(100.0, 3.0), 14_000L)
        assertEquals(listOf(NavigationEvent.BackOnRoute), back.events)
    }

    @Test
    fun `distance from route measures the perpendicular offset`() {
        val progress = engine().update(at(100.0, 18.0), 0L).progress
        assertEquals(18.0, progress.distanceFromRouteMeters, 1.0)
        // The snapped point sits back on the corridor.
        assertEquals(0.0, progress.snappedPoint.lon - at(100.0, 0.0).lon, 1e-7)
    }

    @Test
    fun `travelled and remaining polylines split at the walker`() {
        val progress = engine().update(at(150.0, 0.0), 0L).progress
        assertEquals(progress.snappedPoint, progress.travelledPolyline.last())
        assertEquals(progress.snappedPoint, progress.remainingPolyline.first())
        assertEquals(at(0.0, 0.0), progress.travelledPolyline.first())
        assertEquals(at(400.0, 0.0), progress.remainingPolyline.last())
    }

    @Test
    fun `progress is clamped when the walker overshoots the end`() {
        val progress = engine().update(at(500.0, 0.0), 0L).progress
        assertEquals(0.0, progress.remainingMeters, 1.0)
        assertEquals(1.0, progress.progressFraction, 0.01)
    }

    @Test
    fun `eta uses the shared walking speed`() {
        val progress = engine().update(at(0.0, 0.0), 0L).progress
        // 400 m at 1.3 m/s, rounded up.
        assertEquals(308, progress.remainingSeconds)
    }

    @Test
    fun `an empty route degrades without throwing`() {
        val empty = NavigationRoute(waypoints = emptyList(), legs = emptyList())
        val progress = NavigationEngine(empty).update(GeoPoint(14.11, 101.38), 0L).progress
        assertEquals(0.0, progress.totalMeters, 0.0)
        assertEquals(0.0, progress.progressFraction, 0.0)
        assertNull(progress.nextWaypoint)
    }
}
