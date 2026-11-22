package th.ac.kmutnb.prachin.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation

class TrailGuideTest {

    // A trail running due north along lon 101.0; 0.00001 degree of longitude is about 1.08 m here.
    private val line = listOf(GeoPoint(14.0000, 101.0), GeoPoint(14.0100, 101.0))
    private val stationA = TrailStation("a", "A", "", "", GeoPoint(14.0020, 101.0))
    private val stationB = TrailStation("b", "B", "", "", GeoPoint(14.0080, 101.0))
    private val trail = Trail(
        id = "t", name = "t", emoji = "", photo = "", distanceKm = 1.1, durationMinutes = 30,
        difficulty = TrailDifficulty.EASY, startPoint = "", endPoint = "", summary = "", description = "",
        stations = listOf(stationA, stationB), needsGuide = false, isOpen = true, openNote = "",
        trailhead = line.first(), path = line, entryRadiusMeters = 1000, demoUnlocked = false, sourceUrl = "",
        gpsToleranceMeters = 10,
    )

    private fun east(meters: Double, lat: Double = 14.005) = GeoPoint(lat, 101.0 + meters / 107_900.0)

    @Test
    fun `within the tolerance the dot is held on the line, and the real gap is kept`() {
        val (state, event) = TrailGuide().update(trail, east(7.0), 5f, emptySet())
        assertTrue(state.isSnapped)
        assertFalse(state.isOffTrail)
        assertNull(event)
        assertEquals(101.0, state.pointOnTrail!!.lon, 1e-7)
        assertEquals(7.0, state.distanceFromTrail!!, 0.5)
    }

    @Test
    fun `between the tolerance and the alert the dot is free but the walker is still on the trail`() {
        val (state, event) = TrailGuide().update(trail, east(18.0), 5f, emptySet())
        assertFalse(state.isSnapped)
        assertFalse(state.isOffTrail)
        assertNull(event)
    }

    @Test
    fun `leaving the trail alerts once, and coming back needs to be well inside`() {
        val guide = TrailGuide()
        assertEquals(TrailGuideEvent.LEFT_TRAIL, guide.update(trail, east(30.0), 5f, emptySet()).second)
        // Still out: no second alert.
        assertNull(guide.update(trail, east(40.0), 5f, emptySet()).second)
        // At 20 m - inside the 25 m alert line but not inside the 15 m return line - still off.
        val (hovering, none) = guide.update(trail, east(20.0), 5f, emptySet())
        assertTrue(hovering.isOffTrail)
        assertNull(none)
        assertEquals(TrailGuideEvent.BACK_ON_TRAIL, guide.update(trail, east(12.0), 5f, emptySet()).second)
    }

    @Test
    fun `the next station is the first not yet reached, with how far it is`() {
        val guide = TrailGuide()
        val (first, _) = guide.update(trail, GeoPoint(14.0010, 101.0), 5f, emptySet())
        assertEquals("a", first.nextStation?.id)
        assertEquals(111.0, first.metersToNextStation!!, 2.0)
        val (second, _) = guide.update(trail, GeoPoint(14.0021, 101.0), 5f, setOf("a"))
        assertEquals("b", second.nextStation?.id)
        val (done, _) = guide.update(trail, GeoPoint(14.0080, 101.0), 5f, setOf("a", "b"))
        assertNull(done.nextStation)
    }

    @Test
    fun `distance walked ignores jitter and coarse fixes`() {
        val guide = TrailGuide()
        guide.update(trail, GeoPoint(14.0000, 101.0), 5f, emptySet())
        guide.update(trail, GeoPoint(14.00001, 101.0), 5f, emptySet()) // 1 m: jitter
        guide.update(trail, GeoPoint(14.0050, 101.0), 60f, emptySet()) // too coarse to count
        val (state, _) = guide.update(trail, GeoPoint(14.0010, 101.0), 5f, emptySet())
        assertEquals(111.0, state.walkedMeters, 2.0)
    }

    @Test
    fun `a trail without a line gives no distance and never alerts`() {
        val (state, event) = TrailGuide().update(trail.copy(path = emptyList()), east(500.0), 5f, emptySet())
        assertNull(state.distanceFromTrail)
        assertFalse(state.isSnapped || state.isOffTrail)
        assertNull(event)
    }
}
