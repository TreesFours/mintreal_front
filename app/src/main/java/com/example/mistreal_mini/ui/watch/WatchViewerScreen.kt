package com.example.mistreal_mini.ui.watch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.webrtc.nearbyRuntimePermissions
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

/**
 * Join-by-code viewer: enter the host's share code, then watch their live
 * screen. "Pause" freezes the on-screen view (resume picks back up live —
 * not a scrub through what was missed, since a live WebRTC track has no
 * seek concept); the full session is still recording in the background
 * regardless of pause state, and Save copies that finished recording to
 * the device's gallery, where it's a normal, fully scrubbable video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchViewerScreen(
    onBack: () -> Unit,
    initialSessionId: String? = null,
    viewModel: WatchViewerViewModel = hiltViewModel()
) {
    var sessionIdInput by remember { mutableStateOf(initialSessionId.orEmpty()) }
    var isPaused by remember { mutableStateOf(false) }
    val status by viewModel.status
    val remoteTrack by viewModel.remoteVideoTrack.collectAsState()
    val hasRecording by viewModel.hasRecording
    val saveState by viewModel.saveState

    // Requested right before joining — denial just means this viewer can
    // only reach a host over the internet, not via Bluetooth/Wi-Fi-Direct.
    val nearbyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    // Arriving here from a business profile's "Watch Live" button already
    // has the sessionId — join immediately instead of making the user
    // retype the code they didn't even see.
    LaunchedEffect(initialSessionId) {
        if (!initialSessionId.isNullOrBlank()) {
            val nearbyPermissions = nearbyRuntimePermissions()
            if (nearbyPermissions.isNotEmpty()) nearbyPermissionLauncher.launch(nearbyPermissions)
            viewModel.joinSession(initialSessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (remoteTrack == null) {
                OutlinedTextField(
                    value = sessionIdInput,
                    onValueChange = { sessionIdInput = it },
                    label = { Text("Share code") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val nearbyPermissions = nearbyRuntimePermissions()
                        if (nearbyPermissions.isNotEmpty()) nearbyPermissionLauncher.launch(nearbyPermissions)
                        viewModel.joinSession(sessionIdInput)
                    },
                    enabled = sessionIdInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Join") }
                Spacer(modifier = Modifier.height(16.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    RemoteVideoView(track = remoteTrack, eglBaseContext = viewModel.eglBaseContext, isPaused = isPaused)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { isPaused = !isPaused }, modifier = Modifier.weight(1f)) {
                        Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isPaused) "Resume" else "Pause")
                    }
                    Button(
                        onClick = { viewModel.saveRecording() },
                        enabled = hasRecording && saveState != "saving" && saveState != "saved",
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            when (saveState) {
                                "saving" -> "Saving..."
                                "saved" -> "Saved"
                                "failed" -> "Retry Save"
                                else -> "Save"
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Recording auto-deletes when this session ends unless you tap Save.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RemoteVideoView(track: VideoTrack?, eglBaseContext: org.webrtc.EglBase.Context?, isPaused: Boolean) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    // The sink below is created once per (track, renderer) and keeps running
    // across recompositions — rememberUpdatedState is what lets its onFrame
    // body see the latest isPaused on every call instead of the stale value
    // from whenever the sink object was constructed.
    val currentIsPaused = rememberUpdatedState(isPaused)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                eglBaseContext?.let { init(it, null) }
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                renderer = this
            }
        }
    )

    // A shim sink (not the renderer directly) so "pause" can simply stop
    // forwarding new frames — the renderer then keeps displaying whatever
    // it last drew, which is the freeze-frame effect. Resuming just lets
    // frames through again, which is always whatever is live at that moment.
    DisposableEffect(track, renderer) {
        val r = renderer
        val sink = object : VideoSink {
            override fun onFrame(frame: VideoFrame) {
                if (!currentIsPaused.value) r?.onFrame(frame)
            }
        }
        if (track != null) track.addSink(sink)
        onDispose {
            if (track != null) track.removeSink(sink)
        }
    }

    DisposableEffect(Unit) {
        onDispose { renderer?.release() }
    }
}
