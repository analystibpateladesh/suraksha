package com.suraksha.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        var lastLat: Double = 0.0
        var lastLng: Double = 0.0

        // Helper to get one-time location reading
        fun getLastLocation(context: Context, callback: (Double, Double) -> Unit) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            try {
                client.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        lastLat = location.latitude
                        lastLng = location.longitude
                        callback(location.latitude, location.longitude)
                    } else {
                        // Request fresh location if last is null
                        val request = LocationRequest.Builder(
                            Priority.PRIORITY_HIGH_ACCURACY, 1000L).setMaxUpdates(1).build()
                        client.requestLocationUpdates(request,
                            object : LocationCallback() {
                                override fun onLocationResult(result: LocationResult) {
                                    result.lastLocation?.let {
                                        lastLat = it.latitude
                                        lastLng = it.longitude
                                        callback(it.latitude, it.longitude)
                                    }
                                    client.removeLocationUpdates(this)
                                }
                            }, context.mainLooper)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithNotification()
        startLocationUpdates()
        return START_STICKY  // Restart automatically if killed by system
    }

    // ── Foreground Notification ────────────────────────────────────────────
    // Required to keep service alive when screen is off

    private fun startForegroundWithNotification() {
        val notification = NotificationCompat.Builder(this, SurakshaApp.CHANNEL_TRACKING)
            .setContentTitle("🛡️ Suraksha — SOS Active")
            .setContentText("Your location is being shared with your trusted contacts")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)  // Can't be dismissed by user
            .addAction(
                android.R.drawable.ic_delete,
                "Cancel SOS",
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(1001, notification)
    }

    // ── Location Updates ───────────────────────────────────────────────────
    // Updates every 30 seconds — works even with screen off!

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            30_000L  // every 30 seconds
        ).apply {
            setMinUpdateIntervalMillis(15_000L)
            setMaxUpdateDelayMillis(60_000L)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    lastLat = location.latitude
                    lastLng = location.longitude
                    uploadLocationToFirestore(location.latitude, location.longitude)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    // ── Upload to Firestore ────────────────────────────────────────────────

    private fun uploadLocationToFirestore(lat: Double, lng: Double) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("live_locations").document(uid).set(
            hashMapOf(
                "uid" to uid,
                "lat" to lat,
                "lng" to lng,
                "mapsLink" to "https://maps.google.com/?q=$lat,$lng",
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}