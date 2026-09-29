package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.survey.RecordedTrack
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Hand edits to a walked track: move a vertex, add one, delete one, or walk further.
 *
 * Every edit keeps the evidence. A moved vertex remembers where the receiver put it and
 * how good that fix was; a placed vertex says nobody's receiver was there. An edited track
 * is therefore still a measurement with a visible correction on top, never a line that
 * quietly stopped being one - which matters because the correction is made by eye against
 * a basemap that has its own few metres of error (`docs/ACCURACY.md`).
 *
 * Pure Kotlin, so the bookkeeping is unit tested rather than trusted.
 */
object TrackEdits {

    /** Below this, "moved back" is the same as "never moved". */
    private const val SAME_PLACE_METERS = 0.05

    /** A line needs two vertices to be a line, and to stay routable. */
    const val MIN_VERTICES = 2

    /**
     * Puts vertex [index] at [to].
     *
     * A walked vertex becomes [VertexSource.MOVED] and keeps its original position, however
     * many times it is moved afterwards, so the distance shown is always from where the
     * receiver put it. Moving it back onto that spot makes it walked again.
     */
    fun moveVertex(track: TrackLog, index: Int, to: GeoPoint): TrackLog {
        require(index in track.points.indices) { "no vertex $index" }
        val info = track.vertexInfoOrUnknown.toMutableList()
        val before = info[index]
        info[index] = when (before.source) {
            VertexSource.PLACED -> before
            VertexSource.WALKED, VertexSource.MOVED -> {
                val original = before.originalPoint ?: track.points[index]
                if (GeoUtils.haversineMeters(original, to) < SAME_PLACE_METERS) {
                    before.copy(source = VertexSource.WALKED, originalPoint = null)
                } else {
                    before.copy(source = VertexSource.MOVED, originalPoint = original)
                }
            }
        }
        val points = track.points.toMutableList().also { it[index] = to }
        return withGeometry(track, points, info)
    }

    /**
     * Adds a hand-placed vertex so that it becomes index [index].
     *
     * `0` puts it before the start, `points.size` after the end, anything between inserts
     * it between two existing vertices.
     */
    fun insertVertex(track: TrackLog, index: Int, point: GeoPoint): TrackLog {
        require(index in 0..track.points.size) { "cannot insert at $index" }
        val points = track.points.toMutableList().also { it.add(index, point) }
        val info = track.vertexInfoOrUnknown.toMutableList()
            .also { it.add(index, TrackVertexInfo.PLACED) }
        return withGeometry(track, points, info)
    }

    fun canDelete(track: TrackLog): Boolean = track.points.size > MIN_VERTICES

    fun deleteVertex(track: TrackLog, index: Int): TrackLog {
        require(index in track.points.indices) { "no vertex $index" }
        require(canDelete(track)) { "a track needs $MIN_VERTICES vertices" }
        val points = track.points.toMutableList().also { it.removeAt(index) }
        val info = track.vertexInfoOrUnknown.toMutableList().also { it.removeAt(index) }
        return withGeometry(track, points, info)
    }

    /**
     * Joins a further walk onto the end of [track].
     *
     * The walked distance grows by what was walked plus the step from the old end to the
     * first new vertex, which is a straight line nobody recorded but somebody did cover.
     * The walk statistics are merged, not replaced: fix counts and time add up, the mean
     * is weighted by fixes, the worst is the worse of the two. Satellite counts are the
     * fix-weighted mean of the two medians - the per-fix counts behind the old median were
     * never stored, so an exact median of the whole walk cannot be rebuilt.
     */
    fun appendWalk(track: TrackLog, walk: RecordedTrack): TrackLog {
        if (walk.points.isEmpty()) return track
        val gap = track.points.lastOrNull()
            ?.let { GeoUtils.haversineMeters(it, walk.points.first()) }
            ?: 0.0
        val newInfo = if (walk.vertexAccuracies.size == walk.points.size) {
            walk.vertexAccuracies.map { TrackVertexInfo.walked(it) }
        } else {
            List(walk.points.size) { TrackVertexInfo.UNKNOWN }
        }

        val oldFixes = track.fixCount
        val newFixes = walk.fixCount
        val fixes = oldFixes + newFixes
        fun weighted(old: Double, new: Double): Double =
            if (fixes == 0) old else (old * oldFixes + new * newFixes) / fixes

        return track.copy(
            points = track.points + walk.points,
            vertexInfo = track.vertexInfoOrUnknown + newInfo,
            lengthMeters = track.lengthMeters + gap + walk.lengthMeters,
            averageAccuracyMeters = weighted(
                track.averageAccuracyMeters.toDouble(),
                walk.averageAccuracyMeters.toDouble(),
            ).toFloat(),
            worstAccuracyMeters = max(track.worstAccuracyMeters, walk.worstAccuracyMeters),
            satellitesUsed = weighted(
                track.satellitesUsed.toDouble(),
                walk.satellites.inUse.toDouble(),
            ).roundToInt(),
            satellitesVisible = weighted(
                track.satellitesVisible.toDouble(),
                walk.satellites.visible.toDouble(),
            ).roundToInt(),
            fixCount = fixes,
            rejectedCount = track.rejectedCount + walk.rejectedCount,
            durationSeconds = track.durationSeconds + walk.durationSeconds,
        )
    }

    /**
     * New geometry with the walked distance adjusted by how much the line itself changed.
     *
     * `lengthMeters` is the distance walked, measured before simplification, so it is
     * longer than the stored line. An edit changes the line, not the walk - so the figure
     * moves by exactly the change in line length, and never drops below the line itself.
     */
    private fun withGeometry(
        track: TrackLog,
        points: List<GeoPoint>,
        info: List<TrackVertexInfo>,
    ): TrackLog {
        val lineBefore = GeoUtils.pathLengthMeters(track.points)
        val lineAfter = GeoUtils.pathLengthMeters(points)
        val length = max(track.lengthMeters + (lineAfter - lineBefore), lineAfter)
        return track.copy(points = points, vertexInfo = info, lengthMeters = length)
    }
}
