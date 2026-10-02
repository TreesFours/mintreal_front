package com.example.mistreal_mini.ui.dashboard.components.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.data.model.SocialComment

/**
 * Translucent comment panel that slides up over the still-playing media instead of
 * replacing it — the post's video/image keeps running underneath. Reuses
 * [IntelligenceTrench]'s existing comment-tree renderer unchanged; this file only
 * supplies the overlay chrome (slide animation, scrim, reply box) around it.
 */
@Composable
fun BoxScope.CommentsSidePanel(
    visible: Boolean,
    comments: List<SocialComment>,
    onDismiss: () -> Unit,
    onReply: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .fillMaxHeight(0.55f)
    ) {
        var replyText by remember { mutableStateOf("") }
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    IntelligenceTrench(comments = comments)
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
