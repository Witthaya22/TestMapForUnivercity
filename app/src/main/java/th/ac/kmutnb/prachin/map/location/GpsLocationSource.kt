package th.ac.kmutnb.prachin.map.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.core.geo.GpsKalmanFilter
import th.ac.kmutnb.prachin.map.core.ext.hasFineLocationPermission
import th.ac.kmutnb.prachin.map.core.ext.isGpsEnabled

/**
 * Position updates straight from `android.location.LocationManager`, GPS provider only.
 *
 * Google Play Services' fused provider is deliberately not used: it is unavailable on
 * devices without GMS, and its balanced modes fall back to Wi-Fi and cell positioning that
 * is out by hundreds of metres - useless when the footpaths being navigated are ten metres
 * apart. Raw GPS also keeps working with no network at all, which is the whole point here.
 *
 * Every fix passes four gates before it reaches the app; see `docs/ACCURACY.md` A2-A4.
 */
class GpsLocationSource(context: Context) {

    private val appContext = context.applicationContext

    /**
     * Cold flow of location state. Collecting registers the listeners; cancelling removes
     * them, which is what stops the GPS chip draining the battery when nothing is watching.
     *
     * @param minIntervalMs how often the provider may report. One second matches the panel
     * refresh rate the brief asks for; a slower rate is used in battery-saving mode.
     */
    @SuppressLint("MissingPermission") // Guarded by the hasFineLocationPermission() check below.
    fun updates(
        minIntervalMs: Long = DEFAULT_INTERVAL_MS,
        minDistanceMeters: Float = 0f,
    ): Flow<LocationState> = callbackFlow {
        if (!appContext.hasFineLocationPermission()) {
            trySend(LocationState.PermissionMissing)
            awaitClose { }
            return@callbackFlow
        }

        val locationManager = appContext.getSystemService(LocationManager::class.java)
        if (locationManager == null || !appContext.isGpsEnabled()) {
            trySend(LocationState.ProviderDisabled)
            awaitClose { }
            return@callbackFlow
        }

        val filter = GpsKalmanFilter()
        var satellites = SatelliteInfo.UNKNOWN
        var lastAccepted: LocationFix? = null
        var lastAccuracy: Float? = null
        var lastRejection: FixRejection? = null
        var rejectedCount = 0
        var hasLock = false

        fun emitSearching() {
            trySend(LocationState.Searching(lastAccuracy, satellites, lastRejection))
        }

        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastAccuracy = if (location.hasAccuracy()) location.accuracy else null

                val rejection = reject(location, lastAccepted)
                if (rejection != null) {
                    rejectedCount++
                    lastRejection = rejection
                    if (!hasLock) emitSearching()
                    return
                }
                lastRejection = null

                val raw = GeoPoint(lat = location.latitude, lon = location.longitude)

                // A long gap means the previous estimate describes somewhere else entirely -
                // coming back outdoors, or resuming the app - so start the filter over rather
                // than dragging the dot across campus.
                val previous = lastAccepted
                if (previous != null &&
                    location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos > FILTER_RESET_GAP_NANOS
                ) {
                    filter.reset()
                }

                val smoothed = filter.process(raw, location.accuracy, location.time)
                val fix = LocationFix(
                    point = smoothed,
                    rawPoint = raw,
                    accuracyMeters = location.accuracy,
                    speedMps = if (location.hasSpeed()) location.speed else null,
                    bearingDegrees = if (location.hasBearing()) location.bearing else null,
                    elapsedRealtimeNanos = location.elapsedRealtimeNanos,
                    timeMs = location.time,
                    provider = location.provider ?: LocationManager.GPS_PROVIDER,
                )
                lastAccepted = fix

                // Hold the dot back until the receiver has genuinely settled. Showing the
                // first fix of a cold start makes the marker leap across the campus and reads
                // as a broken app.
                if (!hasLock && location.accuracy < LOCK_ACCURACY_M) hasLock = true

                if (hasLock) {
                    trySend(LocationState.Available(fix, satellites, rejectedCount))
                } else {
                    emitSearching()
                }
            }

            @Deprecated("Required by the pre-API-29 LocationListener contract")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = emitSearching().let { }

            override fun onProviderDisabled(provider: String) {
                hasLock = false
                filter.reset()
                trySend(LocationState.ProviderDisabled)
            }
        }

        val gnssCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var inUse = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) inUse++
                }
                satellites = SatelliteInfo(inUse = inUse, visible = status.satelliteCount)
                if (!hasLock) emitSearching()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        emitSearching()

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                minIntervalMs,
                minDistanceMeters,
                locationListener,
                Looper.getMainLooper(),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                locationManager.registerGnssStatusCallback(gnssCallback, handler)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "location permission revoked while subscribing", e)
            trySend(LocationState.PermissionMissing)
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(locationListener) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching { locationManager.unregisterGnssStatusCallback(gnssCallback) }
            }
        }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "GpsLocationSource"

        const val DEFAULT_INTERVAL_MS = 1_000L

        /** Below this the dot is shown. Roughly the width of a footpath plus its verge. */
        const val LOCK_ACCURACY_M = 20f

        /** Above this a fix is discarded outright; too coarse to tell two paths apart. */
        const val MAX_ACCURACY_M = 25f

        /** Older than this and the fix is a cached position from another app. */
        const val MAX_FIX_AGE_NANOS = 10_000L * 1_000_000L

        /** No pedestrian sustains this; a fix implying it is a receiver glitch. */
        const val MAX_SPEED_MPS = 10.0

        /** A gap this long invalidates the smoothing history. */
        const val FILTER_RESET_GAP_NANOS = 60_000L * 1_000_000L

        /**
         * Applies the four rejection rules from docs/ACCURACY.md A4.
         * Returns the reason to discard the fix, or null to accept it.
         */
        fun reject(location: Location, previous: LocationFix?): FixRejection? {
            if (location.provider != LocationManager.GPS_PROVIDER) return FixRejection.WRONG_PROVIDER
            if (!location.hasAccuracy() || location.accuracy > MAX_ACCURACY_M) {
                return FixRejection.TOO_INACCURATE
            }
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (age > MAX_FIX_AGE_NANOS) return FixRejection.TOO_OLD

            if (previous != null) {
                val elapsedSeconds =
                    (location.elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / 1_000_000_000.0
                if (elapsedSeconds > 0.0) {
                    val moved = GeoUtils.haversineMeters(
                        previous.rawPoint,
                        GeoPoint(location.latitude, location.longitude),
                    )
                    if (moved / elapsedSeconds > MAX_SPEED_MPS) return FixRejection.IMPLAUSIBLE_JUMP
                }
            }
            return null
        }
    }
}
