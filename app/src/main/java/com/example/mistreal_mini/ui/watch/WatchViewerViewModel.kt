package com.example.mistreal_mini.ui.watch

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.repository.SignalPayload
import com.example.mistreal_mini.data.repository.WatchRepository
import com.example.mistreal_mini.data.webrtc.NearbyTransport
import com.example.mistreal_mini.data.webrtc.SignalingChannel
import com.example.mistreal_mini.data.webrtc.SimplePeerConnectionObserver
import com.example.mistreal_mini.data.webrtc.SimpleSdpObserver
import com.example.mistreal_mini.data.webrtc.WatchRecorder
import com.example.mistreal_mini.data.webrtc.WebRtcInitializer
import com.example.mistreal_mini.monitor.PressureLevel
import com.example.mistreal_mini.monitor.ResourceMonitor
import com.example.mistreal_mini.util.DeviceIdProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Live render (Phase 1) + a continuously-recording background copy of the
 * same track (Phase 2) that's discarded when the viewer is done watching
 * unless they tap Save. "Pause" on the live view is a freeze (stop
 * forwarding new frames to the on-screen renderer, so it keeps showing the
 * last one) rather than true scrub-back-through-what-you-missed — WebRTC's
 * live track has no seek concept, and the background recording isn't
 * safely readable until MediaMuxer finalizes it. Full pause/seek becomes
 * available on the finished recording once it's saved (it's then a normal,
 * finalized mp4 any player can scrub).
 */
