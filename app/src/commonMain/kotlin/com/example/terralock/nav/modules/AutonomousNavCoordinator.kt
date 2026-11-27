package com.example.terralock.nav.modules

import com.example.terralock.data.RouteMode
import com.example.terralock.nav.NavState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AutonomousNavCoordinator {
    val spoofingDetector = SpoofingDetectorModule()
    val deadReckoning = DeadReckoningModule()
    val mapMatching = MapMatchingModule()
    val movementSimulator = RouteMovementSimulator()

    private val _navState = MutableStateFlow(NavState())
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    private var activeRoutePolyline: List<Pair<Double, Double>> = emptyList()

    fun setActiveRoute(polyline: List<Pair<Double, Double>>) {
        activeRoutePolyline = polyline
        movementSimulator.setRoute(polyline)
        _navState.update { it.copy(hasArrived = false) }
    }

    fun setSimulatedSpoofing(enabled: Boolean) {
        spoofingDetector.setSimulatedSpoof(enabled)
        val diags = spoofingDetector.analyze(
            GnssTelemetry(
                lat = _navState.value.lat,
                lon = _navState.value.lon,
                speedMps = _navState.value.speedMps,
                bearing = _navState.value.heading,
                timestampMs = System.currentTimeMillis()
            )
        )
        _navState.update { current ->
            current.copy(
                isSpoofed = enabled,
                isDeadReckoning = enabled,
                spoofDiagnostics = diags
            )
        }
    }

    fun setSimulateMovement(enabled: Boolean, mode: RouteMode = RouteMode.DRIVING): Boolean {
        if (enabled) {
            if (activeRoutePolyline.size < 2) {
                return false
            }
            movementSimulator.reset()
            deadReckoning.isSimulateMovement = true
            _navState.update { it.copy(hasArrived = false) }
            return true
        } else {
            movementSimulator.stop()
            deadReckoning.stopAndZeroVelocity()
            return true
        }
    }

    fun tickSimulationStep(dtSeconds: Double = 0.1, mode: RouteMode = RouteMode.DRIVING) {
        val step = movementSimulator.tick(dtSeconds, mode) ?: return

        if (step.isArrived) {
            deadReckoning.stopAndZeroVelocity()
            _navState.update {
                it.copy(
                    lat = step.lat,
                    lon = step.lon,
                    heading = step.bearing,
                    speedMps = 0f,
                    hasArrived = true
                )
            }
            return
        }

        if (!_navState.value.isSpoofed) {
            val matched = mapMatching.matchToRoute(
                rawLat = step.lat,
                rawLon = step.lon,
                rawHeading = step.bearing,
                routePolyline = activeRoutePolyline
            )
            _navState.update {
                it.copy(
                    lat = matched.lat,
                    lon = matched.lon,
                    heading = matched.heading,
                    speedMps = step.speedMps,
                    isSpoofed = false,
                    isDeadReckoning = false,
                    hasArrived = false
                )
            }
        } else {
            deadReckoning.seedLastKnownFix(step.lat, step.lon, step.bearing, step.speedMps)
            val drState = deadReckoning.processSensors(0f, 9.8f, System.nanoTime())
            val matched = mapMatching.matchToRoute(
                rawLat = drState.lat,
                rawLon = drState.lon,
                rawHeading = drState.heading,
                routePolyline = activeRoutePolyline
            )
            _navState.update {
                it.copy(
                    lat = matched.lat,
                    lon = matched.lon,
                    heading = matched.heading,
                    speedMps = drState.speedMps,
                    isDeadReckoning = true,
                    hasArrived = false
                )
            }
        }
    }

    fun processGnssUpdate(telemetry: GnssTelemetry) {
        if (spoofingDetector.isSimulatedSpoofActive()) {
            return
        }

        val diagnostics = spoofingDetector.analyze(telemetry)

        if (diagnostics.isSpoofed) {
            deadReckoning.seedLastKnownFix(
                _navState.value.lat,
                _navState.value.lon,
                _navState.value.heading,
                _navState.value.speedMps
            )
            _navState.update {
                it.copy(
                    isSpoofed = true,
                    isDeadReckoning = true,
                    spoofDiagnostics = diagnostics
                )
            }
        } else {
            deadReckoning.seedLastKnownFix(
                telemetry.lat,
                telemetry.lon,
                telemetry.bearing,
                telemetry.speedMps
            )

            val matched = mapMatching.matchToRoute(
                rawLat = telemetry.lat,
                rawLon = telemetry.lon,
                rawHeading = telemetry.bearing,
                routePolyline = activeRoutePolyline
            )

            _navState.update {
                it.copy(
                    lat = matched.lat,
                    lon = matched.lon,
                    heading = matched.heading,
                    speedMps = telemetry.speedMps,
                    isSpoofed = false,
                    isDeadReckoning = false,
                    spoofDiagnostics = diagnostics
                )
            }
        }
    }

    fun processInertialSensor(gyroZ: Float, accelNorm: Float, timestampNs: Long) {
        if (_navState.value.isDeadReckoning) {
            val drState = deadReckoning.processSensors(gyroZ, accelNorm, timestampNs)

            val matched = mapMatching.matchToRoute(
                rawLat = drState.lat,
                rawLon = drState.lon,
                rawHeading = drState.heading,
                routePolyline = activeRoutePolyline
            )

            _navState.update {
                it.copy(
                    lat = matched.lat,
                    lon = matched.lon,
                    heading = matched.heading,
                    speedMps = drState.speedMps,
                    isDeadReckoning = true
                )
            }
        }
    }
}