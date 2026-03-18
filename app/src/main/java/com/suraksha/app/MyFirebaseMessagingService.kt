package com.suraksha.app

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: "Emergency Nearby"
        val body = message.data["body"] ?: "Someone near you needs help"
        val mapsLink = message.data["mapsLink"] ?: ""
        showHelperNotification(title, body, mapsLink)
    }

    private fun showHelperNotification(title: String, body: String, mapsLink: String) {
        val mapsPendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(Intent.ACTION_VIEW, Uri.parse(mapsLink)),
            PendingIntent.FLAG_IMMUTABLE
        )
        val policePendingIntent = PendingIntent.getActivity(
            this, 1,
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:100")),
            PendingIntent.FLAG_IMMUTABLE
        )
        val appPendingIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SurakshaApp.CHANNEL_HELPER)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_mylocation, "Open Location", mapsPendingIntent)
            .addAction(android.R.drawable.ic_menu_call, "Call Police (100)", policePendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2001, notification)
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("helpers")
            .document(uid)
            .update("fcmToken", token)
    }
}