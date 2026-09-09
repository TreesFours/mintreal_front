package com.example.mistreal_mini.ui.dashboard.components.dispatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.model.PlatformUpdate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchCenterView(
    deviceId: String,
    socialUpdates: List<PlatformUpdate>,
    onPostToSocial: suspend (String, String, String, String) -> Boolean,
    snackbarHostState: SnackbarHostState
) {
    var content by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf(setOf<String>()) }
    var postType by remember { mutableStateOf("post") }
    val connectedSocials = socialUpdates.map { it.platform.lowercase() }
    var isPosting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Compose Dispatch", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("What's happening?") },
            modifier = Modifier.fillMaxWidth().height(150.dp),
            placeholder = { Text("Type your tweet, status update, or story caption...") }
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dispatch Type:", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = postType == "post",
                onClick = { postType = "post" },
                label = { Text("Standard Post") }
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilterChip(
                selected = postType == "status",
                onClick = { postType = "status" },
                label = { Text("Status / Story") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Target Socials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        
        if (connectedSocials.isEmpty()) {
            Text("No connected socials found. Connect accounts in Settings.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                connectedSocials.forEach { platform ->
                    FilterChip(
                        selected = selectedPlatforms.contains(platform),
                        onClick = {
                            selectedPlatforms = if (selectedPlatforms.contains(platform)) {
                                selectedPlatforms - platform
                            } else {
                                selectedPlatforms + platform
                            }
                        },
                        label = { Text(platform.uppercase()) },
                        leadingIcon = {
                             Icon(
                                imageVector = when(platform) {
                                    "twitter", "x" -> Icons.Default.Public
                                    "whatsapp" -> Icons.Default.Chat
                                    "instagram" -> Icons.Default.CameraAlt
                                    else -> Icons.Default.Public
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                var error = ""
                val isTwitter = selectedPlatforms.contains("twitter") || selectedPlatforms.contains("x")
                if (isTwitter && content.length > 280) {
                    error = "Content exceeds Twitter character limit (280)."
                }
                
                if (error.isNotEmpty()) {
                    scope.launch { snackbarHostState.showSnackbar(error) }
                } else {
                    scope.launch {
                        isPosting = true
                        var successCount = 0
                        selectedPlatforms.forEach { platform ->
                            val result = onPostToSocial(deviceId, platform, postType, content)
                            if (result) successCount++
                        }
                        isPosting = false
                        snackbarHostState.showSnackbar("Dispatched to $successCount platforms!")
                        if (successCount == selectedPlatforms.size) content = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = content.isNotBlank() && selectedPlatforms.isNotEmpty() && !isPosting,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.Send, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Transmit Dispatch")
            }
        }
    }
}
