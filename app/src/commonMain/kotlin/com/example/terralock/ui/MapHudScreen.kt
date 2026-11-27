package com.example.terralock.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.terralock.data.MultiModalRoutes
import com.example.terralock.data.RouteMode
import com.example.terralock.data.RouteRequest
import com.example.terralock.data.RoutesRepository
import com.example.terralock.map.MapViewParams
import com.example.terralock.nav.SensorFusionEngine
import com.example.terralock.nav.modules.SpoofFlag
import com.example.terralock.navigation.AppNavReducer
import com.example.terralock.routing.RouteLockController
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapHudScreen(
    navReducer: AppNavReducer,
    sensorEngine: SensorFusionEngine,
    mapView: @Composable (MapViewParams) -> Unit
) {
    val navState by sensorEngine.navState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val routesRepository = remember { RoutesRepository() }
    val routeLockController = remember { RouteLockController() }
    val routeLockState by routeLockController.state.collectAsState()

    var manualZoomLock by remember { mutableStateOf(false) }
    var testSpoofing by remember { mutableStateOf(false) }
    var simulateMovement by remember { mutableStateOf(false) }
    var isMapTerrain by remember { mutableStateOf(true) }

    var isTelemetryExpanded by remember { mutableStateOf(false) }
    var isControlSheetExpanded by remember { mutableStateOf(false) }

    var activeRoutes by remember { mutableStateOf<MultiModalRoutes?>(null) }
    var selectedMode by remember { mutableStateOf(RouteMode.DRIVING) }

    val selectedOption = activeRoutes?.routes?.get(selectedMode)

    val activePolyline = remember(activeRoutes, selectedMode, routeLockState) {
        if (routeLockState.isRouteFrozen && routeLockState.lockedPolyline.isNotEmpty()) {
            routeLockState.lockedPolyline
        } else {
            selectedOption?.polylinePoints ?: emptyList()
        }
    }

    LaunchedEffect(activePolyline) {
        sensorEngine.coordinator.setActiveRoute(activePolyline)
    }

    fun calculateRoutesTo(destLat: Double, destLon: Double) {
        scope.launch {
            val req = RouteRequest(
                originLat = if (navState.lat != 0.0) navState.lat else 37.422,
                originLon = if (navState.lon != 0.0) navState.lon else -122.084,
                destLat = destLat,
                destLon = destLon,
                waypoints = routeLockState.waypoints.map { Pair(it.lat, it.lon) }
            )
            val result = routesRepository.getRoutes(req)
            activeRoutes = result
            if (routeLockState.isRouteFrozen) {
                routeLockController.setRouteFrozen(true, result.routes[selectedMode]?.polylinePoints ?: emptyList())
            }
        }
    }

    fun toggleSimulateMovement() {
        if (!simulateMovement && activePolyline.size < 2) {
            scope.launch {
                snackbarHostState.showSnackbar("Tap a location on the map first")
            }
            return
        }
        val newState = !simulateMovement
        val success = sensorEngine.setSimulateMovement(newState, selectedMode)
        if (success) {
            simulateMovement = newState
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Tap a location on the map first")
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isTabletLandscape = maxWidth >= 600.dp

        mapView(
            MapViewParams(
                modifier = Modifier.fillMaxSize(),
                navState = navState,
                zoomLocked = manualZoomLock,
                isMapTerrain = isMapTerrain,
                routePath = activePolyline,
                isRouteFrozen = routeLockState.isRouteFrozen,
                selectedMode = selectedMode,
                onMapClickPin = { _, _ -> },
                onRouteRequested = { destLat, destLon -> calculateRoutesTo(destLat, destLon) },
                onAddWaypointRequested = { wpLat, wpLon -> calculateRoutesTo(wpLat, wpLon) }
            )
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )

        if (selectedOption?.isOfflineWarning == true) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 64.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Offline — No cached road graph available",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        activeRoutes?.let { routesMap ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                LazyRow(
                    modifier = Modifier.padding(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(RouteMode.entries.toTypedArray()) { mode ->
                        val option = routesMap.routes[mode]
                        val isSelected = mode == selectedMode
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedMode = mode },
                            modifier = Modifier.widthIn(min = 84.dp),
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = mode.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = option?.durationText ?: "N/A",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = if (activeRoutes != null) 72.dp else 16.dp, start = 16.dp)
                .animateContentSize(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            onClick = { isTelemetryExpanded = !isTelemetryExpanded }
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = if (navState.isDeadReckoning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (navState.isDeadReckoning) "IMU" else "GNSS",
                            color = if (navState.isDeadReckoning) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${"%.1f".format(navState.speedMps)} m/s",
                        style = com.example.terralock.ui.theme.MonoTextStyle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Risk: ${navState.spoofDiagnostics.score}%",
                        color = if (navState.spoofDiagnostics.score > 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = if (isTelemetryExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Telemetry",
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (isTelemetryExpanded) {
                    Spacer(Modifier.height(8.dp))
                    if (navState.isSpoofed) {
                        Text("⚠️ SPOOFING DETECTED", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                    }

                    if (navState.spoofDiagnostics.activeFlags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            navState.spoofDiagnostics.activeFlags.forEach { flag ->
                                val chipText = when (flag) {
                                    SpoofFlag.CN0_UNIFORM_OR_SPIKE -> "CN0"
                                    SpoofFlag.CLOCK_BIAS_JUMP -> "CLK"
                                    SpoofFlag.KINEMATIC_TELEPORT_OR_DOPPLER -> "KIN"
                                    SpoofFlag.IMU_CROSSCHECK_MISMATCH -> "IMU"
                                    SpoofFlag.CONSTELLATION_COLLAPSE -> "CONST"
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = chipText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Lat: ${"%.5f".format(navState.lat)}", style = com.example.terralock.ui.theme.MonoTextStyle)
                    Text("Lon: ${"%.5f".format(navState.lon)}", style = com.example.terralock.ui.theme.MonoTextStyle)
                    Text("Heading: ${"%.1f".format(navState.heading)}°", style = com.example.terralock.ui.theme.MonoTextStyle)
                }
            }
        }

        if (selectedOption != null && selectedOption.elevationProfile.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 90.dp)
                    .size(160.dp, 70.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Elevation", style = MaterialTheme.typography.labelSmall)
                        Text("+${selectedOption.totalAscentMeters.toInt()}m", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    val lineColor = MaterialTheme.colorScheme.secondary
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply { moveTo(0f, size.height); lineTo(size.width, 0f) }
                        drawPath(path, color = lineColor, style = Stroke(width = 4f))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Recenter")
            }
            SmallFloatingActionButton(
                onClick = { },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Reset Compass")
            }
        }

        if (isTabletLandscape) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .width(280.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = { toggleSimulateMovement() },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (simulateMovement) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (simulateMovement) Icons.Default.Clear else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (simulateMovement) "Stop Move" else "Simulate Move")
                    }

                    FilledTonalButton(
                        onClick = {
                            val newFrozen = !routeLockState.isRouteFrozen
                            routeLockController.setRouteFrozen(newFrozen, activePolyline)
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (routeLockState.isRouteFrozen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (routeLockState.isRouteFrozen) "Route Frozen" else "Lock Route")
                    }

                    if (activeRoutes != null) {
                        OutlinedButton(
                            onClick = {
                                activeRoutes = null
                                routeLockController.clearWaypoints()
                                sensorEngine.setSimulateMovement(false)
                                simulateMovement = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear Route")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Terrain Layer", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = isMapTerrain, onCheckedChange = { isMapTerrain = it })
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Zoom Lock", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = manualZoomLock, onCheckedChange = { manualZoomLock = it })
                    }

                    Button(
                        onClick = {
                            testSpoofing = !testSpoofing
                            sensorEngine.simulateSpoofing(testSpoofing)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (testSpoofing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (testSpoofing) "Stop Spoof" else "Test Spoof", color = if (testSpoofing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (activeRoutes == null) {
                        Text(
                            text = "Tap anywhere on the map to route",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = { toggleSimulateMovement() },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (simulateMovement) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(if (simulateMovement) Icons.Default.Clear else Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (simulateMovement) "Stop" else "Simulate")
                            }

                            FilledTonalButton(
                                onClick = {
                                    val newFrozen = !routeLockState.isRouteFrozen
                                    routeLockController.setRouteFrozen(newFrozen, activePolyline)
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = if (routeLockState.isRouteFrozen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (routeLockState.isRouteFrozen) "Frozen" else "Lock Route")
                            }

                            IconButton(onClick = { isControlSheetExpanded = !isControlSheetExpanded }) {
                                Icon(
                                    imageVector = if (isControlSheetExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Expand controls"
                                )
                            }
                        }
                    }

                    if (isControlSheetExpanded) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Terrain", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = isMapTerrain, onCheckedChange = { isMapTerrain = it })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Zoom Lock", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = manualZoomLock, onCheckedChange = { manualZoomLock = it })
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    testSpoofing = !testSpoofing
                                    sensorEngine.simulateSpoofing(testSpoofing)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = if (testSpoofing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (testSpoofing) "Stop Spoof" else "Test Spoof", color = if (testSpoofing) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (activeRoutes != null) {
                                OutlinedButton(
                                    onClick = {
                                        activeRoutes = null
                                        routeLockController.clearWaypoints()
                                        sensorEngine.setSimulateMovement(false)
                                        simulateMovement = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Clear Route")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}