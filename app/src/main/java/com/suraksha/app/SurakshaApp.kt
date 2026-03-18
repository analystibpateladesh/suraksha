package com.suraksha.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp

class SurakshaApp : Application() {

    companion object {
        const val CHANNEL_SOS = "suraksha_sos"
        const val CHANNEL_HELPER = "suraksha_helper"
        const val CHANNEL_TRACKING = "suraksha_tracking"
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // SOS alert channel — high priority, makes sound
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_SOS, "SOS Alerts",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Emergency SOS alerts"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                }
            )

            // Helper alert channel — urgent
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_HELPER, "Helper Alerts",
                    NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nearby emergency alerts for helpers"
                    enableVibration(true)
                }
            )

            // Background tracking — silent, just shows in notification bar
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_TRACKING, "Location Tracking",
                    NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Background location tracking when SOS is active"
                }
            )
        }
    }
}