@HiltViewModel
class WatchViewerViewModel @Inject constructor(
    private val watchRepository: WatchRepository,
    private val nearbyTransport: NearbyTransport,
    private val resourceMonitor: ResourceMonitor,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) : ViewModel() {

    private val deviceId: String
        get() = deviceIdProvider.deviceId

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var currentSessionId: String? = null
    // Whichever transport actually delivered the offer wins — set once, so
    // a slower second offer from the other transport (e.g. both Socket.IO
    // and Nearby discovery find the host) doesn't open a competing
    // PeerConnection. Replies always go back over this same channel.
    private var activeChannel: SignalingChannel? = null

    private var recorder: WatchRecorder? = null
    private var recorderFile: File? = null
    private var recorderStarted = false
    private var hasSavedCurrentRecording = false

    val eglBaseContext: EglBase.Context?
        get() = eglBase?.eglBaseContext

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _status = mutableStateOf("Enter a share code to join")
    val status: State<String> = _status

    private val _hasRecording = mutableStateOf(false)
    val hasRecording: State<Boolean> = _hasRecording

    private val _saveState = mutableStateOf<String?>(null) // null | "saving" | "saved" | "failed"
    val saveState: State<String?> = _saveState

    data class NearbyBroadcast(val endpointId: String, val code: String)

    private val _nearbyBroadcasts = MutableStateFlow<List<NearbyBroadcast>>(emptyList())
    val nearbyBroadcasts: StateFlow<List<NearbyBroadcast>> = _nearbyBroadcasts.asStateFlow()

    private val _isBrowsingNearby = mutableStateOf(false)
    val isBrowsingNearby: State<Boolean> = _isBrowsingNearby

    init {
        WebRtcInitializer.ensureInitialized(context)
        resourceMonitor.ensureMonitoring()
        val eb = EglBase.create()
        eglBase = eb
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eb.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eb.eglBaseContext))
            .createPeerConnectionFactory()

        watchRepository.connect()
        viewModelScope.launch { watchRepository.offerReceived.collect { onOfferReceived(it, watchRepository) } }
        viewModelScope.launch { watchRepository.iceReceived.collect { onIceReceived(it) } }
        viewModelScope.launch {
            watchRepository.sessionEnded.collect {
                _status.value = "Host ended the session"
                finishRecording()
            }
        }

        // Nearby (Bluetooth/Wi-Fi-Direct) runs in parallel with the
        // Socket.IO join below — whichever actually reaches the host first
        // is the one onOfferReceived locks onto.
        viewModelScope.launch { nearbyTransport.offerReceived.collect { onOfferReceived(it, nearbyTransport) } }
        viewModelScope.launch { nearbyTransport.iceReceived.collect { onIceReceived(it) } }

        viewModelScope.launch { watchRepository.viewerRejected.collect { onViewerRejected() } }
        viewModelScope.launch { nearbyTransport.viewerRejected.collect { onViewerRejected() } }

        viewModelScope.launch {
            nearbyTransport.nearbyBroadcastFound.collect { (endpointId, code) ->
                if (_nearbyBroadcasts.value.none { it.endpointId == endpointId }) {
                    _nearbyBroadcasts.value = _nearbyBroadcasts.value + NearbyBroadcast(endpointId, code)
                }
            }
        }
        viewModelScope.launch {
            nearbyTransport.nearbyBroadcastLost.collect { endpointId ->
                _nearbyBroadcasts.value = _nearbyBroadcasts.value.filterNot { it.endpointId == endpointId }
            }
        }
    }

    /** Scans for every nearby broadcast instead of requiring a share code upfront — previously there was no way to find a host without already knowing its code. */
    fun startBrowsingNearby() {
        _nearbyBroadcasts.value = emptyList()
        _isBrowsingNearby.value = true
        _status.value = "Scanning for nearby broadcasts..."
        nearbyTransport.startBrowsing(deviceId)
    }

    fun stopBrowsingNearby() {
        _isBrowsingNearby.value = false
        nearbyTransport.stopBrowsing()
    }

    fun connectToNearbyBroadcast(broadcast: NearbyBroadcast) {
        currentSessionId = broadcast.code
        _status.value = "Connecting..."
        stopBrowsingNearby()
        nearbyTransport.connectToBroadcast(broadcast.endpointId)
    }

    private fun onViewerRejected() {
        if (peerConnection != null) return // already connected over the other transport before the reject arrived
        _status.value = "Host is at capacity — try again shortly"
    }

    /**
     * Tries the internet rail first (the common case, and the only one
     * that works across any distance) — Nearby only starts if that hasn't
     * actually produced an offer within a few seconds, which covers "no
     * internet at all" and "host unreachable over the internet but in
     * Bluetooth/Wi-Fi-Direct range" without racing two PeerConnection
     * negotiations against each other from the start. onOfferReceived's
     * own "already connected" guard is the final backstop either way.
     */
    fun joinSession(sessionId: String) {
        val trimmed = sessionId.trim()
        currentSessionId = trimmed
        _status.value = "Joining..."
        viewModelScope.launch {
            val result = watchRepository.joinAsViewer(trimmed, deviceId)
            if (result.isSuccess) {
                _status.value = "Waiting for host..."
            } else {
                _status.value = "No internet route — trying nearby..."
                nearbyTransport.startDiscovery(deviceId, trimmed)
                return@launch
            }
            delay(5000)
            if (peerConnection == null) {
                _status.value = "Still waiting — trying nearby..."
                nearbyTransport.startDiscovery(deviceId, trimmed)
            }
        }
    }

    private fun onOfferReceived(payload: SignalPayload, channel: SignalingChannel) {
        if (peerConnection != null) return // already connected via the other transport
        val factory = peerConnectionFactory ?: return
        val sdp = payload.sdp ?: return
        val hostPeerId = payload.fromSocketId
        activeChannel = channel

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )
        val pc = factory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                channel.sendIce(hostPeerId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) {
                    _remoteVideoTrack.value = track
                    attachRecorder(track)
                }
            }
        }) ?: return
        peerConnection = pc

        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        answerSdp ?: return
                        pc.setLocalDescription(object : SimpleSdpObserver() {}, answerSdp)
                        channel.sendAnswer(hostPeerId, answerSdp.description)
                        _status.value = "Connected"
                    }
                }, MediaConstraints())
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun onIceReceived(payload: SignalPayload) {
        val pc = peerConnection ?: return
        val candidate = payload.candidate ?: return
        pc.addIceCandidate(IceCandidate(payload.sdpMid, payload.sdpMLineIndex ?: 0, candidate))
    }

    /**
     * The host's screen resolution isn't known until the first frame
     * arrives, so this shim lazily creates the real WatchRecorder on that
     * first frame (reading its size), then forwards every frame — that one
     * included — into it. Runs independently of whatever the on-screen
     * renderer is doing (including while the live view is "paused").
     */
    private fun attachRecorder(track: VideoTrack) {
        track.addSink(object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                if (!recorderStarted) {
                    recorderStarted = true
                    if (resourceMonitor.pressureNow() == PressureLevel.SEVERE) {
                        _status.value = "Connected (local recording paused — device under pressure)"
                        return
                    }
                    val eb = eglBase ?: return
                    val sessionId = currentSessionId ?: "session"
                    val file = File(context.cacheDir, "watch_${sessionId}_${System.currentTimeMillis()}.mp4")
                    val r = WatchRecorder(eb.eglBaseContext, file, frame.rotatedWidth, frame.rotatedHeight)
                    try {
                        r.start()
                        recorder = r
                        recorderFile = file
                        _hasRecording.value = true
                    } catch (e: Exception) {
                        Timber.e(e, "WatchViewer: failed to start recorder")
                    }
                }
                recorder?.onFrame(frame)
            }
        })
    }

    /** Copies the recording to the device's own gallery (Movies/Mistreal). Safe to call mid-session or after the host stops — either way the file isn't deleted until finishRecording() runs. */
    fun saveRecording() {
        val file = recorderFile ?: return
        _saveState.value = "saving"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "mistreal_watch_${System.currentTimeMillis()}.mp4")
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Mistreal")
                }
                val destUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (destUri == null) {
                    _saveState.value = "failed"
                    return@launch
                }
                resolver.openOutputStream(destUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
                hasSavedCurrentRecording = true
                _saveState.value = "saved"
            } catch (e: Exception) {
                Timber.e(e, "WatchViewer: failed to save recording")
                _saveState.value = "failed"
            }
        }
    }

    /**
     * Finalizes and discards the cache-dir recording — called when the
     * host ends the session or the viewer leaves. The cache copy is always
     * transient; saveRecording() already copied a permanent one into
     * MediaStore if the viewer asked for that, so deleting the temp here
     * never loses a save made before this point.
     */
    private fun finishRecording() {
        recorder?.stop()
        recorder = null
        recorderStarted = false
        val file = recorderFile
        if (file != null && file.exists()) {
            file.delete()
        }
        recorderFile = null
        hasSavedCurrentRecording = false
        _hasRecording.value = false
        _saveState.value = null
    }

    override fun onCleared() {
        super.onCleared()
        finishRecording()
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        eglBase?.release()
        watchRepository.disconnect()
        nearbyTransport.stop()
    }
}
