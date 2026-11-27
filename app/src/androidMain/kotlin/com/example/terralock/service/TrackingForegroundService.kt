package com.example.terralock.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.terralock.MainActivity

class TrackingForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "terralock_tracking_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val EXTRA_MODE = "EXTRA_MODE"

        fun startService(context: Context, modeText: String = "GNSS") {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, modeText)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TrackingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val modeText = intent?.getStringExtra(EXTRA_MODE) ?: "GNSS"
        val notification = buildNotification(modeText)
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TerraLock Active Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays live navigation & sensor fusion mode"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(modeText: String): android.app.Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (applicationInfo.icon != 0) applicationInfo.icon else android.R.drawable.ic_menu_compass

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TerraLock Tracking Active")
            .setContentText("Mode: $modeText | Autonomous Sensor Fusion Running")
            .setSmallIcon(iconRes)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}