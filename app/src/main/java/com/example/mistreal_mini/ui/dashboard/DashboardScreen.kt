/** 🛡️ AI SYSTEM PROTOCOL 🛡️
 * SOURCE OF TRUTH: master_system_map.artifact.md
 * 
 * 🚀 FUNCTIONAL PIPELINE:
 * [Input]  <- Real-time feeds (News, Socials, Weather, Orbitals) from Repositories
 * [Process] <- Orchestrates multi-tab intelligence display and dispatch actions
 * [Output] -> Renders tactical dashboard UI; triggers social posts or map search
 *
 * ⚠️ MANDATORY: Never delete history. Only ADD updates/fixes to the Master Map table.
 */
package com.example.mistreal_mini.ui.dashboard

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.api.Article
import com.example.mistreal.data.models.PlatformUpdate
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.mistreal.data.models.SocialPost
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.InteractionMode
import com.example.mistreal_mini.ui.util.AiInsightPopup
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import android.content.Intent
import com.example.mistreal_mini.service.VoiceService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public as PublicIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    onDmClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    
    var selectedArticle by remember { mutableStateOf<Article?>(null) }
    var insightContext by remember { mutableStateOf<String?>(null) }
    var showInsightPopup by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMapPopup by remember { mutableStateOf(false) }
    var startInSpaceMode by remember { mutableStateOf(false) }

    val weather by viewModel.weather
    val news = viewModel.newsArticles
    val socials = viewModel.socialUpdates
    val isLoading by viewModel.isLoading
    val orientation by viewModel.orientation
    val bearing by viewModel.bearing
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val localContext = LocalContext.current
    var showFullSolarSystem by remember { mutableStateOf(false) }

    // 📸 Media Tool Logic
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // 🛡️ AI NOTE: If you overhaul or fix logic here, log it in the "History & Notes" column of the Master Map.
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            chatViewModel.addPendingAttachment(cameraUri!!)
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> 
        uris.forEach { chatViewModel.addPendingAttachment(it) }
    }

    fun captureImage() {
        val file = java.io.File(localContext.cacheDir, "camera_dashboard_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(localContext, "${localContext.packageName}.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(screenshotUri) {
        screenshotUri?.let {
            chatViewModel.addPendingAttachment(it)
            screenshotUri = null
        }
    }

    // 🔋 Lifecycle-Aware Hardware Switch
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startSensors()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopSensors()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopSensors()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDashboardData(deviceId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showFullSolarSystem) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showFullSolarSystem = false }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                        Text("ORBITAL INTELLIGENCE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    SolarSystemCard(viewModel)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 🌌 NASA APOD GLOBAL BACKGROUND
        val nasaBackground = news.find { it.title.contains("[Astro]", ignoreCase = true) }?.url
        if (!nasaBackground.isNullOrEmpty()) {
            AsyncImage(
                model = nasaBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.3f
            )
        }

        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        }) {
            Scaffold(
                containerColor = Color.Transparent, // Let background show
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text("Intelligence Center") },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { viewModel.loadDashboardData(deviceId) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                            }
                        )
                            TabRow(selectedTabIndex = selectedTab) {
                                Tab(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    text = { Text("Global Intel", fontSize = 12.sp) },
                                    icon = { Icon(Icons.Default.Public, null, modifier = Modifier.size(20.dp)) }
                                )
                                Tab(
                                    selected = selectedTab == 1,
                                    onClick = { 
                                        selectedTab = 1
                                        // 🚀 AUTO-REFRESH: Sync socials when entering the Social Intel tab
                                        viewModel.loadDashboardData(deviceId)
                                    },
                                    text = { Text("Social Intel", fontSize = 12.sp) },
                                    icon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp)) }
                                )
                                Tab(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    text = { Text("Dispatch", fontSize = 12.sp) },
                                    icon = { Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp)) }
                                )
                            }
                    }
                }
            ) { padding ->
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(modifier = Modifier.padding(padding)) {
                        when (selectedTab) {
                            0 -> {
                                var citySearch by remember { mutableStateOf("") }
                                IntelligenceFeedView(
                                    weather = weather,
                                    orientation = orientation,
                                    bearing = bearing,
                                    news = news,
                                    onArticleClick = { selectedArticle = it },
                                    onAiClick = { ctx -> 
                                        insightContext = ctx
                                        showInsightPopup = true
                                    },
                                    onReadAloud = { text, mode -> chatViewModel.readAloud(text) },
                                    chatViewModel = chatViewModel,
                                    dashboardViewModel = viewModel,
                                    snackbarHostState = snackbarHostState,
                                    onOrbitalClick = { 
                                        showFullSolarSystem = true
                                    },
                                    citySearch = citySearch,
                                    onCitySearchChange = { citySearch = it },
                                    onSearch = {
                                        if (citySearch.isNotBlank()) {
                                            viewModel.searchCity(citySearch)
                                            showMapPopup = true
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onLocateClick = {
                                        viewModel.pinpointCurrentLocation()
                                        showMapPopup = true
                                    }
                                )
                            }
                            1 -> {
                                SocialIntelView(
                                    socialPosts = viewModel.socialPosts,
                                    onAiClick = { post ->
                                        // discuss with AI logic
                                        chatViewModel.draftSocialReply("Discuss this post from ${post.author} on ${post.platform}: ${post.content}")
                                        onDmClick() // Navigate to chat
                                    },
                                    onActionClick = { post, type ->
                                        coroutineScope.launch {
                                            val success = viewModel.postToSocial(deviceId, post.platform, type, "Action on post", post.id)
                                            snackbarHostState.showSnackbar(if (success) "Action Executed" else "Failed")
                                        }
                                    },
                                    chatViewModel = chatViewModel
                                )
                            }
                            2 -> {
                                DispatchCenterView(
                                    deviceId = deviceId,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showMapPopup) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InteractiveMapView(
                    location = viewModel.mapLocation.value ?: "Unknown Area",
                    onClose = { 
                        showMapPopup = false 
                        startInSpaceMode = false
                    },
                    viewModel = chatViewModel,
                    dashboardViewModel = viewModel,
                    onScreenshotClick = {
                        (localContext as? Activity)?.let { activity ->
                            coroutineScope.launch {
                                screenshotUri = com.example.mistreal_mini.util.ScreenshotHelper.captureAndSave(activity)
                            }
                        }
                    },
                    onCameraClick = { captureImage() },
                    onFileClick = { filePickerLauncher.launch("*/*") },
                    startInSpaceMode = startInSpaceMode
                )
            }
        }

        if (showInsightPopup) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                AiInsightPopup(
                    contextText = insightContext ?: "",
                    onClose = { showInsightPopup = false },
                    viewModel = chatViewModel
                )
            }
        }
    }
}

