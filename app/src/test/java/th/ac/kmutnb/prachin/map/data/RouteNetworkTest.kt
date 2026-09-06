package th.ac.kmutnb.prachin.map.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.repository.RouteNetwork
import th.ac.kmutnb.prachin.map.navigation.RouteGraphBuilder
import th.ac.kmutnb.prachin.map.navigation.TestGeo.at
import th.ac.kmutnb.prachin.map.navigation.TestGeo.path

/**
 * Anchoring a live position onto the network.
 *
 * The regression these cover: the snap used to look for the nearest *node*, which is fine
 * for a GPS trace whose vertices sit metres apart but wrong for an imported OSM way, where a
 * straight road is two vertices hundreds of metres apart. Standing in the middle of one, the
 * app refused to plan a route at all - and over half the length of the shipped network is on
 * segments longer than the snap limit.
 */
class RouteNetworkTest {

    private fun networkOf(vararg paths: th.ac.kmutnb.prachin.map.navigation.model.WalkPath): RouteNetwork {
        val built = RouteGraphBuilder().build(paths.toList())
        return RouteNetwork(
            graph = built.graph,
            paths = paths.toList(),
            poiNodes = emptyMap(),
            poiSnapDistances = emptyMap(),
            unroutablePoiIds = emptyList(),
        )
    }

    @Test
    fun `a position halfway along a very long segment still snaps`() {
        // One straight 600 m road with a vertex only at each end - how OSM draws it.
        val network = networkOf(path("road", at(0.0, 0.0), at(600.0, 0.0)))

        // Standing on the road, 300 m from either vertex.
        val waypoint = network.waypointFor("here", "", at(300.0, 0.0))

        assertNotNull("standing on the road must be routable", waypoint)
    }

    @Test
    fun `distance is measured to the road, not to its corners`() {
        val network = networkOf(path("road", at(0.0, 0.0), at(600.0, 0.0)))

        // 10 m to the side of the road at its midpoint: well within the limit.
        assertNotNull(network.waypointFor("near", "", at(300.0, 10.0)))

        // 200 m to the side: beyond it, whichever vertex you measure from.
        assertNull(network.waypointFor("far", "", at(300.0, 200.0)))
    }

    @Test
    fun `the snapped node is an endpoint of the nearest segment`() {
        val network = networkOf(path("road", at(0.0, 0.0), at(100.0, 0.0), at(200.0, 0.0)))

        // Nearer the middle vertex than either outer one.
        val waypoint = network.waypointFor("here", "", at(90.0, 5.0))!!
        val node = network.graph.nodes[waypoint.nodeId]

        assertEquals(0.0, GeoUtils.haversineMeters(node, at(100.0, 0.0)), 1.0)
    }

    @Test
    fun `the waypoint keeps the real position, not the snapped one`() {
        val network = networkOf(path("road", at(0.0, 0.0), at(600.0, 0.0)))
        val standing = at(300.0, 12.0)

        // RoutePlanner draws the leg from here, so the line reaches the user rather than
        // starting from a vertex somewhere up the road.
        assertEquals(standing, network.waypointFor("here", "", standing)!!.point)
    }

    @Test
    fun `an empty network snaps nothing`() {
        assertNull(RouteNetwork.EMPTY.waypointFor("here", "", at(0.0, 0.0)))
    }
}
