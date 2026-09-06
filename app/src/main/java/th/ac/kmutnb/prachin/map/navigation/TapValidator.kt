package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/** Verdict on a point the user picked on the map. */
sealed interface TapVerdict {

    /** Close enough to a path to be routed to; place it as tapped. */
    data class OnPath(val point: GeoPoint, val distanceMeters: Double) : TapVerdict

    /**
     * Usable but noticeably off the path. The user is warned and offered [suggestion], the
     * nearest point that is actually on the walking network.
     */
    data class NearPath(
        val point: GeoPoint,
        val distanceMeters: Double,
        val suggestion: GeoPoint,
    ) : TapVerdict

    /**
     * Far from any road or path - the middle of a field, or inside a building.
     *
     * Carries [suggestion] like [NearPath] does, because the answer to "you picked somewhere
     * with no way to reach it" is a choice, not a refusal: move it to the network, or keep
     * it and accept that routing may stop short. Marking a spot is a legitimate thing to
     * want, and the map shows unroutable points differently anyway.
     */
    data class FarFromPath(
        val point: GeoPoint,
        val distanceMeters: Double,
        val suggestion: GeoPoint,
    ) : TapVerdict

    /** No walking network has been surveyed yet, so nothing can be checked. */
    data object NoNetwork : TapVerdict
}

/**
 * Decides whether a tapped point can be navigated to (F9).
 *
 * The point of the middle band is that a few metres of offset is normal - the user aims at a
 * building entrance, not at the centreline of the footpath - while a point in the middle of
 * a sports field genuinely cannot be routed to and saying so immediately is kinder than
 * failing later with "no route found".
 */
object TapValidator {

    /** Within this, treat the tap as being on the path. */
    const val ON_PATH_METERS = 15.0

    /** Beyond this, refuse outright. */
    const val MAX_USABLE_METERS = 40.0

    fun validate(point: GeoPoint, graph: RouteGraph): TapVerdict {
        if (graph.isEmpty) return TapVerdict.NoNetwork
        val projection = graph.project(point) ?: return TapVerdict.NoNetwork

        return when {
            projection.distanceMeters <= ON_PATH_METERS ->
                TapVerdict.OnPath(point, projection.distanceMeters)

            projection.distanceMeters <= MAX_USABLE_METERS ->
                TapVerdict.NearPath(point, projection.distanceMeters, projection.point)

            else -> TapVerdict.FarFromPath(point, projection.distanceMeters, projection.point)
        }
    }
}
