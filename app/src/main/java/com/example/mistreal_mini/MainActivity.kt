package com.example.mistreal_mini

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val authViewModel: AuthViewModel = hiltViewModel()
                    val chatViewModel: ChatViewModel = hiltViewModel()
                    var isFirebaseAuthenticated by rememberSaveable { mutableStateOf(authViewModel.isUserLoggedIn()) }

                    // 🔗 Deep Link Handler (Reactive to intentState)
                    val dashboardViewModel: com.example.mistreal_mini.ui.dashboard.DashboardViewModel = hiltViewModel()
                    val settingsViewModel: com.example.mistreal_mini.ui.settings.SettingsViewModel = hiltViewModel()

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
                        when {
                            showSubscription -> showSubscription = false
                            showArchive -> showArchive = false
                            showConnections -> showConnections = false
                            showDashboard -> {
                                showDashboard = false
                                backPressedTime = 0L
                            }
                            showSettings -> {
                                showSettings = false
                                backPressedTime = 0L
                            }
                            else -> {
                                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                                    finish() 
                                } else {
                                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                                    backPressedTime = System.currentTimeMillis()
                                }
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
                                    if (showSubscription) {
                                        SubscriptionScreen(onDismiss = { showSubscription = false })
                                    } else if (showArchive) {
                                        RecordsScreen(
                                            onBack = { showArchive = false },
                                            onTrendClick = { title ->
                                                chatViewModel.loadTrend(title)
                                                showArchive = false
                                            },
                                            chatViewModel = chatViewModel
                                        )
                                    } else if (showConnections) {
                                        com.example.mistreal_mini.ui.settings.SocialConnectionsScreen(
                                            onBack = { showConnections = false }
                                        )
                                    } else if (showDashboard) {
                                        DashboardScreen(
                                            onBack = { showDashboard = false },
                                            onDmClick = { showDashboard = false }
                                        )
                                    } else if (showSettings) {
                                        SettingsScreen(
                                            onBack = { showSettings = false },
                                            onUpgradeClick = { 
                                                showSettings = false
                                                showSubscription = true 
                                            },
                                            onConnectionsClick = {
                                                showSettings = false
                                                showConnections = true
                                            }
                                        )
                                    } else {
                                        ChatScreen(
                                            viewModel = chatViewModel,
                                            onSubscribeClick = { showSubscription = true },
                                            onSettingsClick = { showSettings = true },
                                            onDashboardClick = { showDashboard = true },
                                            onArchiveClick = { showArchive = true }
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
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
    }
}
