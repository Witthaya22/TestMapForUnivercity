package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation

/** Where the walker stands relative to the trail being walked (§2.3.1.4 of the proposal). */
data class TrailGuideState(
    /** Metres from the trail line; null when the trail has no line to measure to. */
    val distanceFromTrail: Double?,
    /** The point on the line nearest the fix: where the dot is drawn while on the trail. */
    val pointOnTrail: GeoPoint?,
    /** Within the trail's GPS tolerance: the dot is held on the line. */
    val isSnapped: Boolean,
    /** Past the off-trail distance, and not yet back within the return distance. */
    val isOffTrail: Boolean,
    val nextStation: TrailStation?,
    val metersToNextStation: Double?,
    /** Metres walked since the trail was started, along the accepted fixes. */
    val walkedMeters: Double,
    val accuracyMeters: Float,
)

enum class TrailGuideEvent { LEFT_TRAIL, BACK_ON_TRAIL }

/**
 * Keeps the walker on the trail the way a car navigator keeps the car on the road.
 *
 * - Within [Trail.gpsToleranceMeters] of the line, the dot is drawn on the line: in a
 *   forest a fix wanders a few metres, and a dot drifting into the trees beside a path
 *   the walker is plainly on only confuses. The real distance and accuracy stay on screen.
 * - Past [offTrailMeters] the walker has left the trail: one alert, not one per fix.
 * - They are back only when within [backOnMeters], closer than where they left, so a fix
 *   hovering at the edge cannot make the alert flicker on and off.
 *
 * Only the drawing uses [TrailGuideState.pointOnTrail]; hazards, logs and SOS keep the
 * real fix. Pure Kotlin, tested in `TrailGuideTest`.
 */
class TrailGuide {

    private var trailId: String? = null
    private var offTrail = false
    private var last: GeoPoint? = null
    private var walked = 0.0

    fun reset() {
        trailId = null
        offTrail = false
        last = null
        walked = 0.0
    }

    /**
     * @param visited stations already reached (their story shown); the next station is the
     * first one with a point that is not among them.
     */
    fun update(
        trail: Trail,
        here: GeoPoint,
        accuracyMeters: Float,
        visited: Set<String>,
    ): Pair<TrailGuideState, TrailGuideEvent?> {
        if (trail.id != trailId) {
            reset()
            trailId = trail.id
        }
        // Distance walked: skip fixes too coarse to trust and steps smaller than the
        // noise, or a walker standing still would rack up metres.
        last?.let { previous ->
            val step = GeoUtils.haversineMeters(previous, here)
            if (accuracyMeters <= MAX_ACCURACY_FOR_DISTANCE && step >= MIN_STEP_METERS) {
                walked += step
                last = here
            }
        } ?: run { last = here }

        val tolerance = trail.gpsToleranceMeters.toDouble().coerceAtLeast(MIN_TOLERANCE_METERS)
        val line = trail.path.takeIf { it.size >= 2 }
        val nearest = line?.let { nearestOnLine(here, it) }
        val distance = nearest?.let { GeoUtils.haversineMeters(here, it) }

        var event: TrailGuideEvent? = null
        if (distance != null) {
            if (!offTrail && distance > tolerance * OFF_TRAIL_FACTOR) {
                offTrail = true
                event = TrailGuideEvent.LEFT_TRAIL
            } else if (offTrail && distance <= tolerance * BACK_ON_FACTOR) {
                offTrail = false
                event = TrailGuideEvent.BACK_ON_TRAIL
            }
        }

        val next = trail.stations.firstOrNull { it.point != null && it.id !in visited }
        val state = TrailGuideState(
            distanceFromTrail = distance,
            pointOnTrail = nearest,
            isSnapped = distance != null && distance <= tolerance,
            isOffTrail = offTrail,
            nextStation = next,
            metersToNextStation = next?.point?.let { GeoUtils.haversineMeters(here, it) },
            walkedMeters = walked,
            accuracyMeters = accuracyMeters,
        )
        return state to event
    }

    private fun nearestOnLine(p: GeoPoint, line: List<GeoPoint>): GeoPoint =
        line.zipWithNext { a, b -> GeoUtils.nearestPointOnSegment(p, a, b) }
            .minBy { GeoUtils.haversineMeters(p, it) }

    companion object {
        /** A tolerance below a phone's best accuracy would never hold the dot at all. */
        const val MIN_TOLERANCE_METERS = 5.0

        /** Off the trail at 2.5x the tolerance: 25 m for the usual 10 m. */
        const val OFF_TRAIL_FACTOR = 2.5

        /** Back on at 1.5x: 15 m for the usual 10 m - well inside where the alert fired. */
        const val BACK_ON_FACTOR = 1.5

        const val MAX_ACCURACY_FOR_DISTANCE = 25f
        const val MIN_STEP_METERS = 3.0
    }
}
