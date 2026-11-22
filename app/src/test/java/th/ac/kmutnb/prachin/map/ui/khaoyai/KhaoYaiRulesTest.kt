package th.ac.kmutnb.prachin.map.ui.khaoyai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.LatLonDraft
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.StationDraft
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailDifficulty
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.NewsDraft
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.SpeciesDraft
import th.ac.kmutnb.prachin.map.ui.khaoyai.admin.TrailDraft

class KhaoYaiRulesTest {

    @Test
    fun `admin in any letter case signs in as admin, anything else as a visitor`() {
        assertTrue(KhaoYaiAuth.signIn("admin").isAdmin)
        assertTrue(KhaoYaiAuth.signIn("Admin").isAdmin)
        assertTrue(KhaoYaiAuth.signIn("  ADMIN ").isAdmin)
        assertFalse(KhaoYaiAuth.signIn("").isAdmin)
        assertFalse(KhaoYaiAuth.signIn("administrator").isAdmin)
        assertEquals("Admin", KhaoYaiAuth.signIn(" Admin ").username)
    }

    @Test
    fun `dates show in Thai with the Buddhist year, anything else as typed`() {
        val months = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
        assertEquals("18 พ.ย. 2569", KhaoYaiFormats.thaiShortDate("2026-11-18", months))
        assertEquals("1 ม.ค. 2570", KhaoYaiFormats.thaiShortDate("2027-01-01", months))
        assertEquals("2026-13-01", KhaoYaiFormats.thaiShortDate("2026-13-01", months))
        assertEquals("เร็ว ๆ นี้", KhaoYaiFormats.thaiShortDate("เร็ว ๆ นี้", months))
    }

    @Test
    fun `a trail form saves only with a name, a distance and a time`() {
        val ok = TrailDraft(name = "ทดลอง", distanceKm = "1,5", durationMinutes = "40")
        val trail = ok.toTrail("t1")!!
        assertEquals(1.5, trail.distanceKm, 0.0)
        assertEquals("🌿", trail.emoji)
        assertNull(trail.trailhead)
        assertEquals(1_000, trail.entryRadiusMeters)
        assertFalse(trail.demoUnlocked)

        assertNull(ok.copy(name = " ").toTrail("t1"))
        assertNull(ok.copy(distanceKm = "abc").toTrail("t1"))
        assertNull(ok.copy(distanceKm = "0").toTrail("t1"))
        assertNull(ok.copy(durationMinutes = "1.5").toTrail("t1"))
        assertNull(ok.copy(entryRadiusMeters = "0").toTrail("t1"))
        // One bad station holds the whole trail back rather than vanishing on save.
        assertNull(ok.copy(stations = listOf(StationDraft(name = ""))).toTrail("t1"))
    }

    @Test
    fun `a point is both fields or neither, on the globe, with either decimal mark`() {
        assertTrue(LatLonDraft().isBlank)
        assertFalse(LatLonDraft().isInvalid)
        assertEquals(GeoPoint(14.4383, 101.3725), LatLonDraft("14,4383", "101.3725").point)
        assertTrue(LatLonDraft("14.43", "").isInvalid)
        assertTrue(LatLonDraft("95", "101").isInvalid)
        assertTrue(LatLonDraft("abc", "101").isInvalid)
        assertNull(TrailDraft(name = "x", distanceKm = "1", durationMinutes = "1", trailhead = LatLonDraft("14", "")).toTrail("t"))
    }

    @Test
    fun `a station needs a name and a radius, a point is optional`() {
        assertEquals(null, StationDraft(name = "ต้นไทร").toStation()!!.point)
        assertEquals(40, StationDraft(name = "ต้นไทร", radiusMeters = "40").toStation()!!.radiusMeters)
        assertNull(StationDraft(name = "ต้นไทร", radiusMeters = "0").toStation())
        assertNull(StationDraft(name = " ").toStation())
    }

    @Test
    fun `editing a trail without changes gives the same trail back`() {
        val trail = Trail(
            id = "t", name = "กองแก้ว", emoji = "💧", photo = "photos/x.jpg", distanceKm = 1.2, durationMinutes = 40,
            difficulty = TrailDifficulty.MODERATE, startPoint = "ศูนย์", endPoint = "น้ำตก", summary = "s", description = "d",
            stations = listOf(
                TrailStation("s1", "ต้นไทร", "🌳", "เรื่อง", GeoPoint(14.4, 101.3), 30, "photos/y.jpg"),
                TrailStation("s2", "หิน", "", "", null),
            ),
            needsGuide = true, isOpen = false, openNote = "08:00–14:00", trailhead = GeoPoint(14.438341, 101.372523),
            path = listOf(GeoPoint(14.1, 101.1), GeoPoint(14.2, 101.2)), entryRadiusMeters = 500, demoUnlocked = true,
            sourceUrl = "https://example.org",
        )
        // The form does not edit the line; the caller hands it back in.
        assertEquals(trail, TrailDraft.from(trail).toTrail("t", trail.path))
    }

    @Test
    fun `news needs a title and an ISO date, species a name`() {
        assertTrue(NewsDraft(title = "x", date = "2026-11-22").isValid)
        assertFalse(NewsDraft(title = "x", date = "22/11/2569").isValid)
        assertFalse(NewsDraft(title = "", date = "2026-11-22").isValid)
        assertFalse(SpeciesDraft(name = " ").isValid)
        assertEquals("🐾", SpeciesDraft(name = "เสือ").toSpecies("s")!!.emoji)
    }
}
