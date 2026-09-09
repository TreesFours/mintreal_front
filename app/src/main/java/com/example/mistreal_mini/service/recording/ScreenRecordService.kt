package com.example.mistreal_mini.service.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.mistreal_mini.R

class ScreenRecordService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when(action) {
            "START" -> startRecording()
            "PAUSE" -> pauseRecording()
            "RESUME" -> resumeRecording()
            "STOP" -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording() {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "recording")
            .setContentTitle("Mistreal Recording")
            .setContentText("Capturing tactical display...")
            .setSmallIcon(androidx.core.R.drawable.notification_bg)
            .build()
        startForeground(1001, notification)
    }

    private fun pauseRecording() {}
    private fun resumeRecording() {}
    private fun stopRecording() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("recording", "Screen Recording", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
