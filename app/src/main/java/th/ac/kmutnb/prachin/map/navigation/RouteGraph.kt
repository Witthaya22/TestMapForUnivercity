package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.navigation.model.PathType

/**
 * An immutable walking network. Nodes are identified by their index in [nodes].
 *
 * Built by [RouteGraphBuilder] from `paths.geojson`; consumed by [AStarRouter].
 */
class RouteGraph(
    val nodes: List<GeoPoint>,
    /** `adjacency[n]` lists every edge leaving node `n`. */
    val adjacency: List<List<Edge>>,
    /** Undirected view of the network, used for nearest-path queries. */
    val segments: List<Segment>,
) {

    /** A directed, weighted connection between two nodes. */
    data class Edge(
        val target: Int,
        /** Real distance in metres. */
        val distanceMeters: Double,
        /** [distanceMeters] scaled by the path type multiplier; what A* minimises. */
        val weight: Double,
        val pathId: String,
    )

    /** An undirected stretch of path between two nodes. */
    data class Segment(
        val fromNode: Int,
        val toNode: Int,
        val pathId: String,
        val type: PathType,
    )

    /** Where an arbitrary point lands on the network. */
    data class Projection(
        /** The closest point on the network. */
        val point: GeoPoint,
        val distanceMeters: Double,
        val segment: Segment,
        /** Position along the segment, `0` at [Segment.fromNode], `1` at [Segment.toNode]. */
        val t: Double,
    ) {
        /** Whichever endpoint of the segment the projection is nearer to. */
        val nearestNode: Int get() = if (t <= 0.5) segment.fromNode else segment.toNode
    }

    val isEmpty: Boolean get() = nodes.isEmpty() || segments.isEmpty()

    val nodeCount: Int get() = nodes.size

    /**
     * Closest point on the whole walking network, or `null` if the network is empty.
     *
     * A linear scan over segments. A campus network is a few thousand segments and this runs
     * only on user gestures and route (re)calculation, never per location fix, so a spatial
     * index would add complexity for no measurable gain.
     */
    fun project(point: GeoPoint): Projection? {
        if (segments.isEmpty()) return null
        var best: Projection? = null
        for (segment in segments) {
            val a = nodes[segment.fromNode]
            val b = nodes[segment.toNode]
            val t = GeoUtils.segmentProjectionFactor(point, a, b)
            val onPath = GeoUtils.interpolate(a, b, t)
            val distance = GeoUtils.haversineMeters(point, onPath)
            if (best == null || distance < best.distanceMeters) {
                best = Projection(onPath, distance, segment, t)
            }
        }
        return best
    }

    /** Distance in metres to the nearest walking path, or [Double.MAX_VALUE] if there is none. */
    fun distanceToNetworkMeters(point: GeoPoint): Double =
        project(point)?.distanceMeters ?: Double.MAX_VALUE

    /**
     * Nearest graph node within [maxDistanceMeters], or `null`.
     *
     * Used to anchor the live GPS position onto the graph. Nodes recorded by the in-app track
     * recorder sit roughly 3 m apart, so snapping to a node rather than to an exact point on
     * an edge costs at most ~1.5 m - well inside GPS noise.
     */
    fun nearestNode(point: GeoPoint, maxDistanceMeters: Double = Double.MAX_VALUE): Int? {
        var bestIndex: Int? = null
        var bestDistance = maxDistanceMeters
        nodes.forEachIndexed { index, node ->
            val d = GeoUtils.approxDistanceMeters(point, node)
            if (d <= bestDistance) {
                bestDistance = d
                bestIndex = index
            }
        }
        return bestIndex
    }

    companion object {
        val EMPTY = RouteGraph(emptyList(), emptyList(), emptyList())
    }
}
