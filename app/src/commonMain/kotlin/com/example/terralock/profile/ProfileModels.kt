package com.example.terralock.profile

import com.example.terralock.routing.VehicleProfile
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarColorHex: Long = 0xFF2196F3,
    val vehicleProfile: VehicleProfile = VehicleProfile(),
    val createdAtMs: Long = System.currentTimeMillis()
)