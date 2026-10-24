package com.example.mistreal_mini.ui.liveai

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.repository.LiveAiRepository
import com.example.mistreal_mini.util.DeviceIdProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.inject.Inject

/**
 * Backend-relayed live voice+video conversation with Gemini — see
 * liveAiRelayService.ts. Raw PCM mic capture/playback (AudioRecord/
 * AudioTrack) is genuinely new in this app; no prior code streamed audio
 * this way (VoiceRecorder.kt is whole-clip MediaRecorder only). Video
 * frames reuse LiveAiScreen's already-built CameraX capture, just sent
 * here instead of to the (reverted) Firebase AI Logic path.
 */
@HiltViewModel
class LiveAiViewModel @Inject constructor(
    private val repository: LiveAiRepository,
    private val deviceIdProvider: DeviceIdProvider
) : ViewModel() {

    // idle | connecting | active | ended | error
    private val _sessionState = MutableStateFlow("idle")
    val sessionState: StateFlow<String> = _sessionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var micJob: Job? = null
    private var collectorsStarted = false

    companion object {
        // Confirmed against Google's own Live API WebSocket reference —
        // input and output run at different rates, not a typo.
        private const val INPUT_SAMPLE_RATE = 16000
        private const val OUTPUT_SAMPLE_RATE = 24000
    }

    fun connect() {
        if (_sessionState.value == "connecting" || _sessionState.value == "active") return
        _sessionState.value = "connecting"
        _errorMessage.value = null
        ensureCollectors()
        repository.connect()
        viewModelScope.launch {
            repository.startSession(deviceIdProvider.deviceId)
                .onSuccess {
                    _sessionState.value = "active"
                    startMicStreaming()
                    startPlayback()
                }
                .onFailure {
                    _errorMessage.value = it.message ?: "Couldn't start the live session"
                    _sessionState.value = "error"
                }
        }
    }

    private fun ensureCollectors() {
        if (collectorsStarted) return
        collectorsStarted = true
        viewModelScope.launch {
            repository.audioChunkReceived.collect { chunk ->
                try {
                    val bytes = Base64.getDecoder().decode(chunk.data)
                    audioTrack?.write(bytes, 0, bytes.size)
                } catch (e: Exception) {
                    Timber.w(e, "LiveAi: failed to play audio chunk")
                }
            }
        }
        viewModelScope.launch {
            repository.sessionEnded.collect {
                _sessionState.value = "ended"
                stopStreaming()
            }
        }
        viewModelScope.launch {
            repository.sessionError.collect { message ->
                _errorMessage.value = message
                _sessionState.value = "error"
                stopStreaming()
            }
        }
    }

    // RECORD_AUDIO is already gated by LiveAiScreen before connect() is ever called.
    @SuppressLint("MissingPermission")
    private fun startMicStreaming() {
        val minBufferSize = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Timber.w("LiveAi: invalid AudioRecord buffer size for this device")
            return
        }
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, INPUT_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, minBufferSize * 2
        )
        audioRecord = record
        record.startRecording()

        micJob = viewModelScope.launch(Dispatchers.IO) {
            val buffer = ByteArray(minBufferSize)
            while (_sessionState.value == "active" && audioRecord != null) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    repository.sendAudioChunk(Base64.getEncoder().encodeToString(chunk))
                }
            }
        }
    }

    private fun startPlayback() {
        val minBufferSize = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(if (minBufferSize > 0) minBufferSize * 2 else 4096)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack = track
        track.play()
    }

    fun sendVideoFrame(bitmap: Bitmap) {
        if (_sessionState.value != "active") return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
                repository.sendVideoFrame(Base64.getEncoder().encodeToString(stream.toByteArray()))
            } catch (e: Exception) {
                Timber.w(e, "LiveAi: failed to send video frame")
            }
        }
    }

    private fun stopStreaming() {
        micJob?.cancel()
        micJob = null
        audioRecord?.let {
            try { it.stop() } catch (e: Exception) { /* already stopped */ }
            it.release()
        }
        audioRecord = null
        audioTrack?.let {
            try { it.stop() } catch (e: Exception) { /* already stopped */ }
            it.release()
        }
        audioTrack = null
    }

    fun disconnect() {
        repository.endSession()
        stopStreaming()
        repository.disconnect()
        _sessionState.value = "ended"
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
        repository.disconnect()
    }
}
