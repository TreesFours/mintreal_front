package com.example.mistreal_mini

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.mistreal_mini.ui.theme.MistrealTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.work.*
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.ui.chat.ChatScreen
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.settings.SettingsScreen
import com.example.mistreal_mini.ui.splash.SplashScreen
import com.example.mistreal_mini.ui.subscription.SubscriptionScreen
import com.example.mistreal_mini.ui.dashboard.DashboardScreen
import com.example.mistreal_mini.ui.dashboard.DashboardViewModel
import com.example.mistreal_mini.ui.onboarding.OnboardingScreen
import com.example.mistreal_mini.ui.auth.AuthScreen
import com.example.mistreal_mini.ui.auth.AuthViewModel
import com.example.mistreal_mini.ui.settings.SettingsViewModel
import com.example.mistreal_mini.ui.records.RecordsScreen
import com.example.mistreal_mini.ui.navigation.Routes
import com.example.mistreal_mini.ui.util.LocalSfxHapticPlayer
import com.example.mistreal_mini.util.DeviceIdProvider
import com.example.mistreal_mini.util.FaceGuard
import com.example.mistreal_mini.util.SfxHapticPlayer
import com.example.mistreal_mini.worker.WeatherWorker
import com.example.mistreal_mini.worker.NewsWorker
import com.example.mistreal_mini.worker.HistoryWorker
import com.example.mistreal_mini.data.worker.CelestialWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.concurrent.TimeUnit
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var faceGuard: FaceGuard
    @Inject lateinit var preferenceManager: PreferenceManager
    @Inject lateinit var deviceIdProvider: DeviceIdProvider
    @Inject lateinit var sfxHapticPlayer: SfxHapticPlayer
    @Inject lateinit var handleDistressUseCase: com.example.mistreal_mini.domain.usecase.HandleDistressUseCase

    private var volumePressCount = 0
    private var lastVolumePressTime = 0L
    private val _showHardwareSosDialog = mutableStateOf(false)

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {
            val now = System.currentTimeMillis()
            if (now - lastVolumePressTime < 1000) {
                volumePressCount++
            } else {
                volumePressCount = 1
            }
            lastVolumePressTime = now
            if (volumePressCount >= 3) {
                volumePressCount = 0
                _showHardwareSosDialog.value = true
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }
        if (deniedPermissions.isNotEmpty()) {
            Toast.makeText(this, "Some features may not work without permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private var _intentState = mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentState.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _intentState.value = intent
        enableEdgeToEdge()
        checkAndRequestPermissions()
        setupBackgroundWorkers()
        
        
        setContent {
            val themeMode by preferenceManager.themeMode.collectAsStateWithLifecycle(initialValue = "auto")
            val isDarkTheme = when(themeMode) {
                "fire" -> true
                "sand" -> false
                else -> isSystemInDarkTheme()
            }

            MistrealTheme(darkTheme = isDarkTheme) {
            CompositionLocalProvider(LocalSfxHapticPlayer provides sfxHapticPlayer) {
                val intentState by _intentState
                val isOnboarded by preferenceManager.isOnboarded.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()

                val showSosDialog by _showHardwareSosDialog
                if (showSosDialog) {
                    var countdown by remember { mutableIntStateOf(3) }
                    LaunchedEffect(showSosDialog) {
                        while (countdown > 0) {
                            kotlinx.coroutines.delay(1000)
                            countdown--
                        }
                        val deviceId = deviceIdProvider.deviceId
                        scope.launch {
                            handleDistressUseCase(deviceId = deviceId, distressSignature = "Hardware Key Fast SOS Triggered", triggerType = "hardware_key")
                        }
                        _showHardwareSosDialog.value = false
                    }

                    AlertDialog(
                        onDismissRequest = { _showHardwareSosDialog.value = false },
                        icon = { Icon(Icons.Default.Warning, null, tint = androidx.compose.ui.graphics.Color.Red) },
                        title = { Text("EMERGENCY SOS TRIGGERED", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                        text = { Text("Silent Emergency Alert dispatching to confirmed nodes in $countdown seconds...") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val deviceId = deviceIdProvider.deviceId
                                    scope.launch {
                                        handleDistressUseCase(deviceId = deviceId, distressSignature = "Hardware Key Fast SOS Triggered", triggerType = "hardware_key")
                                    }
                                    _showHardwareSosDialog.value = false
                                },
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color.Red)
                            ) {
                                Text("SEND NOW")
                            }
                        },
                        dismissButton = {
                            androidx.compose.material3.TextButton(onClick = { _showHardwareSosDialog.value = false }) {
                                Text("CANCEL")
                            }
                        }
                    )
                }
                
                // 🛡️ Security & Authentication State (Rotation Resilient)
                var isAuthenticated by rememberSaveable { mutableStateOf(false) }
                val authViewModel: AuthViewModel = hiltViewModel()
                var isFirebaseAuthenticated by rememberSaveable { mutableStateOf(authViewModel.isUserLoggedIn()) }

                // 🧭 Professional Navigation Controller
                val navController = rememberNavController()
                var backPressedTime by rememberSaveable { mutableLongStateOf(0L) }

                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val chatViewModel: ChatViewModel = hiltViewModel()
                        val dashboardViewModel: DashboardViewModel = hiltViewModel()
                        val feedViewModel: com.example.mistreal_mini.ui.dashboard.FeedViewModel = hiltViewModel()
                        val settingsViewModel: SettingsViewModel = hiltViewModel()
                        val guardianViewModel: com.example.mistreal_mini.ui.guardian.GuardianViewModel = hiltViewModel()

                        LaunchedEffect(isAuthenticated) {
                            if (isAuthenticated) guardianViewModel.fetchAlerts()
                        }

                        // Keeps lastKnownLat/Lon roughly fresh for the Needs/Offers
                        // matching feature's proximity ranking — once per session,
                        // not continuous tracking (see SettingsViewModel.reportLocationOnce).
                        LaunchedEffect(isAuthenticated) {
                            if (isAuthenticated) settingsViewModel.reportLocationOnce()
                        }

                        // So this device is callable the moment the app is ever opened,
                        // not only after whatever screen happens to touch Settings first.
                        LaunchedEffect(isAuthenticated) {
                            if (isAuthenticated) settingsViewModel.registerFcmTokenOnce()
                        }

                        // 🔗 Deep Link Handler
                        LaunchedEffect(intentState) {
                            intentState?.data?.let { uri ->
                                val isMistrealScheme = uri.scheme == "mistreal" && uri.host == "social-connected"
                                val isHttpsAppLink = uri.scheme == "https" && 
                                                    uri.host == BuildConfig.BACKEND_HOST &&
                                                    uri.path?.contains("social-connected") == true
                                
                                if (isMistrealScheme || isHttpsAppLink) {
                                    val platform = uri.getQueryParameter("platform") ?: "platform"
                                    val success = uri.getQueryParameter("success") == "true"
                                    val deviceId = deviceIdProvider.deviceId
                                    
                                    if (success) {
                                        Toast.makeText(this@MainActivity, "✅ $platform linked successfully!", Toast.LENGTH_LONG).show()
                                        settingsViewModel.onSocialConnectionResult(platform, true)
                                        dashboardViewModel.loadDashboardData(deviceId)
                                        feedViewModel.loadFeed(deviceId)
                                        chatViewModel.refreshSocialContacts() 
                                    } else {
                                        val error = uri.getQueryParameter("error") ?: "Connection failed"
                                        Toast.makeText(this@MainActivity, "❌ Error: $error", Toast.LENGTH_LONG).show()
                                        settingsViewModel.onSocialConnectionResult(platform, false)
                                    }
                                    
                                    // Clear intent to prevent re-triggering on configuration change
                                    _intentState.value = null
                                }
                            }
                        }

                        // 📞 Incoming-call notification tap (MistrealFirebaseMessagingService's
                        // full-screen intent) — these extras are only ever present on that path.
                        LaunchedEffect(intentState) {
                            val callId = intentState?.getStringExtra("incoming_call_id")
                            val callerDeviceId = intentState?.getStringExtra("incoming_caller_device_id")
                            if (callId != null && callerDeviceId != null) {
                                navController.navigate(Routes.incomingCall(callId, callerDeviceId))
                                _intentState.value = null
                            }
                        }

                    // 🛡️ Back Navigation Handler (Survives Rotation & Exit Logic)
                    BackHandler(enabled = true) {
                        if (navController.previousBackStackEntry != null) {
                            navController.popBackStack()
                        } else {
                            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                                finish() 
                            } else {
                                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                                backPressedTime = System.currentTimeMillis()
                            }
                        }
                    }

                    // An incoming call must be answerable/declinable the same way a real
                    // phone call is — without first clearing a Face/Firebase auth gate —
                    // so the auth guard below must never navigate out from under it. Read
                    // directly off intentState (not a LaunchedEffect) so it's correct in
                    // the very same composition pass the incoming-call effect above acts on.
                    val hasPendingIncomingCall = intentState?.getStringExtra("incoming_call_id") != null

                    // 🚦 Navigation Guard & Orchestration
                    LaunchedEffect(isOnboarded, isFirebaseAuthenticated, isAuthenticated, hasPendingIncomingCall) {
                        if (isOnboarded == true && !hasPendingIncomingCall) {
                            if (!isFirebaseAuthenticated) {
                                navController.navigate(Routes.AUTH) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else if (!isAuthenticated) {
                                faceGuard.authenticateOwner(
                                    activity = this@MainActivity,
                                    onSuccess = { isAuthenticated = true },
                                    onFailure = { /* Handle Failure */ }
                                )
                            } else {
                                // Fully authenticated: route through "boot" first — it's the
                                // one that actually kicks off the network-backed loads these
                                // ViewModels deferred out of their init{} blocks (so nothing
                                // talks to the backend before this point), and shows that
                                // sequence to the user instead of it happening silently.
                                if (navController.currentDestination?.route == Routes.SPLASH ||
                                    navController.currentDestination?.route == Routes.AUTH) {
                                    navController.navigate(Routes.BOOT) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }

                    // Shared across market/discovery/detail — all three used to
                    // duplicate this exact navigate-on-success/toast-on-failure
                    // block independently.
                    val onContactBusiness: (com.example.mistreal_mini.data.api.RemoteBusiness) -> Unit = { business ->
                        chatViewModel.contactBusiness(business) { success, error ->
                            if (success) {
                                navController.navigate(Routes.chat())
                            } else {
                                Toast.makeText(this@MainActivity, error ?: "Couldn't contact this business.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = Routes.SPLASH,
                        enterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { 300 }) },
                        exitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { -300 }) },
                        popEnterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { -300 }) },
                        popExitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { 300 }) }
                    ) {
                        composable(Routes.SPLASH) {
                            SplashScreen(onTimeout = {
                                if (isOnboarded == false) {
                                    navController.navigate(Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true } }
                                } else {
                                    // Guard logic handled by LaunchedEffect above
                                }
                            })
                        }
                        composable(Routes.BOOT) {
                            com.example.mistreal_mini.ui.boot.BootScreen(
                                chatViewModel = chatViewModel,
                                settingsViewModel = settingsViewModel,
                                onComplete = {
                                    navController.navigate(Routes.chat()) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable(Routes.ONBOARDING) {
                            OnboardingScreen(
                                faceGuard = faceGuard,
                                onComplete = {
                                    scope.launch {
                                        preferenceManager.setOnboarded(true)
                                        // Guard logic handled by LaunchedEffect above
                                    }
                                }
                            )
                        }
                        composable(Routes.AUTH) {
                            AuthScreen(onAuthSuccess = { isFirebaseAuthenticated = true })
                        }
                        composable(
                            Routes.CHAT_TEMPLATE,
                            arguments = listOf(navArgument("initialProvider") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { backStackEntry ->
                            ChatScreen(
                                viewModel = chatViewModel,
                                initialProviderOverride = backStackEntry.arguments?.getString("initialProvider"),
                                onSubscribeClick = { navController.navigate(Routes.SUBSCRIPTION) },
                                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                                onDashboardClick = { navController.navigate(Routes.DASHBOARD) },
                                onArchiveClick = { navController.navigate(Routes.ARCHIVE) },
                                onLiveAiClick = { navController.navigate(Routes.LIVE_AI) }
                            )
                        }
                        composable(Routes.SUBSCRIPTION) {
                            SubscriptionScreen(onDismiss = { navController.popBackStack() })
                        }
                        composable(Routes.ARCHIVE) {
                            RecordsScreen(
                                onBack = { navController.popBackStack() },
                                onTrendClick = { title ->
                                    chatViewModel.loadTrend(title)
                                    navController.popBackStack()
                                },
                                chatViewModel = chatViewModel
                            )
                        }
                        composable(Routes.CONNECTIONS) {
                            com.example.mistreal_mini.ui.settings.SocialConnectionsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                onBack = { navController.popBackStack() },
                                onDmClick = { navController.popBackStack() },
                                onMyProfileClick = {
                                    navController.navigate(Routes.ownerProfile(deviceIdProvider.deviceId))
                                },
                                onAuthorProfileClick = { authorDeviceId ->
                                    navController.navigate(Routes.ownerProfile(authorDeviceId))
                                },
                                onAdvertiseClick = { navController.navigate(Routes.BUSINESS_ADVERTISE) },
                                onOpenAiRender = {
                                    // Map already shares this exact chatViewModel instance with
                                    // the "chat" destination (both are given the same one
                                    // instantiated above, outside the NavHost) — setting the
                                    // provider directly here is correct BECAUSE of that, not a
                                    // shortcut around it. If DashboardScreen/InteractiveMapView
                                    // ever stop sharing that instance, this silently breaks;
                                    // Settings' route below uses a nav-arg instead for exactly
                                    // that reason.
                                    chatViewModel.setProvider(com.example.mistreal_mini.ui.chat.ChatViewModel.ARCH_IMAGE_PROVIDER_ID)
                                    navController.navigate(Routes.chat())
                                }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onUpgradeClick = { navController.navigate(Routes.SUBSCRIPTION) },
                                onConnectionsClick = { navController.navigate(Routes.CONNECTIONS) },
                                onBusinessHubClick = { navController.navigate(Routes.BUSINESS_HUB) },
                                onGuardianAlertsClick = { navController.navigate(Routes.GUARDIAN_ALERTS) },
                                onWatchHostClick = { navController.navigate(Routes.WATCH_HOST_BASE) },
                                onWatchViewerClick = { navController.navigate(Routes.WATCH_VIEWER_BASE) },
                                onMarketClick = { navController.navigate(Routes.MARKET) },
                                onGamesClick = { navController.navigate(Routes.GAMES) },
                                onEmailClick = { navController.navigate(Routes.EMAIL) },
                                onPendingReviewsClick = { navController.navigate(Routes.PENDING_REVIEWS) },
                                onMyProfileClick = {
                                    navController.navigate(Routes.ownerProfile(deviceIdProvider.deviceId))
                                },
                                onNeedsClick = { navController.navigate(Routes.NEEDS) },
                                onAiRenderClick = { navController.navigate(Routes.chat(com.example.mistreal_mini.ui.chat.ChatViewModel.ARCH_IMAGE_PROVIDER_ID)) }
                            )
                        }
                        composable(Routes.NEEDS) {
                            com.example.mistreal_mini.ui.needs.NeedsFeedScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.PENDING_REVIEWS) {
                            com.example.mistreal_mini.ui.profile.PendingReviewsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            Routes.OWNER_PROFILE_TEMPLATE,
                            arguments = listOf(
                                navArgument("ownerName") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("ownerPhotoUrl") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("businessId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            val decode = { s: String -> java.net.URLDecoder.decode(s, "UTF-8") }
                            com.example.mistreal_mini.ui.profile.OwnerProfileScreen(
                                revieweeDeviceId = backStackEntry.arguments?.getString("revieweeDeviceId")?.let(decode) ?: "",
                                ownerName = backStackEntry.arguments?.getString("ownerName")?.let(decode),
                                ownerPhotoUrl = backStackEntry.arguments?.getString("ownerPhotoUrl")?.let(decode),
                                businessId = backStackEntry.arguments?.getString("businessId")?.let(decode),
                                onBack = { navController.popBackStack() },
                                onCallClick = { deviceId -> navController.navigate(Routes.outgoingCall(deviceId)) }
                            )
                        }
                        composable(Routes.MARKET) {
                            com.example.mistreal_mini.ui.market.MarketScreen(
                                onBack = { navController.popBackStack() },
                                onContactBusiness = onContactBusiness,
                                onViewBusinessDetail = { business ->
                                    navController.navigate(Routes.businessDetail(business.businessId))
                                }
                            )
                        }
                        composable(Routes.GAMES) {
                            com.example.mistreal_mini.ui.games.GamesListScreen(
                                onBack = { navController.popBackStack() },
                                onPlayGame = { game ->
                                    navController.navigate(Routes.gamePlay(game.title, game.playUrl))
                                }
                            )
                        }
                        composable(
                            Routes.GAME_PLAY_TEMPLATE,
                            arguments = listOf(
                                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                                navArgument("playUrl") { type = NavType.StringType; defaultValue = "" }
                            )
                        ) { backStackEntry ->
                            com.example.mistreal_mini.ui.games.GamePlayScreen(
                                title = backStackEntry.arguments?.getString("title").orEmpty(),
                                playUrl = backStackEntry.arguments?.getString("playUrl").orEmpty(),
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            Routes.WATCH_HOST_TEMPLATE,
                            arguments = listOf(
                                navArgument("contextType") { type = NavType.StringType; defaultValue = "standalone" },
                                navArgument("contextId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            com.example.mistreal_mini.ui.watch.WatchHostScreen(
                                onBack = { navController.popBackStack() },
                                contextType = backStackEntry.arguments?.getString("contextType") ?: "standalone",
                                contextId = backStackEntry.arguments?.getString("contextId")
                            )
                        }
                        composable(
                            Routes.WATCH_VIEWER_TEMPLATE,
                            arguments = listOf(navArgument("sessionId") { type = NavType.StringType; nullable = true; defaultValue = null })
                        ) { backStackEntry ->
                            com.example.mistreal_mini.ui.watch.WatchViewerScreen(
                                onBack = { navController.popBackStack() },
                                initialSessionId = backStackEntry.arguments?.getString("sessionId")
                            )
                        }
                        composable(
                            Routes.CALL_TEMPLATE,
                            arguments = listOf(
                                navArgument("calleeDeviceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("callId") { type = NavType.StringType; nullable = true; defaultValue = null },
                                navArgument("callerDeviceId") { type = NavType.StringType; nullable = true; defaultValue = null }
                            )
                        ) { backStackEntry ->
                            com.example.mistreal_mini.ui.call.CallScreen(
                                onBack = { navController.popBackStack() },
                                calleeDeviceId = backStackEntry.arguments?.getString("calleeDeviceId"),
                                incomingCallId = backStackEntry.arguments?.getString("callId"),
                                incomingCallerDeviceId = backStackEntry.arguments?.getString("callerDeviceId")
                            )
                        }
                        composable(Routes.LIVE_AI) {
                            com.example.mistreal_mini.ui.liveai.LiveAiScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.EMAIL) {
                            com.example.mistreal_mini.ui.email.EmailScreen(
                                onBack = { navController.popBackStack() },
                                onCompose = { navController.navigate(Routes.EMAIL_COMPOSE) },
                                onOpenMessage = { uid -> navController.navigate(Routes.emailMessage(uid)) }
                            )
                        }
                        composable(Routes.EMAIL_COMPOSE) {
                            com.example.mistreal_mini.ui.email.EmailComposeScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            Routes.EMAIL_MESSAGE_TEMPLATE,
                            arguments = listOf(navArgument("uid") { type = NavType.IntType })
                        ) { backStackEntry ->
                            com.example.mistreal_mini.ui.email.EmailMessageScreen(
                                uid = backStackEntry.arguments?.getInt("uid") ?: 0,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.GUARDIAN_ALERTS) {
                            com.example.mistreal_mini.ui.guardian.AlertHistoryScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.BUSINESS_HUB) {
                            com.example.mistreal_mini.ui.business.components.BusinessHubDialog(
                                onDismiss = { navController.popBackStack() },
                                onNavigateToDiscovery = {
                                    navController.popBackStack()
                                    navController.navigate(Routes.BUSINESS_DISCOVERY)
                                },
                                onNavigateToSellerCommand = {
                                    navController.popBackStack()
                                    navController.navigate(Routes.BUSINESS_SELLER)
                                },
                                onNavigateToMeetups = {
                                    navController.popBackStack()
                                    navController.navigate(Routes.BUSINESS_MEETUPS)
                                },
                                onNavigateToAdvertise = {
                                    navController.popBackStack()
                                    navController.navigate(Routes.BUSINESS_ADVERTISE)
                                }
                            )
                        }
                        composable(Routes.BUSINESS_ADVERTISE) {
                            com.example.mistreal_mini.ui.business.AdvertiseScreen(
                                onBack = { navController.popBackStack() },
                                onRegisterBusiness = {
                                    navController.popBackStack()
                                    navController.navigate(Routes.BUSINESS_SELLER)
                                },
                                onUpgradeClick = { navController.navigate(Routes.SUBSCRIPTION) }
                            )
                        }
                        composable(Routes.BUSINESS_MEETUPS) {
                            com.example.mistreal_mini.ui.business.MeetupListScreen(
                                onBack = { navController.popBackStack() },
                                onOpenComfortMeet = { meetupId -> navController.navigate(Routes.comfortMeet(meetupId)) }
                            )
                        }
                        composable(Routes.COMFORT_MEET_TEMPLATE) { backStackEntry ->
                            val meetupId = backStackEntry.arguments?.getString("meetupId")?.toIntOrNull() ?: 0
                            com.example.mistreal_mini.ui.business.ComfortMeetScreen(
                                meetupId = meetupId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.BUSINESS_DISCOVERY) {
                            com.example.mistreal_mini.ui.business.DiscoverySearchScreen(
                                onBack = { navController.popBackStack() },
                                onContactBusiness = onContactBusiness,
                                onViewBusinessDetail = { business ->
                                    navController.navigate(Routes.businessDetail(business.businessId))
                                }
                            )
                        }
                        composable(Routes.BUSINESS_DETAIL_TEMPLATE) { backStackEntry ->
                            val businessId = backStackEntry.arguments?.getString("businessId") ?: ""
                            com.example.mistreal_mini.ui.business.BusinessDetailScreen(
                                businessId = businessId,
                                onBack = { navController.popBackStack() },
                                onContactBusiness = onContactBusiness,
                                onWatchLiveView = { sessionId ->
                                    navController.navigate(Routes.watchViewer(sessionId))
                                },
                                onViewOwnerProfile = { business ->
                                    val ownerDeviceId = business.ownerDeviceId
                                    if (ownerDeviceId != null) {
                                        navController.navigate(
                                            Routes.ownerProfile(
                                                deviceId = ownerDeviceId,
                                                ownerName = business.ownerName,
                                                ownerPhotoUrl = business.ownerPhotoUrl,
                                                businessId = business.businessId
                                            )
                                        )
                                    }
                                }
                            )
                        }
                        composable(Routes.BUSINESS_SELLER) {
                            com.example.mistreal_mini.ui.business.SellerCommandScreen(
                                onBack = { navController.popBackStack() },
                                onUpgradeClick = { navController.navigate(Routes.SUBSCRIPTION) },
                                onStartLiveView = { businessId ->
                                    navController.navigate(Routes.watchHost(contextType = "market", contextId = businessId))
                                }
                            )
                        }
                    }

                    // Blocking "Safe Haven" check-in — shows app-wide (not tied to any
                    // one screen) whenever this device has a fired, unresolved alert.
                    // Confirming here cancels the 30-day public-escalation sweep
                    // server-side immediately; dismissing by tapping outside is
                    // intentionally not allowed (onDismissRequest is a no-op).
                    val activeAlert by guardianViewModel.activeAlert.collectAsState()
                    if (isAuthenticated && activeAlert != null) {
                        AlertDialog(
                            onDismissRequest = { },
                            icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error) },
                            title = { Text("Active Safety Alert") },
                            text = { Text("You triggered a safety alert. If you're safe now, confirm below — otherwise this may be shared publicly if no confirmed emergency contact responds in time.") },
                            confirmButton = {
                                Button(onClick = { guardianViewModel.resolveActiveAlert() }) {
                                    Text("I'm safe — cancel alert")
                                }
                            }
                        )
                    }
                }
            }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())
    }

    private fun setupBackgroundWorkers() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        
        val weatherRequest = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("WeatherAlerts", ExistingPeriodicWorkPolicy.KEEP, weatherRequest)

        val newsRequest = PeriodicWorkRequestBuilder<NewsWorker>(2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("NewsAlerts", ExistingPeriodicWorkPolicy.KEEP, newsRequest)

        val celestialRequest = PeriodicWorkRequestBuilder<CelestialWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("CelestialTracking", ExistingPeriodicWorkPolicy.KEEP, celestialRequest)

        val historyRequest = PeriodicWorkRequestBuilder<HistoryWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("TrendHistoryExpiry", ExistingPeriodicWorkPolicy.KEEP, historyRequest)

        val silentPartnerRequest = PeriodicWorkRequestBuilder<com.example.mistreal_mini.worker.SilentPartnerWorker>(4, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("SilentPartner", ExistingPeriodicWorkPolicy.KEEP, silentPartnerRequest)

        // While a Guardian alert is active, this re-checks every 30 min and
        // keeps reminding the user to confirm they're safe — silence is
        // what leads to the 30-day public escalation server-side.
        val safeHavenRequest = PeriodicWorkRequestBuilder<com.example.mistreal_mini.worker.SafeHavenCheckinWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("SafeHavenCheckin", ExistingPeriodicWorkPolicy.KEEP, safeHavenRequest)

        val marketAlertRequest = PeriodicWorkRequestBuilder<com.example.mistreal_mini.worker.MarketAlertWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("MarketAlerts", ExistingPeriodicWorkPolicy.KEEP, marketAlertRequest)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
