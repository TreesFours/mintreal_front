package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.PlatformCapabilityRegistry
import com.example.mistreal_mini.data.model.SocialPost
import com.example.mistreal_mini.ui.util.FeedVideoPlayer

/**
 * Platform/type-aware replacement for the old StrategicIntelligenceCard. Media
 * posts (story/reel/video) get a full-bleed background with an overlay caption and
 * a floating right-edge action rail; plain text/link posts keep a conventional card
 * body. Comments always render in a translucent panel that slides up over the
 * content rather than pushing/covering it in-line.
 */
@Composable
fun FeedPostCard(
    post: SocialPost,
    isActive: Boolean,
    onAiClick: () -> Unit,
    onReadAloud: (String) -> Unit,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onFollowClick: (currentlyFollowing: Boolean, onResult: (Boolean) -> Unit) -> Unit,
    onSendComment: (String) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capabilities = remember(post.platform) { PlatformCapabilityRegistry.forPlatform(post.platform) }
    var showComments by remember { mutableStateOf(false) }
    var isFollowing by remember(post.id) { mutableStateOf(false) }

    val brandColor = try {
        Color(android.graphics.Color.parseColor(post.platformColor))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    val isMediaPost = post.type.lowercase() in setOf("story", "reel") || !post.videoUrl.isNullOrEmpty() || post.platform == "youtube"

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 16.dp)
            .clip(RoundedCornerShape(24.dp))
    ) {
        val wasFollowing = isFollowing
        val followClickHandler: () -> Unit = {
            isFollowing = !wasFollowing // optimistic flip
            onFollowClick(wasFollowing) { confirmed -> isFollowing = confirmed }
        }

        if (isMediaPost) {
            MediaPostBody(post, isActive, brandColor, capabilities, isFollowing, onFollowClick = followClickHandler)
        } else {
            TextPostBody(post, brandColor, capabilities, isFollowing, onAiClick, onReadAloud, onFollowClick = followClickHandler)
        }

        // Right-edge action rail — common to every post type.
        ActionRail(
            post = post,
            capabilities = capabilities,
            onLikeClick = onLikeClick,
            onCommentClick = { showComments = true },
            onBookmarkClick = onBookmarkClick,
            onShareClick = onShareClick,
            onAiClick = onAiClick,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
        )

        if (capabilities.supportsComments) {
            CommentsSidePanel(
                visible = showComments,
                comments = post.comments ?: emptyList(),
                platformIcon = post.platformIcon,
                platformDisplayName = post.fetchDisplayName(),
                onDismiss = { showComments = false },
                onReply = onSendComment
            )
        }
    }
}

@Composable
private fun BoxScope.MediaPostBody(
    post: SocialPost,
    isActive: Boolean,
    brandColor: Color,
    capabilities: com.example.mistreal_mini.data.model.PlatformCapabilities,
    isFollowing: Boolean,
    onFollowClick: () -> Unit
) {
    if (post.platform == "youtube") {
        var showPlayer by remember(post.id) { mutableStateOf(false) }
        val videoId = post.sourceUrl?.removePrefix("youtube://")
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = post.imageUrl,
                contentDescription = post.content,
                modifier = Modifier.fillMaxSize().clickable { if (!videoId.isNullOrBlank()) showPlayer = true },
                contentScale = ContentScale.Crop
            )
            Icon(
                Icons.Default.PlayCircle, "Play",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(64.dp)
            )
        }
        if (showPlayer && !videoId.isNullOrBlank()) {
            Dialog(onDismissRequest = { showPlayer = false }) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    com.example.mistreal_mini.ui.util.YoutubePlayerWebView(videoId = videoId, modifier = Modifier.fillMaxSize())
                }
            }
        }
    } else if (!post.videoUrl.isNullOrEmpty()) {
        FeedVideoPlayer(
            videoUrl = post.videoUrl,
            isActive = isActive,
            modifier = Modifier.fillMaxSize()
        )
    } else if (!post.imageUrl.isNullOrEmpty()) {
        AsyncImage(
            model = post.imageUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black))
    }

    // Bottom scrim + caption overlay, reel/story style.
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                )
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(post.platformIcon, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(post.author, color = Color.White, fontWeight = FontWeight.Bold)
                if (capabilities.supportsFollow) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isFollowing) "FOLLOWING" else "FOLLOW",
                        color = if (isFollowing) Color.White.copy(alpha = 0.6f) else brandColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.clickable { onFollowClick() }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (post.type.lowercase()) {
                    "story" -> "STORY"
                    "reel" -> "REEL"
                    else -> post.fetchDisplayName()
                },
                color = brandColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black
            )
            if (post.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(post.content, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TextPostBody(
    post: SocialPost,
    brandColor: Color,
    capabilities: com.example.mistreal_mini.data.model.PlatformCapabilities,
    isFollowing: Boolean,
    onAiClick: () -> Unit,
    onReadAloud: (String) -> Unit,
    onFollowClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, brandColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(end = 48.dp) // leave room for the floating action rail
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(brandColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(post.platformIcon, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val label = when (post.type.lowercase()) {
                        "message", "dm" -> "DIRECT SIGNAL"
                        "comment" -> "FEEDBACK LOOP"
                        else -> "INTELLIGENCE REPORT"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.labelSmall, color = brandColor, fontWeight = FontWeight.Black)
                        if (post.isCommunityPost) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "· via Mistreal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.author, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (capabilities.supportsFollow) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isFollowing) "FOLLOWING" else "FOLLOW",
                                color = if (isFollowing) Color.Gray else brandColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.clickable(onClick = onFollowClick)
                            )
                        }
                    }
                    Text("${post.fetchDisplayName()} • ${post.getRelativeTime()}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                IconButton(onClick = onAiClick) {
                    Icon(Icons.Default.Psychology, "AI Analysis", tint = brandColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(post.content, style = MaterialTheme.typography.bodyLarge)

            if (!post.imageUrl.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAiClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = brandColor.copy(alpha = 0.85f))
            ) {
                Icon(Icons.Default.Psychology, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("DRAFT AI RESPONSE", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { onReadAloud(post.content) }) {
                Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("READ ALOUD", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ActionRail(
    post: SocialPost,
    capabilities: com.example.mistreal_mini.data.model.PlatformCapabilities,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onShareClick: () -> Unit,
    onAiClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (capabilities.supportsLike) {
            RailButton(
                icon = if (post.isLikedByUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                tint = if (post.isLikedByUser) Color.Red else Color.White,
                label = (post.likes ?: 0).toString(),
                onClick = onLikeClick
            )
        }
        if (capabilities.supportsComments) {
            RailButton(
                icon = Icons.Default.Comment,
                tint = Color.White,
                label = (post.commentsCount ?: 0).toString(),
                onClick = onCommentClick
            )
        }
        RailButton(
            icon = if (post.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            tint = Color.White,
            label = null,
            onClick = onBookmarkClick
        )
        RailButton(
            icon = Icons.Default.Share,
            tint = Color.White,
            label = null,
            onClick = onShareClick
        )
        RailButton(
            icon = Icons.Default.Psychology,
            tint = Color.White,
            label = null,
            onClick = onAiClick
        )
    }
}

@Composable
private fun RailButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, label: String?, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
        }
        if (label != null) {
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
