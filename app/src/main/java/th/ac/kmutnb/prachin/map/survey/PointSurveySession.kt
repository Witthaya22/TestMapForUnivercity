package th.ac.kmutnb.prachin.map.survey

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import kotlin.math.roundToInt

/**
 * The coordinate a survey settled on, plus everything needed to judge how much to trust it.
 *
 * The quality fields are not decoration. A coordinate with no evidence behind it cannot be
 * told apart from one measured badly, and the whole point of surveying with the same
 * receiver that will later navigate is to be able to show that evidence later - in the
 * detail sheet, and in the exported file.
 */
data class SurveyedPoint(
    val point: GeoPoint,
    val sampleCount: Int,
    val averageAccuracyMeters: Float,
    /** Distance from the result to the furthest accepted sample. */
    val spreadMeters: Double,
    /** Fixes discarded for being too coarse; high here means a difficult spot. */
    val rejectedCount: Int = 0,
    /** Median altitude of the accepted samples, or null if no fix reported one. */
    val elevationMeters: Double? = null,
    /** Mean of the reported vertical accuracies, or null when the device omits them. */
    val verticalAccuracyMeters: Float? = null,
    /** Median satellite counts over the session, not the reading at one instant. */
    val satellites: SatelliteInfo = SatelliteInfo.UNKNOWN,
    /** Wall-clock seconds between the first and the last accepted sample. */
    val durationSeconds: Int = 0,
)

/**
 * Collects repeated GPS fixes at one spot and reduces them to a single coordinate (F11).
 *
 * The result is the **median** of the latitudes and of the longitudes taken separately, not
 * the mean. GPS error is not symmetric - a couple of multipath reflections off a nearby
 * building drag a mean several metres off, while the median simply ignores them. Altitude
 * and the satellite counts are reduced the same way, for the same reason.
 *
 * Pure Kotlin, so the statistics are unit tested rather than verified by walking around.
 */
class PointSurveySession(
    val targetSampleCount: Int = DEFAULT_SAMPLE_COUNT,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_M,
) {

    private data class Sample(
        val point: GeoPoint,
        val accuracy: Float,
        val altitude: Double?,
        val verticalAccuracy: Float?,
        val satellites: SatelliteInfo?,
        val atMillis: Long,
    )

    private val samples = ArrayList<Sample>()

    var rejectedCount: Int = 0
        private set

    val sampleCount: Int get() = samples.size

    val isComplete: Boolean get() = samples.size >= targetSampleCount

    val averageAccuracyMeters: Float
        get() = if (samples.isEmpty()) 0f else samples.map { it.accuracy }.average().toFloat()

    /** Seconds since the first accepted fix; 0 before there is one. */
    val elapsedSeconds: Int
        get() = if (samples.size < 2) {
            0
        } else {
            ((samples.last().atMillis - samples.first().atMillis) / 1_000L).toInt()
        }

    /**
     * Offers a fix. Returns true if it was accepted.
     *
     * Fixes coarser than [maxAccuracyMeters] are counted and discarded: averaging in a 30 m
     * reading would undo the benefit of standing still for half a minute.
     *
     * Everything past [accuracyMeters] is optional, because the older POI surveyor only has
     * a position and a radius to offer, while the GPS point log records the full fix.
     */
    fun offer(
        point: GeoPoint,
        accuracyMeters: Float,
        altitudeMeters: Double? = null,
        verticalAccuracyMeters: Float? = null,
        satellites: SatelliteInfo? = null,
        atMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (isComplete) return false
        if (accuracyMeters > maxAccuracyMeters) {
            rejectedCount++
            return false
        }
        samples += Sample(
            point = point,
            accuracy = accuracyMeters,
            altitude = altitudeMeters,
            verticalAccuracy = verticalAccuracyMeters,
            satellites = satellites,
            atMillis = atMillis,
        )
        return true
    }

    /** The current best estimate, or null before any fix has been accepted. */
    fun result(): SurveyedPoint? {
        if (samples.isEmpty()) return null
        val median = GeoPoint(
            lat = median(samples.map { it.point.lat }),
            lon = median(samples.map { it.point.lon }),
        )
        val spread = samples.maxOf { GeoUtils.haversineMeters(median, it.point) }
        val altitudes = samples.mapNotNull { it.altitude }
        val verticalAccuracies = samples.mapNotNull { it.verticalAccuracy }
        val satelliteReadings = samples.mapNotNull { it.satellites }

        return SurveyedPoint(
            point = median,
            sampleCount = samples.size,
            averageAccuracyMeters = averageAccuracyMeters,
            spreadMeters = spread,
            rejectedCount = rejectedCount,
            elevationMeters = altitudes.takeIf { it.isNotEmpty() }?.let { median(it) },
            verticalAccuracyMeters = verticalAccuracies
                .takeIf { it.isNotEmpty() }
                ?.let { values -> values.map { it.toDouble() }.average().toFloat() },
            satellites = if (satelliteReadings.isEmpty()) {
                SatelliteInfo.UNKNOWN
            } else {
                SatelliteInfo(
                    inUse = median(satelliteReadings.map { it.inUse.toDouble() }).roundToInt(),
                    visible = median(satelliteReadings.map { it.visible.toDouble() }).roundToInt(),
                )
            },
            durationSeconds = elapsedSeconds,
        )
    }

    fun reset() {
        samples.clear()
        rejectedCount = 0
    }

    companion object {
        /** Roughly thirty seconds at one fix per second. */
        const val DEFAULT_SAMPLE_COUNT = 30

        /** Stricter than the navigation gate: a stored coordinate is used for years. */
        const val DEFAULT_MAX_ACCURACY_M = 15f

        internal fun median(values: List<Double>): Double {
            require(values.isNotEmpty())
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) {
                sorted[middle]
            } else {
                (sorted[middle - 1] + sorted[middle]) / 2.0
            }
        }
    }
}
