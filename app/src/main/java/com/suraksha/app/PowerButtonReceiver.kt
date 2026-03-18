package com.suraksha.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

// Detects 3 quick power button presses to trigger SOS
// Registered in AndroidManifest for ACTION_SCREEN_ON
// Each power press turns screen on → fires ACTION_SCREEN_ON
class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private var pressCount = 0
        private var lastPressTime = 0L
        private const val WINDOW_MS = 2000L
        private val handler = Handler(Looper.getMainLooper())
        private var resetRunnable: Runnable? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_SCREEN_ON) return

        val now = System.currentTimeMillis()
        if (now - lastPressTime > WINDOW_MS) pressCount = 0

        pressCount++
        lastPressTime = now

        resetRunnable?.let { handler.removeCallbacks(it) }

        if (pressCount >= 3) {
            pressCount = 0
            // Open app and auto-trigger SOS
            val sosIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("TRIGGER_SOS", true)
            }
            context.startActivity(sosIntent)
        } else {
            resetRunnable = Runnable { pressCount = 0 }
            handler.postDelayed(resetRunnable!!, WINDOW_MS)
        }
    }
}