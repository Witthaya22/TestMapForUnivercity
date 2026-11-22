package th.ac.kmutnb.prachin.map.data.khaoyai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import java.io.File

class TrailScheduleTest {

    private val trail = Trail(
        id = "t", name = "t", emoji = "", photo = "", distanceKm = 8.0, durationMinutes = 360,
        difficulty = TrailDifficulty.HARD, startPoint = "", endPoint = "", summary = "", description = "",
        stations = emptyList(), needsGuide = true, isOpen = true, openNote = "",
        trailhead = GeoPoint(14.43919, 101.37232), path = emptyList(), entryRadiusMeters = 1000, demoUnlocked = false, sourceUrl = "",
        startFrom = "08:00", startUntil = "10:00", seasonClosedFrom = "07-01", seasonClosedUntil = "08-31",
    )

    private fun at(month: Int, day: Int, h: Int, m: Int) = TrailSchedule.state(trail, month, day, h * 60 + m)

    @Test
    fun `trail 6 starts between eight and ten`() {
        assertEquals(TrailOpenState.NOT_YET, at(11, 23, 7, 59))
        assertEquals(TrailOpenState.OPEN_NOW, at(11, 23, 8, 0))
        assertEquals(TrailOpenState.OPEN_NOW, at(11, 23, 10, 0))
        assertEquals(TrailOpenState.ENDED_TODAY, at(11, 23, 10, 1))
    }

    @Test
    fun `and is shut from the first of July to the end of August`() {
        assertEquals(TrailOpenState.OPEN_NOW, at(6, 30, 9, 0))
        assertEquals(TrailOpenState.SEASON_CLOSED, at(7, 1, 9, 0))
        assertEquals(TrailOpenState.SEASON_CLOSED, at(8, 31, 9, 0))
        assertEquals(TrailOpenState.OPEN_NOW, at(9, 1, 9, 0))
    }

    @Test
    fun `a closure over New Year wraps round`() {
        val winter = trail.copy(seasonClosedFrom = "12-15", seasonClosedUntil = "01-15")
        assertEquals(TrailOpenState.SEASON_CLOSED, TrailSchedule.state(winter, 12, 31, 540))
        assertEquals(TrailOpenState.SEASON_CLOSED, TrailSchedule.state(winter, 1, 10, 540))
        assertEquals(TrailOpenState.OPEN_NOW, TrailSchedule.state(winter, 2, 1, 540))
    }

    @Test
    fun `no limits means always open, and closed beats everything`() {
        val open = trail.copy(startFrom = "", startUntil = "", seasonClosedFrom = "", seasonClosedUntil = "")
        assertEquals(TrailOpenState.OPEN_NOW, TrailSchedule.state(open, 7, 15, 23 * 60))
        assertEquals(TrailOpenState.CLOSED, TrailSchedule.state(trail.copy(isOpen = false), 11, 23, 540))
        assertNull(TrailSchedule.minutes("25:00"))
        assertEquals(480, TrailSchedule.minutes("8.00"))
    }

    @Test
    fun `a real trail outside its hours cannot be started even at the trailhead`() {
        val access = TrailAccessRules.evaluate(trail, trail.trailhead, TrailOpenState.ENDED_TODAY)
        assertTrue(access is TrailAccess.OutsideHours)
        // The demo trail ignores the clock.
        assertEquals(TrailAccess.Demo, TrailAccessRules.evaluate(trail.copy(demoUnlocked = true), null, TrailOpenState.ENDED_TODAY))
    }

    @Test
    fun `the shipped trails carry the park's hours and the demo has a line with its stations on it`() {
        val file = sequenceOf(File("src/main/assets/data/khaoyai_content.json"), File("app/src/main/assets/data/khaoyai_content.json")).first { it.isFile }
        val trails = KhaoYaiContentJson.parse(file.readText(Charsets.UTF_8)).trails
        trails.filterNot { it.demoUnlocked }.forEach { assertEquals(it.id, "08:00", it.startFrom) }
        assertEquals("10:00", trails.first { it.id == "trail_6_visitor_center_haew_suwat" }.startUntil)
        val demo = trails.first { it.demoUnlocked }
        assertTrue(demo.path.size >= 2)
        demo.stations.forEach { s ->
            assertTrue(s.id, th.ac.kmutnb.prachin.map.core.geo.GeoUtils.distanceToPolylineMeters(s.point!!, demo.path) < 4.0)
        }
    }
}
