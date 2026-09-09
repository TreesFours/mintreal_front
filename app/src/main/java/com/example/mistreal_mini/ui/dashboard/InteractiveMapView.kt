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

import android.graphics.Rect
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mistreal_mini.data.local.entity.SavedIntelEntity
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.ChatBubble
import com.example.mistreal_mini.ui.chat.components.ChatInputBar
import com.example.mistreal_mini.ui.chat.components.InteractionMode
import com.example.mistreal_mini.ui.chat.components.TypingIndicator
import com.example.mistreal_mini.ui.dashboard.components.*
import kotlinx.coroutines.launch

data class PendingSave(
    val type: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double? = null
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InteractiveMapView(
    location: String,
    onClose: () -> Unit,
    viewModel: ChatViewModel,
    dashboardViewModel: DashboardViewModel,
    mapViewModel: TacticalMapViewModel,
    celestialViewModel: CelestialViewModel,
    snackbarHostState: SnackbarHostState,
    onScreenshotClick: (Rect?) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onFileClick: () -> Unit = {},
    startInSpaceMode: Boolean = false
) {
    var chatText by remember { mutableStateOf("") }
    var mapSearchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    
    val mapIntelTitle = remember(location) {
        if (location.contains(",")) "MAP_INTEL: COORDINATES" else "MAP_INTEL: $location"
    }
    
    val messages = viewModel.messages
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val mapSearchContext = LocalContext.current

    var showSaveDialog by remember { mutableStateOf(false) }

    fun performMapSearch() {
        val query = mapSearchQuery
        if (query.isBlank()) return
        coroutineScope.launch {
            when (mapViewModel.searchCity(query)) {
                is CitySearchResult.Success -> {
                    mapSearchQuery = ""
                    focusManager.clearFocus()
                    viewModel.loadTrend("Tactical Sector: ${mapViewModel.mapLocation.value}")
                }
                is CitySearchResult.Ambiguous -> {
                    focusManager.clearFocus()
                }
                is CitySearchResult.NotFound -> {
                    focusManager.clearFocus()
                    android.widget.Toast.makeText(mapSearchContext, "No location found for \"$query\"", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    var isChatVisible by remember { mutableStateOf(false) }
    var selectedNavTab by remember { mutableIntStateOf(0) }
    
    val history by mapViewModel.locationHistory.collectAsState()
    val intelLog = mapViewModel.intelLog
    var isDrawMode by remember { mutableStateOf(false) }
    var selectedIntelCategories by remember { mutableStateOf(setOf<String>()) }
    var mapVisibleCategories by remember { mutableStateOf<Set<String>?>(null) }
    var mapPendingSave by remember { mutableStateOf<PendingSave?>(null) }
    val savedIntel by mapViewModel.savedIntel.collectAsState()
    
    val isMapLoading = mapViewModel.isMapLoading.value
    val isCompassCalibrated = dashboardViewModel.isCompassCalibrated.value
    val tacticalCircle = mapViewModel.tacticalCircle.value
    val isCalibrationWizardVisible = dashboardViewModel.isCalibrationWizardVisible.value
    val calibrationProgress = dashboardViewModel.calibrationProgress.value
    val isLocationEnabled = mapViewModel.isLocationEnabled.value
    val searchMarker = mapViewModel.searchMarker.value
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    LaunchedEffect(Unit) {
        viewModel.loadTrend(mapIntelTitle)
        celestialViewModel.fetchCelestialData()
    }

    LaunchedEffect(mapViewModel.mapFocusCoords.value) {
        selectedIntelCategories = emptySet()
        mapVisibleCategories = null
    }

    LaunchedEffect(tacticalCircle) {
        if (tacticalCircle == null) isDrawMode = false
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
                                        mapViewModel.setTacticalCircle(lat, lon, tacticalCircle?.radius ?: 500.0)
                                    } else {
                                        mapViewModel.addPin(lat, lon)
                                    }
                                }
                            }
                            @android.webkit.JavascriptInterface
                            fun onMapCenterReported(lat: Double, lon: Double) {
                                coroutineScope.launch {
                                    mapViewModel.startDrawingCircle(lat, lon)
                                }
                            }
                        }, "AndroidMap")

                        loadDataWithBaseURL(null, getMapHtml(), "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    mapViewModel.mapFocusCoords.value?.let { 
                        webView.evaluateJavascript("map.setView([${it.first}, ${it.second}], 15);", null)
                    }
                    
                    mapViewModel.searchMarker.value?.let { entry ->
                        webView.evaluateJavascript("updateSearchMarker(${entry.latitude}, ${entry.longitude}, '${entry.label}')", null)
                    }

                    if (isLocationEnabled) {
                        coroutineScope.launch {
                            mapViewModel.locationHelper.getCurrentLocation()?.let { 
                                webView.evaluateJavascript("updateGpsLocation(${it.latitude}, ${it.longitude}, true)", null)
                            }
                        }
                    }

                    tacticalCircle?.let {
                        webView.evaluateJavascript("setCircle(${it.latitude}, ${it.longitude}, ${it.radius})", null)
                        val label = mapViewModel.focusPlaceLabel.value?.replace("'", "\\'")
                        if (label != null) {
                            webView.evaluateJavascript("showFocusPlaceMarker(${it.latitude}, ${it.longitude}, '$label')", null)
                        } else {
                            webView.evaluateJavascript("if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);", null)
                        }
                    } ?: webView.evaluateJavascript("if(tacticalCircle) map.removeLayer(tacticalCircle); if(satLayer) map.removeLayer(satLayer); if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);", null)

                    webView.evaluateJavascript("pins.clearLayers();", null)
                    if (!isDrawMode) {
                        mapViewModel.intelLog.filter { it.type == "PIN" }.forEach { pin ->
                            webView.evaluateJavascript("addTacticalPin(${pin.latitude}, ${pin.longitude}, '${pin.label}')", null)
                        }
                    }

                    coroutineScope.launch {
                        val center = tacticalCircle?.let { it.latitude to it.longitude }
                                    ?: mapViewModel.mapFocusCoords.value
                                    ?: (0.0 to 0.0)
                        val visible = if (isDrawMode) emptyList() else {
                            mapVisibleCategories?.let { filter -> mapViewModel.discoveryResults.filter { it.category in filter } }
                                ?: mapViewModel.discoveryResults
                        }
                        val json = com.google.gson.Gson().toJson(visible)
                        webView.evaluateJavascript("renderDiscovery('$json', ${center.first}, ${center.second})", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(location.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                Text("TACTICAL SATELLITE OVERLAY", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            IconButton(onClick = { 
                                mapViewModel.toggleLocation(!isLocationEnabled) 
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
                            trailingIcon = {
                                IconButton(onClick = { performMapSearch() }) {
                                    Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performMapSearch() }),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (dashboardViewModel.compassSupported.value) {
                        Column(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .padding(8.dp).clickable { dashboardViewModel.toggleCalibrationWizard(true) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.Navigation, null, modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = -dashboardViewModel.bearing.value }, tint = Color.White)
                            Text("${dashboardViewModel.bearing.value.toInt()}°", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    FloatingActionButton(
                        onClick = {
                            isDrawMode = !isDrawMode
                            if (isDrawMode) {
                                webViewInstance?.evaluateJavascript("reportMapCenter()", null)
                            } else {
                                mapViewModel.clearTacticalCircle()
                            }
                        },
                        containerColor = if(isDrawMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.align(Alignment.TopStart).size(48.dp)
                    ) { Icon(if(isDrawMode) Icons.Default.Close else Icons.Default.Edit, null) }
                }

                Spacer(modifier = Modifier.weight(1f))

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 12.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column {
                        if (tacticalCircle != null && isDrawMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${tacticalCircle.radius.toInt()}m", fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                                IconButton(onClick = { mapViewModel.adjustCircleRadius(-200.0) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { mapViewModel.adjustCircleRadius(200.0) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    mapPendingSave = PendingSave("CIRCLE", "Circle Focus (${tacticalCircle.radius.toInt()}m)", tacticalCircle.latitude, tacticalCircle.longitude, tacticalCircle.radius)
                                    showSaveDialog = true
                                }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.BookmarkBorder, "Save", modifier = Modifier.size(18.dp)) }
                                IconButton(onClick = { isDrawMode = false; mapViewModel.clearTacticalCircle() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Exit Scope", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                                IconButton(onClick = { mapViewModel.nudgeTacticalCircle(0.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(18.dp)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { mapViewModel.nudgeTacticalCircle(270.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(18.dp)) }
                                    Spacer(modifier = Modifier.width(26.dp))
                                    IconButton(onClick = { mapViewModel.nudgeTacticalCircle(90.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp)) }
                                }
                                IconButton(onClick = { mapViewModel.nudgeTacticalCircle(180.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(18.dp)) }
                            }
                        }

                        if (intelLog.isNotEmpty() || tacticalCircle != null) {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(intelLog) { entry ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { mapViewModel.removeIntelItem(entry) },
                                        label = { Text(entry.label, fontSize = 9.sp) },
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp)) {
                                listOf("EXPLORE", "YOU", "INTEL", "SCAN").forEachIndexed { index, title ->
                                    val sel = selectedNavTab == index
                                    TextButton(onClick = { selectedNavTab = index }, colors = ButtonDefaults.textButtonColors(containerColor = if(sel) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if(sel) Color.White else MaterialTheme.colorScheme.onSurface), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(title, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            FloatingActionButton(onClick = { isChatVisible = !isChatVisible }, containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) { Icon(if(isChatVisible) Icons.Default.ExpandMore else Icons.Default.Psychology, null) }
                        }
                    }
                }
            }

            if (isCalibrationWizardVisible) {
                AlertDialog(
                    onDismissRequest = { dashboardViewModel.toggleCalibrationWizard(false) },
                    title = { Text("Precision Calibration") },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rotate device in figure-8 path", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(20.dp))
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { calibrationProgress }, 
                                    modifier = Modifier.size(100.dp), 
                                    strokeWidth = 8.dp
                                )
                                Icon(Icons.Default.Sync, null, modifier = Modifier.size(40.dp).graphicsLayer { rotationZ = calibrationProgress * 360 * 5 })
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(if(calibrationProgress < 1f) "CONTINUE MOTION..." else "VERIFIED", fontWeight = FontWeight.Black, color = if(calibrationProgress < 1f) Color.Yellow else Color.Green)
                        }
                    },
                    confirmButton = { Button(onClick = { dashboardViewModel.toggleCalibrationWizard(false) }, enabled = calibrationProgress >= 1f) { Text("COMPLETE") } }
                )
            }
            
            if (isChatVisible) {
                Surface(modifier = Modifier.fillMaxWidth().height(550.dp).align(Alignment.BottomCenter).imePadding(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp), tonalElevation = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary)
                            Text(" AI ANALYSIS", fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { isChatVisible = false }) { Icon(Icons.Default.Close, null) }
                        }
                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            items(messages) { msg -> ChatBubble(msg, viewModel, {}, { _, _ -> }, snackbarHostState, coroutineScope) }
                            if (viewModel.isLoading.value) item { TypingIndicator() }
                        }
                        ChatInputBar(
                            text = chatText, 
                            onTextChange = { chatText = it }, 
                            onSend = {
                                val contextParts = mutableListOf("Current map focus: $location.")
                                val prompt = "MAP_CONTEXT:\n${contextParts.joinToString("\n")}\n\nUser question: $chatText"
                                viewModel.sendMessage(prompt, trendTitle = mapIntelTitle); chatText = ""
                                focusManager.clearFocus()
                            }, 
                            onScreenshotClick = { onScreenshotClick(null) }, 
                            onScreenRecordClick = {},
                            onCameraClick = onCameraClick, 
                            onVideoClick = {},
                            onFileClick = onFileClick, 
                            onVoiceClick = {}, 
                            onConversationClick = {},
                            onScribeClick = {}, 
                            isLoading = false, 
                            pendingAttachments = viewModel.pendingAttachments, 
                            onRemoveAttachment = {}
                        )
                    }
                }
            }

            if (mapViewModel.ambiguousLocations.isNotEmpty()) {
                AmbiguousLocationDialog(
                    locations = mapViewModel.ambiguousLocations,
                    onSelect = { address -> mapViewModel.selectAmbiguousLocation(address) },
                    onDismiss = { mapViewModel.clearAmbiguousLocations() }
                )
            }

            if (showSaveDialog && mapPendingSave != null) {
                SaveIntelDialog(
                    existingGroups = savedIntel.mapNotNull { it.groupName }.distinct(),
                    onConfirm = { groupName: String? ->
                        mapViewModel.saveIntelItem(mapPendingSave!!.type, mapPendingSave!!.label, mapPendingSave!!.latitude, mapPendingSave!!.longitude, dashboardViewModel.bearing.value, mapPendingSave!!.radius, groupName)
                        mapPendingSave = null
                        showSaveDialog = false
                    },
                    onDismiss = { showSaveDialog = false }
                )
            }
        }
    }
}

