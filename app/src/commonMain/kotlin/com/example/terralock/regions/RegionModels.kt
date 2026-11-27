package com.example.terralock.regions

import kotlinx.serialization.Serializable

@Serializable
data class CustomMapPoint(
    val id: String = "",
    val regionId: String? = null,
    val lat: Double,
    val lon: Double,
    val title: String,
    val category: String,
    val description: String,
    val details: Map<String, String> = emptyMap(),
    val authorId: String = "",
    val authorName: String = "",
    val updatedAtMs: Long = System.currentTimeMillis()
)

@Serializable
data class OfflineRegionPack(
    val id: String = "",
    val name: String,
    val description: String,
    val authorId: String = "",
    val authorName: String = "",
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minZoom: Double = 10.0,
    val maxZoom: Double = 15.0,
    val customPoints: List<CustomMapPoint> = emptyList(),
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val sizeBytes: Long = 0L
)