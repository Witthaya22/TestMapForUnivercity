package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import java.util.TimeZone

/**
 * The exported track file is the whole output of mapping somewhere that has no map, so its
 * shape is pinned here rather than checked by eye after a walk.
 */
class TrackLogExporterTest {

    private val bangkok: TimeZone = TimeZone.getTimeZone("Asia/Bangkok")

    private fun track(
        code: String = "T001",
        note: String = "ทางไปน้ำตก",
        usedForRouting: Boolean = true,
        captureMode: TrackCaptureMode = TrackCaptureMode.MAP,
    ) = TrackLog(
        id = "track_1",
        code = code,
        points = listOf(
            GeoPoint(lat = 14.1610243, lon = 101.3529617),
            GeoPoint(lat = 14.1612243, lon = 101.3531617),
        ),
        lengthMeters = 31.24,
        averageAccuracyMeters = 7.46f,
        worstAccuracyMeters = 13.2f,
        satellitesUsed = 9,
        satellitesVisible = 17,
        fixCount = 42,
        rejectedCount = 3,
        durationSeconds = 48,
        note = note,
        isUsedForRouting = usedForRouting,
        recordedAt = 1_789_889_525_000L,
        captureMode = captureMode,
    )

    private fun properties(json: String) = JsonParser.parseString(json)
        .asJsonObject
        .getAsJsonArray("features")[0].asJsonObject
        .getAsJsonObject("properties")

    @Test
    fun `a track is written as a LineString, lon first`() {
        val geometry = JsonParser.parseString(TrackLogExporter.writeGeoJson(listOf(track())))
            .asJsonObject
            .getAsJsonArray("features")[0].asJsonObject
            .getAsJsonObject("geometry")

        assertEquals("LineString", geometry["type"].asString)
        val first = geometry.getAsJsonArray("coordinates")[0].asJsonArray
        assertEquals(101.3529617, first[0].asDouble, 1e-9)
        assertEquals(14.1610243, first[1].asDouble, 1e-9)
    }

    @Test
    fun `every measured value reaches the file under its own name`() {
        val properties = properties(TrackLogExporter.writeGeoJson(listOf(track()), bangkok))

        assertEquals("T001", properties["code"].asString)
        assertEquals(31.2, properties["lengthMeters"].asDouble, 1e-9)
        assertEquals(2, properties["vertexCount"].asInt)
        assertEquals(7.5, properties["averageAccuracyMeters"].asDouble, 1e-9)
        assertEquals(13.2, properties["worstAccuracyMeters"].asDouble, 1e-9)
        assertEquals(9, properties["satellitesUsed"].asInt)
        assertEquals(17, properties["satellitesVisible"].asInt)
        assertEquals(42, properties["fixCount"].asInt)
        assertEquals(3, properties["rejectedCount"].asInt)
        assertEquals(48, properties["durationSeconds"].asInt)
        assertEquals("map", properties["captureMode"].asString)
        assertTrue(properties["usedForRouting"].asBoolean)
        assertEquals("ทางไปน้ำตก", properties["note"].asString)
    }

    @Test
    fun `nothing about the campus leaks into a measurement`() {
        val properties = properties(TrackLogExporter.writeGeoJson(listOf(track())))

        // The whole reason this log is separate from the POI surveyor: a track says where
        // somebody walked, never what stands there.
        listOf("name", "category", "faculty", "building", "type", "oneway", "lit", "covered")
            .forEach { key -> assertFalse(key, properties.has(key)) }
    }

    @Test
    fun `the timestamp is ISO 8601 with the offset spelled out`() {
        val properties = properties(TrackLogExporter.writeGeoJson(listOf(track()), bangkok))

        assertEquals("2026-09-20T14:32:05+07:00", properties["recordedAt"].asString)
    }

    @Test
    fun `the CSV header matches the columns that are written`() {
        val lines = TrackLogExporter.writeCsv(listOf(track()), bangkok).trimEnd().split("\r\n")

        assertEquals(2, lines.size)
        assertEquals("﻿" + TrackLogExporter.CSV_HEADER.joinToString(","), lines[0])
        // A Thai note holds no comma, but the geometry column is quoted because WKT does.
        assertEquals(TrackLogExporter.CSV_HEADER.size, splitCsv(lines[1]).size)
    }

    @Test
    fun `the CSV carries the geometry as WKT so it still opens as a map`() {
        val row = splitCsv(
            TrackLogExporter.writeCsv(listOf(track()), bangkok).trimEnd().split("\r\n")[1],
        )

        assertEquals(
            "LINESTRING(101.3529617 14.1610243, 101.3531617 14.1612243)",
            row.last(),
        )
    }

    @Test
    fun `a track switched off for routing says so rather than disappearing`() {
        val properties = properties(
            TrackLogExporter.writeGeoJson(listOf(track(usedForRouting = false))),
        )

        assertFalse(properties["usedForRouting"].asBoolean)
    }

    @Test
    fun `an empty log writes a valid, empty FeatureCollection`() {
        val root = JsonParser.parseString(TrackLogExporter.writeGeoJson(emptyList())).asJsonObject

        assertEquals("FeatureCollection", root["type"].asString)
        assertEquals(0, root.getAsJsonArray("features").size())
    }

    @Test
    fun `the file name carries when the export was made`() {
        val name = TrackLogExporter.defaultFileName(
            extension = "geojson",
            millis = 1_789_889_525_000L,
            timeZone = bangkok,
        )

        assertEquals("tracks_20260920_1432.geojson", name)
    }

    @Test
    fun `a file holding one track is named after it`() {
        // Otherwise a folder of single-track exports is a row of identical names, and the
        // whole point of exporting one track is to be able to find it again.
        val name = TrackLogExporter.defaultFileName(
            extension = "csv",
            code = "T002",
            millis = 1_789_889_525_000L,
            timeZone = bangkok,
        )

        assertEquals("track_T002_20260920_1432.csv", name)
    }

    /** Minimal RFC 4180 splitter, enough to check what this writer produces. */
    private fun splitCsv(line: String): List<String> {
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                quoted && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }

                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> {
                    cells += cell.toString()
                    cell.setLength(0)
                }

                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString()
        return cells
    }
}
