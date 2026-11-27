package com.example.terralock.routing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class Waypoint(
    val lat: Double,
    val lon: Double,
    val name: String = ""
)

data class RouteLockState(
    val isRouteFrozen: Boolean = false,
    val disableFasterRoutePrompts: Boolean = true,
    val waypoints: List<Waypoint> = emptyList(),
    val lockedPolyline: List<Pair<Double, Double>> = emptyList()
)

class RouteLockController {
    private val _state = MutableStateFlow(RouteLockState())
    val state: StateFlow<RouteLockState> = _state.asStateFlow()

    fun setRouteFrozen(frozen: Boolean, activePolyline: List<Pair<Double, Double>> = emptyList()) {
        _state.update { current ->
            current.copy(
                isRouteFrozen = frozen,
                lockedPolyline = if (frozen && activePolyline.isNotEmpty()) activePolyline else current.lockedPolyline
            )
        }
    }

    fun setDisableFasterRoutePrompts(disable: Boolean) {
        _state.update { it.copy(disableFasterRoutePrompts = disable) }
    }

    fun addWaypoint(lat: Double, lon: Double, name: String = "Stop ${state.value.waypoints.size + 1}") {
        _state.update { current ->
            current.copy(waypoints = current.waypoints + Waypoint(lat, lon, name))
        }
    }

    fun removeWaypoint(index: Int) {
        _state.update { current ->
            if (index in current.waypoints.indices) {
                current.copy(waypoints = current.waypoints.filterIndexed { i, _ -> i != index })
            } else current
        }
    }

    fun clearWaypoints() {
        _state.update { it.copy(waypoints = emptyList()) }
    }
}