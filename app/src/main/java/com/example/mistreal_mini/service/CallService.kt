package com.example.mistreal_mini.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mistreal_mini.MainActivity
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.CallAcceptedEvent
import com.example.mistreal_mini.data.repository.CallRepository
import com.example.mistreal_mini.data.repository.CallSignalPayload
import com.example.mistreal_mini.data.webrtc.SimplePeerConnectionObserver
import com.example.mistreal_mini.data.webrtc.SimpleSdpObserver
import com.example.mistreal_mini.data.webrtc.WebRtcInitializer
import com.example.mistreal_mini.monitor.PressureLevel
import com.example.mistreal_mini.monitor.ResourceMonitor
import com.example.mistreal_mini.util.NotificationChannels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.*
import javax.inject.Inject

/**
 * Symmetric 1:1 call engine — unlike WatchHostService/WatchViewerViewModel's
 * host/viewer split, either side can end up creating the offer (whoever's
 * call:accepted/call:incoming fires first), so one service covers both
 * roles instead of a separate "callee" implementation. Foreground +
 * persistent notification for the same reason Watch Hub needs it: capture
 * (camera+mic here, not screen) must survive the app being minimized
 * mid-call. Camera/mic are never touched until the call is actually being
 * connected (outgoing dial, or an incoming call the user chose to answer) —
 * never just because a push arrived.
 */
