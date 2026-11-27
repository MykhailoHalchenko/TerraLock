package com.example.terralock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.terralock.auth.GoogleSignInHelper
import com.example.terralock.map.PlatformMapView
import com.example.terralock.nav.AndroidSensorFusionEngine
import com.example.terralock.permissions.LocationPermissionManager
import com.example.terralock.service.TrackingForegroundService
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize

class MainActivity : ComponentActivity() {
    private lateinit var sensorEngine: AndroidSensorFusionEngine
    private lateinit var permissionManager: LocationPermissionManager
    private lateinit var googleSignInHelper: GoogleSignInHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { Firebase.initialize(this) } catch (_: Exception) {}

        sensorEngine = AndroidSensorFusionEngine(this)
        permissionManager = LocationPermissionManager(this)
        googleSignInHelper = GoogleSignInHelper(this)

        permissionManager.requestForegroundPermissions {
            sensorEngine.start()
            TrackingForegroundService.startService(this, "GNSS")
            permissionManager.requestBackgroundPermission {}
        }

        setContent {
            App(
                sensorEngine = sensorEngine,
                mapView = { params ->
                    PlatformMapView(params)
                },
                onGoogleSignInClick = {
                    googleSignInHelper.signInWithGoogle()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorEngine.isInitialized) sensorEngine.stop()
    }
}