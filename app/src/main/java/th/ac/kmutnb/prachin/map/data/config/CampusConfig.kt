package th.ac.kmutnb.prachin.map.data.config

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

/** The area the offline map covers. Field order mirrors the OSM Export panel. */
data class BoundingBox(
    val minLon: Double,
    val minLat: Double,
    val maxLon: Double,
    val maxLat: Double,
) {
    operator fun contains(point: GeoPoint): Boolean =
        point.lon in minLon..maxLon && point.lat in minLat..maxLat

    val center: GeoPoint get() = GeoPoint(lat = (minLat + maxLat) / 2.0, lon = (minLon + maxLon) / 2.0)

    val widthMeters: Double get() = GeoUtils.haversineMeters(minLat, minLon, minLat, maxLon)

    val heightMeters: Double get() = GeoUtils.haversineMeters(minLat, minLon, maxLat, minLon)

    val areaSquareKilometers: Double get() = (widthMeters / 1000.0) * (heightMeters / 1000.0)
}

/** Parsed `assets/config/campus_config.json`. */
data class CampusConfig(
    val campusName: String,
    val bbox: BoundingBox,
    val center: GeoPoint,
    val defaultZoom: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val styleUrl: String,
)

/**
 * Why a config was rejected. Kept as an enum rather than a message so the Thai wording lives
 * in `strings.xml`; [ConfigProblem.stringKey] names the resource the UI looks up.
 */
enum class ConfigProblem {
    /** bbox or center is still the shipped 0.0 placeholder. */
    PLACEHOLDER_COORDINATES,

    /** minLon >= maxLon, or minLat >= maxLat. */
    BBOX_INVERTED,

    /** A value outside +/-180 / +/-90 - almost always UTM easting/northing pasted in. */
    OUT_OF_DEGREE_RANGE,

    CENTER_OUTSIDE_BBOX,
    ZOOM_RANGE_INVALID,
    MAX_ZOOM_TOO_HIGH,

    /** The style URL carries a key, which this project must never require. */
    STYLE_NEEDS_API_KEY,

    /** Not valid JSON, or a required field is missing. */
    MALFORMED,
}

/** Thrown by [CampusConfigParser]; carries a [ConfigProblem] the UI turns into Thai text. */
class CampusConfigException(
    val problem: ConfigProblem,
    val detail: String? = null,
) : Exception("campus_config.json rejected: $problem${detail?.let { " ($it)" }.orEmpty()}")
