package com.example.mistreal_mini.service

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.mistreal_mini.MainActivity
import com.example.mistreal_mini.data.repository.SignalPayload
import com.example.mistreal_mini.data.repository.ViewerJoinedEvent
import com.example.mistreal_mini.data.repository.WatchRepository
import com.example.mistreal_mini.data.webrtc.NearbyTransport
import com.example.mistreal_mini.data.webrtc.SignalingChannel
import com.example.mistreal_mini.data.webrtc.SimplePeerConnectionObserver
import com.example.mistreal_mini.data.webrtc.SimpleSdpObserver
import com.example.mistreal_mini.data.webrtc.WatchRecorder
import com.example.mistreal_mini.data.webrtc.WebRtcInitializer
import com.example.mistreal_mini.monitor.PressureLevel
import com.example.mistreal_mini.monitor.ResourceMonitor
import com.example.mistreal_mini.util.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.webrtc.*
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Watch Hub host — captures the screen via MediaProjection and holds one
 * PeerConnection per connected viewer (each viewer gets its own independent
 * route to this host, so one viewer's bad connection can't affect another).
 * A foreground service + persistent notification is what makes sharing
 * "minimizable": capture keeps running while the host uses other apps.
 */
@AndroidEntryPoint
class WatchHostService : Service() {

    @Inject lateinit var watchRepository: WatchRepository
    @Inject lateinit var nearbyTransport: NearbyTransport
    @Inject lateinit var resourceMonitor: ResourceMonitor
    @Inject lateinit var deviceIdProvider: com.example.mistreal_mini.util.DeviceIdProvider

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaProjection: MediaProjection? = null
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var sessionId: String? = null
    // Lets the host save their own broadcast — exactly the same captured
    // screen viewers get, recorded locally too (same WatchRecorder viewers
    // already use; this was previously host-side-only missing).
    private var recorder: WatchRecorder? = null
    private var recorderFile: File? = null
    private var recorderStarted = false
    private val peerConnections = mutableMapOf<String, PeerConnection>()
    // Tracks which transport each viewer actually came in on (Socket.IO
    // over the internet, or Nearby over Bluetooth/Wi-Fi-Direct) so replies
    // route back over the same channel the request arrived on.
    private val peerChannels = mutableMapOf<String, SignalingChannel>()

    private val deviceId: String
        get() = deviceIdProvider.deviceId

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SAVE_RECORDING = "ACTION_SAVE_RECORDING"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_CONTEXT_TYPE = "EXTRA_CONTEXT_TYPE"
        const val EXTRA_CONTEXT_ID = "EXTRA_CONTEXT_ID"

        // Lets WatchHostScreen show the live share code without needing a
        // bound service connection — the service is launched fire-and-forget
        // via startForegroundService, not bindService.
        private val _sessionIdState = MutableStateFlow<String?>(null)
        val sessionIdState: StateFlow<String?> = _sessionIdState.asStateFlow()

        private val _viewerCountState = MutableStateFlow(0)
        val viewerCountState: StateFlow<Int> = _viewerCountState.asStateFlow()

        private val _pressureState = MutableStateFlow(PressureLevel.NONE)
        val pressureState: StateFlow<PressureLevel> = _pressureState.asStateFlow()

        private val _hasRecordingState = MutableStateFlow(false)
        val hasRecordingState: StateFlow<Boolean> = _hasRecordingState.asStateFlow()

        // null | "saving" | "saved" | "failed" — same shape as WatchViewerViewModel's.
        private val _saveState = MutableStateFlow<String?>(null)
        val saveState: StateFlow<String?> = _saveState.asStateFlow()