@AndroidEntryPoint
class CallService : Service() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var deviceIdProvider: com.example.mistreal_mini.util.DeviceIdProvider
    @Inject lateinit var resourceMonitor: ResourceMonitor
    @Inject lateinit var preferenceManager: PreferenceManager

    // What the CALLER hears while the callee's side is ringing — a
    // supervisory ring-back tone, which is exactly what ToneGenerator's
    // TONE_SUP_RINGTONE is for; auto-repeats on its own until stopped, no
    // audio asset needed. Distinct from the callee's actual ringtone,
    // which the notification channel/CallScreen handle separately.
    private var ringbackTone: ToneGenerator? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localAudioTrack: AudioTrack? = null
    private var callId: String? = null
    // The other party's current socket id — learned from call:accepted (if
    // we're the caller) or from the fromSocketId on their first signal (if
    // we're the callee), same as WatchViewerViewModel locking onto hostPeerId.
    private var remoteSocketId: String? = null
    private var repositoryConnected = false
    private var ringTimeoutJob: Job? = null

    private val deviceId: String
        get() = deviceIdProvider.deviceId

    companion object {
        // No signaling-level call-timeout exists server-side (callSignalingService.ts's
        // activeCalls has no expiry) — this is the only thing stopping a ringing call
        // from running forever if the callee's device never answers (asleep, Doze,
        // push never delivered).
        private const val RING_TIMEOUT_MS = 45_000L
        const val ACTION_START_OUTGOING = "ACTION_START_OUTGOING"
        const val ACTION_ACCEPT_INCOMING = "ACTION_ACCEPT_INCOMING"
        const val ACTION_HANGUP = "ACTION_HANGUP"
        const val ACTION_TOGGLE_MUTE = "ACTION_TOGGLE_MUTE"
        const val ACTION_SWITCH_CAMERA = "ACTION_SWITCH_CAMERA"
        const val EXTRA_CALLEE_DEVICE_ID = "EXTRA_CALLEE_DEVICE_ID"
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"

        private val _localVideoTrackState = MutableStateFlow<VideoTrack?>(null)
        val localVideoTrackState: StateFlow<VideoTrack?> = _localVideoTrackState.asStateFlow()
        private val _remoteVideoTrackState = MutableStateFlow<VideoTrack?>(null)
        val remoteVideoTrackState: StateFlow<VideoTrack?> = _remoteVideoTrackState.asStateFlow()
        // idle | ringing | connecting | active | ended
        private val _callStatusState = MutableStateFlow("idle")
        val callStatusState: StateFlow<String> = _callStatusState.asStateFlow()
        private val _eglBaseContextState = MutableStateFlow<EglBase.Context?>(null)
        val eglBaseContextState: StateFlow<EglBase.Context?> = _eglBaseContextState.asStateFlow()
        private val _isMutedState = MutableStateFlow(false)
        val isMutedState: StateFlow<Boolean> = _isMutedState.asStateFlow()
        private val _isFrontCameraState = MutableStateFlow(true)
        val isFrontCameraState: StateFlow<Boolean> = _isFrontCameraState.asStateFlow()
        private val _pressureState = MutableStateFlow(PressureLevel.NONE)
        val pressureState: StateFlow<PressureLevel> = _pressureState.asStateFlow()
    }

    private fun hasCallPermissions(): Boolean {
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return camera && mic
    }

    override fun onCreate() {
        super.onCreate()
        resourceMonitor.ensureMonitoring()
        createNotificationChannel()
        val notification = createNotification("Call starting...")
        // CallScreen.startIfPermitted() is the normal gate, but the
        // notification's own "Answer" action starts this service directly,
        // bypassing that check — requesting the camera|microphone foreground
        // type without those permissions actually held throws a
        // SecurityException on Android 14+ and crashes the app, so this
        // falls back to an untyped foreground notification instead of
        // crashing; onStartCommand below is what actually refuses to proceed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasCallPermissions()) {
            startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(3, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureRepositoryConnected()
        when (intent?.action) {
            ACTION_START_OUTGOING -> {
                val calleeDeviceId = intent.getStringExtra(EXTRA_CALLEE_DEVICE_ID) ?: return stopAndFinish()
                if (!hasCallPermissions()) {
                    updateNotification("Camera/microphone permission needed — open Mistreal to grant it")
                    return stopAndFinish()
                }
                initWebRtc()
                startOutgoing(calleeDeviceId)
            }
            ACTION_ACCEPT_INCOMING -> {
                val incomingCallId = intent.getStringExtra(EXTRA_CALL_ID) ?: return stopAndFinish()
                if (!hasCallPermissions()) {
                    updateNotification("Camera/microphone permission needed — open Mistreal to answer")
                    return stopAndFinish()
                }
                callId = incomingCallId
                initWebRtc()
                _callStatusState.value = "connecting"
                serviceScope.launch {
                    callRepository.accept(incomingCallId).onFailure {
                        endCall(it.message ?: "Couldn't answer the call")
                    }
                }
            }
            ACTION_HANGUP -> endCall("Call ended")
            ACTION_TOGGLE_MUTE -> toggleMute()
            ACTION_SWITCH_CAMERA -> switchCamera()
        }
        return START_NOT_STICKY
    }

    private fun ensureRepositoryConnected() {
        if (repositoryConnected) return
        repositoryConnected = true
        callRepository.connect(deviceId)
        serviceScope.launch { callRepository.callAccepted.collect { onCallAccepted(it) } }
        serviceScope.launch { callRepository.offerReceived.collect { onOfferReceived(it) } }
        serviceScope.launch { callRepository.answerReceived.collect { onAnswerReceived(it) } }
        serviceScope.launch { callRepository.iceReceived.collect { onIceReceived(it) } }
        serviceScope.launch { callRepository.callDeclined.collect { if (it == callId) endCall("Call declined") } }
        serviceScope.launch { callRepository.callEnded.collect { if (it == callId) endCall("Call ended") } }
        // Same adaptive-degradation lever Watch Hub uses — a call has one
        // PeerConnection instead of N, so this is simpler (no per-viewer cap
        // concern), just capture format + bitrate.
        serviceScope.launch {
            resourceMonitor.snapshot.map { it.pressure }.distinctUntilChanged().collect { pressure ->
                applyCaptureScaling(pressure)
                peerConnection?.let { applyBitrateCap(it, pressure) }
                _pressureState.value = pressure
            }
        }
    }

    // All camera resolutions below are relative to the base 1280x720 capture
    // startCapture() requests in initWebRtc() — unlike Watch Hub's screen
    // capture, there's no display-metrics-derived size to scale down from.
    private fun applyCaptureScaling(pressure: PressureLevel) {
        val (width, height, fps) = when (pressure) {
            PressureLevel.NONE, PressureLevel.MILD -> Triple(1280, 720, 30)
            PressureLevel.MODERATE -> Triple(640, 360, 20)
            PressureLevel.SEVERE -> Triple(640, 360, 10)
        }
        videoCapturer?.changeCaptureFormat(width, height, fps)
    }

    private fun applyBitrateCap(pc: PeerConnection, pressure: PressureLevel) {
        val targetBps = when (pressure) {
            PressureLevel.NONE -> null // uncapped — let WebRTC's own congestion control run
            PressureLevel.MILD -> 1_500_000
            PressureLevel.MODERATE -> 600_000
            PressureLevel.SEVERE -> 250_000
        }
        val sender = pc.senders.firstOrNull { it.track()?.kind() == "video" } ?: return
        val params = sender.parameters
        params.encodings.firstOrNull()?.maxBitrateBps = targetBps
        sender.parameters = params
    }

    private fun initWebRtc() {
        if (peerConnectionFactory != null) return
        WebRtcInitializer.ensureInitialized(applicationContext)
        val eb = EglBase.create()
        eglBase = eb
        _eglBaseContextState.value = eb.eglBaseContext
        val factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eb.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eb.eglBaseContext))
            .createPeerConnectionFactory()
        peerConnectionFactory = factory

        val enumerator = Camera2Enumerator(applicationContext)
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: enumerator.deviceNames.firstOrNull()
        val capturer = cameraName?.let { enumerator.createCapturer(it, null) }
        val videoSource = factory.createVideoSource(false)
        val sth = SurfaceTextureHelper.create("CallCaptureThread", eb.eglBaseContext)
        surfaceTextureHelper = sth
        capturer?.initialize(sth, applicationContext, videoSource.capturerObserver)
        capturer?.startCapture(1280, 720, 30)
        videoCapturer = capturer

        _localVideoTrackState.value = factory.createVideoTrack("call_video_$deviceId", videoSource)
        val audioSource = factory.createAudioSource(MediaConstraints())
        localAudioTrack = factory.createAudioTrack("call_audio_$deviceId", audioSource)
    }

    private fun createPeerConnection(): PeerConnection? {
        val factory = peerConnectionFactory ?: return null
        // STUN only — KNOWN GAP, not an oversight: no TURN/relay server is
        // configured anywhere. Two devices both behind symmetric/carrier-
        // grade NAT (common on cellular) will gather only server-reflexive
        // candidates, ICE will fail to find a path, and the call will hang
        // at "active" with no media ever flowing. Fixing this needs a real
        // TURN server (self-hosted coturn, or a paid service like Twilio/
        // Xirsys) and credentials — infrastructure this can't invent.
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        )
        val pc = factory.createPeerConnection(rtcConfig, object : SimplePeerConnectionObserver() {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate ?: return
                val cId = callId ?: return
                val target = remoteSocketId ?: return
                callRepository.sendIce(cId, target, candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex)
            }
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is VideoTrack) _remoteVideoTrackState.value = track
            }
        }) ?: return null
        _localVideoTrackState.value?.let { pc.addTrack(it, listOf("call_stream")) }
        localAudioTrack?.let { pc.addTrack(it, listOf("call_stream")) }
        applyBitrateCap(pc, resourceMonitor.pressureNow())
        return pc
    }

    private fun startOutgoing(calleeDeviceId: String) {
        _callStatusState.value = "ringing"
        updateNotification("Calling...")
        startRingback()
        ringTimeoutJob?.cancel()
        ringTimeoutJob = serviceScope.launch {
            delay(RING_TIMEOUT_MS)
            endCall("No answer")
        }
        serviceScope.launch {
            callRepository.invite(calleeDeviceId)
                .onSuccess { newCallId -> callId = newCallId }
                .onFailure {
                    updateNotification(it.message ?: "Call failed")
                    endCall(it.message ?: "Call failed")
                }
        }
    }

    private fun onCallAccepted(event: CallAcceptedEvent) {
        if (event.callId != callId) return
        remoteSocketId = event.calleeSocketId
        stopRingback()
        ringTimeoutJob?.cancel()
        _callStatusState.value = "active"
        updateNotification("Call connected")
        val pc = createPeerConnection() ?: return
        peerConnection = pc
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp ?: return
                pc.setLocalDescription(object : SimpleSdpObserver() {}, sdp)
                callRepository.sendOffer(event.callId, event.calleeSocketId, sdp.description)
            }
        }, MediaConstraints())
    }

    private fun onOfferReceived(payload: CallSignalPayload) {
        if (payload.callId != callId) return
        remoteSocketId = payload.fromSocketId
        val sdp = payload.sdp ?: return
        val pc = peerConnection ?: createPeerConnection()?.also { peerConnection = it } ?: return
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(answerSdp: SessionDescription?) {
                        answerSdp ?: return
                        pc.setLocalDescription(object : SimpleSdpObserver() {}, answerSdp)
                        callRepository.sendAnswer(payload.callId, payload.fromSocketId, answerSdp.description)
                        _callStatusState.value = "active"
                        updateNotification("Call connected")
                    }
                }, MediaConstraints())
            }
        }, SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun onAnswerReceived(payload: CallSignalPayload) {
        if (payload.callId != callId) return
        val pc = peerConnection ?: return
        val sdp = payload.sdp ?: return
        pc.setRemoteDescription(object : SimpleSdpObserver() {}, SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    private fun onIceReceived(payload: CallSignalPayload) {
        if (payload.callId != callId) return
        val pc = peerConnection ?: return
        val candidate = payload.candidate ?: return
        pc.addIceCandidate(IceCandidate(payload.sdpMid, payload.sdpMLineIndex ?: 0, candidate))
    }

    private fun startRingback() {
        serviceScope.launch {
            if (!preferenceManager.isCallRingtoneEnabled.first()) return@launch
            if (ringbackTone != null) return@launch
            try {
                val tg = ToneGenerator(AudioManager.STREAM_VOICE_CALL, ToneGenerator.MAX_VOLUME)
                tg.startTone(ToneGenerator.TONE_SUP_RINGTONE, -1) // -1 duration = play until stopTone()
                ringbackTone = tg
            } catch (e: Exception) {
                // Best-effort — a missing audio output shouldn't block the call itself.
            }
        }
    }

    private fun stopRingback() {
        ringbackTone?.let {
            it.stopTone()
            it.release()
        }
        ringbackTone = null
    }

    private fun toggleMute() {
        val track = localAudioTrack ?: return
        val nowEnabled = !track.enabled()
        track.setEnabled(nowEnabled)
        _isMutedState.value = !nowEnabled
    }

    private fun switchCamera() {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                _isFrontCameraState.value = isFrontCamera
            }
            override fun onCameraSwitchError(errorDescription: String?) {}
        })
    }

    private fun endCall(reason: String) {
        callId?.let { if (_callStatusState.value != "ended") callRepository.hangup(it) }
        _callStatusState.value = "ended"
        updateNotification(reason)
        stopAndFinish()
    }

    private fun stopAndFinish(): Int {
        stopRingback()
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null

        // close()/stopCapture()/dispose()/release() are documented blocking
        // calls — they wait for native capture/encode threads to actually
        // stop. Captured into locals and nulled out here, disposed off the
        // main thread below, so Hang Up reacts instantly instead of
        // momentarily freezing the UI on teardown.
        val pcToClose = peerConnection
        val capturerToStop = videoCapturer
        val surfaceHelperToDispose = surfaceTextureHelper
        val factoryToDispose = peerConnectionFactory
        val eglToRelease = eglBase
        peerConnection = null
        videoCapturer = null
        surfaceTextureHelper = null
        peerConnectionFactory = null
        eglBase = null

        if (repositoryConnected) callRepository.disconnect()
        repositoryConnected = false
        _localVideoTrackState.value = null
        _remoteVideoTrackState.value = null
        _eglBaseContextState.value = null
        _isMutedState.value = false
        _isFrontCameraState.value = true
        _pressureState.value = PressureLevel.NONE
        callId = null
        remoteSocketId = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        // A fresh, unscoped IO coroutine — not serviceScope — since onDestroy()
        // (called very soon after stopSelf()) cancels serviceScope, which would
        // race with and could abort this disposal before it finishes.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                pcToClose?.close()
                capturerToStop?.stopCapture()
                capturerToStop?.dispose()
                surfaceHelperToDispose?.dispose()
                factoryToDispose?.dispose()
                eglToRelease?.release()
            } catch (e: Exception) {
                // Best-effort teardown — the call is already ending either way.
            }
        }

        return START_NOT_STICKY
    }

    private fun updateNotification(content: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(3, createNotification(content))
    }

    private fun createNotification(content: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val hangupIntent = Intent(this, CallService::class.java).apply { action = ACTION_HANGUP }
        val hangupPendingIntent = PendingIntent.getService(this, 3, hangupIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, NotificationChannels.CALL_CHANNEL_ID)
            .setContentTitle("Mistreal Call")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Hang Up", hangupPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationChannels.ensureCallChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
