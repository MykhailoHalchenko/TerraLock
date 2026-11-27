package com.example.terralock.regions

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse

class OfflineRegionRepository {
    private val firestore: FirebaseFirestore? by lazy {
        runCatching { Firebase.firestore }.getOrNull()
    }

    private val _localPacks = MutableStateFlow<List<OfflineRegionPack>>(emptyList())
    val localPacks: StateFlow<List<OfflineRegionPack>> = _localPacks.asStateFlow()

    private val _customPoints = MutableStateFlow<List<CustomMapPoint>>(emptyList())
    val customPoints: StateFlow<List<CustomMapPoint>> = _customPoints.asStateFlow()

    private val store: Store<String, List<OfflineRegionPack>> = StoreBuilder
        .from(
            fetcher = Fetcher.of { _: String ->
                fetchRemoteRegionPacks()
            }
        )
        .build()

    suspend fun getRemoteRegionPacks(): List<OfflineRegionPack> {
        return try {
            val res = store.stream(StoreReadRequest.fresh("all_packs"))
                .first { it is StoreReadResponse.Data }
            res.requireData()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchRemoteRegionPacks(): List<OfflineRegionPack> {
        return try {
            val snapshot = firestore?.collection("region_packs")?.get()
            snapshot?.documents?.mapNotNull { doc ->
                runCatching { doc.data(OfflineRegionPack.serializer()) }.getOrNull()
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveRegionPack(pack: OfflineRegionPack) {
        _localPacks.value = _localPacks.value + pack.copy(isDownloaded = true)
        runCatching {
            firestore?.collection("region_packs")?.document(pack.id)?.set(OfflineRegionPack.serializer(), pack)
        }
    }

    suspend fun addCustomPoint(point: CustomMapPoint) {
        _customPoints.value = _customPoints.value + point
        runCatching {
            firestore?.collection("custom_points")?.document(point.id)?.set(CustomMapPoint.serializer(), point)
        }
    }
}