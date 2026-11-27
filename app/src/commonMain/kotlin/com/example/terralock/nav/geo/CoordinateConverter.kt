package com.example.terralock.nav.geo

import kotlin.math.floor
import kotlin.math.roundToInt

enum class CoordinateFormat {
    WGS84, MGRS, UTM, MAIDENHEAD
}

object CoordinateConverter {

    fun formatCoordinates(lat: Double, lon: Double, format: CoordinateFormat): String {
        return when (format) {
            CoordinateFormat.WGS84 -> "${"%.5f".format(lat)}, ${"%.5f".format(lon)}"
            CoordinateFormat.UTM -> toUtm(lat, lon)
            CoordinateFormat.MGRS -> toMgrs(lat, lon)
            CoordinateFormat.MAIDENHEAD -> toMaidenhead(lat, lon)
        }
    }

    fun toUtm(lat: Double, lon: Double): String {
        val zoneNumber = floor((lon + 180) / 6).toInt() + 1
        val zoneLetter = getUtmLetter(lat)
        val easting = ((lon - (-183 + zoneNumber * 6)) * 111320 * cosDeg(lat) + 500000).roundToInt()
        val northing = (if (lat >= 0) lat * 111000 else 10000000 + lat * 111000).roundToInt()
        return "${zoneNumber}${zoneLetter} ${easting}E ${northing}N"
    }

    fun toMgrs(lat: Double, lon: Double): String {
        val utm = toUtm(lat, lon)
        val parts = utm.split(" ")
        if (parts.size < 3) return utm
        val gridZone = parts[0]
        val easting = parts[1].removeSuffix("E").padStart(6, '0')
        val northing = parts[2].removeSuffix("N").padStart(7, '0')

        val sq1 = "ABCDEFGHJDKLMNPQRSTUVWXYZ"[(easting.substring(0, 2).toIntOrNull() ?: 1) % 24]
        val sq2 = "ABCDEFGHJKLMNPQRSTUV"[(northing.substring(0, 2).toIntOrNull() ?: 1) % 20]

        return "$gridZone $sq1$sq2 ${easting.takeLast(5)} ${northing.takeLast(5)}"
    }

    fun toMaidenhead(lat: Double, lon: Double, precision: Int = 8): String {
        val adjustedLon = lon + 180.0
        val adjustedLat = lat + 90.0

        val f1 = (adjustedLon / 20.0).toInt()
        val f2 = (adjustedLat / 10.0).toInt()

        val rLon = adjustedLon - (f1 * 20.0)
        val rLat = adjustedLat - (f2 * 10.0)

        val d1 = (rLon / 2.0).toInt()
        val d2 = rLat.toInt()

        val rrLon = rLon - (d1 * 2.0)
        val rrLat = rLat - d2

        val s1 = (rrLon * 12.0).toInt()
        val s2 = (rrLat * 24.0).toInt()

        val c1 = ('A'.code + f1).toChar()
        val c2 = ('A'.code + f2).toChar()
        val c3 = ('0'.code + d1).toChar()
        val c4 = ('0'.code + d2).toChar()
        val c5 = ('a'.code + s1).toChar()
        val c6 = ('a'.code + s2).toChar()

        return "$c1$c2$c3$c4$c5$c6"
    }

    private fun getUtmLetter(lat: Double): Char {
        val letters = "CDEFGHJKLMNPQRSTUVWXX"
        val idx = ((lat + 80) / 8).toInt().coerceIn(0, 20)
        return letters[idx]
    }

    private fun cosDeg(deg: Double): Double {
        return Math.cos(Math.toRadians(deg))
    }
}