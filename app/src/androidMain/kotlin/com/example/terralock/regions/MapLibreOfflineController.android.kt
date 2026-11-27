package com.example.terralock.regions

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition

class MapLibreOfflineController(private val context: Context) {
    private val offlineManager = OfflineManager.getInstance(context)

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    fun downloadRegion(pack: OfflineRegionPack, styleUrl: String = "https://tiles.openfreemap.org/styles/liberty", onComplete: () -> Unit) {
        val bounds = LatLngBounds.from(pack.maxLat, pack.maxLon, pack.minLat, pack.minLon)
        val pixelRatio = context.resources.displayMetrics.density
        val definition = OfflineTilePyramidRegionDefinition(
            styleUrl,
            bounds,
            pack.minZoom,
            pack.maxZoom,
            pixelRatio
        )

        val metadata = pack.id.toByteArray(Charsets.UTF_8)

        offlineManager.createOfflineRegion(
            definition,
            metadata,
            object : OfflineManager.CreateOfflineRegionCallback {
                override fun onCreate(offlineRegion: OfflineRegion) {
                    offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                        override fun onStatusChanged(status: OfflineRegionStatus) {
                            val progress = if (status.requiredResourceCount > 0) {
                                status.completedResourceCount.toFloat() / status.requiredResourceCount.toFloat()
                            } else 0f

                            _downloadProgress.update { current ->
                                current + (pack.id to progress)
                            }

                            if (status.isComplete) {
                                onComplete()
                            }
                        }

                        override fun onError(error: OfflineRegionError) {}
                        override fun mapboxTileCountLimitExceeded(limit: Long) {}
                    })

                    offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                }

                override fun onError(error: String) {}
            }
        )
    }
}