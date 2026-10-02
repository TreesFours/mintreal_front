package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.model.SocialPost
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.dashboard.FeedViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SocialPagerView(
    posts: List<SocialPost>,
    onPostClick: (SocialPost) -> Unit,
    onAiClick: (SocialPost, String) -> Unit,
    chatViewModel: ChatViewModel,
    feedViewModel: FeedViewModel,
    deviceId: String,
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
                FeedPostCard(
                    post = post,
                    isActive = pagerState.currentPage == page,
                    onAiClick = { onAiClick(post, post.content) },
                    onReadAloud = { text -> chatViewModel.readAloud(text) },
                    onLikeClick = { feedViewModel.toggleLike(deviceId, post) },
                    onBookmarkClick = { feedViewModel.toggleBookmark(post) },
                    onFollowClick = { currentlyFollowing, onResult ->
                        feedViewModel.toggleFollow(deviceId, post.platform, post.author, currentlyFollowing, onResult)
                    },
                    onSendComment = { text ->
                        coroutineScope.launch { feedViewModel.postToSocial(deviceId, post.platform, "comment", text, post.id) }
                    },
                    onShareClick = { /* share intent dispatched by caller via onPostClick hook if needed */ onPostClick(post) }
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
