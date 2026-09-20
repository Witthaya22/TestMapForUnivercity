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

class PointSurveySessionTest {

    @Test
    fun `median of an odd and an even sample count`() {
        assertEquals(3.0, PointSurveySession.median(listOf(1.0, 5.0, 3.0)), 1e-9)
        // Even count: the mean of the two middle values, (2 + 4) / 2.
        assertEquals(3.0, PointSurveySession.median(listOf(1.0, 5.0, 2.0, 4.0)), 1e-9)
        assertEquals(7.0, PointSurveySession.median(listOf(7.0)), 1e-9)
    }

    @Test
    fun `fixes coarser than the limit are rejected and counted`() {
        val session = PointSurveySession()
        assertFalse(session.offer(at(0.0, 0.0), accuracyMeters = 22f))
        assertTrue(session.offer(at(0.0, 0.0), accuracyMeters = 8f))
        assertEquals(1, session.rejectedCount)
        assertEquals(1, session.sampleCount)
    }

    @Test
    fun `the session completes at the target count and stops accepting`() {
        val session = PointSurveySession(targetSampleCount = 3)
        repeat(3) { assertTrue(session.offer(at(0.0, 0.0), 5f)) }
        assertTrue(session.isComplete)
        assertFalse(session.offer(at(0.0, 0.0), 5f))
        assertEquals(3, session.sampleCount)
    }

    @Test
    fun `the median ignores multipath outliers that would drag a mean`() {
        val session = PointSurveySession(targetSampleCount = 11)
        // Nine readings clustered on the real spot...
        repeat(9) { session.offer(at(0.0, 0.0), 5f) }
        // ...and two reflections 12 m away, which is what a nearby wall produces.
        session.offer(at(12.0, 0.0), 12f)
        session.offer(at(12.0, 0.0), 12f)

        val result = session.result()!!
        // The median stays on the cluster; a mean would sit about 2.2 m north of it.
        assertEquals(0.0, GeoUtils.haversineMeters(result.point, at(0.0, 0.0)), 0.5)
        assertEquals(11, result.sampleCount)
    }

    @Test
    fun `spread reports the furthest accepted sample`() {
        val session = PointSurveySession(targetSampleCount = 3)
        session.offer(at(0.0, 0.0), 5f)
        session.offer(at(0.0, 0.0), 5f)
        session.offer(at(6.0, 0.0), 5f)

        val result = session.result()!!
        assertEquals(6.0, result.spreadMeters, 0.5)
    }

    @Test
    fun `average accuracy is reported across accepted samples only`() {
        val session = PointSurveySession()
        session.offer(at(0.0, 0.0), 4f)
        session.offer(at(0.0, 0.0), 8f)
        session.offer(at(0.0, 0.0), 30f) // rejected
        assertEquals(6f, session.averageAccuracyMeters, 0.01f)
    }

    @Test
    fun `the quality fields summarise the session, not the last reading`() {
        val session = PointSurveySession(targetSampleCount = 3)
        val start = 1_789_889_525_000L
        session.offer(at(0.0, 0.0), 4f, 60.0, 9f, SatelliteInfo(8, 20), start)
        session.offer(at(0.0, 0.0), 6f, 61.0, 7f, SatelliteInfo(12, 22), start + 10_000L)
        // A momentary dropout at the end must not become the recorded satellite count.
        session.offer(at(0.0, 0.0), 5f, 68.0, 8f, SatelliteInfo(5, 21), start + 20_000L)

        val result = session.result()!!
        // Medians throughout: the 68 m altitude spike and the 5-satellite dropout are
        // outliers of exactly the kind a mean would carry into a stored coordinate.
        assertEquals(61.0, result.elevationMeters!!, 1e-9)
        assertEquals(8, result.satellites.inUse)
        assertEquals(21, result.satellites.visible)
        assertEquals(8f, result.verticalAccuracyMeters!!, 0.01f)
        assertEquals(20, result.durationSeconds)
    }

    @Test
    fun `rejections are reported on the result, not only on the session`() {
        val session = PointSurveySession(targetSampleCount = 2)
        session.offer(at(0.0, 0.0), 40f)
        session.offer(at(0.0, 0.0), 5f)
        assertEquals(1, session.result()!!.rejectedCount)
    }

    @Test
    fun `a fix with no altitude leaves the height unknown rather than zero`() {
        val session = PointSurveySession(targetSampleCount = 2)
        session.offer(at(0.0, 0.0), 5f)
        session.offer(at(0.0, 0.0), 5f)

        val result = session.result()!!
        assertNull(result.elevationMeters)
        assertNull(result.verticalAccuracyMeters)
        assertEquals(SatelliteInfo.UNKNOWN, result.satellites)
    }

    @Test
    fun `there is no result before any fix is accepted`() {
        val session = PointSurveySession()
        assertNull(session.result())
        session.offer(at(0.0, 0.0), 5f)
        assertNotNull(session.result())
        session.reset()
        assertNull(session.result())
        assertEquals(0, session.rejectedCount)
    }
}

class TrackRecorderTest {

    @Test
    fun `fixes closer than the spacing are dropped`() {
        val recorder = TrackRecorder(minSpacingMeters = 3.0)
        assertTrue(recorder.offer(at(0.0, 0.0)))
        // Standing still: 1 m of jitter must not add points.
        assertFalse(recorder.offer(at(1.0, 0.0)))
        assertFalse(recorder.offer(at(2.0, 0.0)))
        assertTrue(recorder.offer(at(4.0, 0.0)))
        assertEquals(2, recorder.pointCount)
    }

    @Test
    fun `length follows the recorded points`() {
        val recorder = TrackRecorder()
        listOf(0.0, 10.0, 20.0, 30.0).forEach { recorder.offer(at(it, 0.0)) }
        assertEquals(30.0, recorder.lengthMeters, 1.0)
    }

    @Test
    fun `simplification collapses a straight walk to its ends`() {
        val recorder = TrackRecorder()
        (0..20).forEach { recorder.offer(at(it * 5.0, 0.0)) }
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
        (0..10).forEach { recorder.offer(at(it * 5.0, 0.0)) }
        (1..10).forEach { recorder.offer(at(50.0, it * 5.0)) }

        val simplified = recorder.simplified()
        assertEquals(3, simplified.size)
        assertEquals(0.0, GeoUtils.haversineMeters(simplified[1], at(50.0, 0.0)), 1.0)
    }

    @Test
    fun `reset clears the recording`() {
        val recorder = TrackRecorder()
        recorder.offer(at(0.0, 0.0))
        recorder.offer(at(10.0, 0.0))
        recorder.reset()
        assertEquals(0, recorder.pointCount)
        assertEquals(0.0, recorder.lengthMeters, 0.0)
    }
}
