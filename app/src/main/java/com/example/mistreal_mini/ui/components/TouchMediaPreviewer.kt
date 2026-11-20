package com.example.mistreal_mini.ui.components

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.mistreal_mini.ui.util.VideoPlayer

/**
 * Touch-Sensitive Floating Media Previewer Overlay
 * Features a full-screen image/video player with floating action controls (Like, Comments, Captions)
 * and touch-sensitive tap-to-hide gesture sensitivity.
 */
@Composable
fun TouchMediaPreviewer(
    uri: Uri,
    isVideo: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onLikeClick: () -> Unit = {},
    onCommentClick: () -> Unit = {},
    isLiked: Boolean = false,
    likeCount: Int = 0
) {
    var showControls by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { showControls = !showControls })
                },
            color = Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Media Layer
                if (isVideo) {
                    VideoPlayer(
                        videoUrl = uri.toString(),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = uri,
                        contentDescription = "Full Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Touch-Sensitive Floating Controls Overlay
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top Bar Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .align(Alignment.TopStart),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ThinkingTreeIcon(size = 18.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("MEDIA PREVIEW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, "Close Preview", tint = Color.White)
                            }
                        }

                        // Right Floating Actions (Like / Comment)
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Like Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onLikeClick,
                                    modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(
                                        Icons.Default.Favorite,
                                        "Like",
                                        tint = if (isLiked) Color.Red else Color.White
                                    )
                                }
                                if (likeCount > 0) {
                                    Text(likeCount.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Comment Button
                            IconButton(
                                onClick = onCommentClick,
                                modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Comment, "Comments", tint = Color.White)
                            }
                        }

                        // Bottom Caption Overlay
                        if (!caption.isNullOrBlank()) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = caption,
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
