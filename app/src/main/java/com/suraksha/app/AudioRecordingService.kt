package com.suraksha.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AudioRecordingService : Service() {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false

    companion object {
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, AudioRecordingService::class.java))
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, AudioRecordingService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        startRecording()
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, SurakshaApp.CHANNEL_TRACKING)
            .setContentTitle("Recording evidence")
            .setContentText("Audio saved locally. Uploads to cloud when internet available.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        startForeground(1003, notification)
    }

    private fun startRecording() {
        try {
            val dir = File(getExternalFilesDir(null), "SurakshaEvidence")
            if (!dir.exists()) dir.mkdirs()

            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(dir, "SOS_$ts.mp4")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
                isRecording = true
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun stopAndUpload() {
        try {
            if (isRecording) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                outputFile?.let { uploadToCloud(it) }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun uploadToCloud(file: File) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ref = FirebaseStorage.getInstance()
            .reference.child("evidence/$uid/${file.name}")
        ref.putFile(Uri.fromFile(file)).addOnSuccessListener {
            ref.downloadUrl.addOnSuccessListener { uri ->
                FirebaseFirestore.getInstance().collection("evidence").add(hashMapOf(
                    "uid" to uid,
                    "fileUrl" to uri.toString(),
                    "fileName" to file.name,
                    "uploadedAt" to FieldValue.serverTimestamp(),
                    "type" to "audio"
                ))
            }
        }
        // If no internet — file stays safely at:
        // Android/data/com.suraksha.app/files/SurakshaEvidence/
    }

    override fun onDestroy() { super.onDestroy(); stopAndUpload() }
    override fun onBind(intent: Intent?): IBinder? = null
}