        // How many concurrent viewers this host accepts at each pressure
        // level — feature-local policy, not something ResourceMonitor itself
        // should know about (Tactical Map/ComfortMeet will have their own).
        private fun PressureLevel.watchHubViewerCap(): Int = when (this) {
            PressureLevel.NONE -> 4
            PressureLevel.MILD -> 3
            PressureLevel.MODERATE -> 2
            PressureLevel.SEVERE -> 1
        }
    }

    override fun onCreate() {
        super.onCreate()
        resourceMonitor.ensureMonitoring()
        createNotificationChannel()
        val notification = createNotification("Preparing to share...", null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(2, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val contextType = intent.getStringExtra(EXTRA_CONTEXT_TYPE) ?: "standalone"
                val contextId = intent.getStringExtra(EXTRA_CONTEXT_ID)
                if (resultData != null) startSharing(resultCode, resultData, contextType, contextId)
            }
            ACTION_STOP -> stopSharing()
            ACTION_SAVE_RECORDING -> saveRecording()
        }
        return START_NOT_STICKY
    }

    private fun startSharing(resultCode: Int, resultData: Intent, contextType: String, contextId: String?) {
        WebRtcInitializer.ensureInitialized(applicationContext)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)

        val eb = EglBase.create()
        eglBase = eb
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eb.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eb.eglBaseContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val videoSource = factory.createVideoSource(true)
        val sth = SurfaceTextureHelper.create("WatchCaptureThread", eb.eglBaseContext)
        surfaceTextureHelper = sth

        val capturer = ScreenCapturerAndroid(resultData, object : MediaProjection.Callback() {
            override fun onStop() { stopSharing() }
        })
        capturer.initialize(sth, applicationContext, videoSource.capturerObserver)
        val metrics = resources.displayMetrics
        capturer.startCapture(metrics.widthPixels, metrics.heightPixels, 30)
        videoCapturer = capturer

        val track = factory.createVideoTrack("screen_video_$deviceId", videoSource)
        videoTrack = track
        attachRecorder(track)

        watchRepository.connect()
        serviceScope.launch { watchRepository.viewerJoined.collect { onViewerJoined(it, watchRepository) } }
        serviceScope.launch { watchRepository.answerReceived.collect { onAnswerReceived(it) } }
        serviceScope.launch { watchRepository.iceReceived.collect { onIceReceived(it) } }
        serviceScope.launch { watchRepository.viewerLeft.collect { onViewerLeft(it) } }

        // Nearby (Bluetooth/Wi-Fi-Direct) is a second, independent signaling
        // source feeding the exact same handlers — advertising starts once
        // startHosting() below returns a sessionId, since Nearby advertises
        // under that same code so a viewer can find this host either way.
        serviceScope.launch { nearbyTransport.viewerJoined.collect { onViewerJoined(it, nearbyTransport) } }
        serviceScope.launch { nearbyTransport.answerReceived.collect { onAnswerReceived(it) } }
        serviceScope.launch { nearbyTransport.iceReceived.collect { onIceReceived(it) } }
        serviceScope.launch { nearbyTransport.peerLeft.collect { onViewerLeft(it) } }

        serviceScope.launch {
            resourceMonitor.snapshot.map { it.pressure }.distinctUntilChanged().collect { pressure ->
                applyCaptureScaling(pressure)
                peerConnections.values.forEach { applyBitrateCap(it, pressure) }
                _pressureState.value = pressure
                if (sessionId != null) {
                    updateNotification(pressureNotificationTitle(pressure), "Share code: $sessionId")
                }
            }
        }

        serviceScope.launch {
            val result = watchRepository.startHosting(deviceId, contextType, contextId)
            result.onSuccess { newSessionId ->
                sessionId = newSessionId
                _sessionIdState.value = newSessionId
                updateNotification("Screen sharing live", "Share code: $newSessionId")
                nearbyTransport.startAdvertising(deviceId, newSessionId)
            }.onFailure {
                updateNotification("Sharing failed to start", it.message ?: "Unknown error")
            }
        }
    }

    /**
     * Same shim WatchViewerViewModel.attachRecorder uses — screen
     * resolution isn't known until the first captured frame, so the real
     * WatchRecorder is created lazily on it, then every frame (viewers'
     * copy included) also forwards here. Independent of how many viewers
     * are connected, or whether there are zero.
     */
    private fun attachRecorder(track: VideoTrack) {
        track.addSink(object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                if (!recorderStarted) {
                    recorderStarted = true
                    if (resourceMonitor.pressureNow() == PressureLevel.SEVERE) {
                        return
                    }
                    val eb = eglBase ?: return
                    val sid = sessionId ?: "session"
                    val file = File(cacheDir, "watch_host_${sid}_${System.currentTimeMillis()}.mp4")
                    val r = WatchRecorder(eb.eglBaseContext, file, frame.rotatedWidth, frame.rotatedHeight)
                    try {
                        r.start()
                        recorder = r
                        recorderFile = file
                        _hasRecordingState.value = true
                    } catch (e: Exception) {
                        Timber.e(e, "WatchHost: failed to start recorder")
                    }
                }
                recorder?.onFrame(frame)
            }
        })
    }

    /** Copies the host's own recording of their broadcast to the device's gallery (Movies/Mistreal). */
    fun saveRecording() {
        val file = recorderFile ?: return
        _saveState.value = "saving"
        serviceScope.launch(Dispatchers.IO) {
            try {
                val resolver = contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, "mistreal_watch_host_${System.currentTimeMillis()}.mp4")
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
                _saveState.value = "saved"
            } catch (e: Exception) {
                Timber.e(e, "WatchHost: failed to save recording")
                _saveState.value = "failed"
            }
        }
    }

    /** Transient cache copy only — same "delete unless explicitly saved" rule WatchViewerViewModel uses. */
    private fun finishRecording() {
        recorder?.stop()
        recorder = null
        recorderStarted = false
        recorderFile?.let { if (it.exists()) it.delete() }
        recorderFile = null
        _hasRecordingState.value = false
        _saveState.value = null
    }

    private fun onViewerJoined(event: ViewerJoinedEvent, channel: SignalingChannel) {
        val factory = peerConnectionFactory ?: return
        val track = videoTrack ?: return

        val cap = resourceMonitor.pressureNow().watchHubViewerCap()
        if (peerConnections.size >= cap) {
            channel.sendViewerRejected(event.viewerSocketId, reason = "host_at_capacity")
            return
        }

        peerChannels[event.viewerSocketId] = channel

        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )
        val pc = factory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                peerChannels[event.viewerSocketId]?.sendIce(event.viewerSocketId, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }
        }) ?: return

        pc.addTrack(track, listOf("screen_stream"))
        peerConnections[event.viewerSocketId] = pc
        _viewerCountState.value = peerConnections.size
        applyBitrateCap(pc, resourceMonitor.pressureNow())

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SimpleSdpObserver() {}, sdp)
                channel.sendOffer(event.viewerSocketId, sdp.description)
            }
        }, MediaConstraints())
    }

    private fun onViewerLeft(viewerId: String) {
        peerConnections.remove(viewerId)?.close()
        peerChannels.remove(viewerId)
        _viewerCountState.value = peerConnections.size
    }

    // All viewers share one VideoTrack off one ScreenCapturerAndroid, so
    // resolution/fps is necessarily a global lever — changeCaptureFormat
    // restarts encoding at the new format without tearing down the session.
    private fun applyCaptureScaling(pressure: PressureLevel) {
        val metrics = resources.displayMetrics
        val (width, height, fps) = when (pressure) {
            PressureLevel.NONE, PressureLevel.MILD -> Triple(metrics.widthPixels, metrics.heightPixels, 30)
            PressureLevel.MODERATE -> Triple(metrics.widthPixels / 2, metrics.heightPixels / 2, 20)
            PressureLevel.SEVERE -> Triple(metrics.widthPixels / 2, metrics.heightPixels / 2, 10)
        }
        videoCapturer?.changeCaptureFormat(width, height, fps)
    }

    // Bitrate is per-RtpSender, so unlike resolution it can (and does) stay
    // per-viewer — every viewer gets the same cap for a given pressure level
    // today, but the lever itself is already viewer-scoped if that changes.
    private fun applyBitrateCap(pc: PeerConnection, pressure: PressureLevel) {
        val targetBps = when (pressure) {
            PressureLevel.NONE -> null // uncapped — let WebRTC's own congestion control run
            PressureLevel.MILD -> 2_000_000
            PressureLevel.MODERATE -> 800_000
            PressureLevel.SEVERE -> 300_000
        }
        val sender = pc.senders.firstOrNull { it.track()?.kind() == "video" } ?: return
        val params = sender.parameters
        params.encodings.firstOrNull()?.maxBitrateBps = targetBps
        sender.parameters = params
    }

    private fun pressureNotificationTitle(pressure: PressureLevel): String = when (pressure) {
        PressureLevel.NONE -> "Screen sharing live"
        PressureLevel.MILD -> "Screen sharing live (reduced quality)"
        PressureLevel.MODERATE -> "Screen sharing live (quality reduced further)"
        PressureLevel.SEVERE -> "Screen sharing live (minimal quality — device under pressure)"
    }

    private fun onAnswerReceived(payload: SignalPayload) {
        val pc = peerConnections[payload.fromSocketId] ?: return
        val sdp = payload.sdp ?: return
        pc.setRemoteDescription(object : SimpleSdpObserver() {}, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun onIceReceived(payload: SignalPayload) {
        val pc = peerConnections[payload.fromSocketId] ?: return
        val candidate = payload.candidate ?: return
        pc.addIceCandidate(IceCandidate(payload.sdpMid, payload.sdpMLineIndex ?: 0, candidate))
    }

    private fun stopSharing() {
        finishRecording()
        sessionId?.let { watchRepository.stopHosting(it) }
        peerConnections.values.forEach { it.close() }
        peerConnections.clear()
        peerChannels.clear()
        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        surfaceTextureHelper?.dispose()
        peerConnectionFactory?.dispose()
        eglBase?.release()
        mediaProjection?.stop()
        watchRepository.disconnect()
        nearbyTransport.stop()
        sessionId = null
        _sessionIdState.value = null
        _viewerCountState.value = 0
        _pressureState.value = PressureLevel.NONE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(title: String, content: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(2, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String?): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, WatchHostService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, "WATCH_CHANNEL")
            .setContentTitle(title)
            .setContentText(content ?: "Tap to return to Mistreal")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Sharing", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationChannels.ensure(this, "WATCH_CHANNEL", "Mistreal Watch Hub", NotificationManager.IMPORTANCE_LOW)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
