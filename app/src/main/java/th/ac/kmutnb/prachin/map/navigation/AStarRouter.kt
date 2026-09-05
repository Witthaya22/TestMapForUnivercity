package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import java.util.PriorityQueue

/**
 * A* shortest path over a [RouteGraph].
 *
 * The heuristic is the straight-line great-circle distance to the goal. Every path type
 * multiplier is >= 1.0, so edge weight is always at least the real distance and the heuristic
 * can never overestimate - which is what keeps A* optimal here.
 */
object AStarRouter {

    /** The path A* found between two nodes. */
    data class Result(
        val nodeIds: List<Int>,
        val points: List<GeoPoint>,
        /** Real walking distance, without path type multipliers. */
        val distanceMeters: Double,
        /** The weighted cost A* minimised, kept for diagnostics. */
        val weight: Double,
    )

    fun findPath(graph: RouteGraph, startNode: Int, goalNode: Int): Result? {
        if (startNode !in graph.nodes.indices || goalNode !in graph.nodes.indices) return null
        if (startNode == goalNode) {
            return Result(listOf(startNode), listOf(graph.nodes[startNode]), 0.0, 0.0)
        }

        val goalPoint = graph.nodes[goalNode]
        val nodeCount = graph.nodeCount

        val costFromStart = DoubleArray(nodeCount) { Double.MAX_VALUE }
        val distanceFromStart = DoubleArray(nodeCount) { Double.MAX_VALUE }
        val cameFrom = IntArray(nodeCount) { -1 }
        val settled = BooleanArray(nodeCount)

        costFromStart[startNode] = 0.0
        distanceFromStart[startNode] = 0.0

        // The f-score is captured in the queue entry rather than read from a mutable array,
        // because lowering a node's score while it sits in the heap would corrupt the ordering.
        // Superseded entries are discarded lazily via `settled`.
        val queue = PriorityQueue<Candidate>(compareBy({ it.fScore }, { it.node }))
        queue += Candidate(startNode, heuristic(graph.nodes[startNode], goalPoint))

        while (queue.isNotEmpty()) {
            val current = queue.poll()!!.node
            if (settled[current]) continue
            if (current == goalNode) break
            settled[current] = true

            for (edge in graph.adjacency[current]) {
                val next = edge.target
                if (settled[next]) continue
                val tentativeCost = costFromStart[current] + edge.weight
                if (tentativeCost < costFromStart[next]) {
                    costFromStart[next] = tentativeCost
                    distanceFromStart[next] = distanceFromStart[current] + edge.distanceMeters
                    cameFrom[next] = current
                    queue += Candidate(next, tentativeCost + heuristic(graph.nodes[next], goalPoint))
                }
            }
        }

        if (costFromStart[goalNode] == Double.MAX_VALUE) return null

        val nodeIds = ArrayList<Int>()
        var cursor = goalNode
        while (cursor != -1) {
            nodeIds += cursor
            if (cursor == startNode) break
            cursor = cameFrom[cursor]
        }
        nodeIds.reverse()

        val points = nodeIds.map { graph.nodes[it] }
        return Result(
            nodeIds = nodeIds,
            points = points,
            distanceMeters = distanceFromStart[goalNode],
            weight = costFromStart[goalNode],
        )
    }

    private class Candidate(val node: Int, val fScore: Double)

    private fun heuristic(from: GeoPoint, to: GeoPoint): Double = GeoUtils.haversineMeters(from, to)
}
