package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.model.SocialComment
import kotlinx.coroutines.launch

// Two drag anchors — resting height and how far a pull-up can expand it, as a
// fraction of the post's own height. Collapsed still leaves the top of the
// video/image visible; expanded covers nearly all of it per the "scrollable
// to the top" request, while staying short of 1f so a sliver of media (and
// the sense that it's still playing behind the sheet) always stays visible.
private const val COLLAPSED_FRACTION = 0.55f
private const val EXPANDED_FRACTION = 0.92f

/**
 * Translucent comment panel that slides up over the still-playing media instead of
 * replacing it — the post's video/image keeps running underneath. Reuses
 * [IntelligenceTrench]'s existing comment-tree renderer unchanged; this file only
 * supplies the overlay chrome (slide animation, scrim, reply box) around it.
 *
 * The panel itself is a drag handle: starts at [COLLAPSED_FRACTION] of the
 * post's height and can be pulled up to [EXPANDED_FRACTION], snapping to
 * whichever anchor is closer on release — rather than a fixed-height sheet.
 */
@Composable
fun BoxScope.CommentsSidePanel(
    visible: Boolean,
    comments: List<SocialComment>,
    platformIcon: String,
    platformDisplayName: String,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val totalHeightPx = with(density) { maxHeight.toPx() }
            val collapsedPx = totalHeightPx * COLLAPSED_FRACTION
            val expandedPx = totalHeightPx * EXPANDED_FRACTION

            val heightPx = remember { Animatable(collapsedPx) }
            val coroutineScope = rememberCoroutineScope()
            // Reset to the resting anchor each time the panel is reopened.
            LaunchedEffect(visible) {
                if (visible) heightPx.snapTo(collapsedPx)
            }

            var replyText by remember { mutableStateOf("") }
            val scrollState = rememberScrollState()
            val panelHeight = with(density) { heightPx.value.toDp() }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(panelHeight)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(totalHeightPx) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val nearestAnchor = if (heightPx.value > (collapsedPx + expandedPx) / 2) expandedPx else collapsedPx
                                    coroutineScope.launch { heightPx.animateTo(nearestAnchor, tween(200)) }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (heightPx.value - dragAmount).coerceIn(collapsedPx, expandedPx)
                                    coroutineScope.launch { heightPx.snapTo(next) }
                                }
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${comments.size} COMMENTS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close comments", tint = Color.White)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    if (comments.isEmpty()) {
                        Text(
                            "No comments yet.",
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    } else {
                        IntelligenceTrench(comments = comments, platformIcon = platformIcon, platformDisplayName = platformDisplayName)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Add a comment…") },
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedTextColor = Color.White,
                            focusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                onReply(replyText)
                                replyText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, "Send reply", tint = Color.White)
                    }
                }
            }
        }
    }
}
