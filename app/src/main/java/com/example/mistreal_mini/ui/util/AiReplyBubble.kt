package com.example.mistreal_mini.ui.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AiReplyBubble(
    isLoading: Boolean,
    replyText: String?,
    onReadAloud: () -> Unit,
    onRecordAgain: () -> Unit = {},
    isAutoReadEnabled: Boolean = false,
    onToggleAutoRead: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.widthIn(max = 300.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 10.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI REPLY", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp).clickable(onClick = onDismiss)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (isLoading || replyText == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing voice & thinking…", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        text = if (isExpanded) replyText else replyText.take(140) + if (replyText.length > 140) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onReadAloud, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.VolumeUp, "Read Aloud", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = onRecordAgain, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Mic, "Record Again", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Auto-Read Toggle Switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto-Read", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Switch(
                                checked = isAutoReadEnabled,
                                onCheckedChange = onToggleAutoRead,
                                modifier = Modifier.scale(0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
