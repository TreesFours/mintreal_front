package com.example.mistreal_mini.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScribeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val results = _results.asSharedFlow()

    // Android's SpeechRecognizer goes idle once it emits a result for one
    // utterance — without restarting it ourselves on each onResults/recoverable
    // onError, "live" scribing would actually stop after the user's first pause
    // instead of transcribing continuously until they tap stop.
    private var isActive = false
    private var accumulatedText = ""
    private var currentLanguage = "en-US"

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            timber.log.Timber.d("🎙️ Scribe Ready")
        }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            val message = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                else -> "Unknown error"
            }
            timber.log.Timber.e("🎙️ Scribe Error: $message ($error)")
            // No-match / timeout just means "silence since the last phrase" — keep
            // listening rather than treating it as a failure that ends the session.
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            if (isActive && recoverable) restartListening()
        }
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.get(0)?.let { finalPhrase ->
                accumulatedText = if (accumulatedText.isBlank()) finalPhrase else "$accumulatedText $finalPhrase"
                _results.tryEmit(accumulatedText)
            }
            if (isActive) restartListening()
        }
        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.get(0)?.let { partialPhrase ->
                val combined = if (accumulatedText.isBlank()) partialPhrase else "$accumulatedText $partialPhrase"
                _results.tryEmit(combined)
            }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun ensureRecognizer() {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(recognitionListener)
            }
        }
    }

    private fun restartListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLanguage)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer?.startListening(intent)
    }

    fun startScribing(language: String = "en-US") {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            timber.log.Timber.e("🎙️ Speech Recognition not available on this device")
            return
        }
        currentLanguage = language
        accumulatedText = ""
        isActive = true
        ensureRecognizer()
        restartListening()
    }

    fun stopScribing() {
        isActive = false
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        accumulatedText = ""
    }
}
