package com.example.mistreal_mini.service

import android.app.*
import android.content.Intent
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.example.mistreal_mini.MainActivity
import com.example.mistreal_mini.R
import com.example.mistreal_mini.util.VoiceManager
import com.example.mistreal_mini.domain.usecase.HandleDistressUseCase
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import kotlinx.coroutines.*

@AndroidEntryPoint
class VoiceService : Service() {

    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var handleDistressUseCase: HandleDistressUseCase
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var speechRecognizer: SpeechRecognizer? = null
    private var isRadioMode = false
    private var isGuardianMode = false
    private var currentFeed: List<String> = emptyList()
    private var currentFeedIndex = 0

    companion object {
        const val ACTION_START_RADIO = "ACTION_START_RADIO"
        const val ACTION_START_GUARDIAN = "ACTION_START_GUARDIAN"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_FEED = "EXTRA_FEED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification("Mistreal Active", "Standby Mode"))
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RADIO -> {
                isRadioMode = true
                isGuardianMode = false
                currentFeed = intent.getStringArrayListExtra(EXTRA_FEED) ?: emptyList()
                currentFeedIndex = 0
                startRadioLoop()
            }
            ACTION_START_GUARDIAN -> {
                isRadioMode = false
                isGuardianMode = true
                startForeground(1, createNotification("Guardian Active", "Monitoring for distress..."))
                startListening()
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

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    if (isGuardianMode && rmsdB > 10.0f) { // Threshold for sudden loud noise
                        Timber.w("🔊 Possible distress signature detected!")
                        // Logic to trigger UseCase
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    if (isGuardianMode) startListening()
                }
                override fun onResults(results: Bundle?) {
                    if (isGuardianMode) startListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.startListening(intent)
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
        speechRecognizer?.destroy()
        voiceManager.stop()
    }
}
