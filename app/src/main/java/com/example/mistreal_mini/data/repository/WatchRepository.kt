package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.webrtc.SignalingChannel
import io.socket.client.Ack
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class ViewerJoinedEvent(val sessionId: String, val viewerDeviceId: String, val viewerSocketId: String)
data class SignalPayload(
    val sessionId: String,
    val fromSocketId: String,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)

/**
 * Thin Socket.IO client wrapper around watchSignalingService.ts's '/watch'
 * namespace — this repository only ever moves session bookkeeping and
 * WebRTC offer/answer/ICE JSON around. It never touches video/audio; that's
 * WatchHostService/the viewer's own PeerConnection using WebRTC directly,
 * reading from the Flows this class exposes to know when to act.
 */
@Singleton
class WatchRepository @Inject constructor() : SignalingChannel {

    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val viewerJoined = MutableSharedFlow<ViewerJoinedEvent>()
    val offerReceived = MutableSharedFlow<SignalPayload>()
    val answerReceived = MutableSharedFlow<SignalPayload>()
    val iceReceived = MutableSharedFlow<SignalPayload>()
    val sessionEnded = MutableSharedFlow<String>() // sessionId
    val viewerLeft = MutableSharedFlow<String>() // viewerSocketId
    val viewerRejected = MutableSharedFlow<String>() // reason

    fun connect() {
        if (socket?.connected() == true) return
        val opts = IO.Options().apply { forceNew = true }
        val s = IO.socket("https://mistreal-backend.onrender.com/watch", opts)

        s.on("viewer:joined") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch {
                viewerJoined.emit(
                    ViewerJoinedEvent(
                        sessionId = json.getString("sessionId"),
                        viewerDeviceId = json.getString("viewerDeviceId"),
                        viewerSocketId = json.getString("viewerSocketId")
                    )
                )
            }
        }
        s.on("signal:offer") { args -> emitSignal(args, offerReceived) }
        s.on("signal:answer") { args -> emitSignal(args, answerReceived) }
        s.on("signal:ice") { args -> emitSignal(args, iceReceived) }
        s.on("host:stopped") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch { sessionEnded.emit(json.getString("sessionId")) }
        }
        s.on("viewer:left") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch { viewerLeft.emit(json.getString("viewerSocketId")) }
        }
        s.on("viewer:rejected") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch { viewerRejected.emit(json.optString("reason", "host_at_capacity")) }
        }

        s.connect()
        socket = s
    }

    private fun emitSignal(args: Array<Any>, target: MutableSharedFlow<SignalPayload>) {
        val json = args.getOrNull(0) as? JSONObject ?: return
        scope.launch {
            target.emit(
                SignalPayload(
                    sessionId = json.optString("sessionId", ""),
                    fromSocketId = json.getString("fromSocketId"),
                    sdp = if (json.has("sdp")) json.getString("sdp") else null,
                    candidate = if (json.has("candidate")) json.getString("candidate") else null,
                    sdpMid = if (json.has("sdpMid")) json.getString("sdpMid") else null,
                    sdpMLineIndex = if (json.has("sdpMLineIndex")) json.getInt("sdpMLineIndex") else null
                )
            )
        }
    }

    suspend fun startHosting(deviceId: String, contextType: String = "standalone", contextId: String? = null): Result<String> =
        suspendCancellableCoroutine { cont ->
            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("contextType", contextType)
                if (contextId != null) put("contextId", contextId)
            }
            val currentSocket = socket
            if (currentSocket == null) {
                cont.resume(Result.failure(Exception("Not connected")))
                return@suspendCancellableCoroutine
            }
            currentSocket.emit("host:start", arrayOf<Any>(payload), Ack { args ->
                val json = args.getOrNull(0) as? JSONObject
                if (json?.optBoolean("success") == true) {
                    cont.resume(Result.success(json.getString("sessionId")))
                } else {
                    cont.resume(Result.failure(Exception(json?.optString("error") ?: "Failed to start hosting")))
                }
            })
        }

    suspend fun joinAsViewer(sessionId: String, deviceId: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val payload = JSONObject().apply {
                put("sessionId", sessionId)
                put("deviceId", deviceId)
            }
            val currentSocket = socket
            if (currentSocket == null) {
                cont.resume(Result.failure(Exception("Not connected")))
                return@suspendCancellableCoroutine
            }
            currentSocket.emit("viewer:join", arrayOf<Any>(payload), Ack { args ->
                val json = args.getOrNull(0) as? JSONObject
                if (json?.optBoolean("success") == true) {
                    cont.resume(Result.success(Unit))
                } else {
                    cont.resume(Result.failure(Exception(json?.optString("error") ?: "Failed to join session")))
                }
            })
        }

    override fun sendOffer(toPeerId: String, sdp: String) = emitSignalOut("signal:offer", toPeerId) { put("sdp", sdp) }
    override fun sendAnswer(toPeerId: String, sdp: String) = emitSignalOut("signal:answer", toPeerId) { put("sdp", sdp) }
    override fun sendIce(toPeerId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        emitSignalOut("signal:ice", toPeerId) {
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }
    override fun sendViewerRejected(toPeerId: String, reason: String) =
        emitSignalOut("viewer:rejected", toPeerId) { put("reason", reason) }

    private fun emitSignalOut(event: String, toSocketId: String, fill: JSONObject.() -> Unit) {
        val payload = JSONObject().apply {
            put("toSocketId", toSocketId)
            fill()
        }
        socket?.emit(event, payload)
    }

    fun stopHosting(sessionId: String) {
        socket?.emit("host:stop", JSONObject().put("sessionId", sessionId))
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
