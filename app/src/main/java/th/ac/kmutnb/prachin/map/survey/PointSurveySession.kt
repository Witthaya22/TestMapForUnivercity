package th.ac.kmutnb.prachin.map.survey

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

/** The coordinate a survey settled on, plus how much to trust it. */
data class SurveyedPoint(
    val point: GeoPoint,
    val sampleCount: Int,
    val averageAccuracyMeters: Float,
    /** Distance from the result to the furthest accepted sample. */
    val spreadMeters: Double,
)

/**
 * Collects repeated GPS fixes at one spot and reduces them to a single coordinate (F11).
 *
 * The result is the **median** of the latitudes and of the longitudes taken separately, not
 * the mean. GPS error is not symmetric - a couple of multipath reflections off a nearby
 * building drag a mean several metres off, while the median simply ignores them.
 *
 * Pure Kotlin, so the statistics are unit tested rather than verified by walking around.
 */
class PointSurveySession(
    val targetSampleCount: Int = DEFAULT_SAMPLE_COUNT,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_M,
) {

    private data class Sample(val point: GeoPoint, val accuracy: Float)

    private val samples = ArrayList<Sample>()

    var rejectedCount: Int = 0
        private set

    val sampleCount: Int get() = samples.size

    val isComplete: Boolean get() = samples.size >= targetSampleCount

    val averageAccuracyMeters: Float
        get() = if (samples.isEmpty()) 0f else samples.map { it.accuracy }.average().toFloat()

    /**
     * Offers a fix. Returns true if it was accepted.
     *
     * Fixes coarser than [maxAccuracyMeters] are counted and discarded: averaging in a 30 m
     * reading would undo the benefit of standing still for half a minute.
     */
    fun offer(point: GeoPoint, accuracyMeters: Float): Boolean {
        if (isComplete) return false
        if (accuracyMeters > maxAccuracyMeters) {
            rejectedCount++
            return false
        }
        samples += Sample(point, accuracyMeters)
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
        return SurveyedPoint(
            point = median,
            sampleCount = samples.size,
            averageAccuracyMeters = averageAccuracyMeters,
            spreadMeters = spread,
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
