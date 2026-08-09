// app/src/main/java/com/example/mistreal/ui/components/SocialPostCard.kt
package com.example.mistreal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal.data.models.SocialPost
import androidx.compose.foundation.text.selection.SelectionContainer

@Composable
fun SocialPostCard(
    post: SocialPost,
    onAskAi: (String) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    
    SelectionContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1E1E2E))
                .padding(12.dp)
        ) {
            // Header: Platform + Author + Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Platform Icon + Name
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(ParseColorHex(post.platformColor)).copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(post.platformIcon, fontSize = 20.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column {
                        Text(
                            post.author,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            post.fetchDisplayName(),
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                Text(
                    post.getRelativeTime(),
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Content
            Text(
                post.content,
                fontSize = 13.sp,
                color = Color.White,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Image (if available)
            if (!post.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Post image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Engagement Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (post.likes != null) {
                    Text("❤️ ${post.likes}", fontSize = 11.sp, color = Color.Gray)
                }
                if (post.comments != null) {
                    Text("💬 ${post.comments}", fontSize = 11.sp, color = Color.Gray)
                }
                if (post.retweets != null) {
                    Text("🔄 ${post.retweets}", fontSize = 11.sp, color = Color.Gray)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Ask AI button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF6B4CFF)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Button(
                        onClick = { onAskAi(post.content) },
                        modifier = Modifier.fillMaxWidth(0.95f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6B4CFF)
                        )
                    ) {
                        Text("Ask AI", fontSize = 12.sp, color = Color.White)
                    }
                }
                
                // Open source link
                if (!post.sourceUrl.isNullOrEmpty()) {
                    IconButton(
                        onClick = { uriHandler.openUri(post.sourceUrl) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Open post", tint = Color.Gray)
                    }
                }
                
                // Like
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = Color.Gray)
                }
            }
        }
    }
}

// Helper to parse hex color strings
private fun ParseColorHex(colorString: String): Long {
    return colorString.removePrefix("#").toLong(16)
}
