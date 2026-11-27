package com.example.terralock.nav.modules

import kotlin.math.cos
import kotlin.math.sin

data class InertialState(
    val lat: Double,
    val lon: Double,
    val heading: Float,
    val speedMps: Float,
    val isStationary: Boolean = false
)

class DeadReckoningModule {
    private var currentLat: Double = 37.422
    private var currentLon: Double = -122.084
    private var currentHeading: Float = 0f
    private var currentSpeedMps: Float = 0f

    private var lastTimestampNs: Long = 0L
    private val accelHistory = mutableListOf<Float>()
    private val r2d = (180.0 / Math.PI).toFloat()

    var isSimulateMovement: Boolean = false

    fun seedLastKnownFix(lat: Double, lon: Double, heading: Float, speedMps: Float) {
        if (lat != 0.0 && lon != 0.0) {
            currentLat = lat
            currentLon = lon
            currentHeading = heading
            currentSpeedMps = speedMps
        }
    }

    fun stopAndZeroVelocity() {
        currentSpeedMps = 0.0f
        isSimulateMovement = false
        accelHistory.clear()
    }

    fun processSensors(
        gyroZ: Float,
        accelNorm: Float,
        timestampNs: Long
    ): InertialState {
        if (lastTimestampNs == 0L) {
            lastTimestampNs = timestampNs
            return getCurrentState()
        }

        val rawDt = (timestampNs - lastTimestampNs) / 1_000_000_000.0f
        val dt = rawDt.coerceIn(0.0f, 0.2f)
        lastTimestampNs = timestampNs

        accelHistory.add(accelNorm)
        if (accelHistory.size > 10) {
            accelHistory.removeAt(0)
        }

        val isStationary = if (accelHistory.size >= 10) {
            val mean = accelHistory.average()
            val variance = accelHistory.sumOf { (it - mean) * (it - mean) } / accelHistory.size
            variance < 0.15
        } else false

        if (isStationary && !isSimulateMovement) {
            currentSpeedMps = 0.0f
        } else if (isSimulateMovement) {
            currentSpeedMps = 10.0f
        }

        val deltaHeading = gyroZ * r2d * dt
        currentHeading = (currentHeading - deltaHeading + 360f) % 360f

        val dist = currentSpeedMps * dt
        val headingRad = Math.toRadians(currentHeading.toDouble())

        if (dist > 0.05f && !isStationary) {
            val deltaLat = (dist * cos(headingRad)) / 111111.0
            val deltaLon = (dist * sin(headingRad)) / (111111.0 * cos(Math.toRadians(currentLat)))
            currentLat += deltaLat
            currentLon += deltaLon
        }

        return InertialState(
            lat = currentLat,
            lon = currentLon,
            heading = currentHeading,
            speedMps = currentSpeedMps,
            isStationary = isStationary
        )
    }

    fun getCurrentState(): InertialState = InertialState(
        lat = currentLat,
        lon = currentLon,
        heading = currentHeading,
        speedMps = currentSpeedMps
    )
}