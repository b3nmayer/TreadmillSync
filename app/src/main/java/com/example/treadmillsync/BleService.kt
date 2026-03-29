package com.example.treadmillsync

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class BleService : Service() {

    private val binder = LocalBinder()
    lateinit var bleManager: BleManager
        private set

    inner class LocalBinder : Binder() {
        fun getService(): BleService = this@BleService
    }

    override fun onCreate() {
        super.onCreate()
        bleManager = BleManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Broadcasting treadmill speed...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun updateSpeed(speedKmh: Float, unit: SpeedUnit) {
        bleManager.updateSpeed(speedKmh)
        
        val displaySpeed = if (unit == SpeedUnit.METRIC) speedKmh else speedKmh * 0.621371f
        val unitText = if (unit == SpeedUnit.METRIC) "km/h" else "mph"
        
        val paceString = if (speedKmh > 0.5f) {
            if (unit == SpeedUnit.METRIC) {
                val totalSeconds = (3600 / speedKmh).toInt()
                "%d:%02d min/km".format(totalSeconds / 60, totalSeconds % 60)
            } else {
                val speedMph = speedKmh * 0.621371f
                val totalSeconds = (3600 / speedMph).toInt()
                "%d:%02d min/mi".format(totalSeconds / 60, totalSeconds % 60)
            }
        } else "--:-- pace"

        updateNotification("Speed: %.1f %s | %s".format(displaySpeed, unitText, paceString))
    }

    private fun updateNotification(message: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(message))
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
            }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Treadmill Sync Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Treadmill Sync Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("BleService", "Service destroyed")
        bleManager.stopAdvertising()
    }

    companion object {
        private const val CHANNEL_ID = "BleServiceChannel"
        private const val NOTIFICATION_ID = 1
    }
}
