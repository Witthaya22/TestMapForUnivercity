package th.ac.kmutnb.prachin.map.survey

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

/**
 * Records a walked path (F11).
 *
 * Two filters keep the output usable. Points closer together than [minSpacingMeters] are
 * dropped as they arrive, so standing still does not pile up hundreds of coincident
 * coordinates; then Douglas-Peucker removes the remaining jitter on stop, leaving a line
 * whose vertices are actual corners.
 *
 * This exists because OpenStreetMap rarely has the footpaths inside a Thai university,
 * and a route can only be found over paths that were actually recorded.
 */
class TrackRecorder(
    private val minSpacingMeters: Double = DEFAULT_MIN_SPACING_M,
) {

    private val recorded = ArrayList<GeoPoint>()

    val points: List<GeoPoint> get() = recorded

    val pointCount: Int get() = recorded.size

    val lengthMeters: Double get() = GeoUtils.pathLengthMeters(recorded)

    /** Adds a fix if it is far enough from the previous one. Returns true if it was kept. */
    fun offer(point: GeoPoint): Boolean {
        val last = recorded.lastOrNull()
        if (last != null && GeoUtils.haversineMeters(last, point) < minSpacingMeters) return false
        recorded += point
        return true
    }

    /** Simplified geometry for export. Leaves the recorder's own buffer untouched. */
    fun simplified(epsilonMeters: Double = DEFAULT_EPSILON_M): List<GeoPoint> =
        GeoUtils.simplifyDouglasPeucker(recorded, epsilonMeters)

    fun reset() = recorded.clear()

    companion object {
        /** Close to a single walking pace; finer spacing only records GPS noise. */
        const val DEFAULT_MIN_SPACING_M = 3.0

        /** Simplification tolerance; well below the width of any path being mapped. */
        const val DEFAULT_EPSILON_M = 2.0

        /** Shorter than this and there is nothing worth saving. */
        const val MIN_USABLE_LENGTH_M = 10.0
    }
}
