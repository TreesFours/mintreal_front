package com.example.mistreal_mini.ui.dashboard.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.Article
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.model.PlatformUpdate
import com.example.mistreal_mini.ui.chat.components.InteractionMode
import com.example.mistreal_mini.ui.dashboard.DashboardViewModel
import kotlinx.coroutines.launch

@Composable
fun SocialUpdateItem(update: PlatformUpdate, onAiClick: () -> Unit, onReadAloud: (String, InteractionMode) -> Unit) {
    val platformColor = update.platformColor?.let { colorStr ->
        try { Color(android.graphics.Color.parseColor(colorStr)) } catch (e: Exception) { null }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = platformColor?.copy(alpha = 0.1f) ?: MaterialTheme.colorScheme.surfaceVariant)
    ) {
        SelectionContainer {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!update.platformIcon.isNullOrEmpty()) {
                    Text(update.platformIcon, fontSize = 24.sp, modifier = Modifier.size(24.dp))
                } else {
                    Icon(
                        imageVector = when(update.platform.lowercase()) {
                            "twitter" -> Icons.Default.Public
                            "whatsapp" -> Icons.Default.Chat
                            "facebook" -> Icons.Default.Public
                            "instagram" -> Icons.Default.CameraAlt
                            else -> Icons.Default.Notifications
                        },
                        contentDescription = null,
                        tint = platformColor ?: MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(update.platformDisplayName ?: update.platform, fontWeight = FontWeight.Bold)
                    Text("${update.count} updates", style = MaterialTheme.typography.labelSmall)
                    update.recentMessage?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                }
                
                Row {
                    IconButton(onClick = onAiClick) { Icon(Icons.Default.Psychology, "AI", tint = MaterialTheme.colorScheme.primary) }
                    val contentToRead = "Update from ${update.platform}: ${update.recentMessage ?: ""}"
                    Box(modifier = Modifier.size(48.dp).pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onReadAloud(contentToRead, InteractionMode.SINGLE) },
                            onDoubleTap = { onReadAloud(contentToRead, InteractionMode.RADIO) },
                            onLongPress = { onReadAloud(contentToRead, InteractionMode.HANDS_FREE) }
                        )
                    }) {
                        Icon(Icons.Default.VolumeUp, "Read", modifier = Modifier.size(24.dp).align(Alignment.Center), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
