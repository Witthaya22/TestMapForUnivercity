package th.ac.kmutnb.prachin.map.data.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/**
 * Reads and validates `campus_config.json`.
 *
 * Pure and Android-free so the validation rules can be unit tested; the same rules are
 * implemented in `tools/geojson_validate.py` for the pre-commit check.
 */
object CampusConfigParser {

    /** Zoom levels past this make the offline pack much larger without adding detail. */
    const val MAX_SUPPORTED_ZOOM = 18

    @Throws(CampusConfigException::class)
    fun parse(json: String): CampusConfig {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: JsonSyntaxException) {
            throw CampusConfigException(ConfigProblem.MALFORMED, e.message)
        } catch (e: IllegalStateException) {
            throw CampusConfigException(ConfigProblem.MALFORMED, e.message)
        }

        val bboxObject = root.obj("bbox")
        val centerObject = root.obj("center")

        val minLon = bboxObject.double("minLon")
        val minLat = bboxObject.double("minLat")
        val maxLon = bboxObject.double("maxLon")
        val maxLat = bboxObject.double("maxLat")
        val centerLon = centerObject.double("lon")
        val centerLat = centerObject.double("lat")

        // The shipped file is all zeroes on purpose; fail loudly rather than dropping the
        // user somewhere in the Gulf of Guinea.
        if (listOf(minLon, minLat, maxLon, maxLat, centerLon, centerLat).any { it == 0.0 }) {
            throw CampusConfigException(ConfigProblem.PLACEHOLDER_COORDINATES)
        }

        // Degrees check comes before anything else that would interpret the numbers, so a
        // UTM easting like 731234 is reported as such instead of as a nonsensical bbox.
        listOf(minLon, maxLon, centerLon).forEach {
            if (it < -180.0 || it > 180.0) {
                throw CampusConfigException(ConfigProblem.OUT_OF_DEGREE_RANGE, "lon=$it")
            }
        }
        listOf(minLat, maxLat, centerLat).forEach {
            if (it < -90.0 || it > 90.0) {
                throw CampusConfigException(ConfigProblem.OUT_OF_DEGREE_RANGE, "lat=$it")
            }
        }

        if (minLon >= maxLon || minLat >= maxLat) {
            throw CampusConfigException(
                ConfigProblem.BBOX_INVERTED,
                "minLon=$minLon maxLon=$maxLon minLat=$minLat maxLat=$maxLat",
            )
        }

        val bbox = BoundingBox(minLon = minLon, minLat = minLat, maxLon = maxLon, maxLat = maxLat)
        val center = GeoPoint(lat = centerLat, lon = centerLon)
        if (center !in bbox) {
            throw CampusConfigException(ConfigProblem.CENTER_OUTSIDE_BBOX, center.format())
        }

        val minZoom = root.int("minZoom")
        val maxZoom = root.int("maxZoom")
        if (minZoom >= maxZoom || minZoom < 0) {
            throw CampusConfigException(ConfigProblem.ZOOM_RANGE_INVALID, "$minZoom..$maxZoom")
        }
        if (maxZoom > MAX_SUPPORTED_ZOOM) {
            throw CampusConfigException(ConfigProblem.MAX_ZOOM_TOO_HIGH, maxZoom.toString())
        }

        val styleUrl = root.string("styleUrl")
        val lowered = styleUrl.lowercase()
        if ("api_key" in lowered || "access_token" in lowered || "apikey" in lowered) {
            throw CampusConfigException(ConfigProblem.STYLE_NEEDS_API_KEY)
        }

        val defaultZoom = root.double("defaultZoom").coerceIn(minZoom.toDouble(), maxZoom.toDouble())

        return CampusConfig(
            campusName = root.string("campusName"),
            bbox = bbox,
            center = center,
            defaultZoom = defaultZoom,
            minZoom = minZoom,
            maxZoom = maxZoom,
            styleUrl = styleUrl,
        )
    }

    // --- small typed accessors that fail with MALFORMED instead of a Gson exception -------

    private fun JsonObject.obj(name: String): JsonObject =
        runCatching { getAsJsonObject(name)!! }
            .getOrElse { throw CampusConfigException(ConfigProblem.MALFORMED, "missing object '$name'") }

    private fun JsonObject.double(name: String): Double =
        runCatching { get(name)!!.asDouble }
            .getOrElse { throw CampusConfigException(ConfigProblem.MALFORMED, "missing number '$name'") }

    private fun JsonObject.int(name: String): Int =
        runCatching { get(name)!!.asInt }
            .getOrElse { throw CampusConfigException(ConfigProblem.MALFORMED, "missing integer '$name'") }

    private fun JsonObject.string(name: String): String =
        runCatching { get(name)!!.asString }
            .getOrElse { throw CampusConfigException(ConfigProblem.MALFORMED, "missing string '$name'") }
}
