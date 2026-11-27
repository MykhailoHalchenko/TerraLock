package com.example.terralock.nav.modules

import kotlin.math.pow
import kotlin.math.roundToInt

data class BaroAltitudeState(
    val currentPressureHpa: Double = 1013.25,
    val relativeAltitudeM: Double = 0.0,
    val estimatedFloor: Int = 0,
    val isCalibrated: Boolean = false
)

class BaroAltitudeModule {
    private var p0Hpa: Double = 1013.25
    private var filteredPressureHpa: Double = 1013.25
    private var entryAltitudeM: Double = 0.0
    private var isCalibrated: Boolean = false

    fun calibrateWithGnssAltitude(pressureHpa: Double, gnssAltitudeM: Double) {
        if (pressureHpa <= 0.0) return
        filteredPressureHpa = pressureHpa
        val ratio = 1.0 - (gnssAltitudeM / 44330.0)
        if (ratio > 0.0) {
            p0Hpa = pressureHpa / ratio.pow(5.255)
            entryAltitudeM = gnssAltitudeM
            isCalibrated = true
        }
    }

    fun processPressure(rawPressureHpa: Double, isGnssAvailable: Boolean, gnssAltitudeM: Double): BaroAltitudeState {
        if (rawPressureHpa <= 0.0) return BaroAltitudeState()

        filteredPressureHpa = (0.2 * rawPressureHpa) + (0.8 * filteredPressureHpa)

        if (isGnssAvailable) {
            calibrateWithGnssAltitude(filteredPressureHpa, gnssAltitudeM)
        }

        val currentAltitudeM = 44330.0 * (1.0 - (filteredPressureHpa / p0Hpa).pow(1.0 / 5.255))
        val relativeAltM = currentAltitudeM - entryAltitudeM
        val floor = (relativeAltM / 3.2).roundToInt()

        return BaroAltitudeState(
            currentPressureHpa = filteredPressureHpa,
            relativeAltitudeM = relativeAltM,
            estimatedFloor = floor,
            isCalibrated = isCalibrated
        )
    }
}