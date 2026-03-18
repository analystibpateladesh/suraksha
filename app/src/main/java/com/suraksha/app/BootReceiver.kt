package com.suraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // If helper mode was enabled before reboot, restart the listener
            val prefs = context.getSharedPreferences("suraksha_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("helper_mode", false)) {
                val serviceIntent = Intent(context, LocationService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}