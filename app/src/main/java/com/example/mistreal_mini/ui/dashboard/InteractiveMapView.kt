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
import androidx.compose.foundation.ExperimentalFoundationApi
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

@OptIn(ExperimentalFoundationApi::class)
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
    val mapSearchContext = androidx.compose.ui.platform.LocalContext.current

    fun performMapSearch() {
        val query = mapSearchQuery
        if (query.isBlank()) return
        coroutineScope.launch {
            when (dashboardViewModel.searchCity(query)) {
                is CitySearchResult.Success -> {
                    mapSearchQuery = ""
                    focusManager.clearFocus()
                }
                is CitySearchResult.Ambiguous -> {
                    // AmbiguousLocationDialog (below) renders while dashboardViewModel.ambiguousLocations is non-empty
                    focusManager.clearFocus()
                }
                is CitySearchResult.NotFound -> {
                    focusManager.clearFocus()
                    android.widget.Toast.makeText(mapSearchContext, "No location found for \"$query\"", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var isChatVisible by remember { mutableStateOf(false) }
    
    var selectedNavTab by remember { mutableIntStateOf(0) } // 0: Explore, 1: You, 2: Intel Log
    val history by dashboardViewModel.locationHistory.collectAsState()
    val intelLog = dashboardViewModel.intelLog
    var isDrawMode by remember { mutableStateOf(false) }
    var selectedIntelCategories by remember { mutableStateOf(setOf<String>()) }
    // 🎯 null = show every currently-fetched category's labels on the map (View All);
    // a specific set = show only those categories' labels (View [Category]); used to
    // filter what renderDiscovery actually draws, independent of what's checked in SCAN.
    var mapVisibleCategories by remember { mutableStateOf<Set<String>?>(null) }
    var expandedHistoryCity by remember { mutableStateOf<String?>(null) }
    var intelSearchRadius by remember { mutableFloatStateOf(3000f) }
    val discoveryCategories = listOf("Government", "Schools", "Markets", "Banks", "Medical", "Rail", "Waterbody", "Religious Building", "Parks")
    var categoriesExpanded by remember { mutableStateOf(false) }
    var expandedScanCategories by remember { mutableStateOf(setOf<String>()) }
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

    // 🛡️ Whenever the map's focus moves to a genuinely new area (new search, pinpoint-me,
    // etc. — the ViewModel clears stale discoveryResults for these), reset which category
    // checkboxes look "checked" in the SCAN tab so it doesn't keep showing an old area's
    // selections against an empty/new result set.
    LaunchedEffect(dashboardViewModel.mapFocusCoords.value) {
        selectedIntelCategories = emptySet()
        mapVisibleCategories = null
    }

    // 🛡️ Keep draw-mode UI in sync when the circle gets cleared out from under it for any
    // reason (a new search, a pin being removed, etc. — see the ViewModel functions that
    // now call clearTacticalCircle()) — previously the mini-bar/hide-pins state could stay
    // stuck "in draw mode" with no circle left to control.
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
                            @android.webkit.JavascriptInterface
                            fun onMapCenterReported(lat: Double, lon: Double) {
                                // 🎯 Freelance circle placement: fired when entering draw mode with
                                // nothing else on the map (no pin/search/GPS) to fall back to.
                                coroutineScope.launch {
                                    dashboardViewModel.startDrawingCircle(lat, lon)
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
                                    var focusPlaceMarker = null;

                                    map.on('contextmenu', function(e) {
                                        AndroidMap.onMapLongClick(e.latlng.lat, e.latlng.lng);
                                    });

                                    function reportMapCenter() {
                                        var c = map.getCenter();
                                        AndroidMap.onMapCenterReported(c.lat, c.lng);
                                    }

                                    function updateMainLocation(lat, lon, name) {
                                        if (mainMarker) map.removeLayer(mainMarker);
                                        mainMarker = L.circleMarker([lat, lon], {
                                            radius: 12, fillColor: "#42A5F5", color: "#fff", weight: 3, opacity: 1, fillOpacity: 0.8
                                        }).addTo(map).bindTooltip(name, {permanent: true, direction: 'top', className: 'pin-label'});
                                        map.flyTo([lat, lon], 14);
                                    }

                                    // 📍 Classic "map pin" teardrop — the tip (not the icon's center)
                                    // is the exact coordinate, anchored at iconAnchor [12,24]. Used
                                    // for anything that represents one specific address/place, so the
                                    // label always visibly points down at the exact spot it describes
                                    // instead of an abstract dot floating near it.
                                    function pinIcon(color) {
                                        return L.divIcon({
                                            className: 'map-pin-icon',
                                            html: "<div style='width:24px;height:24px;position:relative;'>" +
                                                  "<div style='width:20px;height:20px;background:" + color + ";border:2px solid white;border-radius:50% 50% 50% 0;transform:rotate(-45deg);position:absolute;left:2px;top:0;box-shadow:0 2px 4px rgba(0,0,0,0.4);'></div>" +
                                                  "</div>",
                                            iconSize: [24, 24],
                                            iconAnchor: [12, 24]
                                        });
                                    }

                                    function updateSearchMarker(lat, lon, label) {
                                        if (searchMarker) map.removeLayer(searchMarker);
                                        searchMarker = L.marker([lat, lon], { icon: pinIcon('#F44336') })
                                            .addTo(map).bindTooltip(label, {permanent: true, direction: 'top', offset: [0, -24], className: 'pin-label'});
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
                                        L.marker([lat, lon], { icon: pinIcon('#2196F3') })
                                            .addTo(pins).bindTooltip(label, {permanent: true, direction: 'top', offset: [0, -24], className: 'pin-label'});
                                    }

                                    function setCircle(lat, lon, radius) {
                                        if (tacticalCircle) map.removeLayer(tacticalCircle);

                                        tacticalCircle = L.circle([lat, lon], {
                                            radius: radius, color: '#42A5F5', fillColor: '#42A5F5', fillOpacity: 0.1, weight: 1
                                        }).addTo(map);

                                        // 🔦 Frame the circle with surrounding context instead of leaving zoom
                                        // untouched (which let a big circle fill the entire screen edge-to-edge).
                                        map.fitBounds(tacticalCircle.getBounds(), { padding: [40, 40] });

                                        // 🔦 SCOPE LOGIC: satellite imagery only shows inside the circle (CSS clip-path
                                        // over the satellite pane's own container), never over the whole map.
                                        if (!map.hasLayer(satLayer)) satLayer.addTo(map);
                                        requestAnimationFrame(updateSatelliteClip);
                                    }

                                    // 🎯 Marks exactly which place a satellite spotlight circle is
                                    // centered on (used by SCAN's "View" action) — without this the
                                    // circle was just unlabeled satellite imagery with no indication
                                    // of which building within it was actually the result you tapped.
                                    function showFocusPlaceMarker(lat, lon, label) {
                                        if (focusPlaceMarker) map.removeLayer(focusPlaceMarker);
                                        if (!label) return;
                                        focusPlaceMarker = L.marker([lat, lon], { icon: pinIcon('#FFC107') })
                                            .addTo(map).bindTooltip(label, {permanent: true, direction: 'top', offset: [0, -24], className: 'pin-label'});
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
                                        if (focusPlaceMarker) map.removeLayer(focusPlaceMarker);
                                    }

                                    // 🎨 One color per category so multiple simultaneously-active
                                    // category filters stay visually distinguishable on the map.
                                    var CATEGORY_COLORS = {
                                        "Government": "#FF9800", "Schools": "#42A5F5", "Markets": "#66BB6A",
                                        "Banks": "#AB47BC", "Medical": "#EF5350", "Rail": "#8D6E63",
                                        "Waterbody": "#26C6DA", "Religious Building": "#EC407A", "Parks": "#7CB342"
                                    };

                                    function renderDiscovery(resultsJson, focusLat, focusLon) {
                                        discoveryMarkers.clearLayers();
                                        var results = JSON.parse(resultsJson);
                                        results.forEach(res => {
                                            var d = map.distance([res.latitude, res.longitude], [focusLat, focusLon]);
                                            var distStr = (d < 1000) ? Math.round(d) + "m" : (d/1000).toFixed(1) + "km";
                                            var color = CATEGORY_COLORS[res.category] || "#FF9800";

                                            L.marker([res.latitude, res.longitude], { icon: pinIcon(color) })
                                                .addTo(discoveryMarkers).bindTooltip(
                                                "<div>" + res.name + "</div><div class='dist-label'>" + distStr + " away</div>",
                                                {permanent: true, direction: 'right', offset: [8, -12], className: 'pin-label'}
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

                     // GPS Pulse — turning OFF must never depend on a fresh GPS fix succeeding
                     // (that was the bug: if getCurrentLocation() returned null at that exact
                     // moment, the removal call was skipped entirely and the marker got stuck).
                     // 🎯 Hidden while freelance-drawing a circle so pins/GPS don't visually
                     // compete with placing/moving it — restored automatically on exit.
                     if (!isLocationEnabled || isDrawMode) {
                         webView.evaluateJavascript("updateGpsLocation(0, 0, false)", null)
                     } else {
                         coroutineScope.launch {
                             val gps = dashboardViewModel.locationHelper.getCurrentLocation()
                             gps?.let { webView.evaluateJavascript("updateGpsLocation(${it.latitude}, ${it.longitude}, true)", null) }
                         }
                     }

                     // Sync Tactical Circle
                     tacticalCircle?.let {
                         webView.evaluateJavascript("setCircle(${it.latitude}, ${it.longitude}, ${it.radius})", null)
                         val label = dashboardViewModel.focusPlaceLabel.value?.replace("'", "\\'")
                         if (label != null) {
                             webView.evaluateJavascript("showFocusPlaceMarker(${it.latitude}, ${it.longitude}, '$label')", null)
                         } else {
                             webView.evaluateJavascript("if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);", null)
                         }
                     } ?: webView.evaluateJavascript("if(tacticalCircle) map.removeLayer(tacticalCircle); if(satLayer) map.removeLayer(satLayer); if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);", null)

                     // Sync Pins — also hidden during freelance circle drawing, see above.
                     webView.evaluateJavascript("pins.clearLayers();", null)
                     if (!isDrawMode) {
                         dashboardViewModel.intelLog.filter { it.type == "PIN" }.forEach { pin ->
                             webView.evaluateJavascript("addTacticalPin(${pin.latitude}, ${pin.longitude}, '${pin.label}')", null)
                         }
                     }

                     // Discovery with center point — always call this, even when the list is
                     // empty (unchecking a category, or clearing the last one, needs to reach
                     // the JS to actually clear its pins; the old isNotEmpty() guard meant
                     // unchecked categories' markers just stayed stuck on the map forever).
                     // 🎯 While viewing a single specific place (satellite spotlight/draw mode),
                     // hide every other discovery label — only that one place's dedicated
                     // focusPlaceMarker should show. Otherwise, respect mapVisibleCategories
                     // (View All vs View [one category]).
                     coroutineScope.launch {
                        val center = tacticalCircle?.let { it.latitude to it.longitude }
                                    ?: dashboardViewModel.mapFocusCoords.value
                                    ?: (0.0 to 0.0)
                        val visible = if (isDrawMode) emptyList() else {
                            mapVisibleCategories?.let { filter -> dashboardViewModel.discoveryResults.filter { it.category in filter } }
                                ?: dashboardViewModel.discoveryResults
                        }
                        val json = com.google.gson.Gson().toJson(visible)
                        webView.evaluateJavascript("renderDiscovery('$json', ${center.first}, ${center.second})", null)
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
                            trailingIcon = {
                                IconButton(onClick = { performMapSearch() }) {
                                    Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                                }
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { performMapSearch() }),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Compass & Tool Sidebar — hidden entirely on devices with no compass
                // hardware at all, rather than showing a wizard that could never succeed.
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
                                // Ask the WebView where it's currently centered, in case there's no
                                // pin/search/GPS to fall back on — see onMapCenterReported above.
                                webViewInstance?.evaluateJavascript("reportMapCenter()", null)
                            } else {
                                dashboardViewModel.clearTacticalCircle()
                            }
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
                        // 🔦 Satellite Focus mini-bar — a slim single row instead of a big
                        // floating card, so it never covers the circle it's controlling.
                        if (tacticalCircle != null && isDrawMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${tacticalCircle!!.radius.toInt()}m", fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.width(56.dp))
                                IconButton(onClick = { dashboardViewModel.adjustCircleRadius(-200.0) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Remove, null, modifier = Modifier.size(16.dp)) }
                                IconButton(onClick = { dashboardViewModel.adjustCircleRadius(200.0) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)) }
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    pendingSave = PendingSave("CIRCLE", "Circle Focus (${tacticalCircle!!.radius.toInt()}m)", tacticalCircle!!.latitude, tacticalCircle!!.longitude, tacticalCircle!!.radius)
                                }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.BookmarkBorder, "Save", modifier = Modifier.size(18.dp)) }
                                IconButton(onClick = { isDrawMode = false; dashboardViewModel.clearTacticalCircle() }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Close, "Exit Scope", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                            }
                            // 🕹️ Fine-nudge D-pad — small, fixed-distance moves so you don't
                            // accidentally jump the circle far by mis-tapping a long-press elsewhere.
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
                                IconButton(onClick = { dashboardViewModel.nudgeTacticalCircle(0.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.KeyboardArrowUp, "Move north", modifier = Modifier.size(18.dp)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { dashboardViewModel.nudgeTacticalCircle(270.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.KeyboardArrowLeft, "Move west", modifier = Modifier.size(18.dp)) }
                                    Spacer(modifier = Modifier.width(26.dp))
                                    IconButton(onClick = { dashboardViewModel.nudgeTacticalCircle(90.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.KeyboardArrowRight, "Move east", modifier = Modifier.size(18.dp)) }
                                }
                                IconButton(onClick = { dashboardViewModel.nudgeTacticalCircle(180.0) }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.KeyboardArrowDown, "Move south", modifier = Modifier.size(18.dp)) }
                            }
                        }

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
                                listOf("EXPLORE", "YOU", "INTEL", "SCAN").forEachIndexed { index, title ->
                                    val sel = selectedNavTab == index
                                    TextButton(onClick = { selectedNavTab = index }, colors = ButtonDefaults.textButtonColors(containerColor = if(sel) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if(sel) Color.White else MaterialTheme.colorScheme.onSurface), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(title, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                FloatingActionButton(onClick = { isChatVisible = !isChatVisible }, containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) { Icon(if(isChatVisible) Icons.Default.ExpandMore else Icons.Default.Psychology, null) }
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
                            // 🗂️ Grouped by city instead of one row per raw visit — repeat visits
                            // to the same place (e.g. staying in "Ota") no longer pile up as
                            // identical duplicate rows; they collapse into one card you can expand.
                            val groupedHistory = history.groupBy { it.cityName }
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                groupedHistory.forEach { (city, visits) ->
                                    item {
                                        val isExpanded = expandedHistoryCity == city
                                        val latest = visits.maxByOrNull { it.timestamp }!!
                                        Card(
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                expandedHistoryCity = if (isExpanded) null else city
                                            }
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary)
                                                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                                        Text(city, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                        val visitWord = if (visits.size == 1) "visit" else "visits"
                                                        Text(
                                                            "${visits.size} $visitWord · last ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(latest.timestamp)}",
                                                            fontSize = 10.sp, color = Color.Gray
                                                        )
                                                    }
                                                    IconButton(onClick = {
                                                        dashboardViewModel.focusOnHistoryEntry(latest)
                                                        selectedNavTab = 0
                                                    }) {
                                                        Icon(Icons.Default.MyLocation, "Jump to map", modifier = Modifier.size(20.dp))
                                                    }
                                                    Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = Color.Gray)
                                                }

                                                if (isExpanded) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Text("SCAN THIS LOCATION", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                    LazyRow(modifier = Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        items(discoveryCategories) { cat ->
                                                            FilterChip(
                                                                selected = false,
                                                                onClick = {
                                                                    dashboardViewModel.focusOnHistoryEntry(latest)
                                                                    dashboardViewModel.fetchDiscoveryData(cat, intelSearchRadius.toDouble())
                                                                    selectedIntelCategories = selectedIntelCategories + cat
                                                                    selectedNavTab = 3
                                                                },
                                                                label = { Text(cat, fontSize = 11.sp) }
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                                    visits.sortedByDescending { it.timestamp }.forEach { visit ->
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                            val subtitle = listOfNotNull(visit.street, visit.town).joinToString(", ").ifBlank { null }
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                if (subtitle != null) Text(subtitle, fontSize = 11.sp, color = Color.Gray)
                                                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(visit.timestamp), fontSize = 10.sp, color = Color.Gray)
                                                            }
                                                            IconButton(onClick = { dashboardViewModel.deleteLocationHistoryEntry(visit.id) }, modifier = Modifier.size(28.dp)) {
                                                                Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(16.dp))
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
                    }
                }
            }

            // INTEL Tab: saved bookmarks + the pin/search log. Category scanning lives in
            // its own SCAN tab now — keeping it here too made this tab clumsy (per feedback).
            if (selectedNavTab == 2) {
                Surface(modifier = Modifier.fillMaxSize().padding(top = 180.dp, bottom = 80.dp), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    LazyColumn(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stickyHeader {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
                                    .padding(vertical = 12.dp)
                            ) {
                                IconButton(onClick = { selectedNavTab = 0 }) { Icon(Icons.Default.ArrowBack, "Back") }
                                Text("SAVED INTEL", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            }
                        }

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
                    }
                }
            }

            // SCAN Tab: category-filtered discovery, as its own dedicated tab. Categories
            // are a compact expandable checklist instead of a horizontal chip row.
            if (selectedNavTab == 3) {
                Surface(modifier = Modifier.fillMaxSize().padding(top = 180.dp, bottom = 80.dp), color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)) {
                    LazyColumn(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        stickyHeader {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.98f))
                                    .padding(vertical = 12.dp)
                            ) {
                                IconButton(onClick = { selectedNavTab = 0 }) { Icon(Icons.Default.ArrowBack, "Back") }
                                Text("SCAN PLACES", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                                if (dashboardViewModel.discoveryResults.isNotEmpty()) {
                                    TextButton(onClick = {
                                        mapVisibleCategories = null
                                        selectedNavTab = 0
                                    }) { Text("View All", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }

                        item {
                            Column {
                                Surface(
                                    onClick = { categoriesExpanded = !categoriesExpanded },
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (selectedIntelCategories.isEmpty()) "Choose categories"
                                            else "${selectedIntelCategories.size} categor${if (selectedIntelCategories.size == 1) "y" else "ies"} selected",
                                            modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp
                                        )
                                        Icon(if (categoriesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                                    }
                                }
                                if (categoriesExpanded) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                if (selectedIntelCategories.containsAll(discoveryCategories)) {
                                                    discoveryCategories.forEach { dashboardViewModel.clearDiscoveryCategory(it) }
                                                    selectedIntelCategories = emptySet()
                                                } else {
                                                    discoveryCategories.forEach { cat -> dashboardViewModel.fetchDiscoveryData(cat, intelSearchRadius.toDouble()) }
                                                    selectedIntelCategories = discoveryCategories.toSet()
                                                }
                                            }.padding(vertical = 6.dp)
                                        ) {
                                            Checkbox(checked = selectedIntelCategories.containsAll(discoveryCategories), onCheckedChange = null)
                                            Text("All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        discoveryCategories.forEach { cat ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth().clickable {
                                                    if (selectedIntelCategories.contains(cat)) {
                                                        selectedIntelCategories = selectedIntelCategories - cat
                                                        dashboardViewModel.clearDiscoveryCategory(cat)
                                                    } else {
                                                        selectedIntelCategories = selectedIntelCategories + cat
                                                        dashboardViewModel.fetchDiscoveryData(cat, intelSearchRadius.toDouble())
                                                    }
                                                }.padding(vertical = 6.dp)
                                            ) {
                                                Checkbox(checked = selectedIntelCategories.contains(cat), onCheckedChange = null)
                                                Text(cat, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Column {
                                Text("Search radius: ${intelSearchRadius.toInt()}m", fontSize = 11.sp, color = Color.Gray)
                                Slider(
                                    value = intelSearchRadius,
                                    onValueChange = { intelSearchRadius = it },
                                    onValueChangeFinished = {
                                        selectedIntelCategories.forEach { dashboardViewModel.fetchDiscoveryData(it, intelSearchRadius.toDouble()) }
                                    },
                                    valueRange = 200f..5000f
                                )
                            }
                        }

                        if (isMapLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }

                        val focusPoint = tacticalCircle?.let { it.latitude to it.longitude } ?: dashboardViewModel.mapFocusCoords.value
                        // 🗂️ Each category is its own accordion — with a category like Banks
                        // returning 25 results, you'd have to scroll past all of them to reach
                        // Government below it. Collapsed by default; tap a header to expand it.
                        selectedIntelCategories.sorted().forEach { cat ->
                            val catResults = dashboardViewModel.discoveryResults.filter { it.category == cat }
                            val catExpanded = expandedScanCategories.contains(cat)
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            expandedScanCategories = if (catExpanded) expandedScanCategories - cat else expandedScanCategories + cat
                                        }
                                        .padding(top = 10.dp, bottom = 4.dp)
                                ) {
                                    Text(
                                        "${cat.uppercase()} (${catResults.size})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (catResults.isNotEmpty()) {
                                        IconButton(onClick = {
                                            mapVisibleCategories = setOf(cat)
                                            selectedNavTab = 0
                                        }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Visibility, "View $cat on map", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Icon(if (catExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                            if (!catExpanded) {
                                // collapsed — nothing else to render for this category
                            } else if (catResults.isEmpty() && !isMapLoading) {
                                val error = dashboardViewModel.discoveryError.value
                                item {
                                    if (error != null) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("Search failed — try again.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { dashboardViewModel.fetchDiscoveryData(cat, intelSearchRadius.toDouble()) }) { Text("Retry") }
                                        }
                                    } else {
                                        Text("No $cat found nearby.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                    }
                                }
                            } else {
                                items(catResults) { result ->
                                    val distanceLabel = focusPoint?.let { (fLat, fLon) ->
                                        val out = FloatArray(1)
                                        android.location.Location.distanceBetween(fLat, fLon, result.latitude, result.longitude, out)
                                        if (out[0] < 1000) "${out[0].toInt()}m away" else "${"%.1f".format(out[0] / 1000)}km away"
                                    }
                                    Card(modifier = Modifier.fillMaxWidth()) {
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
                                            IconButton(onClick = {
                                                val positionNote = focusPoint?.let { (fLat, fLon) ->
                                                    "The user's reference position is approximately ($fLat, $fLon)."
                                                } ?: "The user's current position and any marked point are not available — ask them where they are before giving directions."
                                                val prompt = "PLACE_INQUIRY: ${result.name}, ${result.address}. $positionNote Give a brief summary of this place and directions from the reference position if available."
                                                viewModel.sendMessage(prompt, trendTitle = mapIntelTitle)
                                                isChatVisible = true
                                                selectedNavTab = 0
                                            }) {
                                                Icon(Icons.Default.Psychology, "Ask AI", modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(onClick = {
                                                dashboardViewModel.viewPlace(result.latitude, result.longitude, result.name)
                                                isDrawMode = true
                                                selectedNavTab = 0
                                            }) {
                                                Icon(Icons.Default.Visibility, "View", modifier = Modifier.size(20.dp))
                                            }
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
                                // 🛡️ Give the AI full situational awareness of the map — previously
                                // it only ever saw a single circle-or-location string and discovery
                                // result names, never the actual pins the user placed or search history.
                                val contextParts = mutableListOf("Current map focus: $location.")
                                val pins = intelLog.filter { it.type == "PIN" }
                                if (pins.isNotEmpty()) {
                                    contextParts.add("Pinned locations: " + pins.joinToString("; ") { "${it.label} (${it.latitude}, ${it.longitude})" } + ".")
                                }
                                val searches = intelLog.filter { it.type == "SEARCH" }
                                if (searches.isNotEmpty()) {
                                    contextParts.add("Recent searches: " + searches.joinToString("; ") { "${it.label} (${it.latitude}, ${it.longitude})" } + ".")
                                }
                                tacticalCircle?.let {
                                    contextParts.add("Active satellite scope circle: centered at (${it.latitude}, ${it.longitude}) with a ${it.radius.toInt()}m radius.")
                                }
                                if (dashboardViewModel.discoveryResults.isNotEmpty()) {
                                    val byCategory = dashboardViewModel.discoveryResults.groupBy { it.category }
                                    val summary = byCategory.entries.joinToString("; ") { (cat, items) -> "$cat: " + items.joinToString(", ") { it.name } }
                                    contextParts.add("Discovered places currently labeled on the map: $summary.")
                                }
                                val prompt = "MAP_CONTEXT:\n${contextParts.joinToString("\n")}\n\nUser question: $chatText"
                                viewModel.sendMessage(prompt, trendTitle = mapIntelTitle); chatText = ""
                                focusManager.clearFocus()
                            }, onScreenshotClick = onScreenshotClick, onCameraClick = onCameraClick, onFileClick = onFileClick, onVoiceClick = {}, onScribeClick = {}, isLoading = false, pendingAttachments = viewModel.pendingAttachments, onRemoveAttachment = {}
                        )
                    }
                }
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

            if (dashboardViewModel.ambiguousLocations.isNotEmpty()) {
                AmbiguousLocationDialog(
                    addresses = dashboardViewModel.ambiguousLocations,
                    onSelect = { address -> dashboardViewModel.selectAmbiguousLocation(address) },
                    onDismiss = { dashboardViewModel.clearAmbiguousLocations() }
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
