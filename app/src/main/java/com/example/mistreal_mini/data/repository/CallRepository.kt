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

data class IncomingCallEvent(val callId: String, val callerDeviceId: String)
data class CallAcceptedEvent(val callId: String, val calleeSocketId: String)
data class CallSignalPayload(
    val callId: String,
    val fromSocketId: String,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)

/**
 * Thin Socket.IO client around callSignalingService.ts's '/call' namespace —
 * the symmetric counterpart to WatchRepository ('/watch'). No Nearby
 * fallback here (unlike Watch Hub) since a call that can't reach the other
 * party over the internet has no offline substitute the way a same-room
 * screencast does.
 */
@Singleton
class CallRepository @Inject constructor() {

    private var socket: Socket? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val incomingCall = MutableSharedFlow<IncomingCallEvent>()
    val callAccepted = MutableSharedFlow<CallAcceptedEvent>()
    val callDeclined = MutableSharedFlow<String>() // callId
    val callEnded = MutableSharedFlow<String>() // callId
    val offerReceived = MutableSharedFlow<CallSignalPayload>()
    val answerReceived = MutableSharedFlow<CallSignalPayload>()
    val iceReceived = MutableSharedFlow<CallSignalPayload>()

    fun connect(deviceId: String) {
        if (socket?.connected() == true) return
        val opts = IO.Options().apply { forceNew = true }
        val s = IO.socket(com.example.mistreal_mini.BuildConfig.BASE_URL + "call", opts)

        s.on("call:incoming") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch { incomingCall.emit(IncomingCallEvent(json.getString("callId"), json.getString("callerDeviceId"))) }
        }
        s.on("call:accepted") { args ->
            val json = args.getOrNull(0) as? JSONObject ?: return@on
            scope.launch { callAccepted.emit(CallAcceptedEvent(json.getString("callId"), json.getString("calleeSocketId"))) }
        }
        s.on("call:declined") { args -> emitCallId(args, callDeclined) }
        s.on("call:ended") { args -> emitCallId(args, callEnded) }
        s.on("signal:offer") { args -> emitSignal(args, offerReceived) }
        s.on("signal:answer") { args -> emitSignal(args, answerReceived) }
        s.on("signal:ice") { args -> emitSignal(args, iceReceived) }
        s.on(Socket.EVENT_CONNECT) { s.emit("register", JSONObject().put("deviceId", deviceId)) }

        s.connect()
        socket = s
    }

    private fun emitCallId(args: Array<Any>, target: MutableSharedFlow<String>) {
        val json = args.getOrNull(0) as? JSONObject ?: return
        scope.launch { target.emit(json.getString("callId")) }
    }

    private fun emitSignal(args: Array<Any>, target: MutableSharedFlow<CallSignalPayload>) {
        val json = args.getOrNull(0) as? JSONObject ?: return
        scope.launch {
            target.emit(
                CallSignalPayload(
                    callId = json.optString("callId", ""),
                    fromSocketId = json.getString("fromSocketId"),
                    sdp = if (json.has("sdp")) json.getString("sdp") else null,
                    candidate = if (json.has("candidate")) json.getString("candidate") else null,
                    sdpMid = if (json.has("sdpMid")) json.getString("sdpMid") else null,
                    sdpMLineIndex = if (json.has("sdpMLineIndex")) json.getInt("sdpMLineIndex") else null
                )
            )
        }
    }

    suspend fun invite(calleeDeviceId: String): Result<String> =
        suspendCancellableCoroutine { cont ->
            val currentSocket = socket
            if (currentSocket == null) {
                cont.resume(Result.failure(Exception("Not connected")))
                return@suspendCancellableCoroutine
            }
            currentSocket.emit("call:invite", arrayOf<Any>(JSONObject().put("calleeDeviceId", calleeDeviceId)), Ack { args ->
                val json = args.getOrNull(0) as? JSONObject
                if (json?.optBoolean("success") == true) {
                    cont.resume(Result.success(json.getString("callId")))
                } else {
                    cont.resume(Result.failure(Exception(json?.optString("error") ?: "Failed to start call")))
                }
            })
        }

    suspend fun accept(callId: String): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            val currentSocket = socket
            if (currentSocket == null) {
                cont.resume(Result.failure(Exception("Not connected")))
                return@suspendCancellableCoroutine
            }
            currentSocket.emit("call:accept", arrayOf<Any>(JSONObject().put("callId", callId)), Ack { args ->
                val json = args.getOrNull(0) as? JSONObject
                if (json?.optBoolean("success") == true) cont.resume(Result.success(Unit))
                else cont.resume(Result.failure(Exception(json?.optString("error") ?: "Failed to accept call")))
            })
        }

    fun decline(callId: String) {
        socket?.emit("call:decline", JSONObject().put("callId", callId))
    }

    fun hangup(callId: String) {
        socket?.emit("call:hangup", JSONObject().put("callId", callId))
    }

    fun sendOffer(callId: String, toSocketId: String, sdp: String) = emitSignalOut("signal:offer", callId, toSocketId) { put("sdp", sdp) }
    fun sendAnswer(callId: String, toSocketId: String, sdp: String) = emitSignalOut("signal:answer", callId, toSocketId) { put("sdp", sdp) }
    fun sendIce(callId: String, toSocketId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        emitSignalOut("signal:ice", callId, toSocketId) {
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }

    private fun emitSignalOut(event: String, callId: String, toSocketId: String, fill: JSONObject.() -> Unit) {
        val payload = JSONObject().apply {
            put("callId", callId)
            put("toSocketId", toSocketId)
            fill()
        }
        socket?.emit(event, payload)
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
    }
}
