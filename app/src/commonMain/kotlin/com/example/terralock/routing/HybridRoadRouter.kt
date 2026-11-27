package com.example.terralock.routing

import com.example.terralock.data.RouteMode
import kotlinx.serialization.Serializable
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class VehicleProfile(
    val heightM: Double? = null,
    val widthM: Double? = null,
    val weightT: Double? = null,
    val maxSlopePct: Double = 15.0
)

data class RouteSegmentResult(
    val points: List<Pair<Double, Double>>,
    val slopePcts: List<Double>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Long,
    val totalAscentMeters: Double,
    val totalDescentMeters: Double,
    val instructions: List<String>
)

class HybridRoadRouter(private val graph: RoadGraph) {

    private data class DijkstraState(
        val nodeId: Long,
        val edgeId: String,
        val cost: Double
    ) : Comparable<DijkstraState> {
        override fun compareTo(other: DijkstraState): Int = cost.compareTo(other.cost)
    }

    private data class AStarNode(
        val nodeId: Long,
        val gScore: Double,
        val fScore: Double
    ) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int = fScore.compareTo(other.fScore)
    }

    fun calculateRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        mode: RouteMode,
        profile: VehicleProfile = VehicleProfile()
    ): RouteSegmentResult {
        val startNode = graph.findNearestNode(startLat, startLon)
        val goalNode = graph.findNearestNode(destLat, destLon)

        if (startNode == null || goalNode == null) {
            return fallbackDirectLine(startLat, startLon, destLat, destLon)
        }

        if (graph.nodes.size > 2000) {
            return runBidirectionalAStar(startNode, goalNode, mode, profile)
        } else {
            return runEdgeBasedDijkstra(startNode, goalNode, mode, profile)
        }
    }

    private fun runEdgeBasedDijkstra(
        startNode: RoadNode,
        goalNode: RoadNode,
        mode: RouteMode,
        profile: VehicleProfile
    ): RouteSegmentResult {
        val pq = PriorityQueue<DijkstraState>()
        val minCosts = mutableMapOf<Pair<Long, String>, Double>()
        val cameFrom = mutableMapOf<Pair<Long, String>, Pair<Long, String>>()
        val edgeLookup = mutableMapOf<String, RoadEdge>()

        val initialEdges = graph.adjacency[startNode.id] ?: emptyList()
        for (edge in initialEdges) {
            if (!isEdgeAllowed(edge, mode, profile)) continue
            edgeLookup[edge.id] = edge
            val state = DijkstraState(edge.toId, edge.id, edge.lengthM / getSpeedMps(edge, mode))
            minCosts[Pair(edge.toId, edge.id)] = state.cost
            pq.add(state)
        }

        var finalState: Pair<Long, String>? = null

        while (pq.isNotEmpty()) {
            val current = pq.poll() ?: break
            if (current.nodeId == goalNode.id) {
                finalState = Pair(current.nodeId, current.edgeId)
                break
            }

            val currentCost = minCosts[Pair(current.nodeId, current.edgeId)] ?: Double.MAX_VALUE
            if (current.cost > currentCost) continue

            val currentEdge = edgeLookup[current.edgeId]
            val nextEdges = graph.adjacency[current.nodeId] ?: emptyList()

            for (nextEdge in nextEdges) {
                if (!isEdgeAllowed(nextEdge, mode, profile)) continue
                edgeLookup[nextEdge.id] = nextEdge

                var stepCost = nextEdge.lengthM / getSpeedMps(nextEdge, mode)

                if (currentEdge != null) {
                    val turnPenalty = calculateTurnPenalty(currentEdge, nextEdge)
                    stepCost += turnPenalty
                }

                if (abs(nextEdge.slopePct) > profile.maxSlopePct) {
                    stepCost *= 2.5
                }

                val newCost = current.cost + stepCost
                val nextKey = Pair(nextEdge.toId, nextEdge.id)

                if (newCost < (minCosts[nextKey] ?: Double.MAX_VALUE)) {
                    minCosts[nextKey] = newCost
                    cameFrom[nextKey] = Pair(current.nodeId, current.edgeId)
                    pq.add(DijkstraState(nextEdge.toId, nextEdge.id, newCost))
                }
            }
        }

        if (finalState == null) {
            return fallbackDirectLine(startNode.lat, startNode.lon, goalNode.lat, goalNode.lon)
        }

        return reconstructPath(finalState, cameFrom, edgeLookup, startNode)
    }

    private fun runBidirectionalAStar(
        startNode: RoadNode,
        goalNode: RoadNode,
        mode: RouteMode,
        profile: VehicleProfile
    ): RouteSegmentResult {
        val forwardPq = PriorityQueue<AStarNode>()
        val backwardPq = PriorityQueue<AStarNode>()

        val forwardG = mutableMapOf<Long, Double>()
        val backwardG = mutableMapOf<Long, Double>()

        val forwardCameFrom = mutableMapOf<Long, Long>()
        val backwardCameFrom = mutableMapOf<Long, Long>()

        val maxSpeed = 13.88

        forwardG[startNode.id] = 0.0
        forwardPq.add(AStarNode(startNode.id, 0.0, haversineMeters(startNode, goalNode) / maxSpeed))

        backwardG[goalNode.id] = 0.0
        backwardPq.add(AStarNode(goalNode.id, 0.0, haversineMeters(goalNode, startNode) / maxSpeed))

        var meetingNodeId: Long? = null
        var bestPathCost = Double.MAX_VALUE

        while (forwardPq.isNotEmpty() && backwardPq.isNotEmpty()) {
            val fHead = forwardPq.peek()
            val bHead = backwardPq.peek()
            if (fHead != null && bHead != null && fHead.fScore + bHead.fScore >= bestPathCost) {
                break
            }

            if (forwardPq.size <= backwardPq.size) {
                val curr = forwardPq.poll() ?: break
                val g = forwardG[curr.nodeId] ?: Double.MAX_VALUE
                if (curr.gScore > g) continue

                val edges = graph.adjacency[curr.nodeId] ?: emptyList()
                for (edge in edges) {
                    if (!isEdgeAllowed(edge, mode, profile)) continue
                    val newG = g + (edge.lengthM / getSpeedMps(edge, mode))
                    if (newG < (forwardG[edge.toId] ?: Double.MAX_VALUE)) {
                        forwardG[edge.toId] = newG
                        forwardCameFrom[edge.toId] = curr.nodeId
                        val h = haversineMeters(graph.nodes[edge.toId] ?: goalNode, goalNode) / maxSpeed
                        forwardPq.add(AStarNode(edge.toId, newG, newG + h))

                        if (backwardG.containsKey(edge.toId)) {
                            val totalCost = newG + (backwardG[edge.toId] ?: Double.MAX_VALUE)
                            if (totalCost < bestPathCost) {
                                bestPathCost = totalCost
                                meetingNodeId = edge.toId
                            }
                        }
                    }
                }
            } else {
                val curr = backwardPq.poll() ?: break
                val g = backwardG[curr.nodeId] ?: Double.MAX_VALUE
                if (curr.gScore > g) continue

                val edges = graph.adjacency[curr.nodeId] ?: emptyList()
                for (edge in edges) {
                    if (!isEdgeAllowed(edge, mode, profile)) continue
                    val newG = g + (edge.lengthM / getSpeedMps(edge, mode))
                    if (newG < (backwardG[edge.fromId] ?: Double.MAX_VALUE)) {
                        backwardG[edge.fromId] = newG
                        backwardCameFrom[edge.fromId] = curr.nodeId
                        val h = haversineMeters(graph.nodes[edge.fromId] ?: startNode, startNode) / maxSpeed
                        backwardPq.add(AStarNode(edge.fromId, newG, newG + h))

                        if (forwardG.containsKey(edge.fromId)) {
                            val totalCost = newG + (forwardG[edge.fromId] ?: Double.MAX_VALUE)
                            if (totalCost < bestPathCost) {
                                bestPathCost = totalCost
                                meetingNodeId = edge.fromId
                            }
                        }
                    }
                }
            }
        }

        if (meetingNodeId == null) {
            return fallbackDirectLine(startNode.lat, startNode.lon, goalNode.lat, goalNode.lon)
        }

        val forwardPath = mutableListOf<Long>()
        var curr: Long? = meetingNodeId
        while (curr != null) {
            forwardPath.add(curr)
            curr = forwardCameFrom[curr]
        }
        forwardPath.reverse()

        val backwardPath = mutableListOf<Long>()
        curr = backwardCameFrom[meetingNodeId]
        while (curr != null) {
            backwardPath.add(curr)
            curr = backwardCameFrom[curr]
        }

        val fullNodeIds = forwardPath + backwardPath
        val points = fullNodeIds.mapNotNull { graph.nodes[it] }.map { Pair(it.lat, it.lon) }

        return RouteSegmentResult(
            points = points,
            slopePcts = List(points.size) { 0.0 },
            totalDistanceMeters = calculatePolylineDistance(points),
            totalDurationSeconds = (bestPathCost).toLong().coerceAtLeast(1L),
            totalAscentMeters = 0.0,
            totalDescentMeters = 0.0,
            instructions = listOf("Follow route to destination")
        )
    }

    private fun isEdgeAllowed(edge: RoadEdge, mode: RouteMode, profile: VehicleProfile): Boolean {
        if (profile.heightM != null && edge.maxHeightM != null && profile.heightM > edge.maxHeightM) return false
        if (profile.widthM != null && edge.maxWidthM != null && profile.widthM > edge.maxWidthM) return false
        if (profile.weightT != null && edge.maxWeightT != null && profile.weightT > edge.maxWeightT) return false

        return when (mode) {
            RouteMode.WALKING -> edge.highwayType != "motorway" && edge.highwayType != "trunk"
            RouteMode.BICYCLING -> edge.highwayType != "motorway"
            RouteMode.DRIVING, RouteMode.TAXI -> edge.highwayType != "footway" && edge.highwayType != "pedestrian" && edge.highwayType != "steps"
            RouteMode.TRANSIT -> true
        }
    }

    private fun getSpeedMps(edge: RoadEdge, mode: RouteMode): Double {
        val baseSpeed = when (mode) {
            RouteMode.WALKING -> 1.38
            RouteMode.BICYCLING -> 4.16
            RouteMode.TRANSIT -> 8.33
            RouteMode.DRIVING, RouteMode.TAXI -> (edge.maxSpeedKmh / 3.6).coerceAtLeast(5.0)
        }
        return baseSpeed
    }

    private fun calculateTurnPenalty(fromEdge: RoadEdge, toEdge: RoadEdge): Double {
        val fromNode = graph.nodes[fromEdge.fromId] ?: return 0.0
        val midNode = graph.nodes[fromEdge.toId] ?: return 0.0
        val toNode = graph.nodes[toEdge.toId] ?: return 0.0

        val b1 = calculateBearing(fromNode.lat, fromNode.lon, midNode.lat, midNode.lon)
        val b2 = calculateBearing(midNode.lat, midNode.lon, toNode.lat, toNode.lon)

        var delta = (b2 - b1) % 360.0
        if (delta > 180.0) delta -= 360.0
        if (delta < -180.0) delta += 360.0

        return if (delta in -120.0..-45.0 && !toEdge.isSignalized) {
            8.0
        } else if (abs(delta) > 45.0) {
            3.0
        } else {
            0.0
        }
    }

    private fun reconstructPath(
        finalState: Pair<Long, String>,
        cameFrom: Map<Pair<Long, String>, Pair<Long, String>>,
        edgeLookup: Map<String, RoadEdge>,
        startNode: RoadNode
    ): RouteSegmentResult {
        val pathKeys = mutableListOf<Pair<Long, String>>()
        var curr: Pair<Long, String>? = finalState
        while (curr != null) {
            pathKeys.add(curr)
            curr = cameFrom[curr]
        }
        pathKeys.reverse()

        val points = mutableListOf<Pair<Double, Double>>()
        points.add(Pair(startNode.lat, startNode.lon))

        var totalDist = 0.0
        var totalTime = 0.0

        for (key in pathKeys) {
            val edge = edgeLookup[key.second] ?: continue
            val node = graph.nodes[edge.toId] ?: continue
            points.add(Pair(node.lat, node.lon))
            totalDist += edge.lengthM
            totalTime += edge.lengthM / (edge.maxSpeedKmh / 3.6)
        }

        return RouteSegmentResult(
            points = points,
            slopePcts = List(points.size) { 0.0 },
            totalDistanceMeters = totalDist,
            totalDurationSeconds = totalTime.toLong().coerceAtLeast(1L),
            totalAscentMeters = 0.0,
            totalDescentMeters = 0.0,
            instructions = listOf("Follow route along road network")
        )
    }

    private fun fallbackDirectLine(
        sLat: Double, sLon: Double, gLat: Double, gLon: Double
    ): RouteSegmentResult {
        val points = listOf(Pair(sLat, sLon), Pair(gLat, gLon))
        val dist = calculatePolylineDistance(points)
        return RouteSegmentResult(
            points = points,
            slopePcts = listOf(0.0, 0.0),
            totalDistanceMeters = dist,
            totalDurationSeconds = (dist / 13.88).toLong().coerceAtLeast(1L),
            totalAscentMeters = 0.0,
            totalDescentMeters = 0.0,
            instructions = listOf("Direct route fallback")
        )
    }

    private fun haversineMeters(n1: RoadNode, n2: RoadNode): Double {
        return calculateHaversineDistance(n1.lat, n1.lon, n2.lat, n2.lon)
    }

    private fun calculatePolylineDistance(points: List<Pair<Double, Double>>): Double {
        var sum = 0.0
        for (i in 0 until points.size - 1) {
            sum += calculateHaversineDistance(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second)
        }
        return sum
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearing = Math.toDegrees(atan2(y, x))
        return (bearing + 360.0) % 360.0
    }
}