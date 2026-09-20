package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity

/** A warning the monitor decided to raise, and how far away the thing is right now. */
data class HazardAlert(
    val hazard: HazardPoint,
    val distanceMeters: Double,
    /** True when the walker has been inside this hazard a while and is being told again. */
    val isRepeat: Boolean,
)

/** A hazard the planned route passes through, and how far along the route it sits. */
data class HazardOnRoute(
    val hazard: HazardPoint,
    /** Metres from the start of the route to the closest approach. */
    val alongRouteMeters: Double,
    /** How close the route actually comes to the hazard's centre. */
    val distanceFromRouteMeters: Double,
)

/**
 * Decides when to warn the walker about a marked hazard.
 *
 * Stateful for the same reason [NavigationEngine] is: "you are near a dog" is a transition,
 * not a property of one GPS fix. Warning on every fix would speak thirty times while
 * someone walks past a single crossing, and the third repetition is the one that makes
 * people turn the voice off for good - after which the system warns nobody about anything.
 *
 * So each hazard is announced once on entry, and not again until the walker has genuinely
 * left it. Only [HazardSeverity.DANGER] repeats, and then only after a minute, because
 * standing next to something dangerous for a minute usually means not having noticed it.
 *
 * Pure Kotlin with no Android dependency, so every rule here is unit tested rather than
 * discovered by walking past a dog thirty times.
 */
class HazardMonitor(
    private val repeatIntervalMillis: Long = DEFAULT_REPEAT_INTERVAL_MS,
    private val clearHysteresisMeters: Double = DEFAULT_CLEAR_HYSTERESIS_M,
) {

    private data class Announcement(val atMillis: Long)

    private val announced = HashMap<String, Announcement>()

    /**
     * Feeds a position in and returns whatever should be said now, most urgent first.
     *
     * [hazards] is the live active set; a hazard switched off or deleted between fixes
     * simply stops appearing, and its state here is forgotten with it.
     *
     * Accuracy is deliberately not folded into the trigger distance. Widening the radius by
     * a poor fix would fire warnings for hazards the walker is nowhere near, and the fixes
     * that reach this point have already passed the app's 25 m accuracy gate.
     */
    fun onPosition(
        position: GeoPoint,
        hazards: List<HazardPoint>,
        nowMillis: Long,
    ): List<HazardAlert> {
        val live = hazards.filter { it.isActive }
        val liveIds = live.mapTo(HashSet()) { it.id }
        announced.keys.retainAll(liveIds)

        val alerts = ArrayList<HazardAlert>()

        live.forEach { hazard ->
            val distance = GeoUtils.haversineMeters(position, hazard.point)
            val trigger = hazard.alertRadiusMeters
            val previous = announced[hazard.id]

            when {
                distance <= trigger && previous == null -> {
                    announced[hazard.id] = Announcement(nowMillis)
                    alerts += HazardAlert(hazard, distance, isRepeat = false)
                }

                distance <= trigger && previous != null -> {
                    val due = nowMillis - previous.atMillis >= repeatIntervalMillis
                    if (hazard.severity == HazardSeverity.DANGER && due) {
                        announced[hazard.id] = Announcement(nowMillis)
                        alerts += HazardAlert(hazard, distance, isRepeat = true)
                    }
                }

                // Left it. The margin stops a fix wobbling across the boundary from
                // announcing the same hazard over and over from the same spot.
                distance > trigger + clearHysteresisMeters -> announced.remove(hazard.id)
            }
        }

        return alerts.sortedWith(
            compareByDescending<HazardAlert> { it.hazard.severity.ordinal }
                .thenBy { it.distanceMeters },
        )
    }

    /** Everything the walker is currently inside, for the on-screen banner. */
    fun nearby(position: GeoPoint, hazards: List<HazardPoint>): List<HazardAlert> =
        hazards.filter { it.isActive }
            .map { HazardAlert(it, GeoUtils.haversineMeters(position, it.point), false) }
            .filter { it.distanceMeters <= it.hazard.alertRadiusMeters }
            .sortedWith(
                compareByDescending<HazardAlert> { it.hazard.severity.ordinal }
                    .thenBy { it.distanceMeters },
            )

    /** Forgets what has been announced; call when navigation starts or the route changes. */
    fun reset() = announced.clear()

    companion object {
        /** How long a DANGER hazard waits before saying so again. */
        const val DEFAULT_REPEAT_INTERVAL_MS = 60_000L

        /**
         * Extra metres to walk away before a hazard can warn again. Comfortably wider than
         * the jitter of a good fix, so standing at the edge cannot chatter.
         */
        const val DEFAULT_CLEAR_HYSTERESIS_M = 15.0

        /**
         * The hazards a planned route runs into, in the order the walker will meet them.
         *
         * Used once, when the route is planned, so the walker can decide to go another way
         * before setting off rather than being told at the kerb. Measured against the
         * hazard's alert radius, not its centre: a route that passes ten metres from an
         * unlit stretch still walks the walker through the dark.
         */
        fun alongRoute(route: List<GeoPoint>, hazards: List<HazardPoint>): List<HazardOnRoute> {
            if (route.size < 2) return emptyList()

            return hazards.filter { it.isActive }.mapNotNull { hazard ->
                var travelled = 0.0
                var bestDistance = Double.MAX_VALUE
                var bestAlong = 0.0

                for (i in 1 until route.size) {
                    val from = route[i - 1]
                    val to = route[i]
                    val segmentLength = GeoUtils.haversineMeters(from, to)
                    val distance = GeoUtils.distanceToSegmentMeters(hazard.point, from, to)
                    if (distance < bestDistance) {
                        bestDistance = distance
                        val factor = GeoUtils.segmentProjectionFactor(hazard.point, from, to)
                        bestAlong = travelled + segmentLength * factor
                    }
                    travelled += segmentLength
                }

                if (bestDistance > hazard.alertRadiusMeters) {
                    null
                } else {
                    HazardOnRoute(hazard, bestAlong, bestDistance)
                }
            }.sortedBy { it.alongRouteMeters }
        }
    }
}