@Composable
fun SaveIntelDialog(
    existingGroups: List<String>,
    onConfirm: (String?) -> Unit,
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

private fun getMapHtml(): String = """
    <!DOCTYPE html>
    <html>
    <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
            #map { height: 100vh; width: 100vw; background: #000; }
            .leaflet-container { background: #000 !important; }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <script>
            var map = L.map('map', { zoomControl: false, attributionControl: false }).setView([0,0], 2);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
            var pins = L.layerGroup().addTo(map);
            var tacticalCircle, satLayer, focusPlaceMarker, searchMarker, gpsMarker;

            function updateSearchMarker(lat, lon, label) {
                if(searchMarker) map.removeLayer(searchMarker);
                searchMarker = L.marker([lat, lon]).addTo(map).bindPopup(label).openPopup();
            }
            function addTacticalPin(lat, lon, label) {
                L.marker([lat, lon]).addTo(pins).bindPopup(label);
            }
            function setCircle(lat, lon, radius) {
                if(tacticalCircle) map.removeLayer(tacticalCircle);
                tacticalCircle = L.circle([lat, lon], { radius: radius, color: 'cyan', fillOpacity: 0.2 }).addTo(map);
            }
            function showFocusPlaceMarker(lat, lon, label) {
                if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);
                focusPlaceMarker = L.marker([lat, lon]).addTo(map).bindPopup(label).openPopup();
            }
            function updateGpsLocation(lat, lon, center) {
                if(gpsMarker) map.removeLayer(gpsMarker);
                gpsMarker = L.circleMarker([lat, lon], { radius: 8, color: 'green' }).addTo(map);
                if(center) map.setView([lat, lon], 15);
            }
            function reportMapCenter() {
                var center = map.getCenter();
                AndroidMap.onMapCenterReported(center.lat, center.lng);
            }
            map.on('contextmenu', function(e) {
                AndroidMap.onMapLongClick(e.latlng.lat, e.latlng.lng);
            });
            function renderDiscovery(json, cLat, cLon) {
                // Clear existing markers if any (simplified)
            }
        </script>
    </body>
    </html>
""".trimIndent()
