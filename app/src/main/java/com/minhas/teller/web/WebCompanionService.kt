package com.minhas.teller.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.minhas.teller.MainActivity
import com.minhas.teller.R

class WebCompanionService : Service() {

    companion object {
        const val ACTION_START = "com.minhas.teller.web.START"
        const val ACTION_STOP = "com.minhas.teller.web.STOP"
        const val NOTIFICATION_ID = 9021
        const val CHANNEL_ID = "teller_web_companion_channel"
        const val PORT = 8080
    }

    private var webServer: TellerWebServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopWebServer()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            ACTION_START, null -> {
                startWebServer()
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startWebServer() {
        val ip = WebCompanionManager.getLocalIpAddress(this)
        val token = CompanionAuth.getOrCreateToken(this)
        if (webServer == null) {
            webServer = TellerWebServer(applicationContext, PORT)
            val success = webServer?.start() ?: false
            if (success) {
                WebCompanionManager.updateServerState(true, ip, PORT, token)
                WebCompanionManager.registerMdns(this, PORT)
                startForegroundCompat(buildNotification(ip, PORT, token))
            } else {
                WebCompanionManager.updateServerState(false, ip, PORT, token)
                stopSelf()
            }
        } else {
            WebCompanionManager.updateServerState(true, ip, PORT, token)
            startForegroundCompat(buildNotification(ip, PORT, token))
        }
    }

    private fun stopWebServer() {
        webServer?.stop()
        webServer = null
        WebCompanionManager.unregisterMdns()
        WebCompanionManager.updateServerState(false, null, PORT)
    }

    override fun onDestroy() {
        stopWebServer()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Teller Desktop Companion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running local web server for PC desktop companion"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(ip: String, port: Int, token: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val stopIntent = Intent(this, WebCompanionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        // Short address only: the notification shows on the lock screen, so it must not carry the secret token.
        val address = "$ip:$port"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Teller Desktop Companion Active")
            .setContentText("Open on PC: $address")
            .setSmallIcon(R.drawable.app_icon)
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Server", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
