package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/** A feature that could not be read, kept so the UI can report it instead of silently skipping. */
data class GeoJsonIssue(val featureId: String?, val reason: Reason, val detail: String? = null) {
    enum class Reason {
        MISSING_ID,
        DUPLICATE_ID,
        MISSING_NAME,
        WRONG_GEOMETRY,
        BAD_COORDINATES,
        /** Value outside +/-180 / +/-90 - typically UTM pasted in place of WGS84. */
        OUT_OF_DEGREE_RANGE,
        UNKNOWN_PATH_TYPE,
    }
}

/** What one GeoJSON file produced, plus anything that had to be skipped. */
data class GeoJsonParseResult<T>(
    val items: List<T>,
    val issues: List<GeoJsonIssue>,
)

/**
 * Reads `pois.geojson` and `paths.geojson`.
 *
 * Hand-rolled on top of Gson rather than a GeoJSON library so it runs unchanged in JVM unit
 * tests and so every rejection carries a specific [GeoJsonIssue.Reason] the UI can explain
 * in Thai. Bad features are skipped and reported; one broken entry never blanks the map.
 */
object GeoJsonParser {

    fun parsePois(json: String, now: Long = System.currentTimeMillis()): GeoJsonParseResult<Poi> {
        val issues = ArrayList<GeoJsonIssue>()
        val pois = ArrayList<Poi>()
        val seen = HashSet<String>()

        for (feature in featuresOf(json)) {
            val properties = feature.getAsJsonObject("properties") ?: JsonObject()
            val id = properties.optString("id")
            if (id.isNullOrBlank()) {
                issues += GeoJsonIssue(null, GeoJsonIssue.Reason.MISSING_ID)
                continue
            }
            if (!seen.add(id)) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.DUPLICATE_ID)
                continue
            }
            val name = properties.optString("name")
            if (name.isNullOrBlank()) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.MISSING_NAME)
                continue
            }

            val geometry = feature.getAsJsonObject("geometry")
            if (geometry?.optString("type") != "Point") {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.WRONG_GEOMETRY, geometry?.optString("type"))
                continue
            }
            val point = readPoint(geometry.getAsJsonArray("coordinates"), id, issues) ?: continue

            pois += Poi(
                id = id,
                name = name,
                shortName = properties.optString("shortName"),
                category = PoiCategory.fromId(properties.optString("category")),
                order = properties.optInt("order") ?: Int.MAX_VALUE,
                description = properties.optString("description").orEmpty(),
                note = properties.optString("note").orEmpty(),
                icon = properties.optString("icon"),
                point = point,
                isUserCreated = properties.optBoolean("isUserCreated") ?: false,
                gpsAccuracy = properties.optDouble("gpsAccuracy")?.toFloat(),
                createdAt = properties.optLong("createdAt") ?: now,
                updatedAt = properties.optLong("updatedAt") ?: now,
            )
        }

        return GeoJsonParseResult(pois, issues)
    }

    fun parsePaths(json: String): GeoJsonParseResult<WalkPath> {
        val issues = ArrayList<GeoJsonIssue>()
        val paths = ArrayList<WalkPath>()
        val seen = HashSet<String>()

        for (feature in featuresOf(json)) {
            val properties = feature.getAsJsonObject("properties") ?: JsonObject()
            val id = properties.optString("id")
            if (id.isNullOrBlank()) {
                issues += GeoJsonIssue(null, GeoJsonIssue.Reason.MISSING_ID)
                continue
            }
            if (!seen.add(id)) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.DUPLICATE_ID)
                continue
            }

            val typeId = properties.optString("type")
            val type = PathType.fromId(typeId)
            if (type == null) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.UNKNOWN_PATH_TYPE, typeId)
                continue
            }

            val geometry = feature.getAsJsonObject("geometry")
            if (geometry?.optString("type") != "LineString") {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.WRONG_GEOMETRY, geometry?.optString("type"))
                continue
            }

            val coordinates = geometry.getAsJsonArray("coordinates")
            if (coordinates == null || coordinates.size() < 2) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.BAD_COORDINATES, "need >= 2 positions")
                continue
            }

            val points = ArrayList<GeoPoint>(coordinates.size())
            var failed = false
            for (element in coordinates) {
                val point = readPoint(element as? JsonArray, id, issues)
                if (point == null) {
                    failed = true
                    break
                }
                points += point
            }
            if (failed || points.size < 2) continue

            paths += WalkPath(
                id = id,
                type = type,
                points = points,
                name = properties.optString("name"),
                oneway = properties.optBoolean("oneway") ?: false,
                lit = properties.optBoolean("lit") ?: false,
                covered = properties.optBoolean("covered") ?: false,
            )
        }

        return GeoJsonParseResult(paths, issues)
    }

    // ----------------------------------------------------------------------------------
    // Writing
    // ----------------------------------------------------------------------------------

    /** Serialises POIs back to GeoJSON, matching the schema the parser reads. */
    fun writePois(pois: List<Poi>): String {
        val features = JsonArray()
        pois.sortedWith(compareBy({ it.order }, { it.id })).forEach { poi ->
            val properties = JsonObject().apply {
                addProperty("id", poi.id)
                addProperty("name", poi.name)
                poi.shortName?.let { addProperty("shortName", it) }
                addProperty("category", poi.category.id)
                addProperty("order", poi.order)
                addProperty("description", poi.description)
                addProperty("note", poi.note)
                poi.icon?.let { addProperty("icon", it) }
                addProperty("isUserCreated", poi.isUserCreated)
                poi.gpsAccuracy?.let { addProperty("gpsAccuracy", it) }
                addProperty("createdAt", poi.createdAt)
                addProperty("updatedAt", poi.updatedAt)
            }
            features.add(feature(pointGeometry(poi.point), properties))
        }
        return collection(features)
    }

    /** Serialises walking paths back to GeoJSON. */
    fun writePaths(paths: List<WalkPath>): String {
        val features = JsonArray()
        paths.forEach { path ->
            val properties = JsonObject().apply {
                addProperty("id", path.id)
                addProperty("type", path.type.id)
                path.name?.let { addProperty("name", it) }
                addProperty("oneway", path.oneway)
                addProperty("lit", path.lit)
                addProperty("covered", path.covered)
            }
            val coordinates = JsonArray()
            path.points.forEach { coordinates.add(coordinateArray(it)) }
            val geometry = JsonObject().apply {
                addProperty("type", "LineString")
                add("coordinates", coordinates)
            }
            features.add(feature(geometry, properties))
        }
        return collection(features)
    }

    // ----------------------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------------------

    private fun featuresOf(json: String): List<JsonObject> {
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return emptyList()
        if (root.optString("type") != "FeatureCollection") return emptyList()
        val features = root.getAsJsonArray("features") ?: return emptyList()
        return features.mapNotNull { it as? JsonObject }
    }

    private fun readPoint(
        coordinates: JsonArray?,
        featureId: String,
        issues: MutableList<GeoJsonIssue>,
    ): GeoPoint? {
        if (coordinates == null || coordinates.size() < 2) {
            issues += GeoJsonIssue(featureId, GeoJsonIssue.Reason.BAD_COORDINATES)
            return null
        }
        val lon = runCatching { coordinates[0].asDouble }.getOrNull()
        val lat = runCatching { coordinates[1].asDouble }.getOrNull()
        if (lon == null || lat == null) {
            issues += GeoJsonIssue(featureId, GeoJsonIssue.Reason.BAD_COORDINATES)
            return null
        }
        // Catch UTM easting/northing before GeoPoint's require() turns it into a crash.
        if (lon < -180.0 || lon > 180.0 || lat < -90.0 || lat > 90.0) {
            issues += GeoJsonIssue(
                featureId,
                GeoJsonIssue.Reason.OUT_OF_DEGREE_RANGE,
                "lon=$lon lat=$lat",
            )
            return null
        }
        return GeoUtils.fromGeoJsonCoordinates(listOf(lon, lat))
    }

    private fun coordinateArray(point: GeoPoint) = JsonArray().apply {
        // GeoJSON is [lon, lat]; GeoUtils owns that ordering.
        GeoUtils.toGeoJsonCoordinates(point).forEach { add(it) }
    }

    private fun pointGeometry(point: GeoPoint) = JsonObject().apply {
        addProperty("type", "Point")
        add("coordinates", coordinateArray(point))
    }

    private fun feature(geometry: JsonObject, properties: JsonObject) = JsonObject().apply {
        addProperty("type", "Feature")
        add("geometry", geometry)
        add("properties", properties)
    }

    private fun collection(features: JsonArray) = JsonObject().apply {
        addProperty("type", "FeatureCollection")
        add("features", features)
    }.toString()

    private fun JsonObject.optString(name: String): String? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()

    private fun JsonObject.optInt(name: String): Int? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asInt }.getOrNull()

    private fun JsonObject.optLong(name: String): Long? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull()

    private fun JsonObject.optDouble(name: String): Double? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asDouble }.getOrNull()

    private fun JsonObject.optBoolean(name: String): Boolean? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
}
