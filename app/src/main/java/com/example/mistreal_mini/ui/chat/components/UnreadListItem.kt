package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.api.UnreadItem

@Composable
fun UnreadListItem(item: UnreadItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(item.platform) {
                        "whatsapp" -> Icons.Default.Chat
                        "twitter" -> Icons.Default.Public
                        "instagram" -> Icons.Default.CameraAlt
                        "facebook" -> Icons.Default.Public
                        else -> Icons.Default.Psychology
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(item.sender, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (item.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.text, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (item.lastSeen != null) {
                Text(item.lastSeen, style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
    }
}
