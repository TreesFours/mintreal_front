package com.example.mistreal_mini.data.webrtc

import android.content.Context
import com.example.mistreal_mini.data.repository.SignalPayload
import com.example.mistreal_mini.data.repository.ViewerJoinedEvent
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.nio.charset.Charset
import javax.inject.Inject
import javax.inject.Singleton

private const val SERVICE_ID = "com.example.mistreal_mini.WATCH_HUB"

/**
 * Bluetooth/Wi-Fi-Direct signaling transport, parallel to WatchRepository's
 * Socket.IO one — same event shapes (ViewerJoinedEvent/SignalPayload) and
 * the same SignalingChannel send methods, so WatchHostService and
 * WatchViewerViewModel can treat whichever transport a given peer showed
 * up on identically, routing replies back over the same one.
 *
 * IMPORTANT media-path caveat, not a bug to fix later: this only ever
 * carries the small offer/answer/ICE JSON messages, never the actual
 * screen-share video. WebRTC's media transport needs a real UDP/IP path.
 * Wi-Fi Direct forms one (an actual local IP network — WebRTC's own ICE
 * gathering will find and use it automatically, no special-casing needed
 * on the media side at all), but raw Bluetooth Classic/BLE does not and
 * structurally cannot carry WebRTC media. Two phones connected via Nearby
 * over Bluetooth alone, with neither Wi-Fi nor internet available to
 * either one, will exchange signaling fine and then have no ICE candidate
 * pair that actually works — connected at the signaling level, black
 * screen at the media level. Nearby's own P2P_CLUSTER strategy prefers
 * Wi-Fi/Wi-Fi-Direct over Bluetooth whenever both are available, which is
 * the only combination that can actually carry the video.
 */
