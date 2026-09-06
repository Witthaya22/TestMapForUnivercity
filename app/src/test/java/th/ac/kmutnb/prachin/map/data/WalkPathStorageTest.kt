package th.ac.kmutnb.prachin.map.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toWalkPath
import th.ac.kmutnb.prachin.map.navigation.RouteGraphBuilder
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at
import th.ac.kmutnb.prachin.map.navigation.TestGeo.path
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/**
 * Storing a walked path and getting it back.
 *
 * The round trip has to be lossless to the centimetre: a surveyed path is the reference the
 * whole map is built from, and a coordinate that shifts on its way through the database
 * would be indistinguishable from a bad GPS fix while being far harder to notice.
 */
class WalkPathStorageTest {

    private val walked = WalkPath(
        id = "surveyed_1",
        type = PathType.STAIRS,
        points = listOf(at(0.0, 0.0), at(12.0, 3.0), at(30.0, 3.0)),
        name = "บันไดหลังโรงอาหาร",
        oneway = true,
        lit = true,
        covered = true,
    )

    @Test
    fun `a recorded path survives a round trip through the database`() {
        val restored = walked.toEntity(lengthMeters = 31.0).toWalkPath()

        assertEquals(walked.id, restored.id)
        assertEquals(walked.name, restored.name)
        assertEquals(walked.type, restored.type)
        assertEquals(walked.oneway, restored.oneway)
        assertEquals(walked.lit, restored.lit)
        assertEquals(walked.covered, restored.covered)
        assertEquals(walked.points.size, restored.points.size)
    }

    @Test
    fun `coordinates come back within a centimetre`() {
        val restored = walked.toEntity().toWalkPath()

        walked.points.zip(restored.points).forEach { (before, after) ->
            assertTrue(
                "moved by ${GeoUtils.haversineMeters(before, after)} m",
                GeoUtils.haversineMeters(before, after) < 0.01,
            )
        }
    }

    @Test
    fun `a Thai name survives storage`() {
        assertEquals("บันไดหลังโรงอาหาร", walked.toEntity().toWalkPath().name)
    }

    @Test
    fun `a surveyed path joins the imported network at a shared junction`() {
        // What makes surveying useful on the spot: a path walked from a point on an existing
        // road has to become part of the same graph, not a second disconnected one.
        val imported = path("imported", at(0.0, 0.0), at(100.0, 0.0))
        val surveyed = WalkPath(
            id = "surveyed_2",
            type = PathType.FOOTWAY,
            // Starts on the imported road and heads away from it.
            points = listOf(at(50.0, 0.0), at(50.0, 40.0)),
        ).toEntity().toWalkPath()

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
        assertEquals("the surveyed path did not join the network", graph.nodeCount, reached)
    }
}
