package th.ac.kmutnb.prachin.map.survey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at

/**
 * Making a path by walking it.
 *
 * Worth testing thoroughly because the recorder is used where nothing can check its
 * output: a line through a forest is right or wrong, and nobody will ever know which
 * unless the geometry is faithful and the statistics recorded beside it are honest.
 */
class TrackRecorderTest {

    // ----------------------------------------------------------------------------------
    // Geometry
    // ----------------------------------------------------------------------------------

    @Test
    fun `fixes closer than the spacing are dropped`() {
        val recorder = TrackRecorder(minSpacingMeters = 3.0)

        assertTrue(recorder.offer(at(0.0, 0.0), 5f))
        // Standing still: 1 m of jitter must not add points.
        assertFalse(recorder.offer(at(1.0, 0.0), 5f))
        assertFalse(recorder.offer(at(2.0, 0.0), 5f))
        assertTrue(recorder.offer(at(4.0, 0.0), 5f))

        assertEquals(2, recorder.pointCount)
        // All four were still fixes: the walk happened even where the line did not move.
        assertEquals(4, recorder.fixCount)
    }

    @Test
    fun `length follows the recorded points`() {
        val recorder = TrackRecorder()
        listOf(0.0, 10.0, 20.0, 30.0).forEach { recorder.offer(at(it, 0.0), 5f) }

        assertEquals(30.0, recorder.lengthMeters, 1.0)
    }

    @Test
    fun `simplification collapses a straight walk to its ends`() {
        val recorder = TrackRecorder()
        (0..20).forEach { recorder.offer(at(it * 5.0, 0.0), 5f) }
        assertEquals(21, recorder.pointCount)

        val simplified = recorder.simplified()

        assertEquals(2, simplified.size)
        // The recorder's own buffer is untouched, so recording can continue.
        assertEquals(21, recorder.pointCount)
    }

    @Test
    fun `simplification keeps corners`() {
        val recorder = TrackRecorder()
        // North 50 m, then east 50 m.
        (0..10).forEach { recorder.offer(at(it * 5.0, 0.0), 5f) }
        (1..10).forEach { recorder.offer(at(50.0, it * 5.0), 5f) }

        val simplified = recorder.simplified()

        assertEquals(3, simplified.size)
        assertEquals(0.0, GeoUtils.haversineMeters(simplified[1], at(50.0, 0.0)), 1.0)
    }

    @Test
    fun `length is measured on the walk, not on the tidied-up line`() {
        val recorder = TrackRecorder()
        (0..5).forEach { recorder.offer(at(it * 10.0, 0.0), 5f) }

        val finished = requireNotNull(recorder.finish())

        assertEquals(2, finished.points.size)
        // The walker asked how far they walked, not how long the simplified line is.
        assertEquals(50.0, finished.lengthMeters, 1.0)
    }

    // ----------------------------------------------------------------------------------
    // Evidence
    // ----------------------------------------------------------------------------------

    @Test
    fun `a fix too coarse to trust is rejected and counted`() {
        val recorder = TrackRecorder()
        recorder.offer(at(0.0, 0.0), 5f)

        assertFalse(recorder.offer(at(10.0, 0.0), TrackRecorder.DEFAULT_MAX_ACCURACY_M + 1f))

        assertEquals(1, recorder.pointCount)
        assertEquals(1, recorder.fixCount)
        assertEquals(1, recorder.rejectedCount)
    }

    @Test
    fun `a finished walk reports what the receiver saw`() {
        val recorder = TrackRecorder()
        var atMillis = 1_000L
        listOf(4f, 6f, 12f, 8f).forEachIndexed { index, accuracy ->
            recorder.offer(
                point = at(index * 10.0, 0.0),
                accuracyMeters = accuracy,
                satellites = SatelliteInfo(inUse = 7 + index, visible = 14),
                atMillis = atMillis,
            )
            atMillis += 4_000L
        }

        val finished = recorder.finish()
        assertNotNull(finished)
        requireNotNull(finished)

        assertEquals(4, finished.fixCount)
        assertEquals(0, finished.rejectedCount)
        assertEquals(7.5f, finished.averageAccuracyMeters, 0.01f)
        // Reported separately, because a 7.5 m mean hides the 12 m corner.
        assertEquals(12f, finished.worstAccuracyMeters, 0.01f)
        assertEquals(12, finished.durationSeconds)
        // Median of 7, 8, 9, 10 rather than the best moment of clear sky.
        assertEquals(9, finished.satellites.inUse)
        assertEquals(14, finished.satellites.visible)
    }

    @Test
    fun `a walk shorter than the minimum is not worth keeping`() {
        val recorder = TrackRecorder()
        recorder.offer(at(0.0, 0.0), 5f)
        recorder.offer(at(5.0, 0.0), 5f)

        assertFalse(recorder.isUsable)
        assertNull(recorder.finish())
    }

    @Test
    fun `reset clears the statistics as well as the line`() {
        val recorder = TrackRecorder()
        (0..3).forEach { recorder.offer(at(it * 10.0, 0.0), 5f) }
        recorder.offer(at(100.0, 0.0), 99f)

        recorder.reset()

        assertEquals(0, recorder.pointCount)
        assertEquals(0, recorder.fixCount)
        assertEquals(0, recorder.rejectedCount)
        assertEquals(0, recorder.durationSeconds)
        assertEquals(0.0, recorder.lengthMeters, 0.0)
        assertFalse(recorder.isUsable)
    }
}
