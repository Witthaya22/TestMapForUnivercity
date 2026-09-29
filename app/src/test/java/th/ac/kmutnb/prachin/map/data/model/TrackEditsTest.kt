package th.ac.kmutnb.prachin.map.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at
import th.ac.kmutnb.prachin.map.survey.RecordedTrack

/**
 * Hand edits to a walked track.
 *
 * The one rule under test everywhere here: an edit changes the line and never erases the
 * measurement behind it. A corrected vertex that forgot it was corrected would make an
 * exported survey claim a precision nobody measured.
 */
class TrackEditsTest {

    /** Ten vertices, 10 m apart heading north - "the walk with ten points". */
    private val track = TrackLog(
        id = "track_1",
        code = "T001",
        points = (0 until 10).map { at(it * 10.0, 0.0) },
        lengthMeters = 95.0,
        averageAccuracyMeters = 5f,
        worstAccuracyMeters = 9f,
        satellitesUsed = 8,
        satellitesVisible = 14,
        fixCount = 40,
        rejectedCount = 1,
        durationSeconds = 80,
        note = "",
        isUsedForRouting = true,
        recordedAt = 0L,
        captureMode = TrackCaptureMode.MAP,
        vertexInfo = (0 until 10).map { TrackVertexInfo.walked(if (it == 6) 9f else 4f) },
    )

    // ----------------------------------------------------------------------------------
    // Moving one point
    // ----------------------------------------------------------------------------------

    @Test
    fun `moving point 7 moves only point 7`() {
        // Point 7 (index 6) was walked 5 m east of the path; put it back on the line.
        val offTrack = TrackEdits.moveVertex(track, 6, at(60.0, 5.0))
        val corrected = TrackEdits.moveVertex(offTrack, 6, at(60.0, 0.5))

        corrected.points.forEachIndexed { index, point ->
            if (index != 6) assertEquals(track.points[index], point)
        }
        assertEquals(0.0, GeoUtils.haversineMeters(corrected.points[6], at(60.0, 0.5)), 0.01)
    }

    @Test
    fun `a moved point remembers where the receiver put it and how good that fix was`() {
        val moved = TrackEdits.moveVertex(track, 6, at(60.0, 5.0))

        val info = moved.vertexInfoAt(6)
        assertEquals(VertexSource.MOVED, info.source)
        assertEquals(9f, info.accuracyMeters)
        assertEquals(5.0, requireNotNull(moved.movedMetersAt(6)), 0.05)
        assertEquals(1, moved.editedVertexCount)
    }

    @Test
    fun `moving twice still measures from the receiver, not from the first correction`() {
        val once = TrackEdits.moveVertex(track, 6, at(60.0, 5.0))
        val twice = TrackEdits.moveVertex(once, 6, at(60.0, 8.0))

        assertEquals(8.0, requireNotNull(twice.movedMetersAt(6)), 0.05)
        assertEquals(track.points[6], twice.vertexInfoAt(6).originalPoint)
    }

    @Test
    fun `moving a point back where it was makes it walked again`() {
        val moved = TrackEdits.moveVertex(track, 6, at(60.0, 5.0))
        val back = TrackEdits.moveVertex(moved, 6, track.points[6])

        assertEquals(VertexSource.WALKED, back.vertexInfoAt(6).source)
        assertNull(back.movedMetersAt(6))
        assertEquals(0, back.editedVertexCount)
    }

    @Test
    fun `the walked length follows the change in the line, not a recount`() {
        // A 5 m sideways kink adds ~2 x (sqrt(10^2 + 5^2) - 10) = ~2.36 m of line.
        val moved = TrackEdits.moveVertex(track, 6, at(60.0, 5.0))

        assertEquals(95.0 + 2.36, moved.lengthMeters, 0.05)
    }

    // ----------------------------------------------------------------------------------
    // Adding and removing points
    // ----------------------------------------------------------------------------------

    @Test
    fun `a point added past the end extends the line without a receiver`() {
        val extended = TrackEdits.insertVertex(track, track.points.size, at(100.0, 0.0))

        assertEquals(11, extended.vertexCount)
        assertEquals(at(100.0, 0.0), extended.points.last())
        assertEquals(VertexSource.PLACED, extended.vertexInfoAt(10).source)
        // Placed by hand: there is no fix, so there is no accuracy to claim.
        assertNull(extended.vertexInfoAt(10).accuracyMeters)
        assertEquals(105.0, extended.lengthMeters, 0.05)
    }

    @Test
    fun `a point inserted in the middle shifts the rest along with their records`() {
        val inserted = TrackEdits.insertVertex(track, 6, at(55.0, 3.0))

        assertEquals(VertexSource.PLACED, inserted.vertexInfoAt(6).source)
        // What was point 7 is now point 8, and still has its own 9 m fix.
        assertEquals(track.points[6], inserted.points[7])
        assertEquals(9f, inserted.vertexInfoAt(7).accuracyMeters)
    }

    @Test
    fun `deleting a point takes its record with it`() {
        val deleted = TrackEdits.deleteVertex(track, 6)

        assertEquals(9, deleted.vertexCount)
        assertTrue(deleted.vertexInfoOrUnknown.none { it.accuracyMeters == 9f })
    }

    @Test
    fun `a line cannot be edited below two points`() {
        val shortest = track.copy(
            points = track.points.take(2),
            vertexInfo = track.vertexInfo.take(2),
        )

        assertFalse(TrackEdits.canDelete(shortest))
        assertTrue(TrackEdits.canDelete(track))
    }

    @Test
    fun `editing a track from before per-vertex accuracy keeps the rest unknown`() {
        val legacy = track.copy(vertexInfo = emptyList())

        val edited = TrackEdits.moveVertex(legacy, 2, at(20.0, 3.0))

        assertEquals(VertexSource.MOVED, edited.vertexInfoAt(2).source)
        assertNull(edited.vertexInfoAt(2).accuracyMeters)
        assertEquals(TrackVertexInfo.UNKNOWN, edited.vertexInfoAt(5))
    }

    // ----------------------------------------------------------------------------------
    // Walking on
    // ----------------------------------------------------------------------------------

    @Test
    fun `walking on appends the walk and merges the evidence`() {
        val walk = RecordedTrack(
            points = listOf(at(100.0, 0.0), at(120.0, 0.0)),
            lengthMeters = 21.0,
            averageAccuracyMeters = 10f,
            worstAccuracyMeters = 15f,
            satellites = SatelliteInfo(inUse = 4, visible = 10),
            fixCount = 10,
            rejectedCount = 2,
            durationSeconds = 20,
            vertexAccuracies = listOf(8f, 12f),
        )

        val longer = TrackEdits.appendWalk(track, walk)

        assertEquals(12, longer.vertexCount)
        assertEquals(listOf(8f, 12f), longer.vertexInfoOrUnknown.takeLast(2).map { it.accuracyMeters })
        // 95 walked + the 10 m step from the old end + 21 walked on.
        assertEquals(126.0, longer.lengthMeters, 0.05)
        assertEquals(50, longer.fixCount)
        assertEquals(3, longer.rejectedCount)
        assertEquals(100, longer.durationSeconds)
        // (5 x 40 + 10 x 10) / 50
        assertEquals(6f, longer.averageAccuracyMeters, 0.01f)
        assertEquals(15f, longer.worstAccuracyMeters)
        assertEquals(7, longer.satellitesUsed)
    }
}
