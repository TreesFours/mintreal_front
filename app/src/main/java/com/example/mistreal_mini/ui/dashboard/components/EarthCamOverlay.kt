package com.example.mistreal_mini.ui.dashboard.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class CameraFeed(
    val id: String,
    val title: String,
    val location: String,
    val streamUrl: String,
    val type: String = "webcam" // "webcam", "earthcam", "drone"
)

private val DEFAULT_CAMERAS = listOf(
    CameraFeed("iss_live", "ISS Earth View", "Low Earth Orbit (NASA)", "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8", "earthcam"),
    CameraFeed("shibuya_cam", "Shibuya Crossing Stream", "Tokyo, Japan", "https://stream.openwebcam.org/hls/shibuya/live.m3u8", "webcam"),
    CameraFeed("times_square", "Times Square Central", "New York, USA", "https://stream.openwebcam.org/hls/timessquare/live.m3u8", "webcam"),
    CameraFeed("venice_canal", "Grand Canal Stream", "Venice, Italy", "https://stream.openwebcam.org/hls/venice/live.m3u8", "webcam"),
    CameraFeed("drone_rtsp", "Local Drone Stream", "Local Network", "rtsp://192.168.1.1:8554/live", "drone")
)

@Composable
fun EarthCamOverlay(
    onDismiss: () -> Unit
) {
    var selectedCam by remember { mutableStateOf(DEFAULT_CAMERAS[0]) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    var customDroneIp by remember { mutableStateOf("") }
    var joystickX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var joystickY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var isScanningDrones by remember { mutableStateOf(false) }
    val cameraList = remember { mutableStateListOf(*DEFAULT_CAMERAS.toTypedArray()) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Live Stream Video Player (WebView)
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            webViewClient = WebViewClient()
                            loadUrl(selectedCam.streamUrl)
                        }
                    },
                    update = { webView ->
                        webView.loadUrl(selectedCam.streamUrl)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("${selectedCam.title.uppercase()} · ${selectedCam.location}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { isDrawerOpen = !isDrawerOpen },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(if (isDrawerOpen) Icons.Default.Close else Icons.Default.Tune, "Camera Controls", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Exit", tint = Color.White)
                    }
                }

                // Virtual PTZ Joystick Overlay (Bottom Left)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                        .size(100.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { joystickX = 0f; joystickY = 0f },
                                onDragCancel = { joystickX = 0f; joystickY = 0f }
                            ) { change, dragAmount ->
                                change.consume()
                                joystickX = (joystickX + dragAmount.x).coerceIn(-40f, 40f)
                                joystickY = (joystickY + dragAmount.y).coerceIn(-40f, 40f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("PTZ JOYSTICK", fontSize = 7.sp, color = Color.Gray, fontWeight = FontWeight.Black)
                    Box(
                        modifier = Modifier
                            .offset(x = joystickX.dp, y = joystickY.dp)
                            .size(36.dp)
                            .background(Color.Cyan.copy(alpha = 0.8f), CircleShape)
                    )
                }

                // Slide-Out Controls Drawer (Right Side Overlay)
                AnimatedVisibility(
                    visible = isDrawerOpen,
                    enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(280.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        tonalElevation = 16.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                            Text("WORLD CAMS & DRONE RADAR", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))

                            // Local Drone Search Section
                            OutlinedTextField(
                                value = customDroneIp,
                                onValueChange = { customDroneIp = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("IP Drone (192.168.x.x)", fontSize = 11.sp) },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        if (customDroneIp.isNotBlank()) {
                                            val droneFeed = CameraFeed(
                                                id = "drone_${System.currentTimeMillis()}",
                                                title = "Local IP Drone",
                                                location = customDroneIp,
                                                streamUrl = if (customDroneIp.startsWith("http")) customDroneIp else "http://$customDroneIp:8080/video",
                                                type = "drone"
                                            )
                                            cameraList.add(0, droneFeed)
                                            selectedCam = droneFeed
                                            customDroneIp = ""
                                        }
                                    }) {
                                        Icon(Icons.Default.Add, "Connect Drone", tint = Color.Cyan)
                                    }
                                },
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Text("AVAILABLE STREAMS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Spacer(modifier = Modifier.height(8.dp))

                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(cameraList) { cam ->
                                    val isSel = cam.id == selectedCam.id
                                    ListItem(
                                        headlineContent = { Text(cam.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSel) Color.Cyan else Color.White) },
                                        supportingContent = { Text(cam.location, fontSize = 9.sp, color = Color.Gray) },
                                        leadingContent = {
                                            Icon(
                                                if (cam.type == "drone") Icons.Default.FlightTakeoff else Icons.Default.Videocam,
                                                null,
                                                tint = if (isSel) Color.Cyan else Color.Gray,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        },
                                        modifier = Modifier.clickable { selectedCam = cam },
                                        colors = ListItemDefaults.colors(containerColor = if (isSel) Color.Cyan.copy(alpha = 0.15f) else Color.Transparent)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
