package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.GpsPoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes the GPS point log out as GeoJSON and as CSV.
 *
 * Kept apart from [GeoJsonParser], which owns the app's POI and path schema. What leaves
 * here is a set of **measurements**, not a map: no category, no place name, nothing about
 * what stands at the coordinate. A reading is worth keeping for years precisely because it
 * says only what the receiver observed, so anyone opening the file later can judge it
 * without having to trust whoever labelled it.
 *
 * Both formats carry the same fields under the same names, and those names are the property
 * names of [GpsPoint]. The field list is documented in `docs/GPS_LOGGING.md`.
 *
 * Pure Gson and `java.text`, with no Android types, so the output is unit tested rather
 * than inspected by eye after a walk.
 */
object GpsPointExporter {

    /** Seven decimals is about a centimetre - far finer than any fix, and never the limit. */
    private const val COORDINATE_DECIMALS = 7

    /** One decimal on metres: a receiver that claims 4.83 m is not that sure. */
    private const val METER_DECIMALS = 1

    val CSV_HEADER: List<String> = listOf(
        "code",
        "lat",
        "lon",
        "accuracyMeters",
        "elevationMeters",
        "verticalAccuracyMeters",
        "satellitesUsed",
        "satellitesVisible",
        "sampleCount",
        "rejectedCount",
        "spreadMeters",
        "durationSeconds",
        "recordedAt",
        "note",
    )

    // ----------------------------------------------------------------------------------
    // GeoJSON
    // ----------------------------------------------------------------------------------

    /**
     * A `FeatureCollection` of `Point` features, RFC 7946, ready for QGIS or umap.
     *
     * No foreign members are added at the collection level: strict readers are allowed to
     * reject them, and provenance already travels in the file name that
     * [defaultFileName] produces.
     */
    fun writeGeoJson(points: List<GpsPoint>, timeZone: TimeZone = TimeZone.getDefault()): String {
        val features = JsonArray()
        points.forEach { gpsPoint ->
            val properties = JsonObject().apply {
                addProperty("code", gpsPoint.code)
                addProperty("accuracyMeters", round(gpsPoint.accuracyMeters.toDouble(), METER_DECIMALS))
                addOrNull("elevationMeters", gpsPoint.elevationMeters?.let { round(it, METER_DECIMALS) })
                addOrNull(
                    "verticalAccuracyMeters",
                    gpsPoint.verticalAccuracyMeters?.let { round(it.toDouble(), METER_DECIMALS) },
                )
                addProperty("satellitesUsed", gpsPoint.satellitesUsed)
                addProperty("satellitesVisible", gpsPoint.satellitesVisible)
                addProperty("sampleCount", gpsPoint.sampleCount)
                addProperty("rejectedCount", gpsPoint.rejectedCount)
                addProperty("spreadMeters", round(gpsPoint.spreadMeters, METER_DECIMALS))
                addProperty("durationSeconds", gpsPoint.durationSeconds)
                addProperty("recordedAt", formatTimestamp(gpsPoint.recordedAt, timeZone))
                addProperty("note", gpsPoint.note)
            }

            val coordinates = JsonArray()
            // GeoJSON is [lon, lat]; GeoUtils owns that ordering.
            GeoUtils.toGeoJsonCoordinates(gpsPoint.point).forEach {
                coordinates.add(round(it, COORDINATE_DECIMALS))
            }
            val geometry = JsonObject().apply {
                addProperty("type", "Point")
                add("coordinates", coordinates)
            }

            features.add(
                JsonObject().apply {
                    addProperty("type", "Feature")
                    add("geometry", geometry)
                    add("properties", properties)
                },
            )
        }

        return JsonObject().apply {
            addProperty("type", "FeatureCollection")
            add("features", features)
        }.toString()
    }

    // ----------------------------------------------------------------------------------
    // CSV
    // ----------------------------------------------------------------------------------

    /**
     * The same rows as a spreadsheet, because most of the people who need this file open it
     * in Excel rather than in a GIS.
     *
     * Starts with a UTF-8 byte order mark and uses CRLF line endings: without the mark,
     * Excel on Windows reads a Thai note as mojibake, and it is the note that carries
     * whatever the surveyor could not express in a number.
     */
    fun writeCsv(points: List<GpsPoint>, timeZone: TimeZone = TimeZone.getDefault()): String {
        val builder = StringBuilder()
        builder.append(UTF8_BOM)
        builder.append(CSV_HEADER.joinToString(",")).append(CRLF)

        points.forEach { gpsPoint ->
            val row = listOf(
                gpsPoint.code,
                decimal(gpsPoint.point.lat, COORDINATE_DECIMALS),
                decimal(gpsPoint.point.lon, COORDINATE_DECIMALS),
                decimal(gpsPoint.accuracyMeters.toDouble(), METER_DECIMALS),
                gpsPoint.elevationMeters?.let { decimal(it, METER_DECIMALS) }.orEmpty(),
                gpsPoint.verticalAccuracyMeters?.let {
                    decimal(it.toDouble(), METER_DECIMALS)
                }.orEmpty(),
                gpsPoint.satellitesUsed.toString(),
                gpsPoint.satellitesVisible.toString(),
                gpsPoint.sampleCount.toString(),
                gpsPoint.rejectedCount.toString(),
                decimal(gpsPoint.spreadMeters, METER_DECIMALS),
                gpsPoint.durationSeconds.toString(),
                formatTimestamp(gpsPoint.recordedAt, timeZone),
                gpsPoint.note,
            )
            builder.append(row.joinToString(",") { escapeCsv(it) }).append(CRLF)
        }
        return builder.toString()
    }

    // ----------------------------------------------------------------------------------
    // Shared helpers
    // ----------------------------------------------------------------------------------

    /**
     * ISO 8601 with the offset spelled out, e.g. `2026-09-20T14:32:05+07:00`.
     *
     * Not epoch milliseconds like the POI schema uses: this file is read by people as often
     * as by machines, and "which afternoon was that point walked" is a question the log has
     * to answer without a converter. The offset is what keeps it unambiguous anyway.
     */
    fun formatTimestamp(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): String {
        val format = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US)
        format.timeZone = timeZone
        return format.format(Date(millis))
    }

    /** `gps_points_20260920_1432.geojson` - the export's own provenance. */
    fun defaultFileName(
        extension: String,
        millis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        val format = SimpleDateFormat(FILE_STAMP_PATTERN, Locale.US)
        format.timeZone = timeZone
        return "gps_points_${format.format(Date(millis))}.$extension"
    }

    private fun round(value: Double, decimals: Int): BigDecimal =
        BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)

    private fun decimal(value: Double, decimals: Int): String = round(value, decimals).toPlainString()

    private fun JsonObject.addOrNull(name: String, value: BigDecimal?) {
        // An explicit null beats a missing key: a reader can tell "the device never reported
        // an altitude" from "this export forgot the column".
        if (value == null) add(name, JsonNull.INSTANCE) else addProperty(name, value)
    }

    private fun escapeCsv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    private const val TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX"
    private const val FILE_STAMP_PATTERN = "yyyyMMdd_HHmm"
    private const val CRLF = "\r\n"
    private const val UTF8_BOM = "\uFEFF"
}
