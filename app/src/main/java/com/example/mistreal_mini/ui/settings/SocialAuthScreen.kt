package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.api.SocialPlatformResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialAuthScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val platforms by viewModel.availablePlatforms.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoadingPlatforms.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchPlatforms()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect Socials") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (platforms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Failed to load platforms", color = Color.Gray)
                    Button(onClick = { viewModel.fetchPlatforms() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Try Again")
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text(
                    "Select a platform to link with Mistreal AI.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(platforms) { platform ->
                        PlatformCard(
                            platform = platform,
                            onClick = {
                                if (platform.isConnected) {
                                    // Maybe show "Already Connected" or disconnect option?
                                } else if (platform.isProOnly) {
                                    onUpgradeClick()
                                } else {
                                    viewModel.connectSocial(platform.id)
                                }
                            }
                        )
                    }
                }
                
                if (platforms.any { it.isConnected }) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Connected Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    platforms.filter { it.isConnected }.forEach { platform ->
                        ListItem(
                            headlineContent = { Text(platform.name) },
                            leadingContent = { Text(platform.icon, fontSize = 20.sp) },
                            trailingContent = { 
                                Text("Connected", color = Color(0xFF81C784), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformCard(platform: SocialPlatformResponse, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                platform.isConnected -> Color(0xFF81C784).copy(alpha = 0.1f)
                platform.isProOnly -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = if (platform.isConnected) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF81C784)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (platform.icon.length <= 2) { // Likely an emoji or short symbol
                    Text(platform.icon, fontSize = 32.sp)
                } else {
                    Icon(
                        imageVector = when(platform.id.lowercase()) {
                            "twitter", "x" -> Icons.Default.Public
                            "whatsapp" -> Icons.Default.Chat
                            "instagram" -> Icons.Default.CameraAlt
                            "linkedin" -> Icons.Default.Work
                            "facebook" -> Icons.Default.Facebook
                            else -> Icons.Default.Link
                        },
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = when {
                            platform.isConnected -> Color(0xFF81C784)
                            platform.isProOnly -> Color.Gray
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(platform.name, fontWeight = FontWeight.Bold)
                if (platform.isConnected) {
                    Text("CONNECTED", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF81C784))
                }
            }
            
            if (platform.isProOnly && !platform.isConnected) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "PRO Only",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                    tint = Color.Gray
                )
            }
            
            if (platform.isConnected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Connected",
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
                    tint = Color(0xFF81C784)
                )
            }
        }
    }
}
