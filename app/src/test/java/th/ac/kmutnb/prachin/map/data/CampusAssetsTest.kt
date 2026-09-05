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
import th.ac.kmutnb.prachin.map.navigation.RouteGraphBuilder
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
import java.io.File

/**
 * Checks the data files actually shipped in `app/src/main/assets`.
 *
 * Until the coordinates have been surveyed those files are empty placeholders, so the
 * content checks are skipped rather than failed - the build must stay green on a fresh
 * clone. As soon as real data lands, every rule below applies. The same rules run from
 * `tools/geojson_validate.py` before a commit.
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
    fun `every poi is within 30 m of a walking path`() {
        val pois = pois()
        val paths = paths()
        assumeTrue("pois.geojson is still empty", pois.isNotEmpty())
        assumeTrue("paths.geojson is still empty", paths.isNotEmpty())

        val tooFar = pois.mapNotNull { poi ->
            val nearest = paths.minOf { GeoUtils.distanceToPolylineMeters(poi.point, it.points) }
            if (nearest > MAX_POI_DISTANCE_TO_PATH_M) poi.id to nearest else null
        }
        assertTrue(
            "these POIs are too far from any path to be routed to: " +
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
            "the walking network is split into disconnected pieces, so some routes will fail; " +
                "walk the missing links in Track Recording mode",
            graph.nodeCount,
            reached,
        )
    }

    private companion object {
        const val MAX_POI_DISTANCE_TO_PATH_M = 30.0
    }
}
