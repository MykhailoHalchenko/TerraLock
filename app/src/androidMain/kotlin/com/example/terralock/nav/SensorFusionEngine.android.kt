package com.example.terralock.nav

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.example.terralock.data.RouteMode
import com.example.terralock.nav.modules.AutonomousNavCoordinator
import com.example.terralock.nav.modules.GnssTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sqrt

class AndroidSensorFusionEngine(private val context: Context) : SensorFusionEngine, LocationListener, SensorEventListener {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    override val coordinator = AutonomousNavCoordinator()
    override val navState: StateFlow<NavState> = coordinator.navState

    private var latestCn0List: List<Double> = emptyList()
    private var latestSatCount: Int = 0

    private var simulationJob: Job? = null
    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val gnssCallback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(eventArgs: GnssMeasurementsEvent) {
            super.onGnssMeasurementsReceived(eventArgs)
            val measurements = eventArgs.measurements
            latestSatCount = measurements.size
            latestCn0List = measurements.map { it.cn0DbHz }
        }
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        runCatching {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, this)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                locationManager.registerGnssMeasurementsCallback(context.mainExecutor, gnssCallback)
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssMeasurementsCallback(gnssCallback)
            }
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun stop() {
        simulationJob?.cancel()
        runCatching {
            locationManager.removeUpdates(this)
            locationManager.unregisterGnssMeasurementsCallback(gnssCallback)
            sensorManager.unregisterListener(this)
        }
    }

    override fun simulateSpoofing(enabled: Boolean) {
        coordinator.setSimulatedSpoofing(enabled)
    }

    override fun setSimulateMovement(enabled: Boolean, mode: RouteMode): Boolean {
        val success = coordinator.setSimulateMovement(enabled, mode)
        if (success && enabled) {
            simulationJob?.cancel()
            simulationJob = engineScope.launch {
                while (isActive) {
                    delay(100L)
                    coordinator.tickSimulationStep(0.1, mode)
                }
            }
        } else if (!enabled) {
            simulationJob?.cancel()
        }
        return success
    }

    override fun onLocationChanged(location: Location) {
        val telemetry = GnssTelemetry(
            lat = location.latitude,
            lon = location.longitude,
            speedMps = location.speed,
            bearing = location.bearing,
            timestampMs = location.time,
            cn0DbHzList = latestCn0List,
            satelliteCount = latestSatCount
        )
        coordinator.processGnssUpdate(telemetry)
    }

    private var lastGyroZ = 0f

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]
                val accelNorm = sqrt(ax * ax + ay * ay + az * az)
                coordinator.processInertialSensor(lastGyroZ, accelNorm, event.timestamp)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}