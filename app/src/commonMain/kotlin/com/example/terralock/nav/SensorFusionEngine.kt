package com.example.terralock.nav

import com.example.terralock.data.RouteMode
import com.example.terralock.nav.modules.AutonomousNavCoordinator
import com.example.terralock.nav.modules.SpoofDiagnostics
import kotlinx.coroutines.flow.StateFlow

data class NavState(
    val lat: Double = 37.422,
    val lon: Double = -122.084,
    val heading: Float = 0f,
    val speedMps: Float = 0f,
    val isSpoofed: Boolean = false,
    val isDeadReckoning: Boolean = false,
    val hasArrived: Boolean = false,
    val spoofDiagnostics: SpoofDiagnostics = SpoofDiagnostics()
)

interface SensorFusionEngine {
    val navState: StateFlow<NavState>
    val coordinator: AutonomousNavCoordinator
    fun start()
    fun stop()
    fun simulateSpoofing(enabled: Boolean)
    fun setSimulateMovement(enabled: Boolean, mode: RouteMode = RouteMode.DRIVING): Boolean
}