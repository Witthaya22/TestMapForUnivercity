package th.ac.kmutnb.prachin.map.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toReport
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosRules
import th.ac.kmutnb.prachin.map.data.model.SosStatus

class SosReportTest {

    private val report = SosReport(
        id = "sos_1", createdAt = 1_000L, username = "นักเดินป่า", point = GeoPoint(14.438341, 101.372523),
        accuracyMeters = 6.4f, category = SosCategory.ACCIDENT, message = "ข้อเท้าพลิก", trailName = "กองแก้ว",
    )

    @Test
    fun `a report survives the table and back, with and without a fix`() {
        assertEquals(report, report.toEntity().toReport())
        val blind = report.copy(point = null, accuracyMeters = null)
        assertEquals(blind, blind.toEntity().toReport())
        assertNull(blind.toEntity().lat)
    }

    @Test
    fun `an unknown category or status read back is not a crash`() {
        val odd = report.toEntity().copy(category = "meteor", status = "lost-in-time").toReport()
        assertEquals(SosCategory.OTHER, odd.category)
        assertEquals(SosStatus.NEW, odd.status)
    }

    @Test
    fun `the shared text carries what, where, how sure, and a map link`() {
        val text = SosRules.shareText(report, "บาดเจ็บ", "SOS", "no fix")
        assertTrue(text.startsWith("SOS"))
        assertTrue(text.contains("บาดเจ็บ"))
        assertTrue(text.contains("ข้อเท้าพลิก"))
        assertTrue(text.contains("14.438341, 101.372523"))
        assertTrue(text.contains("±6 m"))
        assertTrue(text.contains("https://maps.google.com/?q=14.438341,101.372523"))

        val blind = SosRules.shareText(report.copy(point = null), "บาดเจ็บ", "SOS", "no fix")
        assertTrue(blind.contains("no fix"))
        assertFalse(blind.contains("maps.google"))
    }

    @Test
    fun `new calls come first, then taken ones, newest first within each`() {
        val oldNew = report.copy(id = "a", createdAt = 1)
        val newNew = report.copy(id = "b", createdAt = 5)
        val taken = report.copy(id = "c", createdAt = 9, status = SosStatus.ACKNOWLEDGED)
        val done = report.copy(id = "d", createdAt = 10, status = SosStatus.RESOLVED)
        assertEquals(listOf("b", "a", "c", "d"), SosRules.triage(listOf(done, oldNew, taken, newNew)).map { it.id })
    }
}
