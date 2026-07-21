package com.example.mistreal_mini

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.*
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.ui.chat.ChatScreen
import com.example.mistreal_mini.ui.settings.SettingsScreen
import com.example.mistreal_mini.ui.splash.SplashScreen
import com.example.mistreal_mini.ui.subscription.SubscriptionScreen
import com.example.mistreal_mini.ui.dashboard.DashboardScreen
import com.example.mistreal_mini.ui.onboarding.OnboardingScreen
import com.example.mistreal_mini.util.FaceGuard
import com.example.mistreal_mini.worker.WeatherWorker
import com.example.mistreal_mini.worker.NewsWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.concurrent.TimeUnit

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()
        setupBackgroundWorkers()
        
        setContent {
            MaterialTheme {
                val isOnboarded by preferenceManager.isOnboarded.collectAsStateWithLifecycle(initialValue = null)
                var isAuthenticated by remember { mutableStateOf(false) }
                var showSubscription by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var showDashboard by remember { mutableStateOf(false) }
                var showSplash by remember { mutableStateOf(true) }
                val scope = rememberCoroutineScope()

                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
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
                                // 🔒 SECURITY: Trigger FaceGuard on every launch
                                if (!isAuthenticated) {
                                    LaunchedEffect(Unit) {
                                        faceGuard.authenticateOwner(
                                            activity = this@MainActivity,
                                            onSuccess = { isAuthenticated = true },
                                            onFailure = { /* Handle Failure */ }
                                        )
                                    }
                                    // Show a "Locked" state if desired, or just wait for the popup
                                } else {
                                    if (showSubscription) {
                                        SubscriptionScreen(onDismiss = { showSubscription = false })
                                    } else if (showDashboard) {
                                        DashboardScreen(onBack = { showDashboard = false })
                                    } else if (showSettings) {
                                        SettingsScreen(onBack = { showSettings = false })
                                    } else {
                                        ChatScreen(
                                            onSubscribeClick = { showSubscription = true },
                                            onSettingsClick = { showSettings = true },
                                            onDashboardClick = { showDashboard = true }
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
        
        // Weather Worker
        val weatherRequest = PeriodicWorkRequestBuilder<WeatherWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "WeatherAlerts", 
            ExistingPeriodicWorkPolicy.KEEP, 
            weatherRequest
        )

        // News Worker
        val newsRequest = PeriodicWorkRequestBuilder<NewsWorker>(2, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "NewsAlerts",
            ExistingPeriodicWorkPolicy.KEEP,
            newsRequest
        )
    }
}
