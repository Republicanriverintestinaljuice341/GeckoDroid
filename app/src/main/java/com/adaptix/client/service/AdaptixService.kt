package com.adaptix.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.adaptix.client.MainActivity
import com.adaptix.client.R
import com.adaptix.client.viewmodel.MainViewModel

class AdaptixService : Service() {

    companion object {
        const val CHANNEL_ID_PERSISTENT = "adaptix_persistent"
        const val CHANNEL_ID_ALERTS = "adaptix_alerts"
        const val NOTIFICATION_ID_PERSISTENT = 1
        private var notificationCounter = 100

        fun nextNotificationId(): Int = ++notificationCounter
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildPersistentNotification("Connected")
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID_PERSISTENT,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // App swiped away — send clean WS close so server releases session
        MainViewModel.activeWsManager?.disconnect()
        MainViewModel.activeWsManager = null
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        MainViewModel.activeWsManager?.disconnect()
        MainViewModel.activeWsManager = null
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java)

        val persistent = NotificationChannel(
            CHANNEL_ID_PERSISTENT,
            "Adaptix Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps the connection alive in background"
            setShowBadge(false)
        }
        nm.createNotificationChannel(persistent)

        val alerts = NotificationChannel(
            CHANNEL_ID_ALERTS,
            "Adaptix Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New agent connections and important events"
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(alerts)
    }

    private fun buildPersistentNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_PERSISTENT)
            .setContentTitle("Adaptix C2")
            .setContentText(status)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }

    fun updateStatus(status: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID_PERSISTENT, buildPersistentNotification(status))
    }
}
