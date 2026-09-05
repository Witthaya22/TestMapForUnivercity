package th.ac.kmutnb.prachin.map.core.geo

/**
 * One-dimensional Kalman filter over a GPS position.
 *
 * A stationary phone still reports positions wandering by a few metres, which makes the blue
 * dot twitch and makes "distance to the next waypoint" jump around. This smooths the track
 * while staying responsive, because each fix is weighted by the accuracy the receiver
 * reported: a 4 m fix pulls the estimate much harder than a 20 m one, with no hand-tuned
 * weights involved.
 *
 * Latitude and longitude share a single scalar variance. That is an approximation - it
 * assumes horizontal error is isotropic - but it is the same assumption `Location.accuracy`
 * itself makes, since Android reports one radius rather than an error ellipse.
 *
 * Not thread-safe; call from a single collector.
 */
class GpsKalmanFilter(
    /**
     * Assumed movement between fixes, in metres per second. 1.0 sits just under walking pace,
     * which keeps a walker's track smooth without lagging behind when they turn a corner.
     */
    private val processNoiseMetersPerSecond: Double = DEFAULT_PROCESS_NOISE_MPS,
) {

    private var latitude = 0.0
    private var longitude = 0.0

    /** Estimate variance in m^2. Negative means "not initialised yet". */
    private var variance = -1.0
    private var lastTimestampMs = 0L

    val isInitialised: Boolean get() = variance >= 0.0

    /** Current smoothed estimate, or `null` before the first fix. */
    val current: GeoPoint? get() = if (isInitialised) GeoPoint(latitude, longitude) else null

    /**
     * Folds one measurement in and returns the smoothed position.
     *
     * @param accuracyMeters the receiver's reported horizontal accuracy; floored at 1 m
     * because a zero would make the filter trust the measurement absolutely.
     */
    fun process(measurement: GeoPoint, accuracyMeters: Float, timestampMs: Long): GeoPoint {
        val measurementVariance = (accuracyMeters.toDouble().coerceAtLeast(MIN_ACCURACY_M)).let { it * it }

        if (!isInitialised) {
            latitude = measurement.lat
            longitude = measurement.lon
            variance = measurementVariance
            lastTimestampMs = timestampMs
            return GeoPoint(latitude, longitude)
        }

        // Predict: uncertainty grows with the time since the last fix.
        val elapsedSeconds = (timestampMs - lastTimestampMs).coerceAtLeast(0L) / 1000.0
        if (elapsedSeconds > 0.0) {
            variance += elapsedSeconds * processNoiseMetersPerSecond * processNoiseMetersPerSecond
            lastTimestampMs = timestampMs
        }

        // Update: blend by Kalman gain.
        val gain = variance / (variance + measurementVariance)
        latitude += gain * (measurement.lat - latitude)
        longitude += gain * (measurement.lon - longitude)
        variance *= (1.0 - gain)

        return GeoPoint(latitude, longitude)
    }

    /**
     * Drops the estimate. Call after a long gap - returning from indoors, or resuming the app -
     * so a stale position is not blended with a fresh one.
     */
    fun reset() {
        variance = -1.0
        lastTimestampMs = 0L
    }

    companion object {
        const val DEFAULT_PROCESS_NOISE_MPS = 1.0
        private const val MIN_ACCURACY_M = 1.0
    }
}
