package com.example.terralock.nav.modules

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class MapMatchedPosition(
    val lat: Double,
    val lon: Double,
    val heading: Float,
    val isSnapped: Boolean,
    val distanceToSegmentMeters: Double
)

class MapMatchingModule {
    fun matchToRoute(
        rawLat: Double,
        rawLon: Double,
        rawHeading: Float,
        routePolyline: List<Pair<Double, Double>>
    ): MapMatchedPosition {
        if (routePolyline.size < 2) {
            return MapMatchedPosition(
                lat = rawLat,
                lon = rawLon,
                heading = rawHeading,
                isSnapped = false,
                distanceToSegmentMeters = 0.0
            )
        }

        var minDistance = Double.MAX_VALUE
        var bestSnappedLat = rawLat
        var bestSnappedLon = rawLon
        var bestHeading = rawHeading

        for (i in 0 until routePolyline.size - 1) {
            val p1 = routePolyline[i]
            val p2 = routePolyline[i + 1]

            val snapped = projectPointToSegment(rawLat, rawLon, p1.first, p1.second, p2.first, p2.second)
            val dist = calculateDistance(rawLat, rawLon, snapped.first, snapped.second)

            if (dist < minDistance) {
                minDistance = dist
                bestSnappedLat = snapped.first
                bestSnappedLon = snapped.second
                bestHeading = calculateBearing(p1.first, p1.second, p2.first, p2.second)
            }
        }

        return if (minDistance <= 25.0) {
            MapMatchedPosition(
                lat = bestSnappedLat,
                lon = bestSnappedLon,
                heading = bestHeading,
                isSnapped = true,
                distanceToSegmentMeters = minDistance
            )
        } else {
            MapMatchedPosition(
                lat = rawLat,
                lon = rawLon,
                heading = rawHeading,
                isSnapped = false,
                distanceToSegmentMeters = minDistance
            )
        }
    }

    private fun projectPointToSegment(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Pair<Double, Double> {
        val dx = bx - ax
        val dy = by - ay
        if (dx == 0.0 && dy == 0.0) return Pair(ax, ay)

        val t = (((px - ax) * dx) + ((py - ay) * dy)) / ((dx * dx) + (dy * dy))
        val clampedT = t.coerceIn(0.0, 1.0)
        return Pair(ax + clampedT * dx, ay + clampedT * dy)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val bearing = Math.toDegrees(atan2(y, x)).toFloat()
        return (bearing + 360f) % 360f
    }
}