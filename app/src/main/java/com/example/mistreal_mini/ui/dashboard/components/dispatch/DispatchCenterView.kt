package com.example.mistreal_mini.ui.dashboard.components.dispatch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.PlatformIcons
import com.example.mistreal_mini.util.FileUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchCenterView(
    deviceId: String,
    connectedSocials: List<String>,
    onPostToSocial: suspend (String, String, String, String, Boolean, String?, String?) -> Boolean,
    snackbarHostState: SnackbarHostState
) {
    var content by remember { mutableStateOf("") }
    var selectedPlatforms by remember { mutableStateOf(setOf<String>()) }
    var postType by remember { mutableStateOf("post") }
    var mistrealOnly by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    // Zernio's own sendAction only ever accepts one media item per post —
    // performSocialAction (InfoRepository.kt) already reflects that single-
    // item shape, so this only ever holds at most one attachment, by design,
    // not a current limitation of this screen.
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var attachedMimeType by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachedUri = uri
            attachedMimeType = FileUtil.getMimeType(context, uri)
        }
    }

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

        Spacer(modifier = Modifier.height(12.dp))

        if (attachedUri == null) {
            OutlinedButton(onClick = { attachLauncher.launch("image/*,video/*") }) {
                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Attach Photo or Video")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(64.dp)) {
                    if (attachedMimeType?.startsWith("video") == true) {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Videocam, null)
                        }
                    } else {
                        AsyncImage(
                            model = attachedUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    IconButton(
                        onClick = { attachedUri = null; attachedMimeType = null },
                        modifier = Modifier.size(20.dp).align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Attached — will go out with this broadcast", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }

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

        // Mistreal-only toggle — skips every connected external account
        // entirely and posts straight into the in-app Community Feed only,
        // for whoever would rather not cross-post this one.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mistreal Only", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Post to the Mistreal community feed without cross-posting to any connected account.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                val sfx = com.example.mistreal_mini.ui.util.LocalSfxHapticPlayer.current
                Switch(checked = mistrealOnly, onCheckedChange = { sfx?.playToggle(); mistrealOnly = it })
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Target Social Channels
        Text(
            text = "TARGET CHANNELS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = if (mistrealOnly) Color.Gray.copy(alpha = 0.4f) else Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (mistrealOnly) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Not needed — this broadcast won't leave Mistreal.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        } else if (connectedSocials.isEmpty()) {
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
                                imageVector = PlatformIcons.forPlatform(platform) ?: Icons.Default.Link,
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
                } else if (mistrealOnly) {
                    scope.launch {
                        isPosting = true
                        val mediaBase64 = attachedUri?.let { FileUtil.uriToBase64(context, it) }
                        val result = onPostToSocial(deviceId, "mistreal", postType, content, true, mediaBase64, attachedMimeType)
                        isPosting = false
                        snackbarHostState.showSnackbar(if (result) "Posted to the Mistreal community feed!" else "Failed to post.")
                        if (result) { content = ""; attachedUri = null; attachedMimeType = null }
                    }
                } else {
                    scope.launch {
                        isPosting = true
                        val mediaBase64 = attachedUri?.let { FileUtil.uriToBase64(context, it) }
                        var successCount = 0
                        selectedPlatforms.forEach { platform ->
                            val result = onPostToSocial(deviceId, platform, postType, content, false, mediaBase64, attachedMimeType)
                            if (result) successCount++
                        }
                        isPosting = false
                        snackbarHostState.showSnackbar("Broadcast transmitted to $successCount channels!")
                        if (successCount == selectedPlatforms.size) { content = ""; attachedUri = null; attachedMimeType = null }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = content.isNotBlank() && (mistrealOnly || selectedPlatforms.isNotEmpty()) && !isPosting && !isCharLimitExceeded,
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
