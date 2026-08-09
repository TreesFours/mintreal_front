package com.example.mistreal_mini.util

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceManager @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Timber.e(e, "TTS Init failed")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Timber.e("Language not supported")
            } else {
                isInitialized = true
                // 🚀 SPEED PATCH: Make AI sound more natural/conversational
                tts?.setSpeechRate(1.2f)
                tts?.setPitch(1.0f)
            }
        }
    }

    private var onComplete: (() -> Unit)? = null

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (isInitialized) {
            this.onComplete = onComplete
            val params = android.os.Bundle()
            params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MistrealTTS")
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "MistrealTTS")
            
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    this@VoiceManager.onComplete?.invoke()
                }
                override fun onError(utteranceId: String?) {}
            })
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
