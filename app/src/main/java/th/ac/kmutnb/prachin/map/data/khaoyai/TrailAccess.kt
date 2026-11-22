package th.ac.kmutnb.prachin.map.data.khaoyai

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

/** Whether a trail can be started from where the visitor is now, and why not. */
sealed interface TrailAccess {
    val canStart: Boolean get() = false

    /** The demo trail: started from anywhere, on purpose. */
    data object Demo : TrailAccess {
        override val canStart: Boolean get() = true
    }

    data object Closed : TrailAccess

    /** No fix yet, or none allowed; the screen says which. */
    data object NoLocation : TrailAccess

    /** Nobody has given this trail a point on the ground, so there is nothing to measure to. */
    data object Unmapped : TrailAccess {
        override val canStart: Boolean get() = true
    }

    data class Inside(val distanceMeters: Double) : TrailAccess {
        override val canStart: Boolean get() = true
    }

    data class TooFar(val distanceMeters: Double, val radiusMeters: Int) : TrailAccess
}

object TrailAccessRules {

    /**
     * Inside when [here] is within the trail's entry radius of its line - or of its
     * trailhead when no line is known, or when the trailhead is nearer. Measured to the
     * whole line, not only the start, so joining a trail half way along also counts.
     */
    fun evaluate(trail: Trail, here: GeoPoint?): TrailAccess {
        if (trail.demoUnlocked) return TrailAccess.Demo
        if (!trail.isOpen) return TrailAccess.Closed
        val distance = distanceToTrail(trail, here ?: return TrailAccess.NoLocation)
            ?: return TrailAccess.Unmapped
        return if (distance <= trail.entryRadiusMeters) {
            TrailAccess.Inside(distance)
        } else {
            TrailAccess.TooFar(distance, trail.entryRadiusMeters)
        }
    }

    /** Metres from [here] to the nearest known part of [trail]; null when none is known. */
    fun distanceToTrail(trail: Trail, here: GeoPoint): Double? {
        val toLine = trail.path.takeIf { it.size >= 2 }?.let { GeoUtils.distanceToPolylineMeters(here, it) }
        val toHead = trail.trailhead?.let { GeoUtils.haversineMeters(here, it) }
        return listOfNotNull(toLine, toHead).minOrNull()
    }

    /** Where outside directions should lead: the trailhead, else the start of the line. */
    fun destination(trail: Trail): GeoPoint? = trail.trailhead ?: trail.path.firstOrNull()
}
