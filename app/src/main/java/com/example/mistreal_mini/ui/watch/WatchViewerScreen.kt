package com.example.mistreal_mini.ui.watch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.webrtc.nearbyRuntimePermissions
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoTrack

data class ActiveStreamerItem(
    val code: String,
    val streamerName: String,
    val title: String,
    val transport: String,
    val viewers: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchViewerScreen(
    onBack: () -> Unit,
    initialSessionId: String? = null,
    viewModel: WatchViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var sessionIdInput by remember { mutableStateOf(initialSessionId.orEmpty()) }
    var isPaused by remember { mutableStateOf(false) }
    var isDirectoryMode by remember { mutableStateOf(false) } // Data Saver Directory List Mode
    val status by viewModel.status
    val remoteTrack by viewModel.remoteVideoTrack.collectAsState()
    val hasRecording by viewModel.hasRecording
    val saveState by viewModel.saveState
    val isBrowsingNearby by viewModel.isBrowsingNearby
    val nearbyBroadcasts by viewModel.nearbyBroadcasts.collectAsState()

    var submittedMediaUri by remember { mutableStateOf<Uri?>(null) }
    val mediaPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            submittedMediaUri = uri
            android.widget.Toast.makeText(context, "Media submitted to Host for broadcast!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    val activeStreamers = remember(nearbyBroadcasts, sessionIdInput) {
        val list = mutableListOf<ActiveStreamerItem>()
        nearbyBroadcasts.forEach { b ->
            list.add(ActiveStreamerItem(b.code, "Nearby Host (${b.code})", "Local Bluetooth/Wi-Fi Stream", "Bluetooth / Wi-Fi Direct", 1))
        }
        if (sessionIdInput.isNotBlank() && list.none { it.code == sessionIdInput }) {
            list.add(ActiveStreamerItem(sessionIdInput, "Streamer ($sessionIdInput)", "Global WebRTC Live Stream", "Internet WebRTC", 1))
        }
        list
    }

    val onJoin: () -> Unit = {
        val nearbyPermissions = nearbyRuntimePermissions()
        if (nearbyPermissions.isNotEmpty()) nearbyPermissionLauncher.launch(nearbyPermissions)
        viewModel.joinSession(sessionIdInput)
    }

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
                title = { Text("Watch Hub") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // Data Saver Directory List Mode Toggle
                    IconButton(onClick = { isDirectoryMode = !isDirectoryMode }) {
                        Icon(
                            if (isDirectoryMode) Icons.Default.Videocam else Icons.Default.FormatListNumbered,
                            contentDescription = "Toggle Data-Saver Directory Mode",
                            tint = if (isDirectoryMode) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (isDirectoryMode && remoteTrack == null) {
                // Data-Saver Streamer Directory Mode
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DataUsage, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("DATA-SAVER DIRECTORY MODE", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (activeStreamers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No active live streams listed.", color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { isDirectoryMode = false }) {
                                Text("Switch to Code Input / Scan")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(activeStreamers) { streamer ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    sessionIdInput = streamer.code
                                    isDirectoryMode = false
                                    onJoin()
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(streamer.streamerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(streamer.title, fontSize = 11.sp, color = Color.Gray)
                                        Text("Transport: ${streamer.transport}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Button(
                                        onClick = {
                                            sessionIdInput = streamer.code
                                            isDirectoryMode = false
                                            onJoin()
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("JOIN LIVE", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (remoteTrack == null) {
                if (isBrowsingNearby) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Scanning Bluetooth & Wi-Fi Direct nearby...")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (nearbyBroadcasts.isEmpty()) {
                        Text(
                            "No nearby broadcasts found yet. Make sure Bluetooth/Wi-Fi are on and the host is nearby.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(nearbyBroadcasts) { broadcast ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { viewModel.connectToNearbyBroadcast(broadcast) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Nearby Broadcast (${broadcast.code})", fontWeight = FontWeight.Bold)
                                            Text("Direct Bluetooth/Wi-Fi Link", fontSize = 11.sp, color = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.stopBrowsingNearby() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel Nearby Scan")
                    }
                } else {
                    OutlinedTextField(
                        value = sessionIdInput,
                        onValueChange = { sessionIdInput = it },
                        label = { Text("Enter Share Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (sessionIdInput.isNotBlank()) onJoin() })
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onJoin,
                        enabled = sessionIdInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CellTower, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Join Live Broadcast")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val nearbyPermissions = nearbyRuntimePermissions()
                            if (nearbyPermissions.isNotEmpty()) nearbyPermissionLauncher.launch(nearbyPermissions)
                            viewModel.startBrowsingNearby()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scan Bluetooth / Wi-Fi Direct Nearby")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
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
                                else -> "Save Recording"
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { mediaPickerLauncher.launch("video/*,image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Push Media", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            android.widget.Toast.makeText(context, "Co-Host Join Request sent to Host!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Request Co-Host", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RemoteVideoView(track: VideoTrack?, eglBaseContext: org.webrtc.EglBase.Context?, isPaused: Boolean) {
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
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
