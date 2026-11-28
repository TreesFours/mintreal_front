package com.example.mistreal_mini.ui.watch

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.mistreal_mini.data.webrtc.nearbyRuntimePermissions
import com.example.mistreal_mini.monitor.PressureLevel
import com.example.mistreal_mini.service.WatchHostService

data class EarthCamItem(val id: String, val title: String, val location: String, val streamUrl: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHostScreen(
    onBack: () -> Unit,
    contextType: String = "standalone",
    contextId: String? = null,
    // Moved here from Dashboard's own bottomBar — this 5-button row now lives
    // on each of its 5 destination screens instead, so jumping between them
    // doesn't require detouring back through Dashboard every time.
    onOpenGames: () -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onOpenReels: () -> Unit = {},
    onOpenChat: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sessionId by WatchHostService.sessionIdState.collectAsState()
    val viewerCount by WatchHostService.viewerCountState.collectAsState()
    val pressure by WatchHostService.pressureState.collectAsState()
    val hasRecording by WatchHostService.hasRecordingState.collectAsState()
    val saveState by WatchHostService.saveState.collectAsState()
    var isRequesting by remember { mutableStateOf(false) }
    var selectedMediaMode by remember { mutableStateOf("screen") } // "screen", "media", "earthcam"
    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var selectedEarthCam by remember { mutableStateOf<EarthCamItem?>(null) }
    var showEarthCamPicker by remember { mutableStateOf(false) }
    var pendingCoHostRequest by remember { mutableStateOf<String?>(null) }

    // Public World Cams & Drone Streams
    val sampleEarthCams = remember {
        listOf(
            EarthCamItem("cam_iss", "ISS Earth View", "Low Earth Orbit", "https://www.youtube.com/embed/P9C25Un7x3E?autoplay=1&mute=1"),
            EarthCamItem("cam_1", "Times Square Live Cam", "New York, USA", "https://www.youtube.com/embed/1-iS7LArMPA?autoplay=1&mute=1"),
            EarthCamItem("cam_2", "Shibuya Crossing Stream", "Tokyo, Japan", "https://www.youtube.com/embed/3m4aWvSmf_Y?autoplay=1&mute=1"),
            EarthCamItem("cam_4", "Venice Canal Live", "Venice, Italy", "https://www.youtube.com/embed/v9qY2pB8k_c?autoplay=1&mute=1"),
            EarthCamItem("cam_5", "Drone / IP Cam RTSP Stream", "Local Network Stream", "http://192.168.1.1:8080/video")
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isRequesting = false
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(context, WatchHostService::class.java).apply {
                action = WatchHostService.ACTION_START
                putExtra(WatchHostService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(WatchHostService.EXTRA_RESULT_DATA, data)
                putExtra(WatchHostService.EXTRA_CONTEXT_TYPE, contextType)
                if (contextId != null) putExtra(WatchHostService.EXTRA_CONTEXT_ID, contextId)
            }
            context.startForegroundService(intent)
        }
    }

    val mediaFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMediaUri = uri
            android.widget.Toast.makeText(context, "Selected broadcast media: $uri", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val nearbyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    if (pendingCoHostRequest != null) {
        AlertDialog(
            onDismissRequest = { pendingCoHostRequest = null },
            title = { Text("Co-Host Join Request") },
            text = { Text("Viewer '$pendingCoHostRequest' requests permission to join your live broadcast as a split-screen co-host.") },
            confirmButton = {
                Button(onClick = {
                    android.widget.Toast.makeText(context, "Approved! '$pendingCoHostRequest' added as co-host.", android.widget.Toast.LENGTH_SHORT).show()
                    pendingCoHostRequest = null
                }) { Text("Approve Co-Host") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCoHostRequest = null }) { Text("Deny") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch Hub Host") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        bottomBar = {
            com.example.mistreal_mini.ui.chat.components.InstagramStyleBottomBar(
                selectedTab = 3,
                onTabSelected = { tab ->
                    when (tab) {
                        0 -> onOpenGames()
                        1 -> onOpenMarket()
                        2 -> onOpenReels()
                        4 -> onOpenChat()
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sessionId == null) {
                Text(
                    "Broadcast live to other Mistreal users and desktop computers — simultaneously over the internet and directly via Bluetooth/Wi-Fi Direct when offline.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Media Source Selector
                Text("SELECT BROADCAST SOURCE", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = selectedMediaMode == "screen",
                        onClick = { selectedMediaMode = "screen" },
                        label = { Text("Live Screen", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Smartphone, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = selectedMediaMode == "media",
                        onClick = {
                            selectedMediaMode = "media"
                            mediaFilePicker.launch("video/*,image/*")
                        },
                        label = { Text("Video/Photo", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Movie, null, modifier = Modifier.size(16.dp)) }
                    )
                    FilterChip(
                        selected = selectedMediaMode == "earthcam",
                        onClick = {
                            selectedMediaMode = "earthcam"
                            showEarthCamPicker = true
                        },
                        label = { Text("World Cam", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Public, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (selectedMediaUri != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Selected Local File: ${selectedMediaUri?.lastPathSegment}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }

                if (selectedEarthCam != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Selected Cam: ${selectedEarthCam?.title} (${selectedEarthCam?.location})", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(28.dp))
                Button(
                    onClick = {
                        isRequesting = true
                        val nearbyPermissions = nearbyRuntimePermissions()
                        if (nearbyPermissions.isNotEmpty()) nearbyPermissionLauncher.launch(nearbyPermissions)
                        val manager = context.getSystemService(MediaProjectionManager::class.java)
                        launcher.launch(manager.createScreenCaptureIntent())
                    },
                    enabled = !isRequesting,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CellTower, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isRequesting) "Initializing Stream..." else "Start Dual-Transport Broadcast")
                }
            } else {
                Text("Live Broadcast Active — Share Code:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(sessionId.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("$viewerCount viewer${if (viewerCount == 1) "" else "s"} connected", style = MaterialTheme.typography.bodySmall)

                Spacer(modifier = Modifier.height(16.dp))

                // Active Dual Transport Badges
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF4CAF50))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WebRTC Internet", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        }
                    }
                    Surface(
                        color = Color(0xFF3B82F6).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bluetooth/Wi-Fi Nearby", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B82F6))
                        }
                    }
                }

                if (pressure != PressureLevel.NONE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val (bg, message) = when (pressure) {
                        PressureLevel.SEVERE -> MaterialTheme.colorScheme.errorContainer to
                            "Device under heavy pressure — quality reduced to protect the stream."
                        else -> MaterialTheme.colorScheme.tertiaryContainer to
                            "Thermal/battery pressure detected — video quality adjusted automatically."
                    }
                    Surface(color = bg, shape = MaterialTheme.shapes.medium) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(20.dp))

                val watchUrl = "${com.example.mistreal_mini.BuildConfig.BASE_URL}watch/${sessionId}"
                Text(
                    "Casting to a PC, computer, or smart TV? Open this link in any browser (no app needed):",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(watchUrl, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.Cyan)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        clipboardManager.setText(AnnotatedString(watchUrl))
                    }) { Text("Copy Link") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, watchUrl)
                        }, "Send watch link"))
                    }) { Text("Send Link") }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        context.startService(Intent(context, WatchHostService::class.java).apply {
                            action = WatchHostService.ACTION_SAVE_RECORDING
                        })
                    },
                    enabled = hasRecording && saveState != "saving" && saveState != "saved"
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        when (saveState) {
                            "saving" -> "Saving..."
                            "saved" -> "Saved"
                            "failed" -> "Retry Save"
                            else -> "Save My Broadcast"
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = {
                    context.startService(Intent(context, WatchHostService::class.java).apply {
                        action = WatchHostService.ACTION_STOP
                    })
                }) { Text("Stop Broadcast") }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "You can minimize this screen and keep using the app — dual broadcasting continues in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color.Gray
                )
            }
        }
    }

    // World Cams & Drone RTSP Directory Dialog
    if (showEarthCamPicker) {
        var searchQuery by remember { mutableStateOf("") }
        var customRtspUrl by remember { mutableStateOf("") }

        val filteredCams = remember(searchQuery) {
            if (searchQuery.isBlank()) sampleEarthCams
            else sampleEarthCams.filter { it.title.contains(searchQuery, ignoreCase = true) || it.location.contains(searchQuery, ignoreCase = true) }
        }

        Dialog(onDismissRequest = { showEarthCamPicker = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().height(480.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("WORLD LIVE CAMS & DRONE RTSP", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        IconButton(onClick = { showEarthCamPicker = false }) { Icon(Icons.Default.Close, null) }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search city, earth cam, or location...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCams) { cam ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedEarthCam = cam
                                    showEarthCamPicker = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Public, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cam.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(cam.location, fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("CONNECT DRONE / EXTERNAL IP CAM (RTSP/HTTP)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customRtspUrl,
                            onValueChange = { customRtspUrl = it },
                            placeholder = { Text("rtsp://192.168.1.1:554/live") },
                            modifier = Modifier.weight(1f).height(50.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (customRtspUrl.isNotBlank()) {
                                selectedEarthCam = EarthCamItem("custom", "Drone Stream", "Local Network", customRtspUrl)
                                showEarthCamPicker = false
                            }
                        }) { Text("Use") }
                    }
                }
            }
        }
    }
}
