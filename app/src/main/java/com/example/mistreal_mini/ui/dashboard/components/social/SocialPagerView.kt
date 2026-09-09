package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.SocialPost
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.InteractionMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SocialPagerView(
    posts: List<SocialPost>,
    onPostClick: (SocialPost) -> Unit,
    onAiClick: (SocialPost, String) -> Unit,
    chatViewModel: ChatViewModel,
    isLoading: Boolean = false
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 4.dp)
                Spacer(modifier = Modifier.height(16.dp))
                Text("SCANNING ENCRYPTED FEEDS...", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
        }
    } else if (posts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No social intelligence found in this sector.", color = Color.Gray)
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { posts.size })
        val coroutineScope = rememberCoroutineScope()

        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 16.dp
            ) { page ->
                val post = posts[page]
                StrategicIntelligenceCard(
                    post = post,
                    onAiClick = { onAiClick(post, post.content) },
                    onReadAloud = { text -> chatViewModel.readAloud(text) }
                )
            }

            // Tactical Mini-Map Navigation
            TimelineMiniMap(
                posts = posts,
                currentIndex = pagerState.currentPage,
                onIndexSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
                }
            )
        }
    }
}

@Composable
fun StrategicIntelligenceCard(
    post: SocialPost,
    onAiClick: () -> Unit,
    onReadAloud: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Top Deck: Platform & Timestamp
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(post.platformIcon, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(post.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${post.fetchDisplayName()} • ${post.getRelativeTime()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                IconButton(onClick = onAiClick) {
                    Icon(Icons.Default.Psychology, "AI Analysis", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Body: Content & Media
            Text(post.content, style = MaterialTheme.typography.bodyLarge)

            if (!post.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Deck
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FavoriteBorder, null, modifier = Modifier.size(20.dp))
                        Text(post.likes?.toString() ?: "0", modifier = Modifier.padding(start = 4.dp), fontSize = 12.sp)
                    }
                    if (post.platform.lowercase() == "twitter" || post.platform.lowercase() == "x") {
                        Icon(Icons.Default.Repeat, null, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onReadAloud(post.content) }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.VolumeUp, null)
                    }
                }
                Text("${post.commentsCount ?: 0} COMMENTS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(16.dp))

            // The Trench: Comments Section
            IntelligenceTrench(comments = post.comments ?: emptyList())
        }
    }
}
