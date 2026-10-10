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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import com.example.mistreal_mini.util.FaceGuard
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
                val intentState by _intentState
                val isOnboarded by preferenceManager.isOnboarded.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()
                
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

                        // 🔗 Deep Link Handler
                        LaunchedEffect(intentState) {
                            intentState?.data?.let { uri ->
                                val isMistrealScheme = uri.scheme == "mistreal" && uri.host == "social-connected"
                                val isHttpsAppLink = uri.scheme == "https" && 
                                                    uri.host == "mistreal-backend.onrender.com" && 
                                                    uri.path?.contains("social-connected") == true
                                
                                if (isMistrealScheme || isHttpsAppLink) {
                                    val platform = uri.getQueryParameter("platform") ?: "platform"
                                    val success = uri.getQueryParameter("success") == "true"
                                    val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                                    
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

                    // 🚦 Navigation Guard & Orchestration
                    LaunchedEffect(isOnboarded, isFirebaseAuthenticated, isAuthenticated) {
                        if (isOnboarded == true) {
                            if (!isFirebaseAuthenticated) {
                                navController.navigate("auth") {
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
                                if (navController.currentDestination?.route == "splash" ||
                                    navController.currentDestination?.route == "auth") {
                                    navController.navigate("boot") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = "splash",
                        enterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { 300 }) },
                        exitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { -300 }) },
                        popEnterTransition = { fadeIn(animationSpec = tween(400)) + slideInHorizontally(initialOffsetX = { -300 }) },
                        popExitTransition = { fadeOut(animationSpec = tween(400)) + slideOutHorizontally(targetOffsetX = { 300 }) }
                    ) {
                        composable("splash") {
                            SplashScreen(onTimeout = { 
                                if (isOnboarded == false) {
                                    navController.navigate("onboarding") { popUpTo("splash") { inclusive = true } }
                                } else {
                                    // Guard logic handled by LaunchedEffect above
                                }
                            })
                        }
                        composable("boot") {
                            com.example.mistreal_mini.ui.boot.BootScreen(
                                chatViewModel = chatViewModel,
                                settingsViewModel = settingsViewModel,
                                onComplete = {
                                    navController.navigate("chat") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("onboarding") {
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
                        composable("auth") {
                            AuthScreen(onAuthSuccess = { isFirebaseAuthenticated = true })
                        }
                        composable("chat") {
                            ChatScreen(
                                viewModel = chatViewModel,
                                onSubscribeClick = { navController.navigate("subscription") },
                                onSettingsClick = { navController.navigate("settings") },
                                onDashboardClick = { navController.navigate("dashboard") },
                                onArchiveClick = { navController.navigate("archive") }
                            )
                        }
                        composable("subscription") {
                            SubscriptionScreen(onDismiss = { navController.popBackStack() })
                        }
                        composable("archive") {
                            RecordsScreen(
                                onBack = { navController.popBackStack() },
                                onTrendClick = { title ->
                                    chatViewModel.loadTrend(title)
                                    navController.popBackStack()
                                },
                                chatViewModel = chatViewModel
                            )
                        }
                        composable("connections") {
                            com.example.mistreal_mini.ui.settings.SocialConnectionsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("dashboard") {
                            DashboardScreen(
                                onBack = { navController.popBackStack() },
                                onDmClick = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onUpgradeClick = { navController.navigate("subscription") },
                                onConnectionsClick = { navController.navigate("connections") },
                                onBusinessHubClick = { navController.navigate("business_hub") },
                                onGuardianAlertsClick = { navController.navigate("guardian_alerts") }
                            )
                        }
                        composable("guardian_alerts") {
                            com.example.mistreal_mini.ui.guardian.AlertHistoryScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("business_hub") {
                            com.example.mistreal_mini.ui.business.components.BusinessHubDialog(
                                onDismiss = { navController.popBackStack() },
                                onNavigateToDiscovery = { 
                                    navController.popBackStack()
                                    navController.navigate("business_discovery") 
                                },
                                onNavigateToSellerCommand = {
                                    navController.popBackStack()
                                    navController.navigate("business_seller")
                                },
                                onNavigateToMeetups = {
                                    navController.popBackStack()
                                    navController.navigate("business_meetups")
                                }
                            )
                        }
                        composable("business_meetups") {
                            com.example.mistreal_mini.ui.business.MeetupListScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("business_discovery") {
                            com.example.mistreal_mini.ui.business.DiscoverySearchScreen(
                                onBack = { navController.popBackStack() },
                                onContactBusiness = { business ->
                                    chatViewModel.contactBusiness(business) { success, error ->
                                        if (success) {
                                            navController.navigate("chat")
                                        } else {
                                            Toast.makeText(this@MainActivity, error ?: "Couldn't contact this business.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                onViewBusinessDetail = { business ->
                                    navController.navigate("business_detail/${business.businessId}")
                                }
                            )
                        }
                        composable("business_detail/{businessId}") { backStackEntry ->
                            val businessId = backStackEntry.arguments?.getString("businessId") ?: ""
                            com.example.mistreal_mini.ui.business.BusinessDetailScreen(
                                businessId = businessId,
                                onBack = { navController.popBackStack() },
                                onContactBusiness = { business ->
                                    chatViewModel.contactBusiness(business) { success, error ->
                                        if (success) {
                                            navController.navigate("chat")
                                        } else {
                                            Toast.makeText(this@MainActivity, error ?: "Couldn't contact this business.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                        composable("business_seller") {
                            com.example.mistreal_mini.ui.business.SellerCommandScreen(
                                onBack = { navController.popBackStack() },
                                onUpgradeClick = { navController.navigate("subscription") }
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
