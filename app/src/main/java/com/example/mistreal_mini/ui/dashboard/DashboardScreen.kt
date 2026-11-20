/** 🛡️ AI SYSTEM PROTOCOL 🛡️
 * SOURCE OF TRUTH: master_system_map.artifact.md
 * 
 * 🚀 FUNCTIONAL PIPELINE:
 * [Input]  <- Real-time feeds (News, Socials, Weather, Orbitals) from Repositories
 * [Process] <- Orchestrates multi-tab intelligence display and dispatch actions
 * [Output] -> Renders tactical dashboard UI; triggers social posts or map search
 */
package com.example.mistreal_mini.ui.dashboard

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.api.Article
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.InteractionMode
import com.example.mistreal_mini.ui.util.AiInsightPopup
import com.example.mistreal_mini.ui.dashboard.components.*
import com.example.mistreal_mini.ui.dashboard.components.dispatch.DispatchCenterView
import com.example.mistreal_mini.ui.dashboard.components.social.SocialPagerView
import com.example.mistreal_mini.service.VoiceService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import android.content.Intent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onBack: () -> Unit,
    onDmClick: () -> Unit = {},
    onMyProfileClick: () -> Unit = {},
    onAuthorProfileClick: (String) -> Unit = {},
    onOpenAiRender: () -> Unit = {},
    onAdvertiseClick: () -> Unit = {},
    onOpenGames: () -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onOpenWatchHub: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    feedViewModel: FeedViewModel = hiltViewModel(),
    mapViewModel: TacticalMapViewModel = hiltViewModel(),
    celestialViewModel: CelestialViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val deviceId = com.example.mistreal_mini.util.DeviceIdProvider(context).deviceId
    
    var selectedArticle by remember { mutableStateOf<Article?>(null) }
    var insightContext by remember { mutableStateOf<String?>(null) }
    var insightSourcePost by remember { mutableStateOf<com.example.mistreal_mini.data.model.SocialPost?>(null) }
    var showInsightPopup by remember { mutableStateOf(false) }

    // Quick voice-ask — lighter alternative to AiInsightPopup's full sheet,
    // see ui/util/QuickVoiceAskDialog.kt + AiReplyBubble.kt.
    var showQuickVoiceAsk by remember { mutableStateOf(false) }
    var showVoiceReplyBubble by remember { mutableStateOf(false) }
    var voiceReplyText by remember { mutableStateOf<String?>(null) }
    var isAwaitingVoiceReply by remember { mutableStateOf(false) }

    LaunchedEffect(chatViewModel.isLoading.value, isAwaitingVoiceReply) {
        if (isAwaitingVoiceReply && !chatViewModel.isLoading.value) {
            voiceReplyText = chatViewModel.messages.lastOrNull { it.role == "assistant" }?.content
            isAwaitingVoiceReply = false
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMapPopup by remember { mutableStateOf(false) }
    var showFullSolarSystem by remember { mutableStateOf(false) }
    var showEarthCamDialog by remember { mutableStateOf(false) }

    val weather by viewModel.weather
    val unreadCount by viewModel.unreadMessagesCount
    val isLoading by viewModel.isLoading
    val orientation by viewModel.orientation
    val bearing by viewModel.bearing
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 📸 Media Tool Logic
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            chatViewModel.addPendingAttachment(cameraUri!!)
        }
    }

    BackHandler(enabled = showMapPopup || showFullSolarSystem || selectedArticle != null) {
        when {
            showMapPopup -> showMapPopup = false
            showFullSolarSystem -> showFullSolarSystem = false
            selectedArticle != null -> selectedArticle = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadDashboardData(deviceId)
        feedViewModel.loadFeed(deviceId)
        celestialViewModel.fetchCelestialData()
        chatViewModel.fetchAvailablePlatforms()
    }

    LaunchedEffect(Unit) {
        feedViewModel.errorEvents.collect { message ->
            snackbarHostState.showSnackbar(message, withDismissAction = true)
        }
    }

    val currentPersona by viewModel.currentPersona.collectAsStateWithLifecycle(initialValue = "Shadow")
    val isArchitectMode = currentPersona.contains("Architect", ignoreCase = true)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        com.example.mistreal_mini.ui.components.ThinkingTreeIcon(size = 22.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isArchitectMode) "ARCHITECT HUB" else "COMMAND CENTER", fontWeight = FontWeight.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { showEarthCamDialog = true }) {
                        Icon(Icons.Default.Public, "World Cams", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onMyProfileClick) {
                        Icon(Icons.Default.Person, "My Profile")
                    }
                    IconButton(onClick = onDmClick) {
                        BadgedBox(badge = { 
                            if (unreadCount > 0) {
                                Badge { Text(unreadCount.toString()) } 
                            }
                        }) {
                            Icon(Icons.Default.Chat, "Direct Messages")
                        }
                    }
                }
            )
        },
        bottomBar = {
            com.example.mistreal_mini.ui.chat.components.InstagramStyleBottomBar(
                selectedTab = if (selectedTab == 2) 2 else 4,
                onTabSelected = { tab ->
                    when (tab) {
                        0 -> onOpenGames()
                        1 -> onOpenMarket()
                        2 -> selectedTab = 2
                        3 -> onOpenWatchHub()
                        4 -> onOpenChat()
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.RssFeed, contentDescription = "Intel", modifier = Modifier.size(18.dp)) },
                    text = { Text("INTEL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Map", modifier = Modifier.size(18.dp)) },
                    text = { Text("MAP", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                if (isArchitectMode) {
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.Architecture, contentDescription = "Design", modifier = Modifier.size(18.dp)) },
                        text = { Text("DESIGN", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
                val socialIndex = if (isArchitectMode) 3 else 2
                Tab(
                    selected = selectedTab == socialIndex,
                    onClick = { selectedTab = socialIndex },
                    icon = {
                        BadgedBox(badge = {
                            val socialCount = feedViewModel.socialPosts.size
                            if (socialCount > 0) {
                                Badge { Text(socialCount.toString()) }
                            }
                        }) {
                            Icon(Icons.Default.Public, contentDescription = "Social Slider", modifier = Modifier.size(18.dp))
                        }
                    },
                    text = { Text("SOCIAL", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
                val dispatchIndex = if (isArchitectMode) 4 else 3
                Tab(
                    selected = selectedTab == dispatchIndex,
                    onClick = { selectedTab = dispatchIndex },
                    icon = { Icon(Icons.Default.BroadcastOnPersonal, contentDescription = "Broadcast Deck", modifier = Modifier.size(18.dp)) },
                    text = { Text("COMMS", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    selectedTab == 0 -> IntelligenceFeedView(
                        weather = weather,
                        orientation = orientation,
                        bearing = bearing,
                        articles = feedViewModel.newsArticles,
                        onArticleClick = { selectedArticle = it },
                        onAskAi = {
                            insightContext = it
                            showInsightPopup = true
                        },
                        onVoiceAsk = { showQuickVoiceAsk = true },
                        onPinClick = { feedViewModel.togglePin(it) },
                        onReadAloud = { text, _ -> chatViewModel.readAloud(text) },
                        chatViewModel = chatViewModel,
                        dashboardViewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onRefresh = { feedViewModel.loadFeed(deviceId, forceRefresh = true) },
                        onOrbitalClick = { showFullSolarSystem = true }
                    )
                    selectedTab == 1 -> InteractiveMapView(
                        location = weather?.location ?: "Sector Unknown",
                        onClose = { selectedTab = 0 },
                        onOpenAiRender = onOpenAiRender,
                        viewModel = chatViewModel,
                        dashboardViewModel = viewModel,
                        mapViewModel = mapViewModel,
                        celestialViewModel = celestialViewModel,
                        snackbarHostState = snackbarHostState,
                        startInSpaceMode = false
                    )
                    isArchitectMode && selectedTab == 2 -> {
                        ArchitectureDesignView(
                            chatViewModel = chatViewModel,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    (isArchitectMode && selectedTab == 3) || (!isArchitectMode && selectedTab == 2) -> SocialPagerView(
                        posts = feedViewModel.socialPosts,
                        onPostClick = {},
                        onAiClick = { post, content ->
                            insightContext = "SOCIAL_INTEL:\n$content\n\nAnalyze this post for potential leads or threats."
                            insightSourcePost = post
                            showInsightPopup = true
                        },
                        chatViewModel = chatViewModel,
                        feedViewModel = feedViewModel,
                        deviceId = deviceId,
                        isLoading = feedViewModel.isLoading.value,
                        onAuthorClick = onAuthorProfileClick,
                        onVoiceAsk = { showQuickVoiceAsk = true }
                    )
                    (isArchitectMode && selectedTab == 4) || (!isArchitectMode && selectedTab == 3) -> DispatchCenterView(
                        deviceId = deviceId,
                        connectedSocials = chatViewModel.availablePlatforms.filter { it.isConnected }.map { it.id.lowercase() },
                        onPostToSocial = { _, platform, type, content, mistrealOnly, mediaBase64, mediaMimeType, shareToCommunity ->
                            feedViewModel.postToSocial(
                                deviceId, platform, type, content, mistrealOnly = mistrealOnly,
                                mediaBase64 = mediaBase64, mediaMimeType = mediaMimeType,
                                shareToCommunity = shareToCommunity
                            )
                        },
                        onAdvertiseClick = onAdvertiseClick,
                        snackbarHostState = snackbarHostState
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }

                if (showVoiceReplyBubble) {
                    com.example.mistreal_mini.ui.util.AiReplyBubble(
                        isLoading = isAwaitingVoiceReply,
                        replyText = voiceReplyText,
                        onReadAloud = { voiceReplyText?.let { chatViewModel.readAloud(it) } },
                        onDismiss = { showVoiceReplyBubble = false; voiceReplyText = null },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                    )
                }
            }
        }
    }

    if (showFullSolarSystem) {
        DisposableEffect(Unit) {
            celestialViewModel.startAutoRefresh()
            onDispose { celestialViewModel.stopAutoRefresh() }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showFullSolarSystem = false }) { Icon(Icons.Default.ArrowBack, "Back") }
                    Text("ORBITAL INTELLIGENCE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(24.dp))
                CelestialLedger(
                    trackedObjects = celestialViewModel.trackedObjects,
                    onRefresh = { celestialViewModel.fetchCelestialData() },
                    chatViewModel = chatViewModel,
                    onAskAi = { 
                        insightContext = it
                        showInsightPopup = true 
                    }
                )
            }
        }
    }

    if (viewModel.isCalibrationWizardVisible.value) {
        val calibrationProgress = viewModel.calibrationProgress.value
        AlertDialog(
            onDismissRequest = { viewModel.toggleCalibrationWizard(false) },
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
                    Text(if (calibrationProgress < 1f) "CONTINUE MOTION..." else "VERIFIED", fontWeight = FontWeight.Black, color = if (calibrationProgress < 1f) Color.Yellow else Color.Green)
                }
            },
            confirmButton = { Button(onClick = { viewModel.toggleCalibrationWizard(false) }, enabled = calibrationProgress >= 1f) { Text("COMPLETE") } }
        )
    }

    if (showEarthCamDialog) {
        com.example.mistreal_mini.ui.dashboard.components.EarthCamOverlay(
            onDismiss = { showEarthCamDialog = false }
        )
    }

    if (selectedArticle != null) {
        NewsDetailScreen(article = selectedArticle!!, onBack = { selectedArticle = null })
    }

    if (showInsightPopup) {
        AiInsightPopup(
            contextText = insightContext ?: "",
            onClose = { showInsightPopup = false; insightSourcePost = null },
            viewModel = chatViewModel,
            sourcePost = insightSourcePost
        )
    }

    if (showQuickVoiceAsk) {
        com.example.mistreal_mini.ui.util.QuickVoiceAskDialog(
            viewModel = chatViewModel,
            onDismiss = { showQuickVoiceAsk = false },
            onSent = {
                showQuickVoiceAsk = false
                voiceReplyText = null
                isAwaitingVoiceReply = true
                showVoiceReplyBubble = true
            }
        )
    }
}
