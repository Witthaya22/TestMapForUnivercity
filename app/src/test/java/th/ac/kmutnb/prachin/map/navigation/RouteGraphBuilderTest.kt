package th.ac.kmutnb.prachin.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at
import th.ac.kmutnb.prachin.map.navigation.TestGeo.path
import th.ac.kmutnb.prachin.map.navigation.model.PathType

class RouteGraphBuilderTest {

    @Test
    fun `a single line becomes a chain of nodes and edges`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0), at(100.0, 0.0))),
        )
        val graph = built.graph
        assertEquals(3, graph.nodeCount)
        assertEquals(2, graph.segments.size)
        // Undirected, so the two middle-node edges point both ways.
        assertEquals(2, graph.adjacency[1].size)
        assertEquals(1, graph.adjacency[0].size)
    }

    @Test
    fun `vertices closer than the merge radius collapse into one junction`() {
        // Two paths crossing at a junction the surveyor walked twice, 0.8 m apart.
        val built = RouteGraphBuilder().build(
            listOf(
                path("north_south", at(-50.0, 0.0), at(0.0, 0.0), at(50.0, 0.0)),
                path("east_west", at(0.0, -50.0), at(0.0, 0.8), at(0.0, 50.0)),
            ),
        )
        val graph = built.graph
        // 6 recorded vertices, but the two junction vertices merge -> 5 nodes.
        assertEquals(5, graph.nodeCount)
        // The junction now has four ways out, which is what makes the crossing routable.
        val junction = graph.nodes.indices.maxBy { graph.adjacency[it].size }
        assertEquals(4, graph.adjacency[junction].size)
    }

    @Test
    fun `vertices further apart than the merge radius stay separate`() {
        val built = RouteGraphBuilder().build(
            listOf(
                path("north_south", at(-50.0, 0.0), at(0.0, 0.0), at(50.0, 0.0)),
                // 5 m away: a genuinely separate parallel path, not the same junction.
                path("east_west", at(0.0, -50.0), at(0.0, 5.0), at(0.0, 50.0)),
            ),
        )
        assertEquals(6, built.graph.nodeCount)
    }

    @Test
    fun `a poi beside a path is snapped onto it with a new node`() {
        val builder = RouteGraphBuilder()
        val built = builder.build(
            paths = listOf(path("p1", at(0.0, 0.0), at(100.0, 0.0))),
            // 8 m east of the midpoint of a north-south path.
            snapTargets = mapOf("canteen" to at(50.0, 8.0)),
        )

        assertTrue(built.unsnappedIds.isEmpty())
        val nodeId = built.snappedNodes.getValue("canteen")
        assertEquals(8.0, built.snapDistances.getValue("canteen"), 0.5)

        // The original two-node line gained a node, and the segment was split in two.
        assertEquals(3, built.graph.nodeCount)
        assertEquals(2, built.graph.segments.size)

        // The inserted node sits on the line, not at the POI itself.
        val snappedPoint = built.graph.nodes[nodeId]
        assertEquals(
            0.0,
            GeoUtils.distanceToSegmentMeters(snappedPoint, at(0.0, 0.0), at(100.0, 0.0)),
            0.1,
        )
        assertEquals(2, built.graph.adjacency[nodeId].size)
    }

    @Test
    fun `a poi landing on an existing vertex reuses that node`() {
        val built = RouteGraphBuilder().build(
            paths = listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0), at(100.0, 0.0))),
            snapTargets = mapOf("gate" to at(50.0, 0.3)),
        )
        // No node inserted: the projection fell inside the merge radius of the middle vertex.
        assertEquals(3, built.graph.nodeCount)
        assertEquals(1, built.snappedNodes.getValue("gate"))
    }

    @Test
    fun `a poi too far from any path is reported as unsnapped`() {
        val built = RouteGraphBuilder().build(
            paths = listOf(path("p1", at(0.0, 0.0), at(100.0, 0.0))),
            // Well beyond RouteGraphBuilder.DEFAULT_MAX_SNAP_DISTANCE_M: not a building
            // centroid set back from its road, but a point with no way to reach it.
            snapTargets = mapOf("field" to at(50.0, 200.0)),
        )
        assertEquals(listOf("field"), built.unsnappedIds)
        assertNull(built.snappedNodes["field"])
    }

    @Test
    fun `several pois can snap onto the same segment`() {
        val built = RouteGraphBuilder().build(
            paths = listOf(path("p1", at(0.0, 0.0), at(200.0, 0.0))),
            snapTargets = mapOf(
                "a" to at(50.0, 5.0),
                "b" to at(100.0, 5.0),
                "c" to at(150.0, 5.0),
            ),
        )
        assertTrue(built.unsnappedIds.isEmpty())
        assertEquals(3, built.snappedNodes.size)
        assertEquals(5, built.graph.nodeCount)
        assertEquals(4, built.graph.segments.size)
        // All three landed on distinct nodes.
        assertEquals(3, built.snappedNodes.values.toSet().size)
    }

    @Test
    fun `oneway paths only produce a forward edge`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0), oneway = true)),
        )
        assertEquals(1, built.graph.adjacency[0].size)
        assertEquals(0, built.graph.adjacency[1].size)
    }

    @Test
    fun `path type multiplier is applied to edge weight but not to distance`() {
        val built = RouteGraphBuilder().build(
            listOf(path("stairs", at(0.0, 0.0), at(100.0, 0.0), type = PathType.STAIRS)),
        )
        val edge = built.graph.adjacency[0].single()
        assertEquals(100.0, edge.distanceMeters, 0.5)
        assertEquals(150.0, edge.weight, 1.0)
    }

    @Test
    fun `degenerate input produces an empty graph`() {
        val empty = RouteGraphBuilder().build(emptyList())
        assertTrue(empty.graph.isEmpty)

        // A LineString with a single coordinate cannot form an edge.
        val single = RouteGraphBuilder().build(listOf(path("p1", at(0.0, 0.0))))
        assertTrue(single.graph.isEmpty)
    }

    @Test
    fun `duplicated consecutive coordinates do not create zero length edges`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(0.0, 0.0), at(50.0, 0.0))),
        )
        assertEquals(2, built.graph.nodeCount)
        assertEquals(1, built.graph.segments.size)
    }

    @Test
    fun `project finds the nearest point on the network`() {
        val graph = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(100.0, 0.0))),
        ).graph

        val projection = graph.project(at(50.0, 12.0))
        assertNotNull(projection)
        assertEquals(12.0, projection!!.distanceMeters, 0.5)
        assertEquals(0.5, projection.t, 0.02)
        assertEquals(12.0, graph.distanceToNetworkMeters(at(50.0, 12.0)), 0.5)
    }

    @Test
    fun `nearest node respects the distance limit`() {
        val graph = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(100.0, 0.0))),
        ).graph
        assertEquals(0, graph.nearestNode(at(5.0, 0.0)))
        assertNull(graph.nearestNode(at(5.0, 0.0), maxDistanceMeters = 2.0))
    }

    @Test
    fun `an empty network reports no projection`() {
        assertNull(RouteGraph.EMPTY.project(at(0.0, 0.0)))
        assertEquals(Double.MAX_VALUE, RouteGraph.EMPTY.distanceToNetworkMeters(at(0.0, 0.0)), 0.0)
    }
}
