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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    viewModel: DashboardViewModel = hiltViewModel(),
    feedViewModel: FeedViewModel = hiltViewModel(),
    mapViewModel: TacticalMapViewModel = hiltViewModel(),
    celestialViewModel: CelestialViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
    
    var selectedArticle by remember { mutableStateOf<Article?>(null) }
    var insightContext by remember { mutableStateOf<String?>(null) }
    var insightSourcePost by remember { mutableStateOf<com.example.mistreal_mini.data.model.SocialPost?>(null) }
    var showInsightPopup by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showMapPopup by remember { mutableStateOf(false) }
    var showFullSolarSystem by remember { mutableStateOf(false) }

    val weather by viewModel.weather
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
    }

    val currentPersona by viewModel.currentPersona.collectAsStateWithLifecycle(initialValue = "Shadow")
    val isArchitectMode = currentPersona.contains("Architect", ignoreCase = true)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isArchitectMode) "MISTREAL ARCHITECT" else "MISTREAL TACTICAL", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = onDmClick) {
                        BadgedBox(badge = { Badge { Text("3") } }) {
                            Icon(Icons.Default.Chat, "Direct Messages")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("INTEL") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("MAP") })
                if (isArchitectMode) {
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("DESIGN") })
                }
                Tab(selected = selectedTab == (if(isArchitectMode) 3 else 2), onClick = { selectedTab = if(isArchitectMode) 3 else 2 }, text = { Text("SOCIAL") })
                Tab(selected = selectedTab == (if(isArchitectMode) 4 else 3), onClick = { selectedTab = if(isArchitectMode) 4 else 3 }, text = { Text("DISPATCH") })
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
                        onPinClick = { feedViewModel.togglePin(it) },
                        onReadAloud = { text, _ -> chatViewModel.readAloud(text) },
                        chatViewModel = chatViewModel,
                        dashboardViewModel = viewModel,
                        snackbarHostState = snackbarHostState,
                        onRefresh = { feedViewModel.loadFeed(deviceId) },
                        onOrbitalClick = { showFullSolarSystem = true }
                    )
                    selectedTab == 1 -> InteractiveMapView(
                        location = weather?.location ?: "Sector Unknown",
                        onClose = { selectedTab = 0 },
                        viewModel = chatViewModel,
                        dashboardViewModel = viewModel,
                        mapViewModel = mapViewModel,
                        celestialViewModel = celestialViewModel,
                        snackbarHostState = snackbarHostState,
                        onScreenshotClick = { /* ... */ },
                        onCameraClick = { /* ... */ },
                        onFileClick = { /* ... */ },
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
                        isLoading = feedViewModel.isLoading.value
                    )
                    (isArchitectMode && selectedTab == 4) || (!isArchitectMode && selectedTab == 3) -> DispatchCenterView(
                        deviceId = deviceId,
                        socialUpdates = feedViewModel.socialUpdates,
                        onPostToSocial = { _, platform, type, content ->
                            feedViewModel.postToSocial(deviceId, platform, type, content)
                        },
                        snackbarHostState = snackbarHostState
                    )
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }

    if (showFullSolarSystem) {
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
}
