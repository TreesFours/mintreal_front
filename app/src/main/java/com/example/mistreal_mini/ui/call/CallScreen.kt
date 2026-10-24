package com.example.mistreal_mini.ui.call

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.service.CallService
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

/**
 * No ViewModel here — same shape as WatchHostScreen: the actual WebRTC
 * engine lives in CallService (so it survives this screen being left), and
 * this composable just starts/stops it and mirrors its companion
 * StateFlows. Three distinct entry shapes, same screen: outgoing
 * (calleeDeviceId set), incoming (callId+callerDeviceId set, not yet
 * accepted), or neither (shouldn't normally happen — treated as nothing to
 * do, just back out).
 */
@Composable
fun CallScreen(
    onBack: () -> Unit,
    calleeDeviceId: String? = null,
    incomingCallId: String? = null,
    incomingCallerDeviceId: String? = null,
    screenViewModel: CallScreenViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val status by CallService.callStatusState.collectAsState()
    val isRingtoneEnabled by screenViewModel.isRingtoneEnabled.collectAsState(initial = true)
    val localTrack by CallService.localVideoTrackState.collectAsState()
    val remoteTrack by CallService.remoteVideoTrackState.collectAsState()
    val eglContext by CallService.eglBaseContextState.collectAsState()
    var declined by remember { mutableStateOf(false) }
    var missingPermissions by remember { mutableStateOf(false) }

    // The notification channel's sound covers the backgrounded/killed case;
    // this loop is for when the app is already in the foreground showing
    // IncomingCallPrompt below, where no notification sound necessarily
    // played (or the user already swiped it away to open the app).
    // Ringtone.setLooping/setAudioAttributes are API 28+; minSdk here is 26,
    // so pre-28 just gets a single (non-looping) play of the system tone
    // instead of crashing.
    DisposableEffect(incomingCallId, status, isRingtoneEnabled, declined) {
        val shouldRing = incomingCallId != null && status == "idle" && isRingtoneEnabled && !declined
        val ringtone = if (shouldRing) {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                    isLooping = true
                }
                play()
            }
        } else null
        onDispose { ringtone?.stop() }
    }
    // Retried once after the permission prompt resolves, so a user who
    // denied camera/mic at app-install time but grants it right here
    // doesn't have to tap Call/Accept a second time.
    var pendingCallIntent by remember { mutableStateOf<Intent?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            pendingCallIntent?.let { context.startForegroundService(it) }
        } else {
            missingPermissions = true
        }
        pendingCallIntent = null
    }

    fun startIfPermitted(intent: Intent) {
        val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasCamera && hasMic) {
            context.startForegroundService(intent)
        } else {
            pendingCallIntent = intent
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    LaunchedEffect(calleeDeviceId) {
        if (calleeDeviceId != null) {
            startIfPermitted(Intent(context, CallService::class.java).apply {
                action = CallService.ACTION_START_OUTGOING
                putExtra(CallService.EXTRA_CALLEE_DEVICE_ID, calleeDeviceId)
            })
        }
    }

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                missingPermissions -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera and microphone access are required to make or answer a call.")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
                declined -> {
                    Text("Call declined", modifier = Modifier.align(Alignment.Center))
                }
                incomingCallId != null && status == "idle" -> {
                    IncomingCallPrompt(
                        callerDeviceId = incomingCallerDeviceId.orEmpty(),
                        onAccept = {
                            startIfPermitted(Intent(context, CallService::class.java).apply {
                                action = CallService.ACTION_ACCEPT_INCOMING
                                putExtra(CallService.EXTRA_CALL_ID, incomingCallId)
                            })
                        },
                        onDecline = {
                            context.sendBroadcast(Intent(context, com.example.mistreal_mini.service.CallDeclineReceiver::class.java).apply {
                                action = com.example.mistreal_mini.service.CallDeclineReceiver.ACTION_DECLINE
                                putExtra(com.example.mistreal_mini.service.CallDeclineReceiver.EXTRA_CALL_ID, incomingCallId)
                            })
                            declined = true
                        }
                    )
                }
                status == "active" -> {
                    if (remoteTrack != null) {
                        VideoRendererView(track = remoteTrack, eglBaseContext = eglContext, modifier = Modifier.fillMaxSize())
                    } else {
                        Text("Connecting video...", modifier = Modifier.align(Alignment.Center))
                    }
                    if (localTrack != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .size(120.dp, 160.dp)
                        ) {
                            VideoRendererView(track = localTrack, eglBaseContext = eglContext, modifier = Modifier.fillMaxSize())
                        }
                    }
                    val pressure by CallService.pressureState.collectAsState()
                    if (pressure != com.example.mistreal_mini.monitor.PressureLevel.NONE) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                        ) {
                            Text(
                                "Quality reduced — device under pressure",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    val isMuted by CallService.isMutedState.collectAsState()
                    val isFrontCamera by CallService.isFrontCameraState.collectAsState()
                    CallControls(
                        isMuted = isMuted,
                        isFrontCamera = isFrontCamera,
                        onToggleMute = {
                            context.startService(Intent(context, CallService::class.java).apply { action = CallService.ACTION_TOGGLE_MUTE })
                        },
                        onSwitchCamera = {
                            context.startService(Intent(context, CallService::class.java).apply { action = CallService.ACTION_SWITCH_CAMERA })
                        },
                        onHangUp = {
                            context.startService(Intent(context, CallService::class.java).apply { action = CallService.ACTION_HANGUP })
                        }
                    )
                }
                status == "ringing" -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Calling...")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = {
                            context.startService(Intent(context, CallService::class.java).apply { action = CallService.ACTION_HANGUP })
                        }) { Text("Cancel") }
                    }
                }
                status == "connecting" -> {
                    Text("Connecting...", modifier = Modifier.align(Alignment.Center))
                }
                status == "ended" -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Call ended")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
                else -> {
                    Text("Nothing to do here", modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun IncomingCallPrompt(callerDeviceId: String, onAccept: () -> Unit, onDecline: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Incoming call", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(callerDeviceId, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Button(onClick = onDecline, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Decline") }
            Button(onClick = onAccept) { Text("Accept") }
        }
    }
}

@Composable
private fun BoxScope.CallControls(
    isMuted: Boolean,
    isFrontCamera: Boolean,
    onToggleMute: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangUp: () -> Unit
) {
    Row(
        modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledIconButton(onClick = onToggleMute) {
            Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = if (isMuted) "Unmute" else "Mute")
        }
        FloatingActionButton(onClick = onHangUp, containerColor = MaterialTheme.colorScheme.error) {
            Icon(Icons.Default.CallEnd, contentDescription = "Hang up")
        }
        FilledIconButton(onClick = onSwitchCamera) {
            Icon(Icons.Default.Cameraswitch, contentDescription = if (isFrontCamera) "Switch to back camera" else "Switch to front camera")
        }
    }
}

@Composable
private fun VideoRendererView(track: VideoTrack?, eglBaseContext: EglBase.Context?, modifier: Modifier = Modifier) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                eglBaseContext?.let { init(it, null) }
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                renderer = this
            }
        }
    )

    DisposableEffect(track, renderer) {
        val r = renderer
        val sink = object : VideoSink {
            override fun onFrame(frame: VideoFrame) { r?.onFrame(frame) }
        }
        track?.addSink(sink)
        onDispose { track?.removeSink(sink) }
    }

    DisposableEffect(Unit) {
        onDispose { renderer?.release() }
    }
}
