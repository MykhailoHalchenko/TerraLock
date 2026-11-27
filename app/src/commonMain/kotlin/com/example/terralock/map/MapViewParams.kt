package com.example.terralock.map

import androidx.compose.ui.Modifier
import com.example.terralock.data.RouteMode
import com.example.terralock.nav.NavState

data class MapViewParams(
    val modifier: Modifier = Modifier,
    val navState: NavState,
    val zoomLocked: Boolean = false,
    val isMapTerrain: Boolean = true,
    val routePath: List<Pair<Double, Double>> = emptyList(),
    val isRouteFrozen: Boolean = false,
    val selectedMode: RouteMode = RouteMode.DRIVING,
    val onMapClickPin: ((Double, Double) -> Unit)? = null,
    val onRouteRequested: ((Double, Double) -> Unit)? = null,
    val onAddWaypointRequested: ((Double, Double) -> Unit)? = null
)