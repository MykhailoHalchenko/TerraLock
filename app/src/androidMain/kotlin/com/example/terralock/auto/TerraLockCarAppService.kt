package com.example.terralock.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.validation.HostValidator

class TerraLockCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                return TerraLockCarScreen(carContext)
            }
        }
    }
}

class TerraLockCarScreen(carContext: CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val routingInfo = RoutingInfo.Builder()
            .setLoading(false)
            .build()

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Recenter")
                    .setOnClickListener {}
                    .build()
            )
            .build()

        return NavigationTemplate.Builder()
            .setNavigationInfo(routingInfo)
            .setActionStrip(actionStrip)
            .build()
    }
}