package th.ac.kmutnb.prachin.map.navigation.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/**
 * How a stretch of the walking network is travelled. The multiplier makes the router prefer
 * routes that are quicker to walk rather than merely shorter; it never affects the distance
 * shown to the user.
 */
enum class PathType(val id: String, val weightMultiplier: Double) {
    /** Ordinary footpath. */
    FOOTWAY("footway", 1.0),

    /** Walking along a road; no slower than a footpath in practice. */
    ROAD("road", 1.0),

    /** Road crossing - costs extra because of the wait for traffic. */
    CROSSING("crossing", 1.2),

    /** Stairs - slower and more tiring than level ground. */
    STAIRS("stairs", 1.5),
    ;

    companion object {
        fun fromId(id: String?): PathType? = entries.firstOrNull { it.id == id }
    }
}

/** One `LineString` feature from `paths.geojson`. */
data class WalkPath(
    val id: String,
    val type: PathType,
    val points: List<GeoPoint>,
    val name: String? = null,
    /** When true the path may only be walked in the stored coordinate order. */
    val oneway: Boolean = false,
    val lit: Boolean = false,
    val covered: Boolean = false,
)
