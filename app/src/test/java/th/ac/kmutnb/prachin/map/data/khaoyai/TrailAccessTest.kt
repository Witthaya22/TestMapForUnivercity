package th.ac.kmutnb.prachin.map.data.khaoyai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

class TrailAccessTest {

    // About 111 m per 0.001 degree of latitude.
    private val head = GeoPoint(14.4380, 101.3720)
    private val line = listOf(head, GeoPoint(14.4480, 101.3720)) // 1.1 km due north

    private val trail = Trail(
        id = "t", name = "t", emoji = "", photo = "", distanceKm = 1.1, durationMinutes = 30,
        difficulty = TrailDifficulty.EASY, startPoint = "", endPoint = "", summary = "", description = "",
        stations = emptyList(), needsGuide = false, isOpen = true, openNote = "",
        trailhead = head, path = line, entryRadiusMeters = 1_000, demoUnlocked = false, sourceUrl = "",
    )

    @Test
    fun `the demo trail starts from anywhere, even with no fix`() {
        val demo = trail.copy(demoUnlocked = true)
        assertEquals(TrailAccess.Demo, TrailAccessRules.evaluate(demo, null))
        assertTrue(TrailAccessRules.evaluate(demo, GeoPoint(13.75, 100.5)).canStart)
    }

    @Test
    fun `a closed trail stays shut wherever the visitor stands`() {
        assertEquals(TrailAccess.Closed, TrailAccessRules.evaluate(trail.copy(isOpen = false), head))
    }

    @Test
    fun `no fix means no decision yet, not a refusal`() {
        val access = TrailAccessRules.evaluate(trail, null)
        assertEquals(TrailAccess.NoLocation, access)
        assertFalse(access.canStart)
    }

    @Test
    fun `within the radius starts, beyond it is locked and says how far`() {
        val near = TrailAccessRules.evaluate(trail, GeoPoint(14.4380, 101.3780)) // ~650 m east
        assertTrue(near is TrailAccess.Inside && near.canStart)

        val far = TrailAccessRules.evaluate(trail, GeoPoint(14.4380, 101.3850)) // ~1.4 km east
        assertTrue(far is TrailAccess.TooFar)
        far as TrailAccess.TooFar
        assertFalse(far.canStart)
        assertEquals(1_400.0, far.distanceMeters, 50.0)
        assertEquals(1_000, far.radiusMeters)
    }

    @Test
    fun `joining half way along the line counts, not only the trailhead`() {
        // 1.2 km from the trailhead but 30 m from the middle of the line.
        val beside = GeoPoint(14.4488, 101.3723)
        assertTrue(TrailAccessRules.evaluate(trail.copy(entryRadiusMeters = 100), beside).canStart)
        assertFalse(TrailAccessRules.evaluate(trail.copy(path = emptyList(), entryRadiusMeters = 100), beside).canStart)
    }

    @Test
    fun `a trail nobody has mapped cannot be measured, so it is not locked`() {
        val unmapped = trail.copy(trailhead = null, path = emptyList())
        assertEquals(TrailAccess.Unmapped, TrailAccessRules.evaluate(unmapped, head))
        assertEquals(null, TrailAccessRules.destination(unmapped))
        assertEquals(head, TrailAccessRules.destination(trail))
    }

    @Test
    fun `the shipped real trails are all locked from Bangkok`() {
        val bangkok = GeoPoint(13.7563, 100.5018)
        val file = sequenceOf(
            java.io.File("src/main/assets/data/khaoyai_content.json"),
            java.io.File("app/src/main/assets/data/khaoyai_content.json"),
        ).first { it.isFile }
        val trails = KhaoYaiContentJson.parse(file.readText(Charsets.UTF_8)).trails
        trails.filterNot { it.demoUnlocked }.forEach {
            assertTrue(it.id, TrailAccessRules.evaluate(it, bangkok) is TrailAccess.TooFar)
        }
    }
}
