package com.example.mistreal_mini.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.SocialPost
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.InteractionMode

@Composable
fun SocialIntelView(
    posts: List<SocialPost>,
    onPostClick: (SocialPost) -> Unit,
    onAiClick: (SocialPost, String) -> Unit,
    chatViewModel: ChatViewModel
) {
    if (posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No social intelligence found in this sector.", color = Color.Gray)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(posts) { post ->
                SocialPostItem(
                    post = post,
                    onAiClick = { onAiClick(post, post.content) },
                    onReadAloud = { text, _ -> chatViewModel.readAloud(text) },
                    onPostClick = { onPostClick(post) },
                    onShare = {},
                    onLike = {}
                )
            }
        }
    }
}

@Composable
fun SocialPostItem(
    post: SocialPost,
    onAiClick: () -> Unit,
    onReadAloud: (String, InteractionMode) -> Unit,
    onPostClick: () -> Unit,
    onShare: () -> Unit,
    onLike: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(post.platformIcon, fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.author, fontWeight = FontWeight.Bold)
                    Text("${post.fetchDisplayName()} • ${post.getRelativeTime()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                IconButton(onClick = onAiClick) {
                    Icon(Icons.Default.Psychology, "AI", tint = MaterialTheme.colorScheme.primary)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            SelectionContainer {
                Text(post.content, style = MaterialTheme.typography.bodyMedium)
            }

            if (!post.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    IconButton(onClick = onLike) { Icon(Icons.Default.FavoriteBorder, null) }
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, null) }
                }
                IconButton(onClick = { onReadAloud(post.content, InteractionMode.SINGLE) }) {
                    Icon(Icons.Default.VolumeUp, null)
                }
            }
        }
    }
}
