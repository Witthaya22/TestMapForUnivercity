package th.ac.kmutnb.prachin.map.map

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
import kotlin.math.cos
import kotlin.math.sin

/**
 * Builds the GeoJSON documents fed to MapLibre's `GeoJsonSource`.
 *
 * Separate from [th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParser], which reads and writes
 * the app's own on-disk schema. What the map needs is different: only the properties the
 * style expressions reference, and geometry shaped for rendering rather than for storage.
 */
object MapGeoJson {

    const val PROPERTY_ID = "id"
    const val PROPERTY_LABEL = "label"
    const val PROPERTY_CATEGORY = "category"
    const val PROPERTY_ROUTABLE = "routable"

    val EMPTY_COLLECTION: String = collection(JsonArray())

    fun pois(pois: List<Poi>, unroutableIds: Set<String>): String {
        val features = JsonArray()
        pois.forEach { poi ->
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ID, poi.id)
                addProperty(PROPERTY_LABEL, poi.label)
                addProperty(PROPERTY_CATEGORY, poi.category.id)
                addProperty(PROPERTY_ROUTABLE, poi.id !in unroutableIds)
            }
            features.add(feature(point(poi.point), properties))
        }
        return collection(features)
    }

    fun line(points: List<GeoPoint>): String {
        if (points.size < 2) return EMPTY_COLLECTION
        val features = JsonArray()
        features.add(feature(lineString(points), JsonObject()))
        return collection(features)
    }

    fun paths(paths: List<WalkPath>): String {
        val features = JsonArray()
        paths.forEach { path ->
            if (path.points.size < 2) return@forEach
            val properties = JsonObject().apply { addProperty("type", path.type.id) }
            features.add(feature(lineString(path.points), properties))
        }
        return collection(features)
    }

    fun singlePoint(point: GeoPoint?): String {
        if (point == null) return EMPTY_COLLECTION
        val features = JsonArray()
        features.add(feature(point(point), JsonObject()))
        return collection(features)
    }

    /**
     * The GPS accuracy ring, as a polygon in real coordinates rather than a circle sized in
     * pixels. A pixel radius would stay the same size while the user zooms, which would tell
     * them the receiver had suddenly got better or worse.
     */
    fun accuracyCircle(centre: GeoPoint?, radiusMeters: Float, segments: Int = 48): String {
        if (centre == null || radiusMeters <= 0f) return EMPTY_COLLECTION
        val (latDelta, lonDelta) = GeoUtils.degreesForMeters(radiusMeters.toDouble(), centre)

        val ring = JsonArray()
        for (i in 0..segments) {
            val angle = 2.0 * Math.PI * i / segments
            ring.add(
                coordinates(
                    GeoPoint(
                        lat = (centre.lat + latDelta * sin(angle)).coerceIn(-90.0, 90.0),
                        lon = (centre.lon + lonDelta * cos(angle)).coerceIn(-180.0, 180.0),
                    ),
                ),
            )
        }

        val polygon = JsonObject().apply {
            addProperty("type", "Polygon")
            add("coordinates", JsonArray().apply { add(ring) })
        }
        return collection(JsonArray().apply { add(feature(polygon, JsonObject())) })
    }

    // ----------------------------------------------------------------------------------

    private fun coordinates(point: GeoPoint) = JsonArray().apply {
        GeoUtils.toGeoJsonCoordinates(point).forEach { add(it) }
    }

    private fun point(point: GeoPoint) = JsonObject().apply {
        addProperty("type", "Point")
        add("coordinates", coordinates(point))
    }

    private fun lineString(points: List<GeoPoint>) = JsonObject().apply {
        addProperty("type", "LineString")
        add("coordinates", JsonArray().apply { points.forEach { add(coordinates(it)) } })
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
}
