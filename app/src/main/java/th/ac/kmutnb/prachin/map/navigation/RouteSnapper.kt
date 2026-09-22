package th.ac.kmutnb.prachin.map.navigation

/**
 * Decides whether the walker's dot should be drawn on the route line or where the receiver
 * actually put it.
 *
 * A fix good to a metre and a half still lands beside a three-metre path about half the
 * time, so the dot spends the walk in the grass next to the line it is following. Nothing
 * is wrong, but it reads as though something is, and the drawn position is the only thing
 * most people will ever judge the app by.
 *
 * So while navigating, and only while the walker is plausibly on the route, the dot is
 * pulled onto the line. Three rules keep that from becoming a lie:
 *
 * - it stops the moment the walker is far enough away to be somewhere else, at which point
 *   the true position is shown and the off-route warning takes over;
 * - the accuracy circle is never moved, so the uncertainty stays drawn where it really is,
 *   and a dot pinned to the line inside a circle that has drifted off it is visibly a dot
 *   that is being helped;
 * - nothing else in the app ever sees the snapped position. Hazard warnings, the GPS log,
 *   the path recorder and the POI surveyor all keep the raw fix, because they answer
 *   questions about the ground rather than about the drawing.
 *
 * Pure Kotlin so the rule is unit tested rather than discovered by walking.
 */
class RouteSnapper(
    private val enterMeters: Double = DEFAULT_ENTER_M,
    private val exitMeters: Double = DEFAULT_EXIT_M,
) {

    var isSnapped: Boolean = false
        private set

    /**
     * Feeds in how far the fix is from the route line and returns whether to snap.
     *
     * Two thresholds rather than one: on a single threshold a walker standing at exactly
     * that distance makes the dot jump between the line and the grass on every fix, which
     * is worse than either place. Once snapped it takes a clearly longer walk away to let
     * go - the same hysteresis [HazardMonitor] uses to stop warnings chattering.
     */
    fun onDistanceFromRoute(distanceMeters: Double): Boolean {
        isSnapped = if (isSnapped) distanceMeters <= exitMeters else distanceMeters <= enterMeters
        return isSnapped
    }

    /** Call when navigation starts or stops; the walker is not being helped any more. */
    fun reset() {
        isSnapped = false
    }

    companion object {
        /**
         * Close enough that the walker is on the path and the fix is merely off it.
         *
         * Wider than a campus footpath and its verges, so ordinary drift is absorbed, and
         * far short of [NavigationEngine.DEFAULT_OFF_ROUTE_THRESHOLD_M], so a walker who
         * has genuinely stepped away is never quietly put back.
         */
        const val DEFAULT_ENTER_M = 12.0

        /** How far they must get before the dot is let go of again. */
        const val DEFAULT_EXIT_M = 18.0
    }
}
