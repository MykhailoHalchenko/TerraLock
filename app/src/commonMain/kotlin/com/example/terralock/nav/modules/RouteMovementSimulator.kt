package com.example.terralock.nav.modules

import com.example.terralock.data.RouteMode
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SimulatedMovementStep(
    val lat: Double,
    val lon: Double,
    val bearing: Float,
    val speedMps: Float,
    val isArrived: Boolean
)

class RouteMovementSimulator {
    private var routePoints: List<Pair<Double, Double>> = emptyList()
    private var currentSegmentIndex: Int = 0
    private var distanceInCurrentSegmentM: Double = 0.0
    private var isActive: Boolean = false
    private var lastBearing: Float = 0f

    fun setRoute(points: List<Pair<Double, Double>>) {
        routePoints = points
        reset()
    }

    fun reset() {
        currentSegmentIndex = 0
        distanceInCurrentSegmentM = 0.0
        isActive = routePoints.size >= 2
        lastBearing = 0f
    }

    fun isSimulating(): Boolean = isActive && routePoints.size >= 2

    fun stop() {
        isActive = false
    }

    fun tick(dtSeconds: Double, mode: RouteMode): SimulatedMovementStep? {
        if (!isActive || routePoints.size < 2) return null

        val speedMps = when (mode) {
            RouteMode.DRIVING, RouteMode.TAXI -> 13.5f
            RouteMode.TRANSIT -> 10.0f
            RouteMode.BICYCLING -> 5.5f
            RouteMode.WALKING -> 1.4f
        }

        val stepDistance = speedMps * dtSeconds
        distanceInCurrentSegmentM += stepDistance

        while (currentSegmentIndex < routePoints.size - 1) {
            val p1 = routePoints[currentSegmentIndex]
            val p2 = routePoints[currentSegmentIndex + 1]
            val segmentLength = calculateHaversineDistance(p1.first, p1.second, p2.first, p2.second)
            val segmentBearing = calculateBearing(p1.first, p1.second, p2.first, p2.second).toFloat()
            lastBearing = segmentBearing

            if (distanceInCurrentSegmentM <= segmentLength) {
                val fraction = if (segmentLength > 0.001) distanceInCurrentSegmentM / segmentLength else 1.0
                val lat = p1.first + (p2.first - p1.first) * fraction
                val lon = p1.second + (p2.second - p1.second) * fraction

                return SimulatedMovementStep(
                    lat = lat,
                    lon = lon,
                    bearing = segmentBearing,
                    speedMps = speedMps,
                    isArrived = false
                )
            } else {
                distanceInCurrentSegmentM -= segmentLength
                currentSegmentIndex++
            }
        }

        val lastPoint = routePoints.last()
        isActive = false
        return SimulatedMovementStep(
            lat = lastPoint.first,
            lon = lastPoint.second,
            bearing = lastBearing,
            speedMps = 0f,
            isArrived = true
        )
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