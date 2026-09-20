package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/**
 * What kind of hazard a marked spot is.
 *
 * The list is short and closed on purpose. A walker being warned while crossing campus has
 * about two seconds to act on what they hear, so the type has to be one word they already
 * understand - an open text field would produce warnings nobody can react to. Anything that
 * genuinely does not fit is [OTHER] plus a description.
 *
 * `id` is what appears in the exported GeoJSON; the Thai wording lives in `strings.xml`.
 */
enum class HazardType(val id: String) {
    /** Stray or territorial dogs - the most reported hazard on Thai campuses. */
    DOG("dog"),

    /** A crossing or a stretch of road where vehicles are the danger. */
    TRAFFIC("traffic"),

    /** Standing water or a surface that turns slippery in the rain. */
    FLOOD("flood"),

    /** No lighting after dark. */
    DARK("dark"),

    /** Building work, closed path, scaffolding. */
    CONSTRUCTION("construction"),

    /** Broken paving, a hole, a missing drain cover. */
    BROKEN_PATH("broken_path"),

    /** Snakes, wasps, and other wildlife. */
    ANIMAL("animal"),

    OTHER("other"),
    ;

    companion object {
        /** Unknown values fall back to [OTHER] rather than dropping the whole warning. */
        fun fromId(id: String?): HazardType = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/**
 * How bad it is, which decides how loudly the app says so.
 *
 * Three levels rather than a number: whoever marks a hazard is standing in front of it with
 * one hand on the phone, and a 1-to-10 scale invites them to think instead of choosing.
 */
enum class HazardSeverity(val id: String) {
    /** Worth knowing about. Announced once, quietly. */
    CAUTION("caution"),

    /** Should change how you walk. Announced on approach. */
    WARNING("warning"),

    /** Avoid if there is another way round. Announced early and repeated. */
    DANGER("danger"),
    ;

    companion object {
        fun fromId(id: String?): HazardSeverity = entries.firstOrNull { it.id == id } ?: WARNING
    }
}

/**
 * A spot on campus someone marked as dangerous.
 *
 * Distinct from both [Poi] and [GpsPoint]. A POI is somewhere you want to go; a GPS point is
 * a measurement; a hazard is somewhere you want to be told about *before* you get there.
 * That last part is why it carries [radiusMeters] rather than being a bare coordinate - a
 * bad crossing is thirty metres of road, a broken drain cover is one metre, and a warning
 * that fires at the wrong distance is worse than none.
 *
 * Marked by walking to the spot, so the coordinate comes from the same receiver that will
 * later trigger the warning; see `docs/ACCURACY.md` A1.
 */
data class HazardPoint(
    val id: String,
    val type: HazardType,
    val severity: HazardSeverity,
    val point: GeoPoint,
    /** How far the hazard extends from [point]; warnings are timed against this. */
    val radiusMeters: Double,
    /** What exactly is wrong, in the marker's own words. May be empty. */
    val description: String,
    /**
     * False for a hazard that has been dealt with - the building work finished, the drain
     * was fixed. Kept rather than deleted, because "there used to be a hole here" is worth
     * something when it reappears, and because deleting is how a record of a real incident
     * quietly disappears.
     */
    val isActive: Boolean,
    /** Metres of GPS accuracy when marked; null when placed by tapping the map instead. */
    val gpsAccuracy: Float?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Where the warning starts, in metres from [point].
     *
     * The hazard's own extent plus the distance a walker covers while reacting. At 1.3 m/s a
     * warning needs to arrive several seconds ahead to be useful, and the worse the hazard
     * the earlier it has to come - being told about a loose dog as you reach it is no
     * warning at all.
     */
    val alertRadiusMeters: Double get() = radiusMeters + severity.approachMeters

    companion object {
        /** Covers a footpath and its verges - the right default for most marks. */
        const val DEFAULT_RADIUS_M = 15.0

        const val MIN_RADIUS_M = 5.0

        /** Beyond this a "point" hazard is really a region, and needs several marks. */
        const val MAX_RADIUS_M = 100.0
    }
}

/** Extra metres of warning distance, by how bad the hazard is. */
val HazardSeverity.approachMeters: Double
    get() = when (this) {
        HazardSeverity.CAUTION -> 10.0
        HazardSeverity.WARNING -> 20.0
        HazardSeverity.DANGER -> 35.0
    }
