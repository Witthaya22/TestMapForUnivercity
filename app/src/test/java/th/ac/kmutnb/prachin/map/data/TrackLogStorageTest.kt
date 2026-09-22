package th.ac.kmutnb.prachin.map.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toTrackLog
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.data.model.toWalkPath
import th.ac.kmutnb.prachin.map.navigation.RouteGraphBuilder
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at
import th.ac.kmutnb.prachin.map.navigation.TestGeo.path

/**
 * Storing a walked path and getting it back.
 *
 * The round trip has to be lossless to the centimetre: where there is no other map, a
 * recorded track *is* the reference, and a coordinate that shifts on its way through the
 * database would be indistinguishable from a bad GPS fix while being far harder to notice.
 */
class TrackLogStorageTest {

    private val walked = TrackLog(
        id = "track_1",
        code = "T001",
        points = listOf(at(0.0, 0.0), at(12.0, 3.0), at(30.0, 3.0)),
        lengthMeters = 31.0,
        averageAccuracyMeters = 6.4f,
        worstAccuracyMeters = 14.1f,
        satellitesUsed = 8,
        satellitesVisible = 15,
        fixCount = 37,
        rejectedCount = 2,
        durationSeconds = 44,
        note = "บันไดหลังโรงอาหาร",
        isUsedForRouting = true,
        recordedAt = 1_789_889_525_000L,
        captureMode = TrackCaptureMode.READOUT,
    )

    @Test
    fun `everything measured about a track survives a round trip`() {
        val restored = walked.toEntity().toTrackLog()

        // Compared with the geometry set aside: storage rounds coordinates to seven
        // decimals on purpose, and the next test is what holds that to a centimetre.
        assertEquals(walked.copy(points = emptyList()), restored.copy(points = emptyList()))
        assertEquals(walked.points.size, restored.points.size)
    }

    @Test
    fun `coordinates come back within a centimetre`() {
        val restored = walked.toEntity().toTrackLog()

        walked.points.zip(restored.points).forEach { (before, after) ->
            assertTrue(
                "moved by ${GeoUtils.haversineMeters(before, after)} m",
                GeoUtils.haversineMeters(before, after) < 0.01,
            )
        }
    }

    @Test
    fun `a Thai note survives storage`() {
        assertEquals("บันไดหลังโรงอาหาร", walked.toEntity().toTrackLog().note)
    }

    @Test
    fun `a track with no note is still named by its code on the network`() {
        // The router shows a path's name in an instruction, so it cannot be blank.
        assertEquals("T001", walked.copy(note = "").toWalkPath().name)
    }

    @Test
    fun `a walked track joins the imported network at a shared junction`() {
        // What makes walking a path useful on the spot: a track recorded from a point on
        // an existing road has to become part of the same graph, not a second
        // disconnected one.
        val imported = path("imported", at(0.0, 0.0), at(100.0, 0.0))
        val surveyed = walked.copy(
            id = "track_2",
            // Starts on the imported road and heads away from it.
            points = listOf(at(50.0, 0.0), at(50.0, 40.0)),
        ).toEntity().toTrackLog().toWalkPath()

        val graph = RouteGraphBuilder().build(listOf(imported, surveyed)).graph
        val visited = BooleanArray(graph.nodeCount)
        val queue = ArrayDeque<Int>()
        queue += 0
        visited[0] = true
        var reached = 1
        while (queue.isNotEmpty()) {
            for (edge in graph.adjacency[queue.removeFirst()]) {
                if (!visited[edge.target]) {
                    visited[edge.target] = true
                    reached++
                    queue += edge.target
                }
            }
        }
        assertEquals("the walked track did not join the network", graph.nodeCount, reached)
    }
}
