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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.monitor.PressureLevel
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.example.mistreal_mini.data.local.entity.SavedIntelEntity
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.ChatBubble
import com.example.mistreal_mini.ui.chat.components.ChatInputBar
import com.example.mistreal_mini.ui.chat.components.InteractionMode
import com.example.mistreal_mini.ui.chat.components.TypingIndicator
import com.example.mistreal_mini.ui.chat.components.VoiceRecordingBar
import com.example.mistreal_mini.ui.dashboard.components.*
import com.example.mistreal_mini.util.ScreenshotHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File

data class PendingSave(
    val type: String,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double? = null
)

// A saved intel thumbnail may be a remote URL (Pexels/Firebase-hosted) —
// the chat attachment pipeline reads via contentResolver, which can't fetch
// a plain http(s) URL, so it's downloaded to a local file first.
private suspend fun downloadToLocalUri(context: android.content.Context, url: String): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "intel_discuss_${System.currentTimeMillis()}.jpg")
            java.net.URL(url).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InteractiveMapView(
    location: String,
    onClose: () -> Unit,
    onOpenAiRender: () -> Unit = {},
    viewModel: ChatViewModel,
    dashboardViewModel: DashboardViewModel,
    mapViewModel: TacticalMapViewModel,
    celestialViewModel: CelestialViewModel,
    snackbarHostState: SnackbarHostState,
    startInSpaceMode: Boolean = false
) {
    var chatText by remember { mutableStateOf("") }
    var mapSearchQuery by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    
    // Stable map minichat session key so messages persist across map pans/zooms
    val mapIntelTitle = "MAP_INTEL_SESSION"
    
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
    var isHudExpanded by remember { mutableStateOf(false) }
    var isTopBarVisible by remember { mutableStateOf(true) }
    var isSatelliteOn by remember { mutableStateOf(false) }
    var isMapReady by remember { mutableStateOf(false) }
    var isComputingRoute by remember { mutableStateOf(false) }
    var routeInfo by remember { mutableStateOf<String?>(null) }
    
    val history by mapViewModel.locationHistory.collectAsState()
    val intelLog = mapViewModel.intelLog
    var isDrawMode by remember { mutableStateOf(false) }
    var selectedIntelCategories by remember { mutableStateOf(setOf<String>()) }
    var mapVisibleCategories by remember { mutableStateOf<Set<String>?>(null) }
    var mapPendingSave by remember { mutableStateOf<PendingSave?>(null) }
    val savedIntel by mapViewModel.savedIntel.collectAsState()
    
    val isMapLoading = mapViewModel.isMapLoading.value
    val tacticalCircle = mapViewModel.tacticalCircle.value
    val isLocationEnabled = mapViewModel.isLocationEnabled.value
    val searchMarker = mapViewModel.searchMarker.value
    val teleportRequest = mapViewModel.teleportRequest.value
    val ghostMarkersRequest = mapViewModel.ghostMarkersRequest.value
    val targetPoints = mapViewModel.targetPoints
    
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    // Map chat's camera/screenshot/file attachment handling — previously wired to
    // onScreenshotClick/onCameraClick/onFileClick parameters that DashboardScreen
    // passed in as literal no-ops, so none of these buttons did anything. Handled
    // directly here instead, same real logic as the main ChatScreen.
    val mapChatContext = LocalContext.current
    var mapCameraUri by remember { mutableStateOf<Uri?>(null) }
    val mapCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && mapCameraUri != null) viewModel.addPendingAttachment(mapCameraUri!!)
    }
    val mapFilePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        uris.forEach { viewModel.addPendingAttachment(it) }
    }
    fun captureMapCameraImage() {
        val file = File(mapChatContext.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(mapChatContext, "${mapChatContext.packageName}.fileprovider", file)
        mapCameraUri = uri
        mapCameraLauncher.launch(uri)
    }
    // Video/scribe/screen-record on this panel's ChatInputBar were wired to
    // empty lambdas — real on ChatScreen's own ChatInputBar, dead here.
    // Same pattern as the camera capture just above, mirrored exactly.
    var mapVideoUri by remember { mutableStateOf<Uri?>(null) }
    val mapVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && mapVideoUri != null) viewModel.addPendingAttachment(mapVideoUri!!)
    }
    fun captureMapVideo() {
        val file = File(mapChatContext.cacheDir, "camera_capture_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(mapChatContext, "${mapChatContext.packageName}.fileprovider", file)
        mapVideoUri = uri
        mapVideoLauncher.launch(uri)
    }
    val isMapChatScribing by viewModel.isScribing
    val mapChatScribeText by viewModel.scribeText.collectAsStateWithLifecycle()
    LaunchedEffect(mapChatScribeText) {
        if (mapChatScribeText.isNotBlank()) chatText = mapChatScribeText
    }

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

    LaunchedEffect(teleportRequest, isMapReady) {
        if (isMapReady) {
            teleportRequest?.let { (lat, lon, zoom) ->
                webViewInstance?.evaluateJavascript("teleportTo($lat, $lon, $zoom)", null)
                mapViewModel.clearTeleport()
                isHudExpanded = false
            }
        }
    }

    LaunchedEffect(ghostMarkersRequest, isMapReady) {
        if (isMapReady) {
            ghostMarkersRequest?.let { json ->
                webViewInstance?.evaluateJavascript("showGhostMarkers('$json')", null)
                mapViewModel.clearGhostMarkers()
            }
        }
    }

    LaunchedEffect(targetPoints.size, isMapReady) {
        if (isMapReady) {
            val json = com.google.gson.Gson().toJson(targetPoints.map { mapOf("lat" to it.first, "lon" to it.second) })
            webViewInstance?.evaluateJavascript("setTargetBox('$json')", null)
        }
    }

    LaunchedEffect(tacticalCircle) {
        if (tacticalCircle == null && targetPoints.isEmpty()) isDrawMode = false
    }

    // Replaces the old fetch-on-every-recomposition logic (which refired on
    // every keystroke/state change with no cancellation of the prior call,
    // and never actually recentered the map). This fetches once when enabled,
    // teleports to it immediately (the "recenter" behavior the button name
    // implies), then keeps polling so the marker reflects real movement —
    // and LaunchedEffect's own cancellation on key change stops it cleanly
    // the moment location tracking is toggled off.
    LaunchedEffect(isLocationEnabled, isMapReady) {
        if (isLocationEnabled && isMapReady) {
            // Previously: permission-denied, no-GPS-fix, and "still waiting"
            // all looked identical — the button just turned on and nothing
            // ever happened, with zero feedback either way. Checked and
            // surfaced explicitly now instead of silently doing nothing.
            if (!mapViewModel.locationHelper.hasLocationPermission()) {
                android.widget.Toast.makeText(mapSearchContext, "Location permission is required for this — enable it in Settings.", android.widget.Toast.LENGTH_LONG).show()
                return@LaunchedEffect
            }
            mapViewModel.resourceMonitor.ensureMonitoring()
            var isFirstFix = true
            var firstFixFailedOnce = false
            while (true) {
                val loc = mapViewModel.locationHelper.getCurrentLocation()
                if (loc != null) {
                    webViewInstance?.evaluateJavascript("updateGpsLocation(${loc.latitude}, ${loc.longitude}, $isFirstFix)", null)
                    if (isFirstFix) {
                        webViewInstance?.evaluateJavascript("teleportTo(${loc.latitude}, ${loc.longitude}, 15)", null)
                        isFirstFix = false
                    }
                } else if (isFirstFix && !firstFixFailedOnce) {
                    // Only once, not every poll — permission is fine here,
                    // this is "GPS hasn't produced a fix yet" (indoors, cold
                    // start, location services off at the OS level).
                    firstFixFailedOnce = true
                    android.widget.Toast.makeText(mapSearchContext, "Couldn't get your location — check that location/GPS is turned on.", android.widget.Toast.LENGTH_SHORT).show()
                }
                // Backs off under battery/thermal/network pressure instead of
                // polling GPS at a fixed rate regardless of device state.
                val interval = when (mapViewModel.resourceMonitor.pressureNow()) {
                    PressureLevel.NONE -> 8_000L
                    PressureLevel.MILD -> 15_000L
                    PressureLevel.MODERATE -> 30_000L
                    PressureLevel.SEVERE -> 60_000L
                }
                delay(interval)
            }
        }
    }

    // Re-applies whenever either the user's explicit choice or map readiness
    // changes, so a toggle pressed before the WebView finished loading still
    // takes effect once it's actually ready, instead of being silently lost.
    LaunchedEffect(isSatelliteOn, isMapReady) {
        if (isMapReady) {
            webViewInstance?.evaluateJavascript("toggleSatellite($isSatelliteOn)", null)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.exitTrend() }
    }

    // WebView has no lifecycle awareness of its own — without this, it keeps
    // running (and rendering) while the Activity is backgrounded (a
    // notification drawer, the recents screen, a permission prompt), and on
    // some devices resumes to a blank black/white surface instead of
    // repainting, since nothing ever told it to pause/resume. configChanges
    // handling orientation (no Activity recreation) makes this worse, not
    // better — the same WebView instance survives a rotation with no resume
    // signal unless this observer provides one.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> webViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewInstance?.destroy()
        }
    }

    val hudHeight by animateDpAsState(
        targetValue = if (isHudExpanded) 340.dp else 72.dp,
        animationSpec = tween(400, easing = FastOutSlowInEasing), label = "hud_height"
    )

    Card(
        modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.95f),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { isHudExpanded = false }
                    )
            ) {
                // Lets `update` below skip re-pushing pins/discoveryResults into
                // the WebView when nothing actually changed, instead of
                // Gson-serializing and clearing+rebuilding the whole pins
                // layer on every recomposition (including ones triggered by
                // unrelated state like tacticalCircle).
                var lastPushedPinsKey by remember { mutableStateOf<String?>(null) }
                var lastPushedDiscoveryJson by remember { mutableStateOf<String?>(null) }
                var lastPushedBusinessJson by remember { mutableStateOf<String?>(null) }

                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            webViewInstance = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36 MistrealApp/1.0"
                            // JS calls fired before the page (and the CDN-hosted Leaflet
                            // script it loads) finish loading hit undefined functions and
                            // fail silently — this is the root cause behind several
                            // intermittent map issues (routing, satellite, recenter all
                            // depend on JS functions that may not exist yet).
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isMapReady = true
                                }
                            }
                            
                            addJavascriptInterface(object {
                                @android.webkit.JavascriptInterface
                                fun onMapClick(lat: Double, lon: Double) {
                                    coroutineScope.launch {
                                        isHudExpanded = false
                                    }
                                }
                                @android.webkit.JavascriptInterface
                                fun onMapLongClick(lat: Double, lon: Double) {
                                    coroutineScope.launch {
                                        if (isDrawMode) {
                                            mapViewModel.addPointToTarget(lat, lon)
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
                                @android.webkit.JavascriptInterface
                                fun onGhostMarkerSelected(lat: Double, lon: Double, label: String) {
                                    coroutineScope.launch {
                                        mapViewModel.confirmAmbiguousLocation(lat, lon, label)
                                    }
                                }
                                @android.webkit.JavascriptInterface
                                fun onRouteComputed(durationSeconds: Double, distanceMeters: Double) {
                                    coroutineScope.launch {
                                        isComputingRoute = false
                                        val mins = (durationSeconds / 60).toInt()
                                        val km = distanceMeters / 1000.0
                                        routeInfo = "%.1f km · %d min".format(km, mins)
                                    }
                                }
                                @android.webkit.JavascriptInterface
                                fun onRequestRoute(destLat: Double, destLon: Double) {
                                    coroutineScope.launch {
                                        isComputingRoute = true
                                        val currentLoc = mapViewModel.locationHelper.getCurrentLocation()
                                        if (currentLoc != null) {
                                            webViewInstance?.evaluateJavascript(
                                                "showRoute(${currentLoc.latitude}, ${currentLoc.longitude}, $destLat, $destLon)",
                                                null
                                            )
                                        } else {
                                            val focus = mapViewModel.mapFocusCoords.value
                                            if (focus != null) {
                                                webViewInstance?.evaluateJavascript(
                                                    "showRoute(${focus.first}, ${focus.second}, $destLat, $destLon)",
                                                    null
                                                )
                                            } else {
                                                isComputingRoute = false
                                                snackbarHostState.showSnackbar("Turn on location or search a place first to get directions.")
                                            }
                                        }
                                    }
                                }
                                @android.webkit.JavascriptInterface
                                fun onRouteError(message: String) {
                                    coroutineScope.launch {
                                        isComputingRoute = false
                                        routeInfo = null
                                        snackbarHostState.showSnackbar("Route error: $message")
                                    }
                                }
                            }, "AndroidMap")

                            loadDataWithBaseURL("https://openstreetmap.org", getMapHtml(), "text/html", "UTF-8", null)
                        }
                    },
                    update = { webView ->
                        if (isMapReady) {
                            mapViewModel.searchMarker.value?.let { entry ->
                                val safeLabel = entry.label.replace("'", "\\'")
                                webView.evaluateJavascript("updateSearchMarker(${entry.latitude}, ${entry.longitude}, '$safeLabel')", null)
                            }

                            tacticalCircle?.let {
                                webView.evaluateJavascript("setCircle(${it.latitude}, ${it.longitude}, ${it.radius})", null)
                                val label = mapViewModel.focusPlaceLabel.value?.replace("'", "\\'")
                                if (label != null) {
                                    webView.evaluateJavascript("showFocusPlaceMarker(${it.latitude}, ${it.longitude}, '$label')", null)
                                } else {
                                    webView.evaluateJavascript("if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);", null)
                                }
                            } ?: webView.evaluateJavascript("if(tacticalCircle) map.removeLayer(tacticalCircle); if(focusPlaceMarker) map.removeLayer(focusPlaceMarker);", null)

                            val pins = if (isDrawMode) emptyList() else mapViewModel.intelLog.filter { it.type == "PIN" }
                            val pinsKey = pins.joinToString(";") { "${it.latitude},${it.longitude},${it.label}" }
                            if (pinsKey != lastPushedPinsKey) {
                                lastPushedPinsKey = pinsKey
                                webView.evaluateJavascript("pins.clearLayers();", null)
                                pins.forEach { pin ->
                                    val safePinLabel = pin.label.replace("'", "\\'")
                                    webView.evaluateJavascript("addTacticalPin(${pin.latitude}, ${pin.longitude}, '$safePinLabel')", null)
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
                                val json = com.google.gson.Gson().toJson(visible).replace("'", "\\'")
                                val renderKey = "$json|${center.first}|${center.second}"
                                if (renderKey != lastPushedDiscoveryJson) {
                                    lastPushedDiscoveryJson = renderKey
                                    webView.evaluateJavascript("renderDiscovery('$json', ${center.first}, ${center.second})", null)
                                }

                                val businessJson = com.google.gson.Gson().toJson(mapViewModel.registeredBusinesses).replace("'", "\\'")
                                if (businessJson != lastPushedBusinessJson) {
                                    lastPushedBusinessJson = businessJson
                                    webView.evaluateJavascript("renderBusinessPins('$businessJson')", null)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = isTopBarVisible,
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 8.dp,
                        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        location.uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "TACTICAL OVERLAY",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { isTopBarVisible = false }) {
                                    Icon(Icons.Default.ExpandLess, "Hide Top Controls", tint = Color.Gray)
                                }
                                IconButton(onClick = {
                                    val newState = !isLocationEnabled
                                    mapViewModel.toggleLocation(newState)
                                    if (!newState) {
                                        webViewInstance?.evaluateJavascript("if(typeof clearGpsLocation === 'function') clearGpsLocation(); else if(typeof gpsMarker !== 'undefined' && gpsMarker) map.removeLayer(gpsMarker);", null)
                                    }
                                }) {
                                    Icon(Icons.Default.MyLocation, null, tint = if(isLocationEnabled) Color(0xFF4CAF50) else Color.Gray)
                                }
                                IconButton(onClick = {
                                    isSatelliteOn = !isSatelliteOn
                                    webViewInstance?.evaluateJavascript("toggleSatellite($isSatelliteOn)", null)
                                }) {
                                    Icon(Icons.Default.Satellite, "Toggle satellite view", tint = if (isSatelliteOn) Color(0xFF4CAF50) else Color.Gray)
                                }
                                IconButton(onClick = {
                                    viewModel.setProvider(ChatViewModel.ARCH_IMAGE_PROVIDER_ID)
                                    onOpenAiRender()
                                }) {
                                    Icon(Icons.Default.Architecture, "AI Render", tint = MaterialTheme.colorScheme.secondary)
                                }
                                IconButton(onClick = {
                                    (mapChatContext as? Activity)?.let { activity ->
                                        coroutineScope.launch {
                                            val uri = ScreenshotHelper.captureAndSave(activity)
                                            if (uri != null && ScreenshotHelper.saveToGallery(activity, uri) != null) {
                                                snackbarHostState.showSnackbar("Saved to Photos > Mistreal")
                                            } else {
                                                snackbarHostState.showSnackbar("Failed to save to gallery")
                                            }
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.Download, "Save map to gallery", tint = Color.Gray)
                                }
                                if (isHudExpanded) {
                                    IconButton(onClick = { isHudExpanded = false }) {
                                        Icon(Icons.Default.Close, "Collapse HUD")
                                    }
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
                                    if (isMapLoading) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        IconButton(onClick = { performMapSearch() }) {
                                            Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { performMapSearch() }),
                                singleLine = true,
                                enabled = !isMapLoading,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    if (!isTopBarVisible) {
                        FloatingActionButton(
                            onClick = { isTopBarVisible = true },
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            // Matches every other FAB on this screen (48dp) —
                            // this one was a stray 36dp/20dp, the one visibly
                            // smaller control in an otherwise consistent row.
                            modifier = Modifier.align(Alignment.TopCenter).size(48.dp)
                        ) {
                            Icon(Icons.Default.ExpandMore, "Show Top Controls", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

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

                    if (searchMarker != null) {
                        FloatingActionButton(
                            onClick = {
                                coroutineScope.launch {
                                    val loc = mapViewModel.locationHelper.getCurrentLocation()
                                    val focus = mapViewModel.mapFocusCoords.value
                                    val startLat = loc?.latitude ?: focus?.first ?: searchMarker.latitude
                                    val startLon = loc?.longitude ?: focus?.second ?: searchMarker.longitude

                                    isComputingRoute = true
                                    routeInfo = null
                                    webViewInstance?.evaluateJavascript(
                                        "showRoute($startLat, $startLon, ${searchMarker.latitude}, ${searchMarker.longitude})",
                                        null
                                    )
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(top = 56.dp).size(48.dp)
                        ) {
                            if (isComputingRoute) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                            else Icon(Icons.Default.DirectionsWalk, "Get directions")
                        }
                        // Was no way to ever dismiss this — searchMarker only ever got
                        // set, never cleared, so this FAB (and the map pin/popup under
                        // it) stuck around for the rest of the session after the
                        // first-ever search.
                        Icon(
                            Icons.Default.Close, "Dismiss search pin", tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = 56.dp + 48.dp, start = 32.dp)
                                .size(18.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .padding(2.dp)
                                .clickable {
                                    mapViewModel.clearSearchMarker()
                                    routeInfo = null
                                    webViewInstance?.evaluateJavascript(
                                        "if(searchMarker) map.removeLayer(searchMarker); clearRoute();", null
                                    )
                                }
                        )
                    }

                    routeInfo?.let { info ->
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DirectionsWalk, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(info, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Close, "Clear route", tint = Color.White,
                                modifier = Modifier.size(16.dp).clickable {
                                    routeInfo = null
                                    webViewInstance?.evaluateJavascript("clearRoute()", null)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Hidden (not just visually covered) while chat is open — the
                // chat panel's fixed 550dp height always exceeds the HUD's own
                // max height and fully overlaps it, so leaving this mounted
                // and clickable underneath served no purpose and could swallow
                // taps meant for the chat panel above it.
                if (!isChatVisible) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 12.dp,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier.height(hudHeight).clickable { isHudExpanded = true }
                ) {
                    Column {
                        // Was tap-to-expand with no visual cue that it's a
                        // sheet at all — the standard Material drag-handle
                        // signals "this panel is interactive" even though
                        // the actual interaction here is tap, not drag.
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            BottomSheetDefaults.DragHandle()
                        }
                        if (isDrawMode) {
                            TargetingControls(
                                points = targetPoints,
                                onClear = { mapViewModel.clearTargetBox() },
                                onExit = { isDrawMode = false; mapViewModel.clearTargetBox() }
                            )
                        } else if (tacticalCircle != null) {
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
                        }

                        if (intelLog.isNotEmpty()) {
                            LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(intelLog, key = { "${it.label}_${it.timestamp}" }) { entry ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { mapViewModel.removeIntelItem(entry) },
                                        label = { Text(entry.label, fontSize = 9.sp) },
                                        trailingIcon = { Icon(Icons.Default.Close, null, modifier = Modifier.size(12.dp)) }
                                    )
                                }
                            }
                        }

                        if (isHudExpanded) {
                            // TACTICAL TABS CONTENT
                            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp)) {
                                when (selectedNavTab) {
                                    0 -> ExploreTabView(mapViewModel)
                                    1 -> HistoryTabView(mapViewModel)
                                    2 -> IntelTabView(mapViewModel, savedIntel, onDiscussIntel = { intel ->
                                        coroutineScope.launch {
                                            viewModel.loadTrend("Intel: ${intel.label}")
                                            intel.thumbnailPath?.let { path ->
                                                val uri = if (path.startsWith("http")) {
                                                    downloadToLocalUri(mapChatContext, path)
                                                } else {
                                                    Uri.fromFile(File(path))
                                                }
                                                uri?.let { viewModel.addPendingAttachment(it) }
                                            }
                                            val intelContext = buildString {
                                                append("[MAP_INTEL_CONTEXT] Discussing a saved intel pin: \"${intel.label}\" (${intel.type}), ")
                                                append("coordinates ${intel.latitude}, ${intel.longitude}")
                                                intel.radius?.let { append(", radius ${it.toInt()}m") }
                                                if (!intel.discoveryResultsJson.isNullOrBlank()) append(". Nearby points of interest were logged for this area.")
                                                if (!intel.blueprintJson.isNullOrBlank()) append(". An AI-generated structural blueprint exists for this location.")
                                            }
                                            viewModel.sendMessage("$intelContext\n\nGive me a tactical briefing on this location.", trendTitle = "Intel: ${intel.label}")
                                            isChatVisible = true
                                        }
                                    })
                                    3 -> ScanTabView(mapViewModel)
                                }
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("EXPLORE", "YOU", "INTEL", "SCAN").forEachIndexed { index, title ->
                                    val sel = selectedNavTab == index
                                    TextButton(onClick = { selectedNavTab = index; isHudExpanded = true }, colors = ButtonDefaults.textButtonColors(containerColor = if(sel) MaterialTheme.colorScheme.primary else Color.Transparent, contentColor = if(sel) Color.White else MaterialTheme.colorScheme.onSurface), modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 10.dp)) { Text(title, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                                if (isHudExpanded) {
                                    VerticalDivider(modifier = Modifier.height(20.dp).padding(horizontal = 4.dp), color = Color.Gray.copy(alpha = 0.3f))
                                    IconButton(onClick = { isHudExpanded = false }, modifier = Modifier.size(32.dp)) {
                                        Icon(Icons.Default.Close, "Collapse", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                    }
                                }
                            }
                            FloatingActionButton(onClick = { isChatVisible = !isChatVisible }, containerColor = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) { Icon(if(isChatVisible) Icons.Default.ExpandMore else Icons.Default.Psychology, null) }
                        }
                    }
                }
                }
            }

            // Calibration wizard dialog moved to DashboardScreen.kt (hoisted
            // above the tab switch) — previously only reachable from the Map
            // tab's compass icon even though the Intel feed also shows one now;
            // the dialog itself is shared state, not tab-specific.

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
                            items(messages, key = { it.id ?: it.hashCode() }) { msg -> ChatBubble(msg, viewModel, {}, { _, _ -> }, snackbarHostState, coroutineScope) }
                            if (viewModel.isLoading.value) item { TypingIndicator() }
                        }
                        val isMapChatRecording by viewModel.isRecording
                        val mapChatRecordedFile by viewModel.recordedFile
                        if (isMapChatRecording || mapChatRecordedFile != null) {
                            VoiceRecordingBar(
                                isRecording = isMapChatRecording,
                                duration = viewModel.recordingDuration.value,
                                recordedFile = mapChatRecordedFile,
                                isPlayingBack = viewModel.isPlayingBack.value,
                                onStopRecording = { viewModel.stopRecording() },
                                onDelete = { viewModel.deleteRecording() },
                                onPlay = { viewModel.playRecording() },
                                onSend = { viewModel.sendVoiceMessage() },
                                onTrim = { startMs, endMs -> viewModel.trimRecording(startMs, endMs) }
                            )
                        } else {
                            ChatInputBar(
                                text = chatText,
                                onTextChange = { chatText = it },
                                onSend = {
                                    val contextParts = mutableListOf("Current map focus: $location.")
                                    val prompt = "MAP_CONTEXT:\n${contextParts.joinToString("\n")}\n\nUser question: $chatText"
                                    viewModel.sendMessage(prompt, trendTitle = mapIntelTitle); chatText = ""
                                    focusManager.clearFocus()
                                },
                                onScreenshotClick = {
                                    (mapChatContext as? Activity)?.let { activity ->
                                        coroutineScope.launch {
                                            ScreenshotHelper.captureAndSave(activity)?.let { viewModel.addPendingAttachment(it) }
                                        }
                                    }
                                },
                                onScreenRecordClick = {
                                    viewModel.startScreenRecord()
                                    val recordIntent = Intent(mapChatContext, com.example.mistreal_mini.service.recording.ScreenRecordService::class.java).apply {
                                        action = "START"
                                    }
                                    mapChatContext.startForegroundService(recordIntent)
                                },
                                onCameraClick = { captureMapCameraImage() },
                                onVideoClick = { captureMapVideo() },
                                onFileClick = { mapFilePickerLauncher.launch("*/*") },
                                onVoiceClick = { viewModel.startRecording() },
                                onScribeClick = {
                                    if (isMapChatScribing) viewModel.stopScribe() else viewModel.startScribe()
                                },
                                isScribing = isMapChatScribing,
                                isLoading = false,
                                pendingAttachments = viewModel.pendingAttachments,
                                onRemoveAttachment = { viewModel.removePendingAttachment(it) }
                            )
                        }
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
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(groupName) })
                )
                if (existingGroups.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(existingGroups, key = { it }) { g ->
                            FilterChip(
                                selected = groupName == g, 
                                onClick = { groupName = if (groupName == g) "" else g }, 
                                label = { Text(g, fontSize = 12.sp) }
                            )
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
            #map { height: 100vh; width: 100vw; background: #0F172A; }
            .leaflet-container { background: #0F172A !important; }
            .mask-overlay {
                position: absolute; top: 0; left: 0; width: 100%; height: 100%;
                background: rgba(0, 0, 0, 0.6); pointer-events: none; z-index: 1000;
                transition: opacity 0.5s;
            }
            .mask-hole {
                fill: transparent; stroke: cyan; stroke-width: 2;
                filter: drop-shadow(0 0 5px cyan);
            }
            .tactical-tooltip {
                background: rgba(0, 0, 0, 0.8) !important;
                border: 1px solid cyan !important;
                color: cyan !important;
                font-family: 'monospace' !important;
                font-size: 10px !important;
                border-radius: 4px !important;
                box-shadow: 0 0 5px cyan !important;
            }
        </style>
    </head>
    <body>
        <div id="map"></div>
        <div id="mask" class="mask-overlay" style="opacity: 0;">
            <svg width="100%" height="100%" id="maskSvg">
                <defs>
                    <mask id="holeMask">
                        <rect width="100%" height="100%" fill="white"/>
                        <polygon id="holePolygon" points="" fill="black"/>
                    </mask>
                </defs>
                <rect width="100%" height="100%" fill="rgba(0,0,0,0.7)" mask="url(#holeMask)"/>
            </svg>
        </div>
        <script>
            // zoomControl was off with nothing replacing it — no +/- buttons
            // and whole-level-only zoom steps, which is why this never felt
            // like a real map app. zoomSnap/zoomDelta at 0.5 lets pinch-zoom
            // land on half-steps instead of snapping to the nearest whole
            // level, the same smooth-zoom feel Google Maps has.
            var map = L.map('map', { zoomControl: false, attributionControl: false, zoomSnap: 0.5, zoomDelta: 0.5, minZoom: 2, maxZoom: 19 }).setView([0,0], 2);
            L.control.zoom({ position: 'topright' }).addTo(map);
            var baseLayer = L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                subdomains: ['a','b','c']
            }).addTo(map);
            var fallbackLayer = L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                subdomains: 'abcd',
                maxZoom: 20
            });
            baseLayer.on('tileerror', function(e) {
                if (!map.hasLayer(fallbackLayer) && !map.hasLayer(satLayer)) {
                    console.warn('OSM tile load error, falling back to CartoDB Voyager');
                    map.removeLayer(baseLayer);
                    fallbackLayer.addTo(map);
                }
            });
            var satLayer = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', {
                attribution: 'Tiles &copy; Esri',
                // Esri's free World_Imagery service doesn't actually have
                // native tiles at every zoom this map allows (up to 19) in
                // most areas — without maxNativeZoom, Leaflet requests tiles
                // that don't exist past the real coverage depth and gets
                // back blank/error tiles, which looks exactly like
                // "satellite view shows nothing." This tells Leaflet to stop
                // requesting past 17 and instead upscale the deepest real
                // tile it has, so there's always imagery to show.
                maxNativeZoom: 17,
                maxZoom: 19
            });
            var pins = L.layerGroup().addTo(map);
            var ghostMarkers = L.layerGroup().addTo(map);
            var discoveryLayer = L.layerGroup().addTo(map);
            var businessLayer = L.layerGroup().addTo(map);
            var tacticalCircle, focusPlaceMarker, searchMarker, gpsMarker, routeLine;
            var manualSatelliteOn = false;

            // Explicit, user-controlled satellite toggle — independent of setTargetBox's
            // own satLayer handling (which exists purely for the target-box visual
            // effect and shouldn't be the only way to see satellite imagery).
            function toggleSatellite(enabled) {
                manualSatelliteOn = enabled;
                if (enabled) { if (!map.hasLayer(satLayer)) satLayer.addTo(map); }
                else { if (map.hasLayer(satLayer)) map.removeLayer(satLayer); }
            }

            // Routing via OSRM's free public demo server — no API key needed, but it's
            // a shared rate-limited evaluation instance, not a production SLA. A
            // dedicated OSRM instance or a paid provider (Mapbox/Google Directions) is
            // the right call if real traffic needs this reliably.
            function showRoute(lat1, lon1, lat2, lon2) {
                var url = 'https://router.project-osrm.org/route/v1/driving/' + lon1 + ',' + lat1 + ';' + lon2 + ',' + lat2 + '?overview=full&geometries=geojson';
                fetch(url).then(function(r) { return r.json(); }).then(function(data) {
                    if (!data.routes || data.routes.length === 0) { AndroidMap.onRouteError('No route found'); return; }
                    if (routeLine) map.removeLayer(routeLine);
                    var coords = data.routes[0].geometry.coordinates.map(function(c) { return [c[1], c[0]]; });
                    routeLine = L.polyline(coords, { color: '#00E5FF', weight: 5, opacity: 0.85 }).addTo(map);
                    map.fitBounds(routeLine.getBounds(), { padding: [40, 40] });
                    AndroidMap.onRouteComputed(data.routes[0].duration, data.routes[0].distance);
                }).catch(function(e) { AndroidMap.onRouteError(String(e)); });
            }

            function clearRoute() {
                if (routeLine) { map.removeLayer(routeLine); routeLine = null; }
            }

            function teleportTo(lat, lon, zoom) {
                map.setView([lat, lon], zoom, { animate: true, duration: 1.5 });
            }

            function showGhostMarkers(json) {
                ghostMarkers.clearLayers();
                var points = JSON.parse(json);
                points.forEach(function(p) {
                    var m = L.marker([p.lat, p.lon], { opacity: 0.6 }).addTo(ghostMarkers);
                    m.on('click', function() { AndroidMap.onGhostMarkerSelected(p.lat, p.lon, p.label); });
                });
            }

            function setTargetBox(jsonPoints) {
                var points = JSON.parse(jsonPoints);
                var mask = document.getElementById('mask');
                if (points.length < 3) {
                    mask.style.opacity = "0";
                    if (!manualSatelliteOn && map.hasLayer(satLayer)) map.removeLayer(satLayer);
                    return;
                }
                
                if(!map.hasLayer(satLayer)) satLayer.addTo(map);
                mask.style.opacity = "1";
                
                var svgPoints = points.map(function(p) {
                    var pixel = map.latLngToContainerPoint([p.lat, p.lon]);
                    return pixel.x + "," + pixel.y;
                }).join(" ");
                
                document.getElementById('holePolygon').setAttribute("points", svgPoints);
            }

            map.on('move', function() {
                var poly = document.getElementById('holePolygon');
                if (poly.getAttribute("points")) {
                   // Redraw logic for mask if needed
                }
            });

            function updateSearchMarker(lat, lon, label) {
                if(searchMarker) map.removeLayer(searchMarker);
                searchMarker = L.marker([lat, lon]).addTo(map);
                if (label && label.trim().length > 0) {
                    searchMarker.bindPopup("<b>" + label + "</b>").openPopup();
                }
                ghostMarkers.clearLayers();
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
                focusPlaceMarker = L.marker([lat, lon]).addTo(map);
                if (label && label.trim().length > 0) {
                    focusPlaceMarker.bindPopup("<b>" + label + "</b>").openPopup();
                }
            }
            function updateGpsLocation(lat, lon, center) {
                if(gpsMarker) map.removeLayer(gpsMarker);
                gpsMarker = L.circleMarker([lat, lon], { radius: 8, color: 'green', weight: 3, fillOpacity: 0.8 }).addTo(map);
            }
            function clearGpsLocation() {
                if(gpsMarker) { map.removeLayer(gpsMarker); gpsMarker = null; }
            }
            function reportMapCenter() {
                var center = map.getCenter();
                AndroidMap.onMapCenterReported(center.lat, center.lng);
            }
            map.on('click', function(e) {
                AndroidMap.onMapClick(e.latlng.lat, e.latlng.lng);
            });
            map.on('contextmenu', function(e) {
                AndroidMap.onMapLongClick(e.latlng.lat, e.latlng.lng);
            });
            function renderDiscovery(json, cLat, cLon) {
                discoveryLayer.clearLayers();
                try {
                    var results = JSON.parse(json);
                    results.forEach(function(r) {
                        var marker = L.marker([r.latitude, r.longitude]).addTo(discoveryLayer);
                        marker.bindTooltip(r.name, { permanent: true, direction: 'top', className: 'tactical-tooltip' });
                        var popupContent = "<div style='min-width:140px;'><b>" + r.name + "</b><br><small>" + r.category.toUpperCase() + "</small>" +
                            "<br><br><button style='background:#00E5FF;color:#000;border:none;padding:5px 10px;border-radius:6px;font-weight:bold;cursor:pointer;width:100%;' onclick='AndroidMap.onRequestRoute(" + r.latitude + "," + r.longitude + ")'>Get Directions</button></div>";
                        marker.bindPopup(popupContent);
                    });
                } catch(e) { console.error("Discovery Parse Error:", e); }
            }
            function escapeHtml(s) {
                return String(s == null ? "" : s).replace(/[&<>"']/g, function(c) {
                    return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
                });
            }
            // Registered Mistreal businesses, separate from generic
            // discoveryLayer pins — distinct icon/color so a real business
            // doesn't look like a generic OSM place, with enough detail
            // (description/address) in the popup for the AI to explain it.
            function renderBusinessPins(json) {
                businessLayer.clearLayers();
                try {
                    var results = JSON.parse(json);
                    results.forEach(function(r) {
                        var icon = L.divIcon({
                            html: '<div style="background:#FFB300;border:2px solid #fff;border-radius:50%;width:26px;height:26px;display:flex;align-items:center;justify-content:center;font-size:14px;box-shadow:0 1px 4px rgba(0,0,0,0.4);">🏪</div>',
                            className: '', iconSize: [26, 26], iconAnchor: [13, 13]
                        });
                        var marker = L.marker([r.latitude, r.longitude], { icon: icon }).addTo(businessLayer);
                        var desc = r.description ? escapeHtml(r.description) : "";
                        var addr = r.address ? escapeHtml(r.address) : "";
                        var popupContent = "<div style='min-width:160px;'><b>" + escapeHtml(r.name) + "</b><br><small>" + escapeHtml((r.category || "").toUpperCase()) + "</small>" +
                            (desc ? "<br><br>" + desc : "") + (addr ? "<br><small>" + addr + "</small>" : "") +
                            "<br><br><button style='background:#FFB300;color:#000;border:none;padding:5px 10px;border-radius:6px;font-weight:bold;cursor:pointer;width:100%;' onclick='AndroidMap.onRequestRoute(" + r.latitude + "," + r.longitude + ")'>Get Directions</button></div>";
                        marker.bindPopup(popupContent);
                    });
                } catch(e) { console.error("Business Pin Parse Error:", e); }
            }
        </script>
    </body>
    </html>
""".trimIndent()
