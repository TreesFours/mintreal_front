package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialConnectionsScreen(
    onBack: () -> Unit,
    settingsViewModel: com.example.mistreal_mini.ui.settings.SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val platforms by settingsViewModel.availablePlatforms.collectAsStateWithLifecycle()
    val isLoading by settingsViewModel.isLoadingPlatforms.collectAsStateWithLifecycle()
    val isConnecting by settingsViewModel.isConnectingSocial.collectAsStateWithLifecycle()
    var showLimitDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        settingsViewModel.fetchPlatforms()
    }

    LaunchedEffect(Unit) {
        settingsViewModel.socialConnectUrl.collect { url ->
            if (url != null && url.isNotBlank()) {
                timber.log.Timber.d("🔗 Launching Zernio Connect: $url")
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                settingsViewModel.clearSocialConnectUrl()
            }
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.errorEvent.collect { error ->
            if (error == "PLATFORM_LIMIT_REACHED") {
                showLimitDialog = true
            } else {
                android.widget.Toast.makeText(context, "❌ Error: $error", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showLimitDialog) {
        AlertDialog(
            onDismissRequest = { showLimitDialog = false },
            title = { Text("Connection Limit Reached") },
            text = { Text("Your current tier has reached its maximum number of social connections. Upgrade to connect more platforms.") },
            confirmButton = {
                Button(onClick = { 
                    showLimitDialog = false
                    // Navigate to subscription or handle upgrade
                }) {
                    Text("UPGRADE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Link Social Accounts") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { settingsViewModel.fetchPlatforms() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading && platforms.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "SELECT A PLATFORM TO LINK", 
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            fontWeight = FontWeight.Black
                        )
                    }
                    
                    items(platforms) { platform ->
                        ListItem(
                            headlineContent = { Text(platform.name, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(if (platform.isConnected) "Connected" else "Not Linked", fontSize = 12.sp) },
                            leadingContent = { 
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(platform.icon, fontSize = 20.sp) 
                                }
                            },
                            trailingContent = {
                                if (platform.isConnected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(
                                            onClick = { settingsViewModel.disconnectSocial(platform.id) }
                                        ) {
                                            Text("UNLINK", fontSize = 10.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { settingsViewModel.initiateSocialConnection(platform.id) },
                                        shape = RoundedCornerShape(8.dp),
                                        enabled = !isConnecting
                                    ) {
                                        Text("LINK", fontSize = 10.sp)
                                    }
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.2f))
                    }

                    if (platforms.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No platforms available.", color = Color.Gray)
                            }
                        }
                    }
                }
            }

            if (isConnecting) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.3f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Initializing connection...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
