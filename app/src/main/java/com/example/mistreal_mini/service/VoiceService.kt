package com.example.mistreal_mini.service

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import com.example.mistreal_mini.MainActivity
import com.example.mistreal_mini.R
import com.example.mistreal_mini.util.VoiceManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class VoiceService : Service() {

    @Inject lateinit var voiceManager: VoiceManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isRadioMode = false
    private var currentFeed: List<String> = emptyList()
    private var currentFeedIndex = 0

    companion object {
        const val ACTION_START_RADIO = "ACTION_START_RADIO"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_FEED = "EXTRA_FEED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification("Mistreal Active", "Standby Mode"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RADIO -> {
                isRadioMode = true
                currentFeed = intent.getStringArrayListExtra(EXTRA_FEED) ?: emptyList()
                currentFeedIndex = 0
                startRadioLoop()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startRadioLoop() {
        if (currentFeedIndex < currentFeed.size) {
            val text = currentFeed[currentFeedIndex]
            updateNotification("Radio Mode", "Reading: ${text.take(20)}...")
            voiceManager.speak(text) {
                currentFeedIndex++
                serviceScope.launch {
                    delay(500)
                    startRadioLoop()
                }
            }
        } else {
            updateNotification("Radio Mode", "Feed Complete")
            isRadioMode = false
        }
    }

    private fun updateNotification(title: String, content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val stopIntent = Intent(this, VoiceService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "VOICE_CHANNEL")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop AI", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("VOICE_CHANNEL", "Mistreal Voice Hub", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        voiceManager.stop()
    }
}
