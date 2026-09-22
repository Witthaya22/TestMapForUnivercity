package th.ac.kmutnb.prachin.map.survey

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import kotlin.math.roundToInt

/**
 * What a finished walk produced: the line, and the evidence for how much to trust it.
 *
 * The quality fields exist for the same reason [SurveyedPoint]'s do, and matter more here.
 * A path is recorded precisely where no map shows one, so there is nothing to check the
 * result against - the receiver's own account of the walk is the only evidence there will
 * ever be that the line is where the path is.
 */
data class RecordedTrack(
    /** Simplified geometry: the vertices that are actual corners. */
    val points: List<GeoPoint>,
    /** Metres walked, measured on the unsimplified trail. */
    val lengthMeters: Double,
    /** Mean accuracy over the accepted fixes. */
    val averageAccuracyMeters: Float,
    /** The worst accepted fix. A good mean hides the one corner walked under a tree. */
    val worstAccuracyMeters: Float,
    /** Median satellite counts over the walk, not the reading at one instant. */
    val satellites: SatelliteInfo,
    /** Fixes accepted, before the spacing filter thinned them into vertices. */
    val fixCount: Int,
    /** Fixes discarded for being too coarse; high means a walk under heavy cover. */
    val rejectedCount: Int,
    /** Wall-clock seconds between the first and the last accepted fix. */
    val durationSeconds: Int,
)

/**
 * Records a walked path (F11).
 *
 * Two filters keep the output usable. Points closer together than [minSpacingMeters] are
 * dropped as they arrive, so standing still does not pile up hundreds of coincident
 * coordinates; then Douglas-Peucker removes the remaining jitter on stop, leaving a line
 * whose vertices are actual corners.
 *
 * This exists because OpenStreetMap rarely has the footpaths inside a Thai university, and
 * has nothing at all for a track through a forest. A route can only be found over paths
 * that were actually recorded, so walking one is how the map gets made.
 *
 * Pure Kotlin, so the filtering and the statistics are unit tested rather than discovered
 * by walking the same path four times.
 */
class TrackRecorder(
    private val minSpacingMeters: Double = DEFAULT_MIN_SPACING_M,
    private val maxAccuracyMeters: Float = DEFAULT_MAX_ACCURACY_M,
) {

    private data class Fix(
        val point: GeoPoint,
        val accuracy: Float,
        val satellites: SatelliteInfo?,
        val atMillis: Long,
    )

    /** Every accepted fix, for the statistics. */
    private val accepted = ArrayList<Fix>()

    /** The thinned line, for the geometry. */
    private val vertices = ArrayList<GeoPoint>()

    var rejectedCount: Int = 0
        private set

    val points: List<GeoPoint> get() = vertices

    val pointCount: Int get() = vertices.size

    val fixCount: Int get() = accepted.size

    val lengthMeters: Double get() = GeoUtils.pathLengthMeters(vertices)

    val averageAccuracyMeters: Float
        get() = if (accepted.isEmpty()) 0f else accepted.map { it.accuracy }.average().toFloat()

    /** Seconds since the first accepted fix; 0 before there is one. */
    val durationSeconds: Int
        get() {
            val first = accepted.firstOrNull() ?: return 0
            val last = accepted.last()
            return ((last.atMillis - first.atMillis) / 1000L).toInt().coerceAtLeast(0)
        }

    /** True once there is enough line to be worth saving. */
    val isUsable: Boolean
        get() = vertices.size >= 2 && lengthMeters >= MIN_USABLE_LENGTH_M

    /**
     * Offers a fix to the recording.
     *
     * @return true when it became a new vertex. A fix can be accepted for the statistics
     * and still not move the line, which is what happens while the walker stands still.
     */
    fun offer(
        point: GeoPoint,
        accuracyMeters: Float,
        satellites: SatelliteInfo? = null,
        atMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (accuracyMeters > maxAccuracyMeters) {
            rejectedCount++
            return false
        }
        accepted += Fix(point, accuracyMeters, satellites, atMillis)

        val last = vertices.lastOrNull()
        if (last != null && GeoUtils.haversineMeters(last, point) < minSpacingMeters) return false
        vertices += point
        return true
    }

    /** Simplified geometry. Leaves the recorder's own buffer untouched. */
    fun simplified(epsilonMeters: Double = DEFAULT_EPSILON_M): List<GeoPoint> =
        GeoUtils.simplifyDouglasPeucker(vertices, epsilonMeters)

    /**
     * Everything the walk produced, or null when there is not enough of it to keep.
     *
     * Length is measured on the recorded trail rather than on the simplified line: the
     * walker asked how far they walked, not how far the tidied-up version is.
     */
    fun finish(epsilonMeters: Double = DEFAULT_EPSILON_M): RecordedTrack? {
        if (!isUsable) return null
        val simplified = simplified(epsilonMeters)
        if (simplified.size < 2) return null

        val accuracies = accepted.map { it.accuracy }
        val counts = accepted.mapNotNull { it.satellites }
        return RecordedTrack(
            points = simplified,
            lengthMeters = lengthMeters,
            averageAccuracyMeters = accuracies.average().toFloat(),
            worstAccuracyMeters = accuracies.maxOrNull() ?: 0f,
            satellites = SatelliteInfo(
                inUse = medianOf(counts.map { it.inUse }),
                visible = medianOf(counts.map { it.visible }),
            ),
            fixCount = accepted.size,
            rejectedCount = rejectedCount,
            durationSeconds = durationSeconds,
        )
    }

    fun reset() {
        accepted.clear()
        vertices.clear()
        rejectedCount = 0
    }

    /** Median, not mean: one moment of clear sky should not flatter the whole walk. */
    private fun medianOf(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            ((sorted[middle - 1] + sorted[middle]) / 2.0).roundToInt()
        }
    }

    companion object {
        /** Close to a single walking pace; finer spacing only records GPS noise. */
        const val DEFAULT_MIN_SPACING_M = 3.0

        /** Simplification tolerance; well below the width of any path being mapped. */
        const val DEFAULT_EPSILON_M = 2.0

        /** Shorter than this and there is nothing worth saving. */
        const val MIN_USABLE_LENGTH_M = 10.0

        /**
         * Looser than the point survey's gate, on purpose.
         *
         * Standing still for thirty seconds can wait for a good fix; walking cannot. Under
         * tree cover 10-20 m is the normal reading, and a recorder that refused those would
         * produce no line at all in exactly the places that have no map to begin with. The
         * fixes still passed the app's own 25 m gate before arriving here, and how bad they
         * were is recorded rather than hidden.
         */
        const val DEFAULT_MAX_ACCURACY_M = 20f
    }
}
