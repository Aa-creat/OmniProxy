package io.github.aacreat.omniproxy

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class OmniProxyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Every Proxy Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active proxy state and network traffic"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "omni_proxy_service_channel"
        const val NOTIFICATION_ID = 1001
    }
}
