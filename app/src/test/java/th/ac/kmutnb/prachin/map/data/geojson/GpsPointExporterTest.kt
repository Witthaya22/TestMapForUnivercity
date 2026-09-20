package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsPoint
import java.util.TimeZone

/**
 * The exported file is the whole point of the GPS point log, and it is written once and
 * read months later, so its shape is pinned here rather than checked by eye after a walk.
 */
class GpsPointExporterTest {

    private val bangkok: TimeZone = TimeZone.getTimeZone("Asia/Bangkok")

    private val bom = "\uFEFF"

    private val newline = "\r\n"

    /** 2026-09-20T14:32:05+07:00. */
    private val recordedAt = 1_789_889_525_000L

    private fun samplePoint(
        code: String = "P001",
        note: String = "",
        elevation: Double? = 61.4,
        captureMode: GpsCaptureMode = GpsCaptureMode.MAP,
    ) = GpsPoint(
        id = "gps_$code",
        code = code,
        point = GeoPoint(lat = 14.1610243, lon = 101.3529617),
        accuracyMeters = 4.25f,
        elevationMeters = elevation,
        verticalAccuracyMeters = 8.5f,
        satellitesUsed = 11,
        satellitesVisible = 23,
        sampleCount = 30,
        rejectedCount = 2,
        spreadMeters = 3.44,
        durationSeconds = 31,
        note = note,
        recordedAt = recordedAt,
        captureMode = captureMode,
    )

    @Test
    fun `a feature carries the measurement and nothing about the campus`() {
        val json = GpsPointExporter.writeGeoJson(listOf(samplePoint()), bangkok)
        val feature = JsonParser.parseString(json).asJsonObject
            .getAsJsonArray("features")[0].asJsonObject
        val properties = feature.getAsJsonObject("properties")

        assertEquals("P001", properties["code"].asString)
        assertEquals(4.3, properties["accuracyMeters"].asDouble, 1e-9)
        assertEquals(61.4, properties["elevationMeters"].asDouble, 1e-9)
        assertEquals(8.5, properties["verticalAccuracyMeters"].asDouble, 1e-9)
        assertEquals(11, properties["satellitesUsed"].asInt)
        assertEquals(23, properties["satellitesVisible"].asInt)
        assertEquals(30, properties["sampleCount"].asInt)
        assertEquals(2, properties["rejectedCount"].asInt)
        assertEquals(3.4, properties["spreadMeters"].asDouble, 1e-9)
        assertEquals(31, properties["durationSeconds"].asInt)
        assertEquals("2026-09-20T14:32:05+07:00", properties["recordedAt"].asString)
        assertEquals("map", properties["captureMode"].asString)

        // Nothing that describes a place may leak into a file of measurements.
        listOf("category", "name", "description", "icon", "order").forEach { forbidden ->
            assertFalse("$forbidden must not be exported", properties.has(forbidden))
        }
    }

    @Test
    fun `coordinates are lon lat, in that order`() {
        val json = GpsPointExporter.writeGeoJson(listOf(samplePoint()), bangkok)
        val geometry = JsonParser.parseString(json).asJsonObject
            .getAsJsonArray("features")[0].asJsonObject
            .getAsJsonObject("geometry")

        assertEquals("Point", geometry["type"].asString)
        val coordinates = geometry.getAsJsonArray("coordinates")
        // RFC 7946 is [lon, lat]; at Prachinburi the longitude is the 101, not the 14.
        assertEquals(101.3529617, coordinates[0].asDouble, 1e-9)
        assertEquals(14.1610243, coordinates[1].asDouble, 1e-9)
    }

    @Test
    fun `a missing altitude is an explicit null, not a missing key`() {
        val json = GpsPointExporter.writeGeoJson(listOf(samplePoint(elevation = null)), bangkok)
        val properties = JsonParser.parseString(json).asJsonObject
            .getAsJsonArray("features")[0].asJsonObject
            .getAsJsonObject("properties")

        assertTrue(properties.has("elevationMeters"))
        assertTrue(properties["elevationMeters"].isJsonNull)
    }

    @Test
    fun `an empty log still writes a valid FeatureCollection`() {
        val root = JsonParser.parseString(GpsPointExporter.writeGeoJson(emptyList(), bangkok))
            .asJsonObject
        assertEquals("FeatureCollection", root["type"].asString)
        assertEquals(0, root.getAsJsonArray("features").size())
    }

    @Test
    fun `the CSV row matches the header, column for column`() {
        val csv = GpsPointExporter.writeCsv(listOf(samplePoint()), bangkok)
        val lines = csv.removePrefix(bom).split(newline)

        assertEquals(GpsPointExporter.CSV_HEADER.joinToString(","), lines[0])
        assertEquals(
            "P001,14.1610243,101.3529617,4.3,61.4,8.5,11,23,30,2,3.4,31," +
                "2026-09-20T14:32:05+07:00,map,",
            lines[1],
        )
    }

    @Test
    fun `the CSV starts with a BOM so Excel reads a Thai note`() {
        val csv = GpsPointExporter.writeCsv(listOf(samplePoint(note = "หน้าอาคาร")), bangkok)
        assertTrue(csv.startsWith(bom))
        assertTrue(csv.contains("หน้าอาคาร"))
    }

    @Test
    fun `a note containing a comma or a quote cannot break the row`() {
        val note = "ริมถนน, ใกล้ป้าย \"A\""
        val csv = GpsPointExporter.writeCsv(listOf(samplePoint(note = note)), bangkok)
        val columns = splitCsv(csv.removePrefix(bom).split(newline)[1])

        // Every column is still there, so the embedded comma stayed inside its quoted
        // field, and the quote survived doubling and un-doubling unchanged.
        assertEquals(GpsPointExporter.CSV_HEADER.size, columns.size)
        assertEquals(note, columns.last())
    }

    @Test
    fun `a missing value leaves the column empty rather than shifting the row`() {
        val csv = GpsPointExporter.writeCsv(listOf(samplePoint(elevation = null)), bangkok)
        val columns = splitCsv(csv.removePrefix(bom).split(newline)[1])

        assertEquals(GpsPointExporter.CSV_HEADER.size, columns.size)
        assertEquals("", columns[GpsPointExporter.CSV_HEADER.indexOf("elevationMeters")])
    }

    @Test
    fun `the screen a reading was taken on travels with it`() {
        val readout = samplePoint(captureMode = GpsCaptureMode.READOUT)

        val properties = JsonParser.parseString(GpsPointExporter.writeGeoJson(listOf(readout), bangkok))
            .asJsonObject
            .getAsJsonArray("features")[0].asJsonObject
            .getAsJsonObject("properties")
        assertEquals("readout", properties["captureMode"].asString)

        val columns = splitCsv(
            GpsPointExporter.writeCsv(listOf(readout), bangkok)
                .removePrefix(bom)
                .split(newline)[1],
        )
        assertEquals("readout", columns[GpsPointExporter.CSV_HEADER.indexOf("captureMode")])
    }

    @Test
    fun `the file name carries the moment of export`() {
        assertEquals(
            "gps_points_20260920_1432.geojson",
            GpsPointExporter.defaultFileName("geojson", recordedAt, bangkok),
        )
    }

    /** Minimal RFC 4180 splitter, enough to prove the writer's quoting. */
    private fun splitCsv(line: String): List<String> {
        val columns = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes && c == '"' && line.getOrNull(i + 1) == '"' -> {
                    current.append('"')
                    i++
                }

                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    columns += current.toString()
                    current.setLength(0)
                }

                else -> current.append(c)
            }
            i++
        }
        columns += current.toString()
        return columns
    }
}
