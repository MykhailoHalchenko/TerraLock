package com.example.terralock.routing

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TraversabilityPreset {
    PAVED_ONLY, ALL_ROADS, OFFROAD_4X4
}

data class RoadNode(
    val id: Long,
    val lat: Double,
    val lon: Double,
    val elevationM: Double = 0.0
)

data class RoadEdge(
    val id: String,
    val fromId: Long,
    val toId: Long,
    val lengthM: Double,
    val maxSpeedKmh: Double,
    val highwayType: String,
    val oneWay: Boolean,
    val slopePct: Double = 0.0,
    val surface: String = "asphalt",
    val trackType: String = "none",
    val maxHeightM: Double? = null,
    val maxWidthM: Double? = null,
    val maxWeightT: Double? = null,
    val isSignalized: Boolean = false
)

class RoadGraph {
    val nodes = mutableMapOf<Long, RoadNode>()
    val adjacency = mutableMapOf<Long, MutableList<RoadEdge>>()

    fun addNode(node: RoadNode) {
        nodes[node.id] = node
    }

    fun addEdge(edge: RoadEdge) {
        adjacency.getOrPut(edge.fromId) { mutableListOf() }.add(edge)
        if (!edge.oneWay) {
            val reverseEdge = edge.copy(
                id = "${edge.id}_rev",
                fromId = edge.toId,
                toId = edge.fromId,
                slopePct = -edge.slopePct
            )
            adjacency.getOrPut(edge.toId) { mutableListOf() }.add(reverseEdge)
        }
    }

    fun findNearestNode(lat: Double, lon: Double): RoadNode? {
        if (nodes.isEmpty()) return null
        var nearestNode: RoadNode? = null
        var minDistance = Double.MAX_VALUE

        for (node in nodes.values) {
            val dist = calculateHaversineDistance(lat, lon, node.lat, node.lon)
            if (dist < minDistance) {
                minDistance = dist
                nearestNode = node
            }
        }
        return nearestNode
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
}