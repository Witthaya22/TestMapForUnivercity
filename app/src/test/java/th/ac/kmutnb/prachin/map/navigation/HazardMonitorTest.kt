package th.ac.kmutnb.prachin.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardType
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at

/**
 * The rules that decide when the phone speaks.
 *
 * Worth testing hard: a monitor that warns too often gets the voice switched off, after
 * which it warns nobody about anything, and one that warns too late is no warning at all.
 */
class HazardMonitorTest {

    private fun hazard(
        id: String = "h1",
        point: GeoPoint = at(0.0, 0.0),
        severity: HazardSeverity = HazardSeverity.WARNING,
        radius: Double = 10.0,
        active: Boolean = true,
    ) = HazardPoint(
        id = id,
        type = HazardType.DOG,
        severity = severity,
        point = point,
        radiusMeters = radius,
        description = "",
        isActive = active,
        gpsAccuracy = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `a hazard is announced once on approach, not on every fix`() {
        val monitor = HazardMonitor()
        // radius 10 + WARNING's 20 m approach = warn from 30 m out.
        val hazards = listOf(hazard())

        assertTrue(monitor.onPosition(at(0.0, 45.0), hazards, 0L).isEmpty())

        val entering = monitor.onPosition(at(0.0, 25.0), hazards, 1_000L)
        assertEquals("h1", entering.single().hazard.id)
        assertEquals(false, entering.single().isRepeat)

        // Still inside, a second later and five metres on: nothing more to say.
        assertTrue(monitor.onPosition(at(0.0, 20.0), hazards, 2_000L).isEmpty())
        assertTrue(monitor.onPosition(at(0.0, 5.0), hazards, 3_000L).isEmpty())
    }

    @Test
    fun `walking away and coming back warns again`() {
        val monitor = HazardMonitor()
        val hazards = listOf(hazard())

        assertEquals(1, monitor.onPosition(at(0.0, 20.0), hazards, 0L).size)
        // Out past the 30 m trigger plus the 15 m margin.
        assertTrue(monitor.onPosition(at(0.0, 60.0), hazards, 10_000L).isEmpty())
        assertEquals(1, monitor.onPosition(at(0.0, 20.0), hazards, 20_000L).size)
    }

    @Test
    fun `hovering on the boundary does not chatter`() {
        val monitor = HazardMonitor()
        val hazards = listOf(hazard())

        assertEquals(1, monitor.onPosition(at(0.0, 29.0), hazards, 0L).size)
        // Drifting a few metres either side of 30 m is GPS noise, not leaving.
        assertTrue(monitor.onPosition(at(0.0, 33.0), hazards, 1_000L).isEmpty())
        assertTrue(monitor.onPosition(at(0.0, 28.0), hazards, 2_000L).isEmpty())
        assertTrue(monitor.onPosition(at(0.0, 38.0), hazards, 3_000L).isEmpty())
        assertTrue(monitor.onPosition(at(0.0, 29.0), hazards, 4_000L).isEmpty())
    }

    @Test
    fun `only a danger repeats, and only after the interval`() {
        val monitor = HazardMonitor()
        val danger = listOf(hazard(severity = HazardSeverity.DANGER))
        val warning = listOf(hazard(id = "h2", severity = HazardSeverity.WARNING))

        assertEquals(1, monitor.onPosition(at(0.0, 10.0), danger, 0L).size)
        assertTrue(monitor.onPosition(at(0.0, 10.0), danger, 30_000L).isEmpty())

        val repeat = monitor.onPosition(at(0.0, 10.0), danger, 61_000L)
        assertEquals(true, repeat.single().isRepeat)

        // The same standing-still pattern says nothing twice for a mere warning.
        assertEquals(1, monitor.onPosition(at(0.0, 10.0), warning, 0L).size)
        assertTrue(monitor.onPosition(at(0.0, 10.0), warning, 300_000L).isEmpty())
    }

    @Test
    fun `severity buys earlier warning`() {
        val monitor = HazardMonitor()
        // Same 10 m radius, three severities: CAUTION warns at 20 m, WARNING at 30 m,
        // DANGER at 45 m.
        val caution = hazard(id = "c", severity = HazardSeverity.CAUTION)
        val danger = hazard(id = "d", severity = HazardSeverity.DANGER)

        val atForty = monitor.onPosition(at(0.0, 40.0), listOf(caution, danger), 0L)
        assertEquals(listOf("d"), atForty.map { it.hazard.id })

        val atFifteen = monitor.onPosition(at(0.0, 15.0), listOf(caution, danger), 1_000L)
        assertEquals(listOf("c"), atFifteen.map { it.hazard.id })
    }

    @Test
    fun `the worst is announced first when several fire at once`() {
        val monitor = HazardMonitor()
        val hazards = listOf(
            hazard(id = "far-danger", point = at(0.0, 25.0), severity = HazardSeverity.DANGER),
            hazard(id = "near-caution", point = at(0.0, 2.0), severity = HazardSeverity.CAUTION),
        )

        val alerts = monitor.onPosition(at(0.0, 0.0), hazards, 0L)
        assertEquals(listOf("far-danger", "near-caution"), alerts.map { it.hazard.id })
    }

    @Test
    fun `a hazard switched off says nothing`() {
        val monitor = HazardMonitor()
        assertTrue(monitor.onPosition(at(0.0, 0.0), listOf(hazard(active = false)), 0L).isEmpty())
    }

    @Test
    fun `reset makes the next approach announce again`() {
        val monitor = HazardMonitor()
        val hazards = listOf(hazard())

        assertEquals(1, monitor.onPosition(at(0.0, 10.0), hazards, 0L).size)
        monitor.reset()
        assertEquals(1, monitor.onPosition(at(0.0, 10.0), hazards, 1_000L).size)
    }

    @Test
    fun `nearby lists what the walker is inside, worst first, without announcing`() {
        val monitor = HazardMonitor()
        val hazards = listOf(
            hazard(id = "inside", point = at(0.0, 5.0)),
            hazard(id = "far", point = at(0.0, 200.0)),
        )

        assertEquals(listOf("inside"), monitor.nearby(at(0.0, 0.0), hazards).map { it.hazard.id })
        // Asking did not consume the announcement.
        assertEquals(1, monitor.onPosition(at(0.0, 0.0), hazards, 0L).size)
    }

    // ----------------------------------------------------------------------------------
    // Route pre-check
    // ----------------------------------------------------------------------------------

    @Test
    fun `hazards on a route come back in the order they will be met`() {
        // A 200 m walk due east.
        val route = listOf(at(0.0, 0.0), at(0.0, 200.0))
        val hazards = listOf(
            hazard(id = "late", point = at(0.0, 150.0)),
            hazard(id = "early", point = at(0.0, 30.0)),
            hazard(id = "off-route", point = at(500.0, 100.0)),
        )

        val found = HazardMonitor.alongRoute(route, hazards)
        assertEquals(listOf("early", "late"), found.map { it.hazard.id })
        assertEquals(30.0, found.first().alongRouteMeters, 2.0)
        assertEquals(0.0, found.first().distanceFromRouteMeters, 2.0)
    }

    @Test
    fun `a route passing beside a hazard still counts as walking into it`() {
        val route = listOf(at(0.0, 0.0), at(0.0, 100.0))
        // 25 m to the side of the line: outside the 10 m radius, inside the 30 m warning.
        val beside = hazard(point = at(25.0, 50.0))

        assertEquals(1, HazardMonitor.alongRoute(route, listOf(beside)).size)
        // 40 m away is clear of both.
        assertTrue(HazardMonitor.alongRoute(route, listOf(hazard(point = at(40.0, 50.0)))).isEmpty())
    }

    @Test
    fun `a route with nothing to it finds nothing`() {
        assertTrue(HazardMonitor.alongRoute(listOf(at(0.0, 0.0)), listOf(hazard())).isEmpty())
        assertTrue(HazardMonitor.alongRoute(emptyList(), listOf(hazard())).isEmpty())
    }
}