@Singleton
class NearbyTransport @Inject constructor(
    @ApplicationContext private val context: Context
) : SignalingChannel {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectedEndpointIds = mutableSetOf<String>()

    val viewerJoined = MutableSharedFlow<ViewerJoinedEvent>()
    val offerReceived = MutableSharedFlow<SignalPayload>()
    val answerReceived = MutableSharedFlow<SignalPayload>()
    val iceReceived = MutableSharedFlow<SignalPayload>()
    val peerLeft = MutableSharedFlow<String>() // endpointId
    val viewerRejected = MutableSharedFlow<String>() // reason

    private var localDeviceId: String = ""
    private var shareCode: String? = null
    private var isHost = false

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            val json = try {
                JSONObject(String(bytes, Charset.forName("UTF-8")))
            } catch (e: Exception) {
                Timber.e(e, "NearbyTransport: malformed payload from $endpointId")
                return
            }
            handleIncoming(endpointId, json)
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Auto-accept — same trust model as the rest of this feature:
            // anyone who already has the share code can join over the
            // internet rail with no extra confirmation, so a nearby PIN
            // confirmation step here wouldn't add real security, just friction.
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (!result.status.isSuccess) return
            connectedEndpointIds.add(endpointId)
            if (isHost) {
                scope.launch {
                    viewerJoined.emit(ViewerJoinedEvent(shareCode.orEmpty(), endpointId, endpointId))
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointIds.remove(endpointId)
            scope.launch { peerLeft.emit(endpointId) }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Direct-code join only looks for the one host it already has
            // a share code for — browsing (below) is the path that
            // surfaces every nearby broadcast instead of just this one.
            if (info.endpointName == shareCode) {
                connectionsClient.requestConnection(localDeviceId, endpointId, connectionLifecycleCallback)
            }
        }

        override fun onEndpointLost(endpointId: String) {}
    }

    // endpointId -> share code (the endpoint's advertised name) — populated
    // by browseDiscoveryCallback below, read by connectToBroadcast() once
    // the user taps one.
    private val discoveredBroadcasts = mutableMapOf<String, String>()
    val nearbyBroadcastFound = MutableSharedFlow<Pair<String, String>>() // endpointId, code
    val nearbyBroadcastLost = MutableSharedFlow<String>() // endpointId

    /**
     * Unlike endpointDiscoveryCallback above (which only matches one known
     * share code), this surfaces every nearby broadcast so a viewer without
     * a code can see and pick one — the "browse nearby" path.
     */
    private val browseDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            discoveredBroadcasts[endpointId] = info.endpointName
            scope.launch { nearbyBroadcastFound.emit(endpointId to info.endpointName) }
        }

        override fun onEndpointLost(endpointId: String) {
            discoveredBroadcasts.remove(endpointId)
            scope.launch { nearbyBroadcastLost.emit(endpointId) }
        }
    }

    // Nearby's own calls throw SecurityException immediately if the
    // caller hasn't been granted the Bluetooth/Wi-Fi runtime permissions
    // (API 31+ BLUETOOTH_ADVERTISE/CONNECT/SCAN, API 33+
    // NEARBY_WIFI_DEVICES) — the screens request these before calling in,
    // but this is a safety net so a missed/denied permission degrades to
    // "Nearby just doesn't work" instead of crashing the whole feature.
    fun startAdvertising(deviceId: String, sessionShareCode: String) {
        localDeviceId = deviceId
        shareCode = sessionShareCode
        isHost = true
        try {
            val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            connectionsClient.startAdvertising(sessionShareCode, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener { Timber.e(it, "NearbyTransport: advertising failed") }
        } catch (e: SecurityException) {
            Timber.e(e, "NearbyTransport: missing permission for advertising")
        }
    }

    fun startDiscovery(deviceId: String, sessionShareCode: String) {
        localDeviceId = deviceId
        shareCode = sessionShareCode
        isHost = false
        try {
            val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnFailureListener { Timber.e(it, "NearbyTransport: discovery failed") }
        } catch (e: SecurityException) {
            Timber.e(e, "NearbyTransport: missing permission for discovery")
        }
    }

    /** Browse mode: scans for every nearby broadcast instead of one known share code — see browseDiscoveryCallback. */
    fun startBrowsing(deviceId: String) {
        localDeviceId = deviceId
        isHost = false
        discoveredBroadcasts.clear()
        try {
            val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            connectionsClient.startDiscovery(SERVICE_ID, browseDiscoveryCallback, options)
                .addOnFailureListener { Timber.e(it, "NearbyTransport: browse discovery failed") }
        } catch (e: SecurityException) {
            Timber.e(e, "NearbyTransport: missing permission for browse discovery")
        }
    }

    fun stopBrowsing() {
        try {
            connectionsClient.stopDiscovery()
        } catch (e: Exception) {
            Timber.e(e, "NearbyTransport: stopBrowsing failed")
        }
    }

    /** Connects to one of the broadcasts surfaced by startBrowsing(). */
    fun connectToBroadcast(endpointId: String) {
        val code = discoveredBroadcasts[endpointId] ?: return
        shareCode = code
        connectionsClient.requestConnection(localDeviceId, endpointId, connectionLifecycleCallback)
    }

    fun stop() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Timber.e(e, "NearbyTransport: stop failed")
        }
        connectedEndpointIds.clear()
        discoveredBroadcasts.clear()
    }

    private fun handleIncoming(fromEndpointId: String, json: JSONObject) {
        val type = json.optString("type")
        scope.launch {
            when (type) {
                "offer" -> offerReceived.emit(SignalPayload(shareCode.orEmpty(), fromEndpointId, sdp = json.optString("sdp")))
                "answer" -> answerReceived.emit(SignalPayload(shareCode.orEmpty(), fromEndpointId, sdp = json.optString("sdp")))
                "ice" -> iceReceived.emit(
                    SignalPayload(
                        sessionId = shareCode.orEmpty(),
                        fromSocketId = fromEndpointId,
                        candidate = json.optString("candidate"),
                        sdpMid = if (json.has("sdpMid") && !json.isNull("sdpMid")) json.optString("sdpMid") else null,
                        sdpMLineIndex = if (json.has("sdpMLineIndex")) json.optInt("sdpMLineIndex") else null
                    )
                )
                "rejected" -> viewerRejected.emit(json.optString("reason", "host_at_capacity"))
            }
        }
    }

    private fun send(toPeerId: String, json: JSONObject) {
        val bytes = json.toString().toByteArray(Charset.forName("UTF-8"))
        connectionsClient.sendPayload(toPeerId, Payload.fromBytes(bytes))
    }

    override fun sendOffer(toPeerId: String, sdp: String) =
        send(toPeerId, JSONObject().put("type", "offer").put("sdp", sdp))

    override fun sendAnswer(toPeerId: String, sdp: String) =
        send(toPeerId, JSONObject().put("type", "answer").put("sdp", sdp))

    override fun sendIce(toPeerId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int) =
        send(
            toPeerId,
            JSONObject()
                .put("type", "ice")
                .put("candidate", candidate)
                .put("sdpMid", sdpMid)
                .put("sdpMLineIndex", sdpMLineIndex)
        )

    override fun sendViewerRejected(toPeerId: String, reason: String) =
        send(toPeerId, JSONObject().put("type", "rejected").put("reason", reason))
}
