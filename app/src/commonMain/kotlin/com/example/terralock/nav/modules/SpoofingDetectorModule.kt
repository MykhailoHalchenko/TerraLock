package com.example.terralock.nav.modules

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class SpoofFlag {
    CN0_UNIFORM_OR_SPIKE,
    CLOCK_BIAS_JUMP,
    KINEMATIC_TELEPORT_OR_DOPPLER,
    IMU_CROSSCHECK_MISMATCH,
    CONSTELLATION_COLLAPSE
}

data class SpoofDiagnostics(
    val score: Int = 0,
    val activeFlags: Set<SpoofFlag> = emptySet(),
    val isSpoofed: Boolean = false
)

data class GnssTelemetry(
    val lat: Double,
    val lon: Double,
    val speedMps: Float,
    val bearing: Float,
    val timestampMs: Long,
    val cn0DbHzList: List<Double> = emptyList(),
    val clockBiasJumpNs: Long = 0L,
    val clockDriftNsPerSec: Double = 0.0,
    val dopplerSpeedMps: Float? = null,
    val satelliteCount: Int = 0,
    val constellationTypes: List<Int> = emptyList(),
    val satelliteElevationsDeg: List<Float> = emptyList(),
    val isImuStationary: Boolean = false,
    val imuGyroYawDeltaDeg: Float = 0f
)

class SpoofingDetectorModule {
    private var isSimulatedSpoof: Boolean = false
    private var lastValidFix: GnssTelemetry? = null

    private val scoreHistory = mutableListOf<Int>()
    private val cn0BaselineHistory = mutableListOf<Double>()
    private var currentSpoofedState: Boolean = false

    fun setSimulatedSpoof(enabled: Boolean) {
        isSimulatedSpoof = enabled
        if (!enabled) {
            scoreHistory.clear()
            currentSpoofedState = false
            lastValidFix = null
        } else {
            currentSpoofedState = true
        }
    }

    fun isSimulatedSpoofActive(): Boolean = isSimulatedSpoof

    fun analyze(telemetry: GnssTelemetry): SpoofDiagnostics {
        if (isSimulatedSpoof) {
            val flags = setOf(
                SpoofFlag.CN0_UNIFORM_OR_SPIKE,
                SpoofFlag.CLOCK_BIAS_JUMP,
                SpoofFlag.KINEMATIC_TELEPORT_OR_DOPPLER
            )
            return SpoofDiagnostics(score = 95, activeFlags = flags, isSpoofed = true)
        }

        val activeFlags = mutableSetOf<SpoofFlag>()
        var epochScore = 0

        if (telemetry.cn0DbHzList.size >= 4) {
            val mean = telemetry.cn0DbHzList.average()
            val stdDev = sqrt(telemetry.cn0DbHzList.sumOf { (it - mean) * (it - mean) } / telemetry.cn0DbHzList.size)

            cn0BaselineHistory.add(mean)
            if (cn0BaselineHistory.size > 10) cn0BaselineHistory.removeAt(0)
            val baselineMean = cn0BaselineHistory.average()

            if (stdDev < 1.8 || (cn0BaselineHistory.size >= 3 && (mean - baselineMean) > 6.0)) {
                activeFlags.add(SpoofFlag.CN0_UNIFORM_OR_SPIKE)
                epochScore += 30
            }
        }

        if (abs(telemetry.clockBiasJumpNs) > 500_000L || abs(telemetry.clockDriftNsPerSec) > 500.0) {
            activeFlags.add(SpoofFlag.CLOCK_BIAS_JUMP)
            epochScore += 30
        }

        var isTeleport = false
        lastValidFix?.let { prev ->
            val dt = (telemetry.timestampMs - prev.timestampMs) / 1000.0
            if (dt > 0) {
                val distMeters = calculateHaversineDistance(prev.lat, prev.lon, telemetry.lat, telemetry.lon)
                val impliedSpeed = distMeters / dt
                val accel = abs(impliedSpeed - prev.speedMps) / dt

                if (impliedSpeed > 55.0 || telemetry.speedMps > 55.0f || accel > 15.0) {
                    isTeleport = true
                }
            }
        }
        if (telemetry.dopplerSpeedMps != null && abs(telemetry.speedMps - telemetry.dopplerSpeedMps) > 12.0) {
            isTeleport = true
        }
        if (isTeleport) {
            activeFlags.add(SpoofFlag.KINEMATIC_TELEPORT_OR_DOPPLER)
            epochScore += 35
        }

        if (telemetry.isImuStationary && (telemetry.speedMps > 2.5f || (lastValidFix != null && calculateHaversineDistance(lastValidFix!!.lat, lastValidFix!!.lon, telemetry.lat, telemetry.lon) > 15.0))) {
            activeFlags.add(SpoofFlag.IMU_CROSSCHECK_MISMATCH)
            epochScore += 35
        } else if (telemetry.speedMps > 4.0f && lastValidFix != null) {
            val gnssBearingDelta = abs(telemetry.bearing - lastValidFix!!.bearing)
            if (abs(gnssBearingDelta - telemetry.imuGyroYawDeltaDeg) > 40.0f) {
                activeFlags.add(SpoofFlag.IMU_CROSSCHECK_MISMATCH)
                epochScore += 35
            }
        }

        if (telemetry.satelliteElevationsDeg.size >= 6) {
            val uniqueConstellations = telemetry.constellationTypes.distinct().size
            val elevMean = telemetry.satelliteElevationsDeg.map { it.toDouble() }.average()
            val elevStdDev = sqrt(telemetry.satelliteElevationsDeg.sumOf { (it - elevMean) * (it - elevMean) } / telemetry.satelliteElevationsDeg.size)

            if (uniqueConstellations <= 1 && elevStdDev < 3.0) {
                activeFlags.add(SpoofFlag.CONSTELLATION_COLLAPSE)
                epochScore += 20
            }
        }

        val clampedScore = epochScore.coerceIn(0, 100)

        scoreHistory.add(clampedScore)
        if (scoreHistory.size > 5) scoreHistory.removeAt(0)

        if (!currentSpoofedState) {
            if (scoreHistory.takeLast(2).count { it >= 65 } >= 2) {
                currentSpoofedState = true
            }
        } else {
            if (scoreHistory.takeLast(5).count { it < 25 } >= 5) {
                currentSpoofedState = false
            }
        }

        if (!currentSpoofedState) {
            lastValidFix = telemetry
        }

        return SpoofDiagnostics(
            score = clampedScore,
            activeFlags = activeFlags,
            isSpoofed = currentSpoofedState
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
}