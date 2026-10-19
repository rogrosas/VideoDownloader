package com.example.videodl

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Descargas", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
    }
}
