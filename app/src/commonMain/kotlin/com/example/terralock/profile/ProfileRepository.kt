package com.example.terralock.profile

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mobilenativefoundation.store.store5.Fetcher
import org.mobilenativefoundation.store.store5.Store
import org.mobilenativefoundation.store.store5.StoreBuilder
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import kotlinx.coroutines.flow.first

class ProfileRepository {
    private val firestore: FirebaseFirestore? by lazy {
        runCatching { Firebase.firestore }.getOrNull()
    }

    private val _currentProfile = MutableStateFlow<UserProfile?>(null)
    val currentProfile: StateFlow<UserProfile?> = _currentProfile.asStateFlow()

    private val store: Store<String, UserProfile> = StoreBuilder
        .from(
            fetcher = Fetcher.of { uid: String ->
                fetchProfileFromFirestore(uid)
            }
        )
        .build()

    suspend fun getProfile(uid: String): UserProfile? {
        return try {
            val response = store.stream(StoreReadRequest.cached(uid, refresh = false))
                .first { it is StoreReadResponse.Data }
            val profile = response.requireData()
            _currentProfile.value = profile
            profile
        } catch (_: Exception) {
            _currentProfile.value
        }
    }

    private suspend fun fetchProfileFromFirestore(uid: String): UserProfile {
        val doc = firestore?.collection("users")?.document(uid)?.get()
        return doc?.data(UserProfile.serializer()) ?: UserProfile(uid = uid)
    }

    suspend fun saveProfile(profile: UserProfile) {
        _currentProfile.value = profile
        runCatching {
            firestore?.collection("users")?.document(profile.uid)?.set(UserProfile.serializer(), profile)
        }
    }
}