@Composable
fun IntelligenceFeedView(
    weather: com.example.mistreal_mini.data.api.WeatherResponse?,
    orientation: String,
    bearing: Float,
    news: List<Article>,
    onArticleClick: (Article) -> Unit,
    onAiClick: (String) -> Unit,
    onReadAloud: (String, InteractionMode) -> Unit,
    chatViewModel: ChatViewModel,
    dashboardViewModel: DashboardViewModel,
    snackbarHostState: SnackbarHostState,
    onOrbitalClick: () -> Unit,
    citySearch: String,
    onCitySearchChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLocateClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 800.dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { WeatherCard(weather, orientation, bearing) }
        
        // 🛰️ STRATEGIC ROW: Orbitals & Moon
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onOrbitalClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Public, null, modifier = Modifier.size(18.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ORBITALS", fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }

                    weather?.moonPhase?.let { phase ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!weather.moonImageUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = weather.moonImageUrl,
                                    contentDescription = "Moon Phase Image",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text("🌙", fontSize = 24.sp)
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("MOON PHASE", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 8.sp)
                                Text(phase.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = citySearch,
                onValueChange = onCitySearchChange,
                label = { Text("Search City Strategy") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    IconButton(onClick = onLocateClick) {
                        Icon(Icons.Default.MyLocation, "Locate Strategy")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(12.dp)
            )
        }

        item { LocationBanner(weather) }
        item { Text("Global Intelligence Flow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        items(news) { item ->
            val (icon, tint) = when {
                item.title.contains("[Astro]", ignoreCase = true) -> Icons.Default.Brightness3 to Color(0xFFFFD54F)
                item.title.contains("[Sports]", ignoreCase = true) -> Icons.Default.SportsBasketball to Color(0xFFFF5722)
                item.title.contains("[Movie]", ignoreCase = true) -> Icons.Default.Movie to Color(0xFFE91E63)
                item.title.contains("[Novel]", ignoreCase = true) || item.title.contains("[Deep Dive]", ignoreCase = true) -> Icons.Default.AutoStories to Color(0xFF9C27B0)
                else -> Icons.Default.Newspaper to Color.Gray
            }
            NewsItem(
                article = item, 
                icon = icon,
                iconTint = tint,
                onClick = { onArticleClick(item) },
                onAiClick = { onAiClick("News Article: ${item.title}. Link: ${item.url}") },
                onReadAloud = { text, mode ->
                    val fullText = "${item.title}. ${item.description}"
                    chatViewModel.readAloud(fullText)
                }
            )
        }
        
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun SocialIntelView(
    socialPosts: List<SocialPost>,
    onAiClick: (SocialPost) -> Unit,
    onActionClick: (SocialPost, String) -> Unit,
    chatViewModel: ChatViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { Text("Social Intelligence Stream", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        
        items(socialPosts) { post ->
            SocialPostItem(
                post = post,
                onAiClick = { onAiClick(post) },
                onReadAloud = { text, mode -> chatViewModel.readAloud(text) },
                onLikeClick = { onActionClick(post, "Like") },
                onFollowClick = { onActionClick(post, "Follow") },
                onDmClick = { 
                    chatViewModel.switchChat(post.author, post.platform)
                }
            )
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
fun SocialPostItem(
    post: SocialPost, 
    onAiClick: () -> Unit, 
    onReadAloud: (String, InteractionMode) -> Unit,
    onLikeClick: () -> Unit = {},
    onFollowClick: () -> Unit = {},
    onDmClick: () -> Unit = {}
) {
    val platformColor = try { Color(android.graphics.Color.parseColor(post.platformColor)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.platformIcon, fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.author, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("${post.fetchDisplayName()} • ${post.getRelativeTime()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                
                // 🛠️ Feed Tools: AI & Voice
                IconButton(onClick = onAiClick, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AutoFixHigh, "AI Analysis", tint = platformColor, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { onReadAloud(post.content, InteractionMode.SINGLE) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.VolumeUp, "Read Aloud", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.content, style = MaterialTheme.typography.bodyMedium)
            
            if (!post.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Post Image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            // 🚀 Unified Social Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onLikeClick) {
                    Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Like", fontSize = 10.sp, color = Color.Gray)
                }
                TextButton(onClick = onFollowClick) {
                    Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Follow", fontSize = 10.sp, color = Color.Gray)
                }
                Button(
                    onClick = onDmClick,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = platformColor.copy(alpha = 0.2f), contentColor = platformColor)
                ) {
                    Icon(Icons.Default.ChatBubbleOutline, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("DM", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SolarSystemCard(viewModel: DashboardViewModel = hiltViewModel()) {
    val trackedObjects = viewModel.trackedObjects
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Celestial Intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.fetchCelestialData() }) {
                    Icon(Icons.Default.Refresh, "Update Orbitals", modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            if (trackedObjects.isEmpty()) {
                Text("Synchronizing with JPL Horizons...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                trackedObjects.forEach { obj ->
                    Row(
                        modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val icon = when(obj.id) {
                            "10" -> "☀️"
                            "399" -> "🌍"
                            "499" -> "🔴"
                            "599" -> "🪐"
                            else -> "✨"
                        }
                        Text(icon, fontSize = 20.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(obj.name.uppercase(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                            Text("${obj.status}: ${obj.orientation} (${obj.azimuth.toInt()}°)", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchCenterView(
    deviceId: String,
    viewModel: DashboardViewModel,
    snackbarHostState: SnackbarHostState
) {
    var content by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf(setOf<String>()) }
    var postType by remember { mutableStateOf("post") } // New: "post" or "status"
    val connectedSocials = viewModel.socialUpdates.map { it.platform.lowercase() }
    var isPosting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Compose Dispatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("What's happening?") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("Type your tweet, status update, or story caption...") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // New: Post Type Selector
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dispatch Type:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = postType == "post",
                onClick = { postType = "post" },
                label = { Text("Standard Post") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = postType == "status",
                onClick = { postType = "status" },
                label = { Text("Status / Story") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Target Socials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        if (connectedSocials.isEmpty()) {
            Text("No connected socials found. Connect accounts in Settings.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                connectedSocials.forEach { platform ->
                    FilterChip(
                        selected = selectedPlatforms.contains(platform),
                        onClick = {
                            selectedPlatforms = if (selectedPlatforms.contains(platform)) {
                                selectedPlatforms - platform
                            } else {
                                selectedPlatforms + platform
                            }
                        },
                        label = { Text(platform.uppercase()) },
                        leadingIcon = {
                             Icon(
                                imageVector = when(platform) {
                                    "twitter", "x" -> Icons.Default.Public
                                    "whatsapp" -> Icons.Default.Chat
                                    else -> Icons.Default.Public
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                scope.launch {
                    isPosting = true
                    var successCount = 0
                    selectedPlatforms.forEach { platform ->
                        val result = viewModel.postToSocial(deviceId, platform, postType, content)
                        if (result) successCount++
                    }
                    isPosting = false
                    snackbarHostState.showSnackbar("Dispatched to $successCount platforms!")
                    if (successCount == selectedPlatforms.size) content = ""
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = content.isNotBlank() && selectedPlatforms.isNotEmpty() && !isPosting,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.Send, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transmit Dispatch")
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun LocationBanner(weather: com.example.mistreal_mini.data.api.WeatherResponse?) {
    val locationText = weather?.location ?: "Detecting Location..."
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Active Location: $locationText", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(text = "News & Weather updated for this region", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsDetailScreen(article: Article, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("News Detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(article.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(article.description ?: "No description available.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(article.url))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ShapeDefaults.Medium
                ) {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Read Full Story")
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun WeatherCard(weather: com.example.mistreal_mini.data.api.WeatherResponse?, orientation: String, bearing: Float) {
    val cityName = weather?.location ?: "Detecting..."
    val weatherIcon = when {
        weather?.summary?.lowercase()?.contains("rain") == true -> Icons.Default.CloudQueue
        weather?.summary?.lowercase()?.contains("cloud") == true -> Icons.Default.Cloud
        weather?.summary?.lowercase()?.contains("sun") == true || weather?.summary?.lowercase()?.contains("clear") == true -> Icons.Default.WbSunny
        weather?.summary?.lowercase()?.contains("error") == true || weather?.summary?.lowercase()?.contains("fail") == true -> Icons.Default.Warning
        else -> Icons.Default.Cloud
    }
    val weatherColor = when {
        weather?.summary?.lowercase()?.contains("rain") == true -> Color(0xFF64B5F6)
        weather?.summary?.lowercase()?.contains("sun") == true || weather?.summary?.lowercase()?.contains("clear") == true -> Color(0xFFFFD54F)
        weather?.summary?.lowercase()?.contains("error") == true || weather?.summary?.lowercase()?.contains("fail") == true -> Color.Red
        else -> Color.White
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Weather & Astro Info
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = weatherIcon, 
                        contentDescription = null, 
                        modifier = Modifier.size(44.dp).padding(top = 4.dp),
                        tint = weatherColor
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📍 $cityName", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = weather?.summary ?: "Calibrating Environment...", 
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        if (weather?.rainExpected == true) {
                            val eventText = if(weather.rainEventType == "START") "expected in" else "clearing in"
                            val eventIcon = if(weather.rainEventType == "START") "☔" else "⛅"
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$eventIcon INTEL: Rain $eventText ${weather.timeToRain}m", color = Color(0xFFE57373), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Vertical Divider for visual separation
                Box(modifier = Modifier.width(1.dp).height(80.dp).padding(horizontal = 4.dp)) {
                    VerticalDivider(color = Color.White.copy(alpha = 0.2f))
                }

                // Right side: Specialized Compass UI
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally, 
                    modifier = Modifier.width(100.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Compass
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Navigation, 
                            contentDescription = "Compass", 
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer { rotationZ = -bearing },
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = orientation, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text("${bearing.toInt()}°", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun SocialUpdateItem(update: PlatformUpdate, onAiClick: () -> Unit, onReadAloud: (String, InteractionMode) -> Unit) {
    val platformColor = update.platformColor?.let { colorStr ->
        try {
            Color(android.graphics.Color.parseColor(colorStr))
        } catch (e: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = platformColor?.copy(alpha = 0.1f) ?: MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        SelectionContainer {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!update.platformIcon.isNullOrEmpty()) {
                    Text(update.platformIcon, fontSize = 24.sp, modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = when(update.platform.lowercase()) {
                            "twitter" -> Icons.Default.Public
                            "whatsapp" -> Icons.Default.Chat
                            "facebook" -> Icons.Default.Facebook
                            "instagram" -> Icons.Default.CameraAlt
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = platformColor ?: MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(update.platformDisplayName ?: update.platform, fontWeight = FontWeight.Bold)
                    Text("${update.count} updates", style = MaterialTheme.typography.labelSmall)
                    update.recentMessage?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                }
                
                Row {
                    IconButton(onClick = onAiClick) { Icon(Icons.Default.Psychology, "AI", tint = MaterialTheme.colorScheme.primary) }
                    val contentToRead = "Update from ${update.platform}: ${update.recentMessage ?: ""}"
                    Box(modifier = Modifier.size(48.dp).pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onReadAloud(contentToRead, InteractionMode.SINGLE) },
                            onDoubleTap = { onReadAloud(contentToRead, InteractionMode.RADIO) },
                            onLongPress = { onReadAloud(contentToRead, InteractionMode.HANDS_FREE) }
                        )
                    }) {
                        Icon(Icons.Default.VolumeUp, "Read", modifier = Modifier.size(24.dp).align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun NasaApodCard(article: Article) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                coil.compose.AsyncImage(
                    model = article.url,
                    contentDescription = "NASA APOD",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "NASA APOD • ${article.title.replace("[Astro] ", "")}", 
                        color = Color.White, 
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = article.description ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(12.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun NewsItem(
    article: Article, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit, 
    onAiClick: () -> Unit, 
    onReadAloud: (String, InteractionMode) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        SelectionContainer {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(article.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 2, modifier = Modifier.weight(1f))
                    
                    Row {
                        IconButton(onClick = onAiClick) { 
                            Icon(Icons.Default.AutoFixHigh, "AI Analysis", tint = MaterialTheme.colorScheme.primary) 
                        }
                        val contentToRead = "${article.title}. ${article.description ?: ""}"
                        IconButton(onClick = { onReadAloud(contentToRead, InteractionMode.SINGLE) }) {
                            Icon(Icons.Default.VolumeUp, "Read Aloud", tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                if (!article.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = article.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 36.dp)
                    )
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.
