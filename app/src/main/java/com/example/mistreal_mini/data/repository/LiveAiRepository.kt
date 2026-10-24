package com.example.mistreal_mini.data.repository

import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class LiveAiAudioChunk(val data: String, val mimeType: String?)

/**
 * Thin Socket.IO client around liveAiRelayService.ts's '/live-ai'
 * namespace — same shape as CallRepository/WatchRepository. This one never
 * touches Gemini directly; the backend holds the actual Google WebSocket
 * and GEMINI_API_KEY, same as every other AI feature in this app.
 */
@Singleton
class LiveAiRepository @Inject constructor() {

    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val audioChunkReceived = MutableSharedFlow<LiveAiAudioChunk>()
    val turnComplete = MutableSharedFlow<Unit>()
    val sessionEnded = MutableSharedFlow<Unit>()
    val sessionError = MutableSharedFlow<String>()

    fun connect() {
        if (socket?.connected() == true) return
        val opts = IO.Options().apply { forceNew = true }
        val s = IO.socket(com.example.mistreal_mini.BuildConfig.BASE_URL + "live-ai", opts)

        s.on("audio:chunk") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch {
                audioChunkReceived.emit(
                    LiveAiAudioChunk(
                        data = json.getString("data"),
                        mimeType = if (json.has("mimeType")) json.getString("mimeType") else null
                    )
                )
            }
        }
        s.on("turn:complete") { scope.launch { turnComplete.emit(Unit) } }
        s.on("session:ended") { scope.launch { sessionEnded.emit(Unit) } }
        s.on("session:error") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch { sessionError.emit(json.optString("error", "Live AI session error")) }
        }

        s.connect()
        socket = s
    }

    suspend fun startSession(deviceId: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val currentSocket = socket
            if (currentSocket == null) {
                cont.resume(Result.failure(Exception("Not connected")))
                return@suspendCancellableCoroutine
            }
            currentSocket.emit("session:start", arrayOf<Any>(JSONObject().put("deviceId", deviceId)), Ack { args ->
                val json = args.getOrNull(0) as? JSONObject
                if (json?.optBoolean("success") == true) {
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(Exception(json?.optString("error") ?: "Failed to start Live AI session")))
                }
            })
        }

    fun sendAudioChunk(base64: String) {
        socket?.emit("audio:chunk", JSONObject().put("data", base64))
    }

    fun sendVideoFrame(base64: String) {
        socket?.emit("video:frame", JSONObject().put("data", base64))
    }

    fun endSession() {
        socket?.emit("session:end")
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
