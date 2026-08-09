package com.example.mistreal_mini.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.dashboard.DashboardViewModel
import com.example.mistreal_mini.ui.chat.ChatInputBar

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.mistreal_mini.ui.chat.ChatBubble
import com.example.mistreal_mini.ui.chat.InteractionMode

import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun InteractiveMapView(
    location: String,
    onClose: () -> Unit,
    viewModel: ChatViewModel,
    dashboardViewModel: DashboardViewModel,
    onScreenshotClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onFileClick: () -> Unit = {},
    startInSpaceMode: Boolean = false
) {
    var chatText by remember { mutableStateOf("") }
    // 🛡️ Fix: Use derivedStateOf to ensure reactive chat list for Tactical Map
    val messages by remember(viewModel.messages.size) {
        derivedStateOf {
            viewModel.messages.filter { it.trendTitle == "Tactical Map: $location" }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isChatVisible by remember { mutableStateOf(false) }
    var isTrackerVisible by remember { mutableStateOf(false) }
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadTrend("Tactical Map: $location")
        dashboardViewModel.fetchCelestialData() // Initial fetch
    }
    
    // Auto-update celestial data every 30 seconds
    LaunchedEffect(Unit) {
        while(true) {
            kotlinx.coroutines.delay(30000)
            dashboardViewModel.fetchCelestialData()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.exitTrend() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(0.98f)
            .fillMaxHeight(0.95f),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Tactical Header
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = Color(0xFFE57373))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(location.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("STRATEGIC TACTICAL OVERLAY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { isTrackerVisible = !isTrackerVisible }) { 
                        Icon(Icons.Default.Radar, null, tint = if(isTrackerVisible) MaterialTheme.colorScheme.primary else Color.Gray) 
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null) }
                }
                
                // 🌎 FULL SCREEN INTERACTIVE MAP
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                ) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                webViewInstance = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                webViewClient = WebViewClient()
                                // Load Leaflet for better control
                                val html = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                        <style>
                                            body { margin: 0; padding: 0; }
                                            #map { height: 100vh; background: #00020a; }
                                            .leaflet-tile { filter: brightness(0.6) invert(1) contrast(3) hue-rotate(200deg); }
                                            .space-layer { opacity: 0; transition: opacity 0.5s; }
                                            .earth-layer { opacity: 1; transition: opacity 0.5s; }
                                        </style>
                                    </head>
                                    <body>
                                        <div id="map"></div>
                                        <script>
                                            var map = L.map('map', { 
                                                zoomControl: false,
                                                minZoom: 1,
                                                maxZoom: 18,
                                                worldCopyJump: true
                                            }).setView([0, 0], 2);
                                            
                                            var earthTiles = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                                            
                                            if (${startInSpaceMode}) {
                                                map.setZoom(1);
                                                earthTiles.setOpacity(0.2);
                                            }

                                            var markers = {};
                                            var astroMarkers = {};
                                            
                                            map.on('zoomend', function() {
                                                var z = map.getZoom();
                                                // 🌌 DEEP SPACE TRANSITION
                                                if (z < 2) {
                                                    earthTiles.setOpacity(0.2);
                                                    toggleMarkers(astroMarkers, true);
                                                    toggleMarkers(markers, false);
                                                } else {
                                                    earthTiles.setOpacity(1);
                                                    toggleMarkers(astroMarkers, false);
                                                    toggleMarkers(markers, true);
                                                }
                                            });

                                            function toggleMarkers(group, show) {
                                                for (var id in group) {
                                                    if (show) group[id].addTo(map);
                                                    else group[id].remove();
                                                }
                                            }

                                            function updateMarker(id, lat, lon, name, type) {
                                                var isAstro = type === 'PLANET' || type === 'STAR';
                                                var color = type === 'SATELLITE' ? '#00ff00' : (isAstro ? '#ffcc00' : '#e57373');
                                                
                                                var icon = L.divIcon({
                                                    className: 'custom-div-icon',
                                                    html: "<div style='background-color:"+color+"; width:12px; height:12px; border-radius:50%; border:2px solid white; box-shadow: 0 0 10px "+color+";'></div>",
                                                    iconSize: [12, 12],
                                                    iconAnchor: [6, 6]
                                                });
                                                
                                                var targetGroup = isAstro ? astroMarkers : markers;
                                                
                                                if (targetGroup[id]) {
                                                    targetGroup[id].setLatLng([lat, lon]);
                                                } else {
                                                    targetGroup[id] = L.marker([lat, lon], {icon: icon}).bindPopup(name);
                                                    if ((isAstro && map.getZoom() < 2) || (!isAstro && map.getZoom() >= 2)) {
                                                        targetGroup[id].addTo(map);
                                                    }
                                                }
                                            }

                                            function flyTo(lat, lon, zoom) {
                                                map.flyTo([lat, lon], zoom);
                                            }

                                            function clearAllMarkers() {
                                                toggleMarkers(markers, false);
                                                toggleMarkers(astroMarkers, false);
                                                markers = {};
                                                astroMarkers = {};
                                            }
                                        </script>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://appassets.androidplatform.net", html, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                             // 🛡️ Fix Bleeding: Clear and re-inject markers correctly
                             dashboardViewModel.trackedObjects.forEach { obj ->
                                 webView.evaluateJavascript("updateMarker('${obj.id}', ${obj.latitude}, ${obj.longitude}, '${obj.name}', '${obj.type}')", null)
                             }
                             
                             // 🛰️ Focus management for city search
                             val loc = dashboardViewModel.mapLocation.value
                             if (!loc.isNullOrEmpty() && loc.contains(",")) {
                                 val parts = loc.split(",")
                                 if (parts.size == 2) {
                                     val lat = parts[0].toDoubleOrNull()
                                     val lon = parts[1].toDoubleOrNull()
                                     if (lat != null && lon != null) {
                                         webView.evaluateJavascript("flyTo($lat, $lon, 12)", null)
                                     }
                                 }
                             }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // 🛠️ TACTICAL MAP CONTROLS
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    ) {
                        IconButton(
                            onClick = { dashboardViewModel.pinpointCurrentLocation() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.MyLocation, "My Location", tint = Color.White)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        IconButton(
                            onClick = { webViewInstance?.evaluateJavascript("flyTo(0, 0, 1)", null) },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Default.Public, "Global View", tint = Color.White)
                        }
                    }

                    // Compass HUD Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Navigation, 
                            null, 
                            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -dashboardViewModel.bearing.value },
                            tint = Color.White
                        )
                        Text("${dashboardViewModel.bearing.value.toInt()}°", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // 🛸 CELESTIAL TRACKER PANEL
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isTrackerVisible,
                        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight(0.7f)
                                .padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = 12.dp
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("ORBITAL TRACKER", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Divider(modifier = Modifier.padding(vertical = 8.dp))
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(dashboardViewModel.trackedObjects) { obj ->
                                        Surface(
                                            onClick = {
                                                val zoom = if(obj.type == "SATELLITE") 8 else 4
                                                webViewInstance?.evaluateJavascript("flyTo(${obj.latitude}, ${obj.longitude}, $zoom)", null)
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Transparent
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    if(obj.type == "SATELLITE") Icons.Default.SatelliteAlt else Icons.Default.BrightnessHigh,
                                                    null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = if(obj.type == "SATELLITE") Color.Green else Color.Yellow
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(obj.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    Text("${obj.latitude.toInt()}°, ${obj.longitude.toInt()}°", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 💬 HIDEABLE AI INTELLIGENCE PANEL
            Box(modifier = Modifier.align(Alignment.BottomCenter).imePadding()) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isChatVisible,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(450.dp), // Increased room
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        tonalElevation = 8.dp,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TACTICAL ANALYSIS", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = { 
                                    viewModel.deleteTrend("Tactical Map: $location")
                                }) { 
                                    Icon(Icons.Default.DeleteSweep, "Clear Intel", tint = Color.Gray.copy(alpha = 0.6f)) 
                                }
                                IconButton(onClick = { isChatVisible = false }) { Icon(Icons.Default.ExpandMore, null) }
                            }

                            LazyColumn(
                                state = listState, 
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp) // Breatheable space
                            ) {
                                items(messages) { msg ->
                                    ChatBubble(msg, viewModel, {}, { _, _ -> }, snackbarHostState, coroutineScope)
                                }
                            }

                            ChatInputBar(
                                text = chatText,
                                onTextChange = { chatText = it },
                                onSend = {
                                    val compassInfo = "User Bearing: ${dashboardViewModel.bearing.value.toInt()}° (${dashboardViewModel.orientation.value})"
                                    val prompt = "MAP_ANALYSIS: Location $location. $compassInfo. Analysis Request: $chatText"
                                    viewModel.sendMessage(prompt, trendTitle = "Tactical Map: $location")
                                    chatText = ""
                                },
                                onScreenshotClick = onScreenshotClick, 
                                onCameraClick = onCameraClick, 
                                onFileClick = onFileClick, 
                                onVoiceClick = { /* Integrated */ }, 
                                onScribeClick = { /* Integrated */ }, 
                                isLoading = false,
                                onClearClick = { viewModel.deleteTrend("Tactical Map: $location") },
                                pendingAttachments = viewModel.pendingAttachments,
                                onRemoveAttachment = { viewModel.removePendingAttachment(it) }
                            )
                        }
                    }
                }
            }

            // Open Chat FAB
            if (!isChatVisible) {
                ExtendedFloatingActionButton(
                    onClick = { isChatVisible = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    icon = { Icon(Icons.Default.Chat, null) },
                    text = { Text("INTEL") }
                )
            }
        }
    }
}
