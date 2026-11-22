package th.ac.kmutnb.prachin.map.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.EmergencyPlaceKind
import th.ac.kmutnb.prachin.map.data.model.EmergencyPlaces
import th.ac.kmutnb.prachin.map.data.model.ReportPeriod
import th.ac.kmutnb.prachin.map.data.model.Reports
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import java.io.File
import java.util.Calendar
import java.util.TimeZone

class ReportsTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private fun time(y: Int, m: Int, d: Int, h: Int = 12) = Calendar.getInstance(utc).apply {
        clear()
        set(y, m - 1, d, h, 0)
    }.timeInMillis

    private fun call(at: Long, category: SosCategory, status: SosStatus = SosStatus.RESOLVED) =
        SosReport("id$at$category", at, username = "a, \"b\"", point = null, accuracyMeters = null, category = category, message = "", trailName = "", status = status)

    private val now = time(2026, 11, 23)
    private val calls = listOf(
        call(time(2026, 11, 23, 9), SosCategory.LOST, SosStatus.NEW),
        call(time(2026, 11, 22), SosCategory.LOST),
        call(time(2026, 10, 5), SosCategory.ACCIDENT),
        call(time(2025, 3, 1), SosCategory.WILDLIFE),
    )

    @Test
    fun `counts by type and status, and the type called most`() {
        assertEquals(2, Reports.byCategory(calls)[SosCategory.LOST])
        assertEquals(0, Reports.byCategory(calls)[SosCategory.POLICE])
        assertEquals(SosCategory.LOST to 2, Reports.mostFrequent(calls))
        assertNull(Reports.mostFrequent(emptyList()))
        assertEquals(1, Reports.byStatus(calls)[SosStatus.NEW])
    }

    @Test
    fun `timelines by day, month and year include the empty periods`() {
        val days = Reports.timeline(calls, ReportPeriod.DAY, now, buckets = 3, timeZone = utc)
        assertEquals(listOf(0, 1, 1), days.map { it.count })
        assertEquals(23, days.last().day)

        val months = Reports.timeline(calls, ReportPeriod.MONTH, now, buckets = 3, timeZone = utc)
        assertEquals(listOf(9 to 0, 10 to 1, 11 to 2), months.map { it.month to it.count })

        val years = Reports.timeline(calls, ReportPeriod.YEAR, now, buckets = 2, timeZone = utc)
        assertEquals(listOf(2025 to 1, 2026 to 3), years.map { it.year to it.count })
        assertEquals(1, Reports.today(calls, now, utc))
    }

    @Test
    fun `the CSV quotes every field so commas and quotes in names survive`() {
        val csv = Reports.csv(calls.take(1), { it.name }, { it.name })
        assertTrue(csv.lines()[1].contains("\"a, \"\"b\"\"\""))
    }

    @Test
    fun `from the Khao Yai visitor centre the nearest hospital and police station are found offline`() {
        val file = sequenceOf(File("src/main/assets/data/emergency_places.json"), File("app/src/main/assets/data/emergency_places.json")).first { it.isFile }
        val places = EmergencyPlaces.parse(file.readText(Charsets.UTF_8))
        val visitorCentre = GeoPoint(14.43919, 101.37232)
        val hospital = EmergencyPlaces.nearest(places, EmergencyPlaceKind.HOSPITAL, visitorCentre).first()
        assertEquals("โรงพยาบาลกรุงเทพเขาใหญ่", hospital.place.name)
        assertEquals(24_000.0, hospital.distanceMeters, 1_500.0)
        val police = EmergencyPlaces.nearest(places, EmergencyPlaceKind.POLICE, visitorCentre).first()
        assertEquals("สถานีตำรวจภูธรหมูสี", police.place.name)
        assertTrue(EmergencyPlaces.nearest(places, EmergencyPlaceKind.HOSPITAL, visitorCentre, count = 2).let { it[0].distanceMeters <= it[1].distanceMeters })
    }
}
