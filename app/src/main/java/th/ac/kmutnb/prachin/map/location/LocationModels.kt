package th.ac.kmutnb.prachin.map.location

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/** One accepted GPS fix, after filtering and smoothing. */
data class LocationFix(
    /** Kalman-smoothed position; this is what the blue dot and the navigation maths use. */
    val point: GeoPoint,
    /** The unsmoothed position as reported, kept for the debug screen. */
    val rawPoint: GeoPoint,
    val accuracyMeters: Float,
    val speedMps: Float?,
    val bearingDegrees: Float?,
    /** Monotonic timestamp, immune to clock changes; used to judge how stale a fix is. */
    val elapsedRealtimeNanos: Long,
    val timeMs: Long,
    val provider: String,
)

/** How many satellites the receiver can see and how many it is actually using. */
data class SatelliteInfo(val inUse: Int, val visible: Int) {
    /** A 3D fix needs at least four satellites; below that the position is not trustworthy. */
    val hasEnoughForFix: Boolean get() = inUse >= 4

    companion object {
        val UNKNOWN = SatelliteInfo(0, 0)
    }
}

/** Why a fix was thrown away, for the debug screen. */
enum class FixRejection {
    /** Coarser than the app's accuracy gate. */
    TOO_INACCURATE,

    /** A cached fix from another app, old enough to describe somewhere the user has left. */
    TOO_OLD,

    /** Implies a speed no walker reaches, so the receiver jumped. */
    IMPLAUSIBLE_JUMP,

    /** Not from GPS_PROVIDER. */
    WRONG_PROVIDER,
}

/** What the app currently knows about where the user is. */
sealed interface LocationState {

    /** Precise location has not been granted, so nothing can be reported. */
    data object PermissionMissing : LocationState

    /** The GPS provider is switched off in system settings. */
    data object ProviderDisabled : LocationState

    /**
     * Waiting for a fix good enough to show.
     *
     * A cold start with no network has no almanac to work from and can take 30-90 seconds,
     * so this state is normal rather than an error, and the UI says so.
     */
    data class Searching(
        val lastAccuracyMeters: Float?,
        val satellites: SatelliteInfo,
        val lastRejection: FixRejection?,
    ) : LocationState

    data class Available(
        val fix: LocationFix,
        val satellites: SatelliteInfo,
        val rejectedCount: Int,
    ) : LocationState
}
