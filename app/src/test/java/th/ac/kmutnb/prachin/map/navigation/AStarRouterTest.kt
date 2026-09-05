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
import th.ac.kmutnb.prachin.map.navigation.model.RoutePlanResult
import th.ac.kmutnb.prachin.map.navigation.model.RouteWaypoint

class AStarRouterTest {

    @Test
    fun `finds the only path along a straight line`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0), at(100.0, 0.0))),
        )
        val result = AStarRouter.findPath(built.graph, 0, 2)
        assertNotNull(result)
        assertEquals(listOf(0, 1, 2), result!!.nodeIds)
        assertEquals(100.0, result.distanceMeters, 0.5)
    }

    @Test
    fun `start equal to goal is a zero length path`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0))),
        )
        val result = AStarRouter.findPath(built.graph, 1, 1)!!
        assertEquals(listOf(1), result.nodeIds)
        assertEquals(0.0, result.distanceMeters, 0.0)
    }

    @Test
    fun `picks the shorter of two alternatives`() {
        // A long way round (300 m) and a direct link (100 m) between the same two ends.
        val built = RouteGraphBuilder().build(
            listOf(
                path("direct", at(0.0, 0.0), at(100.0, 0.0)),
                path("detour", at(0.0, 0.0), at(0.0, 100.0), at(100.0, 100.0), at(100.0, 0.0)),
            ),
        )
        val graph = built.graph
        val start = graph.nearestNode(at(0.0, 0.0))!!
        val goal = graph.nearestNode(at(100.0, 0.0))!!
        val result = AStarRouter.findPath(graph, start, goal)!!
        assertEquals(100.0, result.distanceMeters, 1.0)
    }

    @Test
    fun `prefers a longer footway over shorter stairs`() {
        // Stairs: 100 m direct, weighted 150. Footway detour: 120 m, weighted 120.
        val built = RouteGraphBuilder().build(
            listOf(
                path("stairs", at(0.0, 0.0), at(100.0, 0.0), type = PathType.STAIRS),
                path("ramp", at(0.0, 0.0), at(50.0, 33.17), at(100.0, 0.0)),
            ),
        )
        val graph = built.graph
        val start = graph.nearestNode(at(0.0, 0.0))!!
        val goal = graph.nearestNode(at(100.0, 0.0))!!
        val result = AStarRouter.findPath(graph, start, goal)!!

        assertEquals("should take the 120 m ramp, not the 100 m stairs", 120.0, result.distanceMeters, 2.0)
        assertEquals(120.0, result.weight, 2.0)
        assertEquals(3, result.nodeIds.size)
    }

    @Test
    fun `takes the stairs when the alternative is far longer`() {
        val built = RouteGraphBuilder().build(
            listOf(
                path("stairs", at(0.0, 0.0), at(100.0, 0.0), type = PathType.STAIRS),
                path("long_way", at(0.0, 0.0), at(50.0, 200.0), at(100.0, 0.0)),
            ),
        )
        val graph = built.graph
        val result = AStarRouter.findPath(
            graph,
            graph.nearestNode(at(0.0, 0.0))!!,
            graph.nearestNode(at(100.0, 0.0))!!,
        )!!
        assertEquals(100.0, result.distanceMeters, 2.0)
    }

    @Test
    fun `routes across a junction formed by merged vertices`() {
        // Two paths that only connect because their crossing vertices merged.
        val built = RouteGraphBuilder().build(
            listOf(
                path("north_south", at(-50.0, 0.0), at(0.0, 0.0), at(50.0, 0.0)),
                path("east_west", at(0.0, -50.0), at(0.0, 0.5), at(0.0, 50.0)),
            ),
        )
        val graph = built.graph
        val result = AStarRouter.findPath(
            graph,
            graph.nearestNode(at(-50.0, 0.0))!!,
            graph.nearestNode(at(0.0, 50.0))!!,
        )
        assertNotNull("the crossing should be routable", result)
        assertEquals(100.0, result!!.distanceMeters, 2.0)
    }

    @Test
    fun `returns null between disconnected components`() {
        val built = RouteGraphBuilder().build(
            listOf(
                path("island_a", at(0.0, 0.0), at(50.0, 0.0)),
                path("island_b", at(0.0, 500.0), at(50.0, 500.0)),
            ),
        )
        val graph = built.graph
        val result = AStarRouter.findPath(
            graph,
            graph.nearestNode(at(0.0, 0.0))!!,
            graph.nearestNode(at(0.0, 500.0))!!,
        )
        assertNull(result)
    }

    @Test
    fun `respects oneway direction`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(100.0, 0.0), oneway = true)),
        )
        assertNotNull(AStarRouter.findPath(built.graph, 0, 1))
        assertNull(AStarRouter.findPath(built.graph, 1, 0))
    }

    @Test
    fun `out of range node ids are rejected`() {
        val built = RouteGraphBuilder().build(
            listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0))),
        )
        assertNull(AStarRouter.findPath(built.graph, 0, 99))
        assertNull(AStarRouter.findPath(built.graph, -1, 1))
    }

    @Test
    fun `a star result matches an exhaustive search on a grid`() {
        // 5x5 lattice with 40 m spacing; every shortest path is known analytically.
        val paths = buildList {
            for (row in 0..4) {
                add(path("row_$row", *(0..4).map { at(row * 40.0, it * 40.0) }.toTypedArray()))
            }
            for (col in 0..4) {
                add(path("col_$col", *(0..4).map { at(it * 40.0, col * 40.0) }.toTypedArray()))
            }
        }
        val graph = RouteGraphBuilder().build(paths).graph
        assertEquals(25, graph.nodeCount)

        val corner = graph.nearestNode(at(0.0, 0.0))!!
        val opposite = graph.nearestNode(at(160.0, 160.0))!!
        val result = AStarRouter.findPath(graph, corner, opposite)!!
        // Manhattan distance on the lattice: 4 + 4 hops of 40 m.
        assertEquals(320.0, result.distanceMeters, 2.0)
    }

    // ----------------------------------------------------------------------------------
    // RoutePlanner
    // ----------------------------------------------------------------------------------

    @Test
    fun `plans a route through three waypoints in the given order`() {
        val built = RouteGraphBuilder().build(
            paths = listOf(path("main", at(0.0, 0.0), at(100.0, 0.0), at(200.0, 0.0))),
            snapTargets = mapOf(
                "gate_1" to at(0.0, 3.0),
                "gate_3" to at(100.0, 3.0),
                "dorm" to at(200.0, 3.0),
            ),
        )
        val waypoints = listOf("gate_1", "gate_3", "dorm").map { id ->
            RouteWaypoint(
                id = id,
                name = id,
                point = built.graph.nodes[built.snappedNodes.getValue(id)],
                nodeId = built.snappedNodes.getValue(id),
            )
        }

        val result = RoutePlanner.plan(built.graph, waypoints)
        assertTrue(result is RoutePlanResult.Success)
        val route = (result as RoutePlanResult.Success).route

        assertEquals(2, route.legs.size)
        assertEquals(200.0, route.totalDistanceMeters, 2.0)
        assertEquals(GeoUtils.walkingSecondsFor(route.totalDistanceMeters), route.totalDurationSeconds)
        assertEquals(listOf(0.0, 100.0), route.legStartDistances.map { Math.round(it).toDouble() })

        // The joined geometry must not repeat the shared waypoint between legs.
        assertEquals(
            route.legs[0].points.size + route.legs[1].points.size - 1,
            route.points.size,
        )
        // Cumulative distances are monotonic and end at the total.
        assertEquals(route.points.size, route.cumulativeDistances.size)
        assertEquals(0.0, route.cumulativeDistances.first(), 0.0)
        assertTrue(route.cumulativeDistances.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun `planner reports which leg has no path`() {
        val built = RouteGraphBuilder().build(
            paths = listOf(
                path("island_a", at(0.0, 0.0), at(50.0, 0.0)),
                path("island_b", at(0.0, 500.0), at(50.0, 500.0)),
            ),
            snapTargets = mapOf("a" to at(0.0, 2.0), "b" to at(0.0, 498.0)),
        )
        val waypoints = listOf("a", "b").map { id ->
            RouteWaypoint(id, id, built.graph.nodes[built.snappedNodes.getValue(id)], built.snappedNodes.getValue(id))
        }
        val result = RoutePlanner.plan(built.graph, waypoints)
        assertEquals(RoutePlanResult.NoPath(0, 1), result)
    }

    @Test
    fun `planner needs at least two waypoints and a network`() {
        val built = RouteGraphBuilder().build(listOf(path("p1", at(0.0, 0.0), at(50.0, 0.0))))
        val single = listOf(RouteWaypoint("a", "a", at(0.0, 0.0), 0))
        assertEquals(RoutePlanResult.NotEnoughWaypoints, RoutePlanner.plan(built.graph, single))
        assertEquals(
            RoutePlanResult.NotEnoughWaypoints,
            RoutePlanner.plan(RouteGraph.EMPTY, single + RouteWaypoint("b", "b", at(50.0, 0.0), 1)),
        )
    }
}
