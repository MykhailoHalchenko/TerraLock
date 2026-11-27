package com.example.terralock.map

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val LOCATION_SOURCE_ID = "terralock-location-source"
private const val LOCATION_LAYER_ID = "terralock-location-layer"
private const val ROUTE_SOURCE_ID = "terralock-route-source"
private const val ROUTE_LAYER_ID = "terralock-route-layer"
private const val TARGET_SOURCE_ID = "terralock-target-source"
private const val TARGET_LAYER_ID = "terralock-target-layer"

private const val OPEN_TOPO_STYLE = """
{
  "version": 8,
  "sources": {
    "opentopomap": {
      "type": "raster",
      "tiles": ["https://tile.opentopomap.org/{z}/{x}/{y}.png"],
      "tileSize": 256
    }
  },
  "layers": [
    {
      "id": "opentopomap-layer",
      "type": "raster",
      "source": "opentopomap",
      "minzoom": 0,
      "maxzoom": 17
    }
  ]
}
"""

@Composable
fun PlatformMapView(params: MapViewParams) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    remember { MapLibre.getInstance(context) }
    val mapView = remember { MapView(context) }
    var mapReady by remember { mutableStateOf(false) }

    var droppedPin by remember { mutableStateOf<LatLng?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(Bundle())
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    fun setupMapLayers(style: Style) {
        if (style.getSource(ROUTE_SOURCE_ID) == null) {
            val routeSource = GeoJsonSource(ROUTE_SOURCE_ID)
            style.addSource(routeSource)
            val routeLayer = LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                lineColor(if (params.isRouteFrozen) android.graphics.Color.MAGENTA else android.graphics.Color.BLUE),
                lineWidth(6f)
            )
            style.addLayer(routeLayer)
        }

        if (style.getSource(TARGET_SOURCE_ID) == null) {
            val targetSource = GeoJsonSource(TARGET_SOURCE_ID)
            style.addSource(targetSource)
            val targetLayer = CircleLayer(TARGET_LAYER_ID, TARGET_SOURCE_ID).withProperties(
                circleRadius(10f),
                circleColor(android.graphics.Color.RED),
                circleStrokeWidth(2f),
                circleStrokeColor(android.graphics.Color.WHITE)
            )
            style.addLayer(targetLayer)
        }

        if (style.getSource(LOCATION_SOURCE_ID) == null) {
            val locationSource = GeoJsonSource(LOCATION_SOURCE_ID)
            style.addSource(locationSource)
            val locationLayer = CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                circleRadius(9f),
                circleColor(if (params.navState.isSpoofed) android.graphics.Color.RED else if (params.navState.isDeadReckoning) android.graphics.Color.rgb(255, 140, 0) else android.graphics.Color.GREEN),
                circleStrokeWidth(2f),
                circleStrokeColor(android.graphics.Color.WHITE)
            )
            style.addLayer(locationLayer)
        }
    }

    fun updateMapData(style: Style) {
        if (params.navState.lat != 0.0 && params.navState.lon != 0.0) {
            val locationSource = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE_ID)
            locationSource?.setGeoJson(Point.fromLngLat(params.navState.lon, params.navState.lat))

            val locationLayer = style.getLayerAs<CircleLayer>(LOCATION_LAYER_ID)
            locationLayer?.setProperties(
                circleColor(if (params.navState.isSpoofed) android.graphics.Color.RED else if (params.navState.isDeadReckoning) android.graphics.Color.rgb(255, 140, 0) else android.graphics.Color.GREEN)
            )
        }

        droppedPin?.let { pin ->
            val targetSource = style.getSourceAs<GeoJsonSource>(TARGET_SOURCE_ID)
            targetSource?.setGeoJson(Point.fromLngLat(pin.longitude, pin.latitude))
        }

        if (params.routePath.size >= 2) {
            val routePoints = params.routePath.map { Point.fromLngLat(it.second, it.first) }
            val routeSource = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID)
            routeSource?.setGeoJson(LineString.fromLngLats(routePoints))

            val routeLayer = style.getLayerAs<LineLayer>(ROUTE_LAYER_ID)
            routeLayer?.setProperties(
                lineColor(if (params.isRouteFrozen) android.graphics.Color.MAGENTA else android.graphics.Color.BLUE)
            )
        }
    }

    LaunchedEffect(params.isMapTerrain) {
        if (mapReady) {
            mapView.getMapAsync { map ->
                val builder = if (params.isMapTerrain) {
                    Style.Builder().fromJson(OPEN_TOPO_STYLE)
                } else {
                    Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                }
                map.setStyle(builder) { style ->
                    setupMapLayers(style)
                    updateMapData(style)
                }
            }
        }
    }

    AndroidView(
        factory = { mapView },
        update = { view ->
            view.getMapAsync { map ->
                if (!mapReady) {
                    val builder = Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                    map.setStyle(builder) { style ->
                        mapReady = true
                        setupMapLayers(style)
                        updateMapData(style)

                        val initialTarget = LatLng(
                            if (params.navState.lat != 0.0) params.navState.lat else 37.422,
                            if (params.navState.lon != 0.0) params.navState.lon else -122.084
                        )
                        map.cameraPosition = CameraPosition.Builder().target(initialTarget).zoom(15.0).build()

                        map.addOnMapClickListener { latLng ->
                            droppedPin = latLng
                            params.onMapClickPin?.invoke(latLng.latitude, latLng.longitude)
                            params.onRouteRequested?.invoke(latLng.latitude, latLng.longitude)
                            true
                        }

                        map.addOnMapLongClickListener { latLng ->
                            droppedPin = latLng
                            params.onAddWaypointRequested?.invoke(latLng.latitude, latLng.longitude)
                            true
                        }
                    }
                } else {
                    map.getStyle { style ->
                        updateMapData(style)
                    }
                }
            }
        },
        modifier = params.modifier.fillMaxSize()
    )

    LaunchedEffect(params.navState.lat, params.navState.lon, params.zoomLocked) {
        if (mapReady && !params.zoomLocked && params.navState.lat != 0.0 && params.navState.lon != 0.0) {
            mapView.getMapAsync { map ->
                if (map.style?.isFullyLoaded == true) {
                    val target = LatLng(params.navState.lat, params.navState.lon)
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(target)
                                .zoom(map.cameraPosition.zoom.coerceAtLeast(15.0))
                                .build()
                        )
                    )
                }
            }
        }
    }
}