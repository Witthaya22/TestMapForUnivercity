package th.ac.kmutnb.prachin.map.data

import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.CampusConfigParser
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParser
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.navigation.AStarRouter
import th.ac.kmutnb.prachin.map.navigation.RouteGraphBuilder
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
import java.io.File

/**
 * Checks the data files actually shipped in `app/src/main/assets`.
 *
 * The shipped files are seeded from OpenStreetMap by `tools/osm_import.py`, so all of the
 * rules below apply. They still start with `assumeTrue` rather than a hard failure: someone
 * who empties the files to survey the campus from scratch should get a green build, not a
 * broken one. The same rules run from `tools/geojson_validate.py` before a commit.
 */
class CampusAssetsTest {

    private val assetsDir: File = sequenceOf(
        File("src/main/assets"),
        File("app/src/main/assets"),
    ).firstOrNull { it.isDirectory } ?: error("cannot locate the assets directory")

    private fun read(relative: String): String? =
        File(assetsDir, relative).takeIf { it.isFile }?.readText(Charsets.UTF_8)

    private fun configOrNull(): CampusConfig? {
        val json = read("config/campus_config.json") ?: return null
        return try {
            CampusConfigParser.parse(json)
        } catch (e: CampusConfigException) {
            // Placeholders are the expected state before the survey; anything else is a bug.
            assertEquals(
                "campus_config.json is invalid for a reason other than being unfilled: ${e.message}",
                ConfigProblem.PLACEHOLDER_COORDINATES,
                e.problem,
            )
            null
        }
    }

    private fun pois(): List<Poi> =
        read("data/pois.geojson")?.let { GeoJsonParser.parsePois(it).items }.orEmpty()

    private fun paths(): List<WalkPath> =
        read("data/paths.geojson")?.let { GeoJsonParser.parsePaths(it).items }.orEmpty()

    @Test
    fun `shipped geojson files parse without issues`() {
        read("data/pois.geojson")?.let {
            assertTrue(
                "pois.geojson has unreadable features",
                GeoJsonParser.parsePois(it).issues.isEmpty(),
            )
        }
        read("data/paths.geojson")?.let {
            assertTrue(
                "paths.geojson has unreadable features",
                GeoJsonParser.parsePaths(it).issues.isEmpty(),
            )
        }
    }

    @Test
    fun `every poi id is unique`() {
        val pois = pois()
        assumeTrue("pois.geojson is still empty", pois.isNotEmpty())
        assertEquals(pois.size, pois.map { it.id }.toSet().size)
    }

    @Test
    fun `every poi sits inside the downloaded map area`() {
        val config = configOrNull()
        assumeTrue("campus_config.json is not filled in yet", config != null)
        val pois = pois()
        assumeTrue("pois.geojson is still empty", pois.isNotEmpty())

        val outside = pois.filterNot { it.point in config!!.bbox }
        assertTrue(
            "these POIs fall outside the bbox, so the offline map will not cover them: " +
                outside.joinToString { "${it.id} (${it.point.format()})" },
            outside.isEmpty(),
        )
    }

    @Test
    fun `every poi is close enough to the network to be routed to`() {
        val pois = pois()
        val paths = paths()
        assumeTrue("pois.geojson is still empty", pois.isNotEmpty())
        assumeTrue("paths.geojson is still empty", paths.isNotEmpty())

        val tooFar = pois.mapNotNull { poi ->
            val nearest = paths.minOf { GeoUtils.distanceToPolylineMeters(poi.point, it.points) }
            if (nearest > RouteGraphBuilder.DEFAULT_MAX_SNAP_DISTANCE_M) poi.id to nearest else null
        }
        assertTrue(
            "these POIs are too far from any road or path to be routed to: " +
                tooFar.joinToString { "${it.first} (${"%.1f".format(it.second)} m)" },
            tooFar.isEmpty(),
        )
    }

    @Test
    fun `every poi snaps onto the routing graph`() {
        val pois = pois()
        val paths = paths()
        assumeTrue("pois.geojson is still empty", pois.isNotEmpty())
        assumeTrue("paths.geojson is still empty", paths.isNotEmpty())

        val built = RouteGraphBuilder().build(paths, pois.associate { it.id to it.point })
        assertTrue(
            "unroutable POIs: ${built.unsnappedIds}",
            built.unsnappedIds.isEmpty(),
        )
    }

    @Test
    fun `the walking network is one connected component`() {
        val paths = paths()
        assumeTrue("paths.geojson is still empty", paths.isNotEmpty())

        val graph = RouteGraphBuilder().build(paths).graph
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
        assertEquals(
            "the route network is split into disconnected pieces, so some routes will fail; " +
                "re-run tools/osm_import.py, or walk the missing links in Track Recording mode",
            graph.nodeCount,
            reached,
        )
    }

    @Test
    fun `a route exists between every pair of pois`() {
        val pois = pois()
        val paths = paths()
        assumeTrue("pois.geojson is still empty", pois.size >= 2)
        assumeTrue("paths.geojson is still empty", paths.isNotEmpty())

        // What F4-F6 actually promise: pick any two places from the list and get a route.
        // The connectivity test above proves the network is one piece; this proves the POIs
        // all hang off that piece rather than off some stub of their own.
        val built = RouteGraphBuilder().build(paths, pois.associate { it.id to it.point })
        val nodes = built.snappedNodes
        assumeTrue("no POI snapped onto the network", nodes.size >= 2)

        val failures = ArrayList<String>()
        val ids = nodes.keys.toList()
        for (i in ids.indices) {
            for (j in i + 1 until ids.size) {
                val route = AStarRouter.findPath(built.graph, nodes[ids[i]]!!, nodes[ids[j]]!!)
                if (route == null) failures += "${ids[i]} -> ${ids[j]}"
            }
        }
        assertTrue("no route found for: ${failures.take(10)}", failures.isEmpty())
    }
}
