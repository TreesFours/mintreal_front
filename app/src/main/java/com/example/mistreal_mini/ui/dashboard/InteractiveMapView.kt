/** 🛡️ AI SYSTEM PROTOCOL 🛡️
 * SOURCE OF TRUTH: master_system_map.artifact.md
 * 
 * 🚀 FUNCTIONAL PIPELINE:
 * [Input]  <- Location data (Coordinates/City Names) and Discovery Categories
 * [Process] <- Renders Hybrid Masked Leaflet map; manages real-time calibration
 * [Output] -> Professional "Satellite Scope" UI; tactical pathfinding via AI
 *
 * ⚠️ MANDATORY: Never delete history. Only ADD updates/fixes to the Master Map table.
 */
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.dashboard.DashboardViewModel
import com.example.mistreal_mini.ui.chat.ChatInputBar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.example.mistreal_mini.ui.chat.ChatBubble
import com.example.mistreal_mini.ui.chat.InteractionMode
import kotlinx.coroutines.launch

import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView

data class PendingSave(
    val type: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double? = null
)

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
    var mapSearchQuery by remember { mutableStateOf("") }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    val mapIntelTitle = remember(location) {
        if (location.contains(",")) "MAP_INTEL: COORDINATES" else "MAP_INTEL: $location"
    }
    
    val messages by remember(viewModel.messages.size, mapIntelTitle) {
        derivedStateOf {
            viewModel.messages.filter { it.trendTitle == mapIntelTitle }
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
    val snackbarHostState = remember { SnackbarHostState() }
    var isChatVisible by remember { mutableStateOf(false) }
    
    var selectedNavTab by remember { mutableIntStateOf(0) } // 0: Explore, 1: You, 2: Intel Log
    val history by dashboardViewModel.locationHistory.collectAsState()
    val intelLog = dashboardViewModel.intelLog
    var showDiscoveryPopup by remember { mutableStateOf(false) }
    var isDrawMode by remember { mutableStateOf(false) }
    var selectedIntelCategory by remember { mutableStateOf<String?>(null) }
    val discoveryCategories = listOf("Government", "Schools", "Markets", "Banks", "Medical")
    var intelSubTab by remember { mutableIntStateOf(0) } // 0: SCAN, 1: SAVED
    var pendingSave by remember { mutableStateOf<PendingSave?>(null) }
    val savedIntel by dashboardViewModel.savedIntel.collectAsState()
    
    val isMapLoading by dashboardViewModel.isMapLoading
    val isCompassCalibrated by dashboardViewModel.isCompassCalibrated
    val tacticalCircle by dashboardViewModel.tacticalCircle
    val isCalibrationWizardVisible by dashboardViewModel.isCalibrationWizardVisible
    val calibrationProgress by dashboardViewModel.calibrationProgress
    val isLocationEnabled by dashboardViewModel.isLocationEnabled
    val searchMarker by dashboardViewModel.searchMarker
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadTrend(mapIntelTitle)
        dashboardViewModel.fetchCelestialData() 
    }
    
    DisposableEffect(Unit) {
        onDispose { viewModel.exitTrend() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 🌎 BASE LAYER: MULTI-LAYER MASKED MAP
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
                        
                        addJavascriptInterface(object {
                            @android.webkit.JavascriptInterface
                            fun onMapLongClick(lat: Double, lon: Double) {
                                coroutineScope.launch {
                                    if (isDrawMode) {
                                        // 🎯 Reposition the tactical circle here, keeping its current radius
                                        dashboardViewModel.setTacticalCircle(lat, lon, tacticalCircle?.radius ?: 500.0)
                                    } else {
                                        dashboardViewModel.addPin(lat, lon)
                                    }
                                }
                            }
                        }, "AndroidMap")

                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                                <style>
                                    body { margin: 0; padding: 0; background: #0b0d0f; }
                                    #map { height: 100vh; width: 100vw; }
                                    .leaflet-tile { filter: saturate(1.1) contrast(1.1); }
                                    .pin-label {
                                        background: rgba(0,0,0,0.8);
                                        border: 1px solid rgba(255,255,255,0.3);
                                        color: #fff;
                                        font-size: 10px;
                                        font-weight: bold;
                                        padding: 2px 6px;
                                        border-radius: 4px;
                                        white-space: nowrap;
                                    }
                                    .dist-label { color: #81C784; font-size: 9px; font-weight: normal; margin-top: 2px; }
                                    
                                    /* 🔦 SATELLITE SCOPE CLIP CSS */
                                    .satellite-pane { z-index: 400; }
                                </style>
                            </head>
                            <body>
                                <div id="map"></div>
                                <script>
                                    var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([0, 0], 2);
                                    
                                    // 1. Base Layer: Standard OpenStreetMap tiles (no API key required, always available)
                                    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                                        maxZoom: 19,
                                        attribution: '&copy; OpenStreetMap contributors'
                                    }).addTo(map);
                                    
                                    // 2. Satellite Layer (Masked)
                                    var satLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                                        className: 'satellite-tiles'
                                    });

                                    var mainMarker = null;
                                    var searchMarker = null;
                                    var pins = L.layerGroup().addTo(map);
                                    var discoveryMarkers = L.layerGroup().addTo(map);
                                    var gpsMarker = null;
                                    var tacticalCircle = null;

                                    map.on('contextmenu', function(e) {
                                        AndroidMap.onMapLongClick(e.latlng.lat, e.latlng.lng);
                                    });

                                    function updateMainLocation(lat, lon, name) {
                                        if (mainMarker) map.removeLayer(mainMarker);
                                        mainMarker = L.circleMarker([lat, lon], {
                                            radius: 12, fillColor: "#42A5F5", color: "#fff", weight: 3, opacity: 1, fillOpacity: 0.8
                                        }).addTo(map).bindTooltip(name, {permanent: true, direction: 'top', className: 'pin-label'});
                                        map.flyTo([lat, lon], 14);
                                    }

                                    function updateSearchMarker(lat, lon, label) {
                                        if (searchMarker) map.removeLayer(searchMarker);
                                        searchMarker = L.marker([lat, lon], {
                                            icon: L.divIcon({
                                                className: 'search-icon',
                                                html: "<div style='background-color:#F44336; width:14px; height:14px; border-radius:50%; border:3px solid white; box-shadow: 0 0 10px #F44336;'></div>",
                                                iconSize: [14, 14],
                                                iconAnchor: [7, 7]
                                            })
                                        }).addTo(map).bindTooltip(label, {permanent: true, direction: 'top', className: 'pin-label'});
                                        map.flyTo([lat, lon], 15);
                                    }

                                    function updateGpsLocation(lat, lon, enabled) {
                                        if (gpsMarker) map.removeLayer(gpsMarker);
                                        if (!enabled) return;
                                        gpsMarker = L.circleMarker([lat, lon], {
                                            radius: 10, fillColor: "#4CAF50", color: "#fff", weight: 3, opacity: 1, fillOpacity: 1
                                        }).addTo(map).bindTooltip("Your Location", {permanent: true, direction: 'top', className: 'pin-label'});
                                    }

                                    function addTacticalPin(lat, lon, label) {
                                        L.marker([lat, lon], {
                                            icon: L.divIcon({
                                                className: 'pin-icon',
                                                html: "<div style='background-color:#2196F3; width:12px; height:12px; border-radius:50%; border:2px solid white;'></div>",
                                                iconSize: [12, 12]
                                            })
                                        }).addTo(pins).bindTooltip(label, {permanent: true, direction: 'top', className: 'pin-label'});
                                    }

                                    function setCircle(lat, lon, radius) {
                                        if (tacticalCircle) map.removeLayer(tacticalCircle);

                                        tacticalCircle = L.circle([lat, lon], {
                                            radius: radius, color: '#42A5F5', fillColor: '#42A5F5', fillOpacity: 0.1, weight: 1
                                        }).addTo(map);

                                        // 🔦 SCOPE LOGIC: satellite imagery only shows inside the circle (CSS clip-path
                                        // over the satellite pane's own container), never over the whole map.
                                        if (!map.hasLayer(satLayer)) satLayer.addTo(map);
                                        requestAnimationFrame(updateSatelliteClip);
                                    }

                                    function updateSatelliteClip() {
                                        if (!tacticalCircle || !tacticalCircle._path) return;
                                        var mapRect = document.getElementById('map').getBoundingClientRect();
                                        var circleRect = tacticalCircle._path.getBoundingClientRect();
                                        var cx = (circleRect.left + circleRect.right) / 2 - mapRect.left;
                                        var cy = (circleRect.top + circleRect.bottom) / 2 - mapRect.top;
                                        var r = circleRect.width / 2;
                                        var satPane = satLayer.getContainer && satLayer.getContainer();
                                        if (satPane) {
                                            var clip = 'circle(' + r + 'px at ' + cx + 'px ' + cy + 'px)';
                                            satPane.style.clipPath = clip;
                                            satPane.style.webkitClipPath = clip;
                                        }
                                    }

                                    map.on('move zoom', function() {
                                        if (tacticalCircle) updateSatelliteClip();
                                    });

                                    function clearAllTactical() {
                                        pins.clearLayers();
                                        discoveryMarkers.clearLayers();
                                        if (tacticalCircle) map.removeLayer(tacticalCircle);
                                        if (satLayer) map.removeLayer(satLayer);
                                        if (searchMarker) map.removeLayer(searchMarker);
                                    }

                                    function renderDiscovery(resultsJson, focusLat, focusLon) {
                                        discoveryMarkers.clearLayers();
                                        var results = JSON.parse(resultsJson);
                                        results.forEach(res => {
                                            var d = map.distance([res.latitude, res.longitude], [focusLat, focusLon]);
                                            var distStr = (d < 1000) ? Math.round(d) + "m" : (d/1000).toFixed(1) + "km";
                                            
                                            L.circleMarker([res.latitude, res.longitude], {
                                                radius: 6, fillColor: "#FF9800", color: "#fff", weight: 1, opacity: 1, fillOpacity: 0.7
                                            }).addTo(discoveryMarkers).bindTooltip(
                                                "<div>" + res.name + "</div><div class='dist-label'>" + distStr + " away</div>", 
                                                {permanent: true, direction: 'right', className: 'pin-label'}
                                            );
                                        });
                                    }
                                </script>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://appassets.androidplatform.net", html, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                     // Handle Focus Updates — always driven by real coordinates, never a
                     // re-geocoded or comma-parsed display string (that was the source of
                     // the map silently landing at 0,0 for city-name or "City, Country" focuses).
                     val focusCoords = dashboardViewModel.mapFocusCoords.value
                     val focusLabel = dashboardViewModel.mapLocation.value ?: location
                     if (focusCoords != null) {
                         webView.evaluateJavascript("updateMainLocation(${focusCoords.first}, ${focusCoords.second}, '$focusLabel')", null)
                     }
                     
                     // Handle Search Marker
                     searchMarker?.let { 
                         webView.evaluateJavascript("updateSearchMarker(${it.latitude}, ${it.longitude}, '${it.label}')", null)
                     } ?: webView.evaluateJavascript("if(searchMarker) map.removeLayer(searchMarker);", null)

                     // GPS Pulse
                     coroutineScope.launch {
                         val gps = dashboardViewModel.locationHelper.getCurrentLocation()
                         gps?.let { webView.evaluateJavascript("updateGpsLocation(${it.latitude}, ${it.longitude}, $isLocationEnabled)", null) }
                     }

                     // Sync Tactical Circle
                     tacticalCircle?.let { 
                         webView.evaluateJavascript("setCircle(${it.latitude}, ${it.longitude}, ${it.radius})", null)
                     } ?: webView.evaluateJavascript("if(tacticalCircle) map.removeLayer(tacticalCircle); if(satLayer) map.removeLayer(satLayer);", null)

                     // Sync Pins
                     webView.evaluateJavascript("pins.clearLayers();", null)
                     dashboardViewModel.intelLog.filter { it.type == "PIN" }.forEach { pin ->
                         webView.evaluateJavascript("addTacticalPin(${pin.latitude}, ${pin.longitude}, '${pin.label}')", null)
                     }

                     // Discovery with center point
                     if (dashboardViewModel.discoveryResults.isNotEmpty()) {
                         coroutineScope.launch {
                            val center = tacticalCircle?.let { it.latitude to it.longitude } 
                                        ?: (0.0 to 0.0) // Fallback
                            val json = com.google.gson.Gson().toJson(dashboardViewModel.discoveryResults)
                            webView.evaluateJavascript("renderDiscovery('$json', ${center.first}, ${center.second})", null)
                         }
                     }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 🛡️ COMMAND OVERLAY (Top)
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, null) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(location.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text("TACTICAL SATELLITE OVERLAY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            IconButton(onClick = { 
                                dashboardViewModel.toggleLocation(!isLocationEnabled) 
                            }) {
                                Icon(Icons.Default.MyLocation, null, tint = if(isLocationEnabled) Color(0xFF4CAF50) else Color.Gray) 
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = mapSearchQuery,
                            onValueChange = { mapSearchQuery = it },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            placeholder = { Text("Search coordinates or city...", fontSize = 14.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = {
                                if (mapSearchQuery.isNotBlank()) {
                                    dashboardViewModel.searchCity(mapSearchQuery)
                                    mapSearchQuery = ""
                                    focusManager.clearFocus()
                                }
                            }),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Compass & Tool Sidebar
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(
                        modifier = Modifier.align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(8.dp).clickable { dashboardViewModel.toggleCalibrationWizard(true) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Navigation, null, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -dashboardViewModel.bearing.value }, tint = Color.White)
                        Text("${dashboardViewModel.bearing.value.toInt()}°", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    FloatingActionButton(
                        onClick = {
                            isDrawMode = !isDrawMode
                            if (isDrawMode) dashboardViewModel.startDrawingCircle() else dashboardViewModel.clearTacticalCircle()
                        },
                        containerColor = if(isDrawMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopStart).size(48.dp)
                    ) { Icon(if(isDrawMode) Icons.Default.Close else Icons.Default.Edit, null) }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom HUD
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 12.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column {
                        // Asset Management
                        if (intelLog.isNotEmpty() || tacticalCircle != null) {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(intelLog) { entry ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { dashboardViewModel.removeIntelItem(entry) },
                                        label = { Text(entry.label, fontSize = 9.sp) },
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp)) {
                                listOf("EXPLORE", "YOU", "INTEL").forEachIndexed { index, title ->
                                    val sel = selectedNavTab == index
                                    TextButton(onClick = { selectedNavTab = index }, colors = ButtonDefaults.textButtonColors(containerColor = if(sel) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if(sel) Color.White else MaterialTheme.colorScheme.onSurface), modifier = Modifier.height(36.dp)) { Text(title, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FloatingActionButton(onClick = { showDiscoveryPopup = true }, containerColor = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Search, null) }
                                FloatingActionButton(onClick = { isChatVisible = !isChatVisible }, containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) { Icon(if(isChatVisible) Icons.Default.ExpandMore else Icons.Default.Psychology, null) }
                            }
                        }
                    }
                }
            }

            // 🔦 SATELLITE FOCUS POPUP
            if (tacticalCircle != null && isDrawMode) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(modifier = Modifier.fillMaxWidth(0.9f).height(180.dp).offset(y = (-120).dp), color = Color.Black.copy(alpha = 0.85f), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SATELLITE FOCUS: ${tacticalCircle!!.radius.toInt()}m", color = Color.White, fontWeight = FontWeight.Black)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                                IconButton(onClick = { dashboardViewModel.adjustCircleRadius(-200.0) }) { Icon(Icons.Default.Remove, null, tint = Color.White) }
                                Text("ADJUST SCOPE", color = Color.White, fontSize = 12.sp)
                                IconButton(onClick = { dashboardViewModel.adjustCircleRadius(200.0) }) { Icon(Icons.Default.Add, null, tint = Color.White) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    pendingSave = PendingSave("CIRCLE", "Circle Focus (${tacticalCircle!!.radius.toInt()}m)", tacticalCircle!!.latitude, tacticalCircle!!.longitude, tacticalCircle!!.radius)
                                }) { Icon(Icons.Default.BookmarkBorder, null, tint = Color.White); Text(" SAVE", color = Color.White) }
                                Button(onClick = { isDrawMode = false; dashboardViewModel.clearTacticalCircle() }) { Text("EXIT SCOPE") }
                            }
                        }
                    }
                }
            }

            // 🧭 CALIBRATION WIZARD (Interactive)
            if (isCalibrationWizardVisible) {
                AlertDialog(
                    onDismissRequest = { dashboardViewModel.toggleCalibrationWizard(false) },
                    title = { Text("Precision Calibration") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rotate device in figure-8 path", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = calibrationProgress, modifier = Modifier.size(100.dp), strokeWidth = 8.dp)
                                Icon(Icons.Default.Sync, null, modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = calibrationProgress * 360 * 5 })
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(if(calibrationProgress < 1f) "CONTINUE MOTION..." else "VERIFIED", fontWeight = FontWeight.Black, color = if(calibrationProgress < 1f) Color.Yellow else Color.Green)
                        }
                    },
                    confirmButton = { Button(onClick = { dashboardViewModel.toggleCalibrationWizard(false) }, enabled = calibrationProgress >= 1f) { Text("COMPLETE") } }
                )
            }
            
            // 📍 YOU Tab: Location History / Trend
            if (selectedNavTab == 1) {
                Surface(modifier = Modifier.fillMaxSize().padding(top = 180.dp, bottom = 80.dp), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            IconButton(onClick = { selectedNavTab = 0 }) { Icon(Icons.Default.ArrowBack, "Back") }
                            Text("VISIT HISTORY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            if (history.isNotEmpty()) {
                                IconButton(onClick = { dashboardViewModel.clearLocationHistory() }) {
                                    Icon(Icons.Default.DeleteSweep, "Clear All", tint = Color.Red)
                                }
                            }
                        }
                        if (history.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No visited locations yet.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(history) { entry ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            dashboardViewModel.focusOnHistoryEntry(entry)
                                            selectedNavTab = 0
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                                Text(entry.cityName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                val subtitle = listOfNotNull(entry.street, entry.town).joinToString(", ").ifBlank { null }
                                                if (subtitle != null) {
                                                    Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                                                }
                                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(entry.timestamp), fontSize = 10.sp, color = Color.Gray)
                                            }
                                            IconButton(onClick = { dashboardViewModel.deleteLocationHistoryEntry(entry.id) }) {
                                                Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // INTEL Tab: Discovery-as-a-list — same category scan as the map's discovery
            // button, but results are a scrollable address list instead of map pins.
            // Tapping a result jumps straight into the mini-chat with that place as context.
            if (selectedNavTab == 2) {
                Surface(modifier = Modifier.fillMaxSize().padding(top = 180.dp, bottom = 80.dp), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    LazyColumn(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                IconButton(onClick = { selectedNavTab = 0 }) { Icon(Icons.Default.ArrowBack, "Back") }
                                Text("STRATEGIC INTEL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            }
                        }
                        item {
                            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp)) {
                                listOf("SCAN", "SAVED").forEachIndexed { index, title ->
                                    val sel = intelSubTab == index
                                    TextButton(
                                        onClick = { intelSubTab = index },
                                        colors = ButtonDefaults.textButtonColors(containerColor = if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if (sel) Color.White else MaterialTheme.colorScheme.onSurface),
                                        modifier = Modifier.height(36.dp)
                                    ) { Text(title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                        }

                        if (intelSubTab == 0) {
                            item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(discoveryCategories) { cat ->
                                        FilterChip(
                                            selected = selectedIntelCategory == cat,
                                            onClick = {
                                                selectedIntelCategory = cat
                                                dashboardViewModel.fetchDiscoveryData(cat)
                                            },
                                            label = { Text(cat, fontSize = 12.sp) }
                                        )
                                    }
                                }
                            }

                            if (selectedIntelCategory != null) {
                                if (isMapLoading) {
                                    item {
                                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                        }
                                    }
                                } else if (dashboardViewModel.discoveryResults.isEmpty()) {
                                    item { Text("No $selectedIntelCategory found nearby.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                                } else {
                                    items(dashboardViewModel.discoveryResults) { result ->
                                        val focusPoint = tacticalCircle?.let { it.latitude to it.longitude } ?: dashboardViewModel.mapFocusCoords.value
                                        val distanceLabel = focusPoint?.let { (fLat, fLon) ->
                                            val out = FloatArray(1)
                                            android.location.Location.distanceBetween(fLat, fLon, result.latitude, result.longitude, out)
                                            if (out[0] < 1000) "${out[0].toInt()}m away" else "${"%.1f".format(out[0] / 1000)}km away"
                                        }
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                val positionNote = focusPoint?.let { (fLat, fLon) ->
                                                    "The user's reference position is approximately ($fLat, $fLon)."
                                                } ?: "The user's current position and any marked point are not available — ask them where they are before giving directions."
                                                val prompt = "PLACE_INQUIRY: ${result.name}, ${result.address}. $positionNote Give a brief summary of this place and directions from the reference position if available."
                                                viewModel.sendMessage(prompt, trendTitle = mapIntelTitle)
                                                isChatVisible = true
                                                selectedNavTab = 0
                                            }
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Place, null, tint = MaterialTheme.colorScheme.primary)
                                                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                                    Text(result.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(result.address, fontSize = 11.sp, color = Color.Gray)
                                                    distanceLabel?.let { Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary) }
                                                }
                                                IconButton(onClick = { pendingSave = PendingSave("SEARCH", result.name, result.latitude, result.longitude) }) {
                                                    Icon(Icons.Default.BookmarkBorder, "Save", modifier = Modifier.size(20.dp))
                                                }
                                                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }

                            if (intelLog.isNotEmpty()) {
                                item {
                                    Text("PIN & SEARCH LOG", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                                }
                                items(intelLog) { entry ->
                                    Card(modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if(entry.type == "PIN") Icons.Default.PushPin else Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                                Text(entry.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text(java.text.DateFormat.getTimeInstance().format(entry.timestamp), fontSize = 10.sp, color = Color.Gray)
                                            }
                                            IconButton(onClick = { pendingSave = PendingSave(entry.type, entry.label, entry.latitude, entry.longitude) }) {
                                                Icon(Icons.Default.BookmarkBorder, "Save", modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = { dashboardViewModel.removeIntelItem(entry) }) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
                                        }
                                    }
                                }
                            }
                        } else {
                            // SAVED sub-tab: grouped bookmarks, ungrouped ones listed flat below
                            if (savedIntel.isEmpty()) {
                                item { Text("Nothing saved yet — use the bookmark icon on a pin, search result, or circle.", color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp)) }
                            } else {
                                val grouped = savedIntel.filter { it.groupName != null }.groupBy { it.groupName!! }
                                val ungrouped = savedIntel.filter { it.groupName == null }

                                grouped.forEach { (group, items) ->
                                    item {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                            Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Text(" $group", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            IconButton(onClick = { dashboardViewModel.deleteSavedGroup(group) }) { Icon(Icons.Default.Delete, "Delete Group", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) }
                                        }
                                    }
                                    items(items) { entry ->
                                        SavedIntelRow(entry, onDelete = { dashboardViewModel.deleteSavedIntel(entry.id) }) {
                                            dashboardViewModel.focusOnSavedIntel(entry)
                                            isChatVisible = false
                                            selectedNavTab = 0
                                        }
                                    }
                                }

                                if (ungrouped.isNotEmpty()) {
                                    item {
                                        Text("UNGROUPED", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                                    }
                                    items(ungrouped) { entry ->
                                        SavedIntelRow(entry, onDelete = { dashboardViewModel.deleteSavedIntel(entry.id) }) {
                                            dashboardViewModel.focusOnSavedIntel(entry)
                                            isChatVisible = false
                                            selectedNavTab = 0
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // AI INTELLIGENCE
            androidx.compose.animation.AnimatedVisibility(visible = isChatVisible, modifier = Modifier.align(Alignment.BottomCenter).imePadding()) {
                Surface(modifier = Modifier.fillMaxWidth().height(550.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), tonalElevation = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                            Text(" AI ANALYSIS", fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { isChatVisible = false }) { Icon(Icons.Default.Close, null) }
                        }
                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            items(messages) { msg -> ChatBubble(msg, viewModel, {}, { _, _ -> }, snackbarHostState, coroutineScope) }
                            if (viewModel.isLoading.value) item { com.example.mistreal_mini.ui.chat.TypingIndicator() }
                        }
                        ChatInputBar(text = chatText, onTextChange = { chatText = it }, onSend = {
                                val focus = tacticalCircle?.let { "Circle Area (${it.radius}m)" } ?: location
                                val items = dashboardViewModel.discoveryResults.joinToString { it.name }
                                val prompt = "ANALYSIS_REQ: Focus on $focus. Data: $items\nQuery: $chatText"
                                viewModel.sendMessage(prompt, trendTitle = mapIntelTitle); chatText = ""
                            }, onScreenshotClick = onScreenshotClick, onCameraClick = onCameraClick, onFileClick = onFileClick, onVoiceClick = {}, onScribeClick = {}, isLoading = false, pendingAttachments = viewModel.pendingAttachments, onRemoveAttachment = {}
                        )
                    }
                }
            }

            if (showDiscoveryPopup) {
                AlertDialog(onDismissRequest = { showDiscoveryPopup = false }, title = { Text("Deep Scan Focus") }, text = {
                        Column { discoveryCategories.forEach { cat ->
                                TextButton(onClick = { dashboardViewModel.fetchDiscoveryData(cat); showDiscoveryPopup = false }, modifier = Modifier.fillMaxWidth()) { Text(cat, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }, confirmButton = { TextButton(onClick = { showDiscoveryPopup = false }) { Text("Cancel") } }
                )
            }

            pendingSave?.let { save ->
                SaveIntelDialog(
                    existingGroups = savedIntel.mapNotNull { it.groupName }.distinct(),
                    onConfirm = { groupName ->
                        dashboardViewModel.saveIntelItem(save.type, save.label, save.latitude, save.longitude, save.radius, groupName)
                        pendingSave = null
                    },
                    onDismiss = { pendingSave = null }
                )
            }
        }
    }
}

@Composable
fun SavedIntelRow(
    entry: com.example.mistreal_mini.data.local.entity.SavedIntelEntity,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when (entry.type) {
                    "CIRCLE" -> Icons.Default.RadioButtonUnchecked
                    "PIN" -> Icons.Default.PushPin
                    else -> Icons.Default.Search
                },
                null, tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val subtitleParts = mutableListOf<String>()
                entry.radius?.let { subtitleParts.add("${it.toInt()}m radius") }
                entry.bearing?.let { subtitleParts.add("${it.toInt()}°") }
                subtitleParts.add(java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(entry.timestamp))
                Text(subtitleParts.joinToString("  •  "), fontSize = 10.sp, color = Color.Gray)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
fun SaveIntelDialog(
    existingGroups: List<String>,
    onConfirm: (groupName: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to Intel") },
        text = {
            Column {
                Text("Leave blank to save individually, or name/pick a group.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = { Text("Group name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (existingGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(existingGroups) { g ->
                            FilterChip(selected = groupName == g, onClick = { groupName = g }, label = { Text(g, fontSize = 12.sp) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(groupName) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
