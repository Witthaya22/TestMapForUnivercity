package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
import kotlin.math.floor

/** Result of turning `paths.geojson` plus a set of POIs into a routable graph. */
data class BuiltRouteGraph(
    val graph: RouteGraph,
    /** POI id -> graph node the POI was snapped onto. */
    val snappedNodes: Map<String, Int>,
    /** POI id -> how far it sat from the nearest path, in metres. */
    val snapDistances: Map<String, Double>,
    /** POIs that were further than the snap limit from any path, so cannot be routed to. */
    val unsnappedIds: List<String>,
)

/**
 * Turns walking paths into a graph A* can search.
 *
 * Three things happen, in order:
 *  1. every coordinate of every `LineString` becomes a node, consecutive ones become edges;
 *  2. nodes closer together than [nodeMergeRadiusMeters] collapse into one - this is what
 *     actually connects two paths that cross, because a surveyor walking the same junction
 *     twice never records the exact same coordinate;
 *  3. each POI is projected onto the nearest edge and a node is inserted at that spot, so a
 *     route can start or end at the POI itself instead of at some nearby path vertex.
 */
class RouteGraphBuilder(
    private val nodeMergeRadiusMeters: Double = DEFAULT_NODE_MERGE_RADIUS_M,
    private val maxSnapDistanceMeters: Double = DEFAULT_MAX_SNAP_DISTANCE_M,
) {

    private class MutableSegment(
        var fromNode: Int,
        var toNode: Int,
        val pathId: String,
        val type: PathType,
        val oneway: Boolean,
    )

    private val nodes = ArrayList<GeoPoint>()
    private val segments = ArrayList<MutableSegment>()

    /** Spatial hash of node indices, so merging does not degrade to an O(n^2) scan. */
    private val grid = HashMap<Long, MutableList<Int>>()
    private var cellSizeDegrees = 0.0

    fun build(
        paths: List<WalkPath>,
        snapTargets: Map<String, GeoPoint> = emptyMap(),
    ): BuiltRouteGraph {
        nodes.clear()
        segments.clear()
        grid.clear()

        // Cell size is derived from the first available coordinate, since metres-per-degree
        // of longitude depends on latitude.
        val reference = paths.firstNotNullOfOrNull { it.points.firstOrNull() }
            ?: snapTargets.values.firstOrNull()
        cellSizeDegrees = if (reference == null) {
            0.0
        } else {
            val (latDelta, lonDelta) = GeoUtils.degreesForMeters(nodeMergeRadiusMeters, reference)
            maxOf(latDelta, lonDelta)
        }

        paths.forEach(::addPath)

        val snappedNodes = LinkedHashMap<String, Int>()
        val snapDistances = LinkedHashMap<String, Double>()
        val unsnapped = ArrayList<String>()
        snapTargets.forEach { (id, point) ->
            val snapped = snapOntoNetwork(point)
            if (snapped == null) {
                unsnapped += id
            } else {
                snappedNodes[id] = snapped.nodeId
                snapDistances[id] = snapped.distanceMeters
            }
        }

        return BuiltRouteGraph(
            graph = toGraph(),
            snappedNodes = snappedNodes,
            snapDistances = snapDistances,
            unsnappedIds = unsnapped,
        )
    }

    // ----------------------------------------------------------------------------------
    // Step 1 + 2: nodes and edges, with merging
    // ----------------------------------------------------------------------------------

    private fun addPath(path: WalkPath) {
        if (path.points.size < 2) return
        var previous = -1
        for (point in path.points) {
            val node = addOrMergeNode(point)
            // A zero-length edge appears when two consecutive coordinates merge into the
            // same node; skip it rather than poisoning the graph with weight-0 self loops.
            if (previous >= 0 && previous != node) {
                segments += MutableSegment(previous, node, path.id, path.type, path.oneway)
            }
            previous = node
        }
    }

    /** Returns an existing node within the merge radius, or creates a new one. */
    private fun addOrMergeNode(point: GeoPoint): Int {
        findNearbyNode(point)?.let { return it }
        val index = nodes.size
        nodes += point
        gridCellsAround(point, includeNeighbours = false).forEach { key ->
            grid.getOrPut(key) { ArrayList() } += index
        }
        return index
    }

    private fun findNearbyNode(point: GeoPoint): Int? {
        if (cellSizeDegrees <= 0.0) return null
        var best: Int? = null
        var bestDistance = nodeMergeRadiusMeters
        for (key in gridCellsAround(point, includeNeighbours = true)) {
            val bucket = grid[key] ?: continue
            for (index in bucket) {
                val d = GeoUtils.approxDistanceMeters(point, nodes[index])
                if (d <= bestDistance) {
                    bestDistance = d
                    best = index
                }
            }
        }
        return best
    }

    private fun gridCellsAround(point: GeoPoint, includeNeighbours: Boolean): List<Long> {
        if (cellSizeDegrees <= 0.0) return listOf(0L)
        val latCell = floor(point.lat / cellSizeDegrees).toLong()
        val lonCell = floor(point.lon / cellSizeDegrees).toLong()
        if (!includeNeighbours) return listOf(cellKey(latCell, lonCell))
        return buildList(9) {
            for (dLat in -1..1) {
                for (dLon in -1..1) {
                    add(cellKey(latCell + dLat, lonCell + dLon))
                }
            }
        }
    }

    private fun cellKey(latCell: Long, lonCell: Long): Long = latCell * 73_856_093L xor lonCell

    // ----------------------------------------------------------------------------------
    // Step 3: snap POIs onto the network
    // ----------------------------------------------------------------------------------

    private data class Snapped(val nodeId: Int, val distanceMeters: Double)

    /**
     * Projects [point] onto the closest segment and returns the node to route from.
     *
     * If the projection lands on (or within the merge radius of) an existing node that node is
     * reused; otherwise the segment is split in two around a newly inserted node.
     */
    private fun snapOntoNetwork(point: GeoPoint): Snapped? {
        var bestIndex = -1
        var bestPoint: GeoPoint? = null
        var bestDistance = maxSnapDistanceMeters

        segments.forEachIndexed { index, segment ->
            val a = nodes[segment.fromNode]
            val b = nodes[segment.toNode]
            val t = GeoUtils.segmentProjectionFactor(point, a, b)
            val onSegment = GeoUtils.interpolate(a, b, t)
            val distance = GeoUtils.haversineMeters(point, onSegment)
            if (distance <= bestDistance) {
                bestDistance = distance
                bestIndex = index
                bestPoint = onSegment
            }
        }

        val projection = bestPoint ?: return null
        val segment = segments[bestIndex]

        findNearbyNode(projection)?.let { return Snapped(it, bestDistance) }

        val newNode = addOrMergeNode(projection)
        // Replace the original segment with the two halves around the inserted node,
        // keeping the direction so `oneway` still means the same thing.
        val originalTo = segment.toNode
        segment.toNode = newNode
        segments += MutableSegment(newNode, originalTo, segment.pathId, segment.type, segment.oneway)
        return Snapped(newNode, bestDistance)
    }

    // ----------------------------------------------------------------------------------
    // Freeze
    // ----------------------------------------------------------------------------------

    private fun toGraph(): RouteGraph {
        val adjacency = List(nodes.size) { ArrayList<RouteGraph.Edge>() }
        val frozenSegments = ArrayList<RouteGraph.Segment>(segments.size)

        for (segment in segments) {
            val a = nodes[segment.fromNode]
            val b = nodes[segment.toNode]
            val distance = GeoUtils.haversineMeters(a, b)
            val weight = distance * segment.type.weightMultiplier

            adjacency[segment.fromNode] += RouteGraph.Edge(
                target = segment.toNode,
                distanceMeters = distance,
                weight = weight,
                pathId = segment.pathId,
            )
            if (!segment.oneway) {
                adjacency[segment.toNode] += RouteGraph.Edge(
                    target = segment.fromNode,
                    distanceMeters = distance,
                    weight = weight,
                    pathId = segment.pathId,
                )
            }
            frozenSegments += RouteGraph.Segment(
                fromNode = segment.fromNode,
                toNode = segment.toNode,
                pathId = segment.pathId,
                type = segment.type,
            )
        }

        return RouteGraph(
            nodes = nodes.toList(),
            adjacency = adjacency.map { it.toList() },
            segments = frozenSegments,
        )
    }

    companion object {
        /**
         * Two path vertices this close are treated as the same junction. Chosen to be larger
         * than the sub-metre repeatability of a good GPS fix but smaller than the narrowest
         * gap between genuinely separate parallel footpaths.
         */
        const val DEFAULT_NODE_MERGE_RADIUS_M = 1.5

        /**
         * A POI further than this from any road or path cannot be routed to.
         *
         * Generous on purpose. A POI is usually the centre of a building footprint, and the
         * centre of a large one - the auditorium here is the extreme case - sits 60-70 m from
         * the nearest road even though walking there is trivial. The last stretch is drawn as
         * a straight line from the road to the marker, which is what a walk across a forecourt
         * looks like anyway. A tap the user makes is judged separately and much more strictly
         * by [TapValidator], which is where "you picked the middle of a field" belongs.
         */
        const val DEFAULT_MAX_SNAP_DISTANCE_M = 120.0
    }
}
