package th.ac.kmutnb.prachin.map.ui.khaoyai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
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
        val ok = TrailDraft(name = "ทดลอง", distanceKm = "1,5", durationMinutes = "40", stations = " ก \n\n ข ")
        val trail = ok.toTrail("t1")!!
        assertEquals(1.5, trail.distanceKm, 0.0)
        assertEquals(listOf("ก", "ข"), trail.stations)
        assertEquals("🌿", trail.emoji)

        assertNull(ok.copy(name = " ").toTrail("t1"))
        assertNull(ok.copy(distanceKm = "abc").toTrail("t1"))
        assertNull(ok.copy(distanceKm = "0").toTrail("t1"))
        assertNull(ok.copy(durationMinutes = "1.5").toTrail("t1"))
    }

    @Test
    fun `editing a trail without changes gives the same trail back`() {
        val trail = Trail(
            id = "t", name = "กองแก้ว", emoji = "💧", distanceKm = 1.2, durationMinutes = 40,
            difficulty = TrailDifficulty.MODERATE, startPoint = "ศูนย์", summary = "s", description = "d",
            stations = listOf("a", "b"), needsGuide = true, isOpen = false,
        )
        assertEquals(trail, TrailDraft.from(trail).toTrail("t"))
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
