package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardType

/**
 * Reads and writes the marked-hazard file.
 *
 * Exists so a survey can be shared: one person walks the campus marking bad crossings and
 * unlit paths, and everyone else imports the result instead of finding each one the hard
 * way. That is also why ids are preserved across a round trip - importing the same file
 * twice must update the hazards, not duplicate every one of them.
 *
 * Follows the POI schema's conventions (camelCase keys, epoch milliseconds) rather than the
 * GPS log's, because this is campus data that people edit and merge, not a measurement.
 *
 * Pure Gson, no Android types, so the format is unit tested.
 */
object HazardGeoJson {

    fun write(hazards: List<HazardPoint>): String {
        val features = JsonArray()
        hazards.forEach { hazard ->
            val properties = JsonObject().apply {
                addProperty("id", hazard.id)
                addProperty("type", hazard.type.id)
                addProperty("severity", hazard.severity.id)
                addProperty("radiusMeters", hazard.radiusMeters)
                addProperty("description", hazard.description)
                addProperty("isActive", hazard.isActive)
                // Only written when it was chosen: an absent key means "whatever suits
                // the severity", which is what most hazards want and what a reader of
                // the file should not have to interpret.
                hazard.soundId?.let { addProperty("soundId", it) }
                hazard.gpsAccuracy?.let { addProperty("gpsAccuracy", it) }
                addProperty("createdAt", hazard.createdAt)
                addProperty("updatedAt", hazard.updatedAt)
            }
            val coordinates = JsonArray()
            // GeoJSON is [lon, lat]; GeoUtils owns that ordering.
            GeoUtils.toGeoJsonCoordinates(hazard.point).forEach { coordinates.add(it) }
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

    /**
     * Reads hazards back, skipping and reporting anything unusable.
     *
     * An unknown [HazardType] is **not** an error - it becomes OTHER. A file written by a
     * newer version that knows about a hazard this build has never heard of should still
     * warn the walker that something is there; silently dropping it is the one outcome that
     * could get someone hurt.
     */
    fun parse(json: String, now: Long = System.currentTimeMillis()): GeoJsonParseResult<HazardPoint> {
        val issues = ArrayList<GeoJsonIssue>()
        val hazards = ArrayList<HazardPoint>()
        val seen = HashSet<String>()

        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull()
            ?: return GeoJsonParseResult(hazards, issues)
        if (root.optString("type") != "FeatureCollection") {
            return GeoJsonParseResult(hazards, issues)
        }
        val features = root.getAsJsonArray("features")
            ?: return GeoJsonParseResult(hazards, issues)

        for (element in features) {
            val feature = element as? JsonObject ?: continue
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

            val geometry = feature.getAsJsonObject("geometry")
            if (geometry?.optString("type") != "Point") {
                issues += GeoJsonIssue(
                    id,
                    GeoJsonIssue.Reason.WRONG_GEOMETRY,
                    geometry?.optString("type"),
                )
                continue
            }

            val coordinates = geometry.getAsJsonArray("coordinates")
            if (coordinates == null || coordinates.size() < 2) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.BAD_COORDINATES)
                continue
            }
            val lon = runCatching { coordinates[0].asDouble }.getOrNull()
            val lat = runCatching { coordinates[1].asDouble }.getOrNull()
            if (lon == null || lat == null) {
                issues += GeoJsonIssue(id, GeoJsonIssue.Reason.BAD_COORDINATES)
                continue
            }
            // Catch UTM easting/northing before GeoPoint's require() turns it into a crash.
            if (lon < -180.0 || lon > 180.0 || lat < -90.0 || lat > 90.0) {
                issues += GeoJsonIssue(
                    id,
                    GeoJsonIssue.Reason.OUT_OF_DEGREE_RANGE,
                    "lon=$lon lat=$lat",
                )
                continue
            }

            hazards += HazardPoint(
                id = id,
                type = HazardType.fromId(properties.optString("type")),
                severity = HazardSeverity.fromId(properties.optString("severity")),
                point = GeoUtils.fromGeoJsonCoordinates(listOf(lon, lat)),
                radiusMeters = (properties.optDouble("radiusMeters") ?: HazardPoint.DEFAULT_RADIUS_M)
                    .coerceIn(HazardPoint.MIN_RADIUS_M, HazardPoint.MAX_RADIUS_M),
                description = properties.optString("description").orEmpty(),
                // Kept even when this device has no such sound. The id belongs to the
                // person who marked the hazard, and it becomes right again the moment
                // they import the same file on the phone they meant it for; until then
                // resolveHazardSound falls back to the severity's tone.
                soundId = properties.optString("soundId")?.takeIf { it.isNotBlank() },
                isActive = properties.optBoolean("isActive") ?: true,
                gpsAccuracy = properties.optDouble("gpsAccuracy")?.toFloat(),
                createdAt = properties.optLong("createdAt") ?: now,
                updatedAt = properties.optLong("updatedAt") ?: now,
            )
        }

        return GeoJsonParseResult(hazards, issues)
    }

    // ----------------------------------------------------------------------------------

    private fun JsonObject.optString(name: String): String? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asString }.getOrNull()

    private fun JsonObject.optLong(name: String): Long? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asLong }.getOrNull()

    private fun JsonObject.optDouble(name: String): Double? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asDouble }.getOrNull()

    private fun JsonObject.optBoolean(name: String): Boolean? =
        runCatching { get(name)?.takeIf { !it.isJsonNull }?.asBoolean }.getOrNull()
}
