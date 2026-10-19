package com.example.mistreal_mini.ui.watch

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.webrtc.nearbyRuntimePermissions
import com.example.mistreal_mini.monitor.PressureLevel
import com.example.mistreal_mini.service.WatchHostService

/**
 * Host side of the Watch Hub — requests the system's screen-capture
 * consent dialog, then hands the resulting resultCode/data to
 * WatchHostService, which does the actual MediaProjection capture and
 * per-viewer WebRTC negotiation. This screen itself holds no WebRTC state;
 * it just starts/stops the service and mirrors its live share code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchHostScreen(
    onBack: () -> Unit,
    contextType: String = "standalone",
    contextId: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val sessionId by WatchHostService.sessionIdState.collectAsState()
    val viewerCount by WatchHostService.viewerCountState.collectAsState()
    val pressure by WatchHostService.pressureState.collectAsState()
    var isRequesting by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isRequesting = false
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val intent = Intent(context, WatchHostService::class.java).apply {
                action = WatchHostService.ACTION_START
                putExtra(WatchHostService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(WatchHostService.EXTRA_RESULT_DATA, data)
                putExtra(WatchHostService.EXTRA_CONTEXT_TYPE, contextType)
                if (contextId != null) putExtra(WatchHostService.EXTRA_CONTEXT_ID, contextId)
            }
            context.startForegroundService(intent)
        }
    }

    // Requested right before the screen-capture consent dialog — denial
    // here just means Nearby (Bluetooth/Wi-Fi-Direct) viewers won't be able
    // to find this host; the internet route is unaffected either way.
    val nearbyPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share your screen") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (sessionId == null) {
                Text(
                    "Share your screen live with other Mistreal users — over the internet, or directly via Bluetooth/Wi-Fi if they're nearby with no internet. You can keep using your phone while sharing continues in the background.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        isRequesting = true
                        val nearbyPermissions = nearbyRuntimePermissions()
                        if (nearbyPermissions.isNotEmpty()) nearbyPermissionLauncher.launch(nearbyPermissions)
                        val manager = context.getSystemService(MediaProjectionManager::class.java)
                        launcher.launch(manager.createScreenCaptureIntent())
                    },
                    enabled = !isRequesting
                ) { Text(if (isRequesting) "Starting..." else "Start Sharing") }
            } else {
                Text("Live — share this code with other Mistreal users:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(sessionId.orEmpty(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("$viewerCount viewer${if (viewerCount == 1) "" else "s"} watching", style = MaterialTheme.typography.bodySmall)

                if (pressure != PressureLevel.NONE) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val (bg, message) = when (pressure) {
                        PressureLevel.SEVERE -> MaterialTheme.colorScheme.errorContainer to
                            "Device under heavy pressure — quality reduced to minimum and new viewers may be turned away to protect the stream."
                        else -> MaterialTheme.colorScheme.tertiaryContainer to
                            "Battery or thermal pressure detected — video quality has been reduced automatically."
                    }
                    Surface(color = bg, shape = MaterialTheme.shapes.medium) {
                        Text(
                            message,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                val watchUrl = "https://mistreal-backend.onrender.com/watch/${sessionId}"
                Text(
                    "Casting to a computer, smart TV, or anything else with a browser? Open this link there instead — no app needed:",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(watchUrl, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = {
                        clipboardManager.setText(AnnotatedString(watchUrl))
                    }) { Text("Copy Link") }
                    OutlinedButton(onClick = {
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, watchUrl)
                        }, "Send watch link"))
                    }) { Text("Send Link") }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    context.startService(Intent(context, WatchHostService::class.java).apply {
                        action = WatchHostService.ACTION_STOP
                    })
                }) { Text("Stop Sharing") }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "You can leave this screen and keep using the app — sharing continues in the background until you stop it.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
