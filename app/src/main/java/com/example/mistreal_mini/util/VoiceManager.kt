package com.example.mistreal_mini.util

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import com.example.mistreal_mini.data.local.PreferenceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.resume

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    // ... recognized speech logic ...
    private val _transcripts = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val transcripts: SharedFlow<String> = _transcripts.asSharedFlow()

    fun emitTranscript(text: String) {
        _transcripts.tryEmit(text)
    }

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Timber.e(e, "TTS Init failed")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val voiceName = runBlocking { preferenceManager.ttsVoiceName.first() }
            val selectedVoice = tts?.voices?.find { it.name == voiceName }
            
            if (selectedVoice != null) {
                tts?.voice = selectedVoice
            } else {
                tts?.setLanguage(Locale.US)
            }
            
            isInitialized = true
            tts?.setSpeechRate(1.2f)
            tts?.setPitch(1.0f)
        }
    }

    private var onComplete: (() -> Unit)? = null

    // Some OEM TTS engines still fire onDone for an utterance that was just
    // stop()-ped instead of suppressing it — without this guard, that stale
    // callback re-triggers the hands-free listen/speak loop even after the
    // user pressed stop, which is exactly the "keeps talking" bug this guards.
    @Volatile private var isStopped = false

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            isStopped = false
            this.onComplete = onComplete
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MistrealTTS")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MistrealTTS")

            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (!isStopped) this@VoiceManager.onComplete?.invoke()
                }
                override fun onError(utteranceId: String?) {}
                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    isStopped = true
                }
            })
        }
    }

    /**
     * Renders [text] to a playable audio file instead of speaking it through the
     * speaker directly, so it can be shown as a replayable voice-note chat bubble
     * (WhatsApp-style) rather than an ephemeral speaker-only utterance.
     */
    suspend fun synthesizeToFile(text: String, outputFile: java.io.File): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            if (!isInitialized || tts == null) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            val utteranceId = "MistrealTTS_File_${System.currentTimeMillis()}"
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(true)
                }
                override fun onError(utteranceId: String?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            })
            val result = tts?.synthesizeToFile(text, null, outputFile, utteranceId)
            if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                continuation.resume(false)
            }
        }

    fun stop() {
        isStopped = true
        onComplete = null
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
