package com.example.mistreal_mini

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.toMutableStateList
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.ui.chat.ChatScreen
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.settings.SettingsScreen
import com.example.mistreal_mini.ui.splash.SplashScreen
import com.example.mistreal_mini.ui.subscription.SubscriptionScreen
import com.example.mistreal_mini.ui.dashboard.DashboardScreen
import com.example.mistreal_mini.ui.onboarding.OnboardingScreen
import com.example.mistreal_mini.ui.auth.AuthScreen
import com.example.mistreal_mini.ui.auth.AuthViewModel
import com.example.mistreal_mini.ui.records.RecordsScreen
import com.example.mistreal_mini.util.FaceGuard
import com.example.mistreal_mini.worker.WeatherWorker
import com.example.mistreal_mini.worker.NewsWorker
import com.example.mistreal_mini.worker.HistoryWorker
import com.example.mistreal_mini.data.worker.CelestialWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
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
            MaterialTheme {
                val intentState by _intentState
                val isOnboarded by preferenceManager.isOnboarded.collectAsStateWithLifecycle(initialValue = null)
                val scope = rememberCoroutineScope()
                
                // 🛡️ Ultimate Rotation & Navigation Patch
                var isAuthenticated by rememberSaveable { mutableStateOf(false) }
                var showSubscription by rememberSaveable { mutableStateOf(false) }
                var showSettings by rememberSaveable { mutableStateOf(false) }
                var showDashboard by rememberSaveable { mutableStateOf(false) }
                var showArchive by rememberSaveable { mutableStateOf(false) }
                var showConnections by rememberSaveable { mutableStateOf(false) }
                var showSplash by rememberSaveable { mutableStateOf(true) }
                
                // 🖱️ Double-Back-to-Exit logic (SURVIVES ROTATION)
                var backPressedTime by rememberSaveable { mutableLongStateOf(0L) }

                // 🧭 Navigation Stack (Survives Rotation)
                val navStack = rememberSaveable(
                    saver = listSaver(
                        save = { it.toList() },
                        restore = { it.toMutableStateList() }
                    )
                ) { mutableStateListOf("chat") }

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    var isFirebaseAuthenticated by rememberSaveable { mutableStateOf(authViewModel.isUserLoggedIn()) }

                    // 🔗 Deep Link Handler (Reactive to intentState)
                    val dashboardViewModel: com.example.mistreal_mini.ui.dashboard.DashboardViewModel = hiltViewModel()
                    val settingsViewModel: com.example.mistreal_mini.ui.settings.SettingsViewModel = hiltViewModel()

                    fun navigateTo(screen: String) {
                        if (navStack.lastOrNull() != screen) {
                            navStack.add(screen)
                        }
                    }

                    fun navigateBack() {
                        if (navStack.size > 1) {
                            navStack.removeAt(navStack.size - 1)
                        }
                    }

                    LaunchedEffect(intentState) {
                        intentState?.data?.let { uri ->
                            if (uri.scheme == "mistreal" && uri.host == "social-connected") {
                                val platform = uri.getQueryParameter("platform") ?: "platform"
                                val success = uri.getQueryParameter("success") == "true"
                                val deviceId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                                
                                if (success) {
                                    Toast.makeText(this@MainActivity, "✅ $platform linked successfully!", Toast.LENGTH_LONG).show()
                                    // 🚀 Instant Handshake & Data Hydration
                                    settingsViewModel.onSocialConnectionResult(platform, true)
                                    dashboardViewModel.loadDashboardData(deviceId)
                                    chatViewModel.refreshSocialContacts() 
                                } else {
                                    val error = uri.getQueryParameter("error") ?: "Connection failed"
                                    Toast.makeText(this@MainActivity, "❌ Error: $error", Toast.LENGTH_LONG).show()
                                    settingsViewModel.onSocialConnectionResult(platform, false)
                                }
                            }
                        }
                    }

                    // 🛡️ Back Navigation Handler (Survives Rotation & Clears State)
                    BackHandler(enabled = true) {
                        if (navStack.size > 1) {
                            navigateBack()
                            backPressedTime = 0L
                        } else {
                            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                                finish() 
                            } else {
                                Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                                backPressedTime = System.currentTimeMillis()
                            }
                        }
                    }

                    if (showSplash) {
                        SplashScreen(onTimeout = { showSplash = false })
                    } else {
                        when (isOnboarded) {
                            null -> { /* Loading */ }
                            false -> {
                                OnboardingScreen(
                                    faceGuard = faceGuard,
                                    onComplete = { scope.launch { preferenceManager.setOnboarded(true) } }
                                )
                            }
                            true -> {
                                if (!isFirebaseAuthenticated) {
                                    AuthScreen(onAuthSuccess = { isFirebaseAuthenticated = true })
                                } else if (!isAuthenticated) {
                                    LaunchedEffect(Unit) {
                                        faceGuard.authenticateOwner(
                                            activity = this@MainActivity,
                                            onSuccess = { isAuthenticated = true },
                                            onFailure = { /* Handle Failure */ }
                                        )
                                    }
                                } else {
                                    val currentScreen = navStack.last()
                                    when (currentScreen) {
                                        "subscription" -> {
                                            SubscriptionScreen(onDismiss = { navigateBack() })
                                        }
                                        "archive" -> {
                                            RecordsScreen(
                                                onBack = { navigateBack() },
                                                onTrendClick = { title ->
                                                    chatViewModel.loadTrend(title)
                                                    navigateBack()
                                                },
                                                chatViewModel = chatViewModel
                                            )
                                        }
                                        "connections" -> {
                                            com.example.mistreal_mini.ui.settings.SocialConnectionsScreen(
                                                onBack = { navigateBack() }
                                            )
                                        }
                                        "dashboard" -> {
                                            DashboardScreen(
                                                onBack = { navigateBack() },
                                                onDmClick = { navigateBack() }
                                            )
                                        }
                                        "settings" -> {
                                            SettingsScreen(
                                                onBack = { navigateBack() },
                                                onUpgradeClick = { 
                                                    navigateTo("subscription")
                                                },
                                                onConnectionsClick = {
                                                    navigateTo("connections")
                                                }
                                            )
                                        }
                                        else -> {
                                            ChatScreen(
                                                viewModel = chatViewModel,
                                                onSubscribeClick = { navigateTo("subscription") },
                                                onSettingsClick = { navigateTo("settings") },
                                                onDashboardClick = { navigateTo("dashboard") },
                                                onArchiveClick = { navigateTo("archive") }
                                            )
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
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
