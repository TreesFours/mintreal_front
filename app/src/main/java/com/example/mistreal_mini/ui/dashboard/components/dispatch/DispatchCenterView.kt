package com.example.mistreal_mini.ui.dashboard.components.dispatch

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val scrollState = rememberScrollState()

    val isTwitterSelected = selectedPlatforms.contains("twitter") || selectedPlatforms.contains("x")
    val charLimit = if (isTwitterSelected) 280 else 2000
    val isCharLimitExceeded = content.length > charLimit

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BroadcastOnPersonal,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "TACTICAL BROADCAST CONTROL",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Broadcast across linked channels simultaneously.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Content Input
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Broadcast Content") },
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            placeholder = { Text("Compose intelligence post, tweet, or broadcast update...") },
            shape = RoundedCornerShape(16.dp),
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${content.length}/$charLimit",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCharLimitExceeded) MaterialTheme.colorScheme.error else Color.Gray,
                        fontWeight = if (isCharLimitExceeded) FontWeight.Bold else FontWeight.Normal
                    )
                }
            },
            isError = isCharLimitExceeded
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Broadcast Mode
        Text(
            text = "BROADCAST FORMAT",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = postType == "post",
                onClick = { postType = "post" },
                label = { Text("Standard Post") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = postType == "status",
                onClick = { postType = "status" },
                label = { Text("Status / Story") },
                leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Target Social Channels
        Text(
            text = "TARGET CHANNELS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (connectedSocials.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LinkOff, null, tint = Color.Gray)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "No connected social channels found. Link accounts in Settings to broadcast.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                connectedSocials.forEach { platform ->
                    val isSelected = selectedPlatforms.contains(platform)
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPlatforms = if (isSelected) selectedPlatforms - platform else selectedPlatforms + platform
                        },
                        label = { Text(platform.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (platform) {
                                    "twitter", "x" -> Icons.Default.Public
                                    "whatsapp" -> Icons.Default.Chat
                                    "instagram" -> Icons.Default.CameraAlt
                                    "facebook" -> Icons.Default.Public
                                    "linkedin" -> Icons.Default.Business
                                    else -> Icons.Default.Link
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Action Button
        Button(
            onClick = {
                if (isCharLimitExceeded) {
                    scope.launch { snackbarHostState.showSnackbar("Content exceeds character limit ($charLimit).") }
                } else {
                    scope.launch {
                        isPosting = true
                        var successCount = 0
                        selectedPlatforms.forEach { platform ->
                            val result = onPostToSocial(deviceId, platform, postType, content)
                            if (result) successCount++
                        }
                        isPosting = false
                        snackbarHostState.showSnackbar("Broadcast transmitted to $successCount channels!")
                        if (successCount == selectedPlatforms.size) content = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = content.isNotBlank() && selectedPlatforms.isNotEmpty() && !isPosting && !isCharLimitExceeded,
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
            } else {
                Icon(Icons.Default.Send, null)
                Spacer(modifier = Modifier.width(12.dp))
                Text("TRANSMIT BROADCAST", fontWeight = FontWeight.Black)
            }
        }
    }
}
