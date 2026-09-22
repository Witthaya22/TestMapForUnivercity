package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes the walked-path log out as GeoJSON and as CSV.
 *
 * The line counterpart of [GpsPointExporter], and deliberately the same shape: the same
 * timestamp format, the same rounding, the same rule that what leaves here is a set of
 * **measurements**. No path type, no name of a building, nothing about what the path is
 * for - a track says where somebody walked and how good the signal was while they did.
 *
 * This is the file that matters for making a map of somewhere that has none. It carries
 * absolute WGS84 coordinates, so whatever basemap is put under it later - satellite
 * imagery, OSM, a scan, or nothing at all - the line lands where the walk happened.
 *
 * Pure Gson and `java.text`, with no Android types, so the output is unit tested rather
 * than inspected by eye after a walk.
 */
object TrackLogExporter {

    /** Seven decimals is about a centimetre - far finer than any fix, and never the limit. */
    private const val COORDINATE_DECIMALS = 7

    /** One decimal on metres: a receiver that claims 4.83 m is not that sure. */
    private const val METER_DECIMALS = 1

    val CSV_HEADER: List<String> = listOf(
        "code",
        "lengthMeters",
        "vertexCount",
        "averageAccuracyMeters",
        "worstAccuracyMeters",
        "satellitesUsed",
        "satellitesVisible",
        "fixCount",
        "rejectedCount",
        "durationSeconds",
        "recordedAt",
        "captureMode",
        "usedForRouting",
        "note",
        "wkt",
    )

    // ----------------------------------------------------------------------------------
    // GeoJSON
    // ----------------------------------------------------------------------------------

    /** A `FeatureCollection` of `LineString` features, RFC 7946, ready for QGIS or umap. */
    fun writeGeoJson(tracks: List<TrackLog>, timeZone: TimeZone = TimeZone.getDefault()): String {
        val features = JsonArray()
        tracks.forEach { track ->
            val coordinates = JsonArray()
            track.points.forEach { point ->
                val pair = JsonArray()
                // GeoJSON is [lon, lat]; GeoUtils owns that ordering.
                GeoUtils.toGeoJsonCoordinates(point).forEach {
                    pair.add(round(it, COORDINATE_DECIMALS))
                }
                coordinates.add(pair)
            }
            val geometry = JsonObject().apply {
                addProperty("type", "LineString")
                add("coordinates", coordinates)
            }
            features.add(
                JsonObject().apply {
                    addProperty("type", "Feature")
                    add("geometry", geometry)
                    add("properties", propertiesOf(track, timeZone))
                },
            )
        }
        return JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", features)
        }.toString()
    }

    private fun propertiesOf(track: TrackLog, timeZone: TimeZone): JsonObject = JsonObject().apply {
        addProperty("code", track.code)
        addProperty("lengthMeters", round(track.lengthMeters, METER_DECIMALS))
        addProperty("vertexCount", track.vertexCount)
        addProperty(
            "averageAccuracyMeters",
            round(track.averageAccuracyMeters.toDouble(), METER_DECIMALS),
        )
        addProperty(
            "worstAccuracyMeters",
            round(track.worstAccuracyMeters.toDouble(), METER_DECIMALS),
        )
        addProperty("satellitesUsed", track.satellitesUsed)
        addProperty("satellitesVisible", track.satellitesVisible)
        addProperty("fixCount", track.fixCount)
        addProperty("rejectedCount", track.rejectedCount)
        addProperty("durationSeconds", track.durationSeconds)
        addProperty("recordedAt", formatTimestamp(track.recordedAt, timeZone))
        addProperty("captureMode", track.captureMode.id)
        addProperty("usedForRouting", track.isUsedForRouting)
        addProperty("note", track.note)
    }

    // ----------------------------------------------------------------------------------
    // CSV
    // ----------------------------------------------------------------------------------

    /**
     * One row per track, geometry in the last column as WKT `LINESTRING(lon lat, ...)`.
     *
     * Most of the people who need this file open it in Excel rather than in a GIS, and one
     * row per track is the only shape that reads as a survey there - a row per vertex would
     * repeat every measurement hundreds of times. WKT keeps it a map anyway: QGIS's
     * delimited-text import reads this column directly.
     *
     * Starts with a UTF-8 byte order mark and uses CRLF line endings: without the mark,
     * Excel on Windows reads a Thai note as mojibake, and it is the note that carries
     * whatever the walker could not express in a number.
     */
    fun writeCsv(tracks: List<TrackLog>, timeZone: TimeZone = TimeZone.getDefault()): String {
        val builder = StringBuilder()
        builder.append(UTF8_BOM)
        builder.append(CSV_HEADER.joinToString(",")).append(CRLF)

        tracks.forEach { track ->
            val row = listOf(
                track.code,
                decimal(track.lengthMeters, METER_DECIMALS),
                track.vertexCount.toString(),
                decimal(track.averageAccuracyMeters.toDouble(), METER_DECIMALS),
                decimal(track.worstAccuracyMeters.toDouble(), METER_DECIMALS),
                track.satellitesUsed.toString(),
                track.satellitesVisible.toString(),
                track.fixCount.toString(),
                track.rejectedCount.toString(),
                track.durationSeconds.toString(),
                formatTimestamp(track.recordedAt, timeZone),
                track.captureMode.id,
                if (track.isUsedForRouting) "true" else "false",
                track.note,
                wktOf(track),
            )
            builder.append(row.joinToString(",") { escapeCsv(it) }).append(CRLF)
        }
        return builder.toString()
    }

    /** `LINESTRING(101.3529617 14.1610243, ...)` - WKT is lon then lat, space separated. */
    fun wktOf(track: TrackLog): String = track.points.joinToString(
        separator = ", ",
        prefix = "LINESTRING(",
        postfix = ")",
    ) { point ->
        "${decimal(point.lon, COORDINATE_DECIMALS)} ${decimal(point.lat, COORDINATE_DECIMALS)}"
    }

    // ----------------------------------------------------------------------------------
    // Shared helpers
    // ----------------------------------------------------------------------------------

    /**
     * ISO 8601 with the offset spelled out, e.g. `2026-09-20T14:32:05+07:00`.
     *
     * The same form the GPS point log uses, and for the same reason: the file is read by
     * people as often as by machines, and "which afternoon was that walked" should not
     * need a converter.
     */
    fun formatTimestamp(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)
        format.timeZone = timeZone
        return format.format(Date(millis))
    }

    /**
     * `tracks_20260920_1432.geojson`, or `track_T002_20260920_1432.geojson` when the file
     * holds one named track - the export's own provenance, in its name.
     *
     * @param code the single track's code, when exactly one is being written. Naming the
     * file after it is what stops a folder of exports becoming indistinguishable.
     */
    fun defaultFileName(
        extension: String,
        code: String? = null,
        millis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val format = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US)
        format.timeZone = timeZone
        val stamp = format.format(Date(millis))
        return if (code.isNullOrBlank()) {
            "tracks_$stamp.$extension"
        } else {
            "track_${code}_$stamp.$extension"
        }
    }

    private fun round(value: Double, decimals: Int): BigDecimal =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)

    private fun decimal(value: Double, decimals: Int): String =
        round(value, decimals).toPlainString()

    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX"
    private const val FILE_STAMP_PATTERN = "yyyyMMdd_HHmm"
    private const val CRLF = "\r\n"
    private const val UTF8_BOM = "﻿"
}
