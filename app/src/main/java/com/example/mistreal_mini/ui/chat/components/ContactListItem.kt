package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.api.SocialContact

@Composable
fun ContactListItem(contact: SocialContact, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { 
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(contact.name, modifier = Modifier.weight(1f))
                        if (contact.isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Green)
                            )
                        }
                    }
                    contact.lastSeen?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                    }
                }
                if (contact.unreadCount > 0) {
                    Badge { Text(contact.unreadCount.toString()) }
                }
            }
        },
        selected = false,
        onClick = onClick
    )
}
