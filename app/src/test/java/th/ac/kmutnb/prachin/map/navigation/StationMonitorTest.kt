package th.ac.kmutnb.prachin.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation

class StationMonitorTest {

    // 0.0001 degree of latitude is about 11 m.
    private val fig = TrailStation("fig", "ต้นไทร", "🌳", "", GeoPoint(14.0000, 101.0), radiusMeters = 25)
    private val rock = TrailStation("rock", "หิน", "⛰", "", GeoPoint(14.0010, 101.0), radiusMeters = 25)
    private val listed = TrailStation("listed", "ไม่มีพิกัด", "", "", point = null)
    private val stations = listOf(fig, rock, listed)

    @Test
    fun `a station pops once on arrival, not again on the way back`() {
        val monitor = StationMonitor()
        assertTrue(monitor.onPosition("t", stations, GeoPoint(13.9990, 101.0), 5f).isEmpty()) // 110 m short
        assertEquals(listOf(fig), monitor.onPosition("t", stations, GeoPoint(13.9999, 101.0), 5f))
        assertTrue(monitor.onPosition("t", stations, GeoPoint(14.0000, 101.0), 5f).isEmpty())
        assertEquals(listOf(rock), monitor.onPosition("t", stations, GeoPoint(14.0010, 101.0), 5f))
        // Walking back past the fig tree tells nothing new.
        assertTrue(monitor.onPosition("t", stations, GeoPoint(14.0000, 101.0), 5f).isEmpty())
    }

    @Test
    fun `a station without a point is listed but never pops`() {
        val monitor = StationMonitor()
        assertTrue(monitor.onPosition("t", listOf(listed), GeoPoint(14.0, 101.0), 3f).isEmpty())
    }

    @Test
    fun `a fix too coarse to tell the stations apart is ignored, and the station waits`() {
        val monitor = StationMonitor()
        assertTrue(monitor.onPosition("t", stations, GeoPoint(14.0, 101.0), 45f).isEmpty())
        assertEquals(listOf(fig), monitor.onPosition("t", stations, GeoPoint(14.0, 101.0), 8f))
    }

    @Test
    fun `two stations reached at once come nearest first`() {
        val near = TrailStation("near", "a", "", "", GeoPoint(14.00005, 101.0), radiusMeters = 40)
        val far = TrailStation("far", "b", "", "", GeoPoint(14.00025, 101.0), radiusMeters = 40)
        val hits = StationMonitor().onPosition("t", listOf(far, near), GeoPoint(14.0, 101.0), 5f)
        assertEquals(listOf(near, far), hits)
    }

    @Test
    fun `a new walk, or another trail, starts fresh`() {
        val monitor = StationMonitor()
        monitor.onPosition("t", stations, GeoPoint(14.0, 101.0), 5f)
        assertEquals(listOf(fig), monitor.onPosition("other", stations, GeoPoint(14.0, 101.0), 5f))
        monitor.reset()
        assertEquals(listOf(fig), monitor.onPosition("other", stations, GeoPoint(14.0, 101.0), 5f))
    }
}
