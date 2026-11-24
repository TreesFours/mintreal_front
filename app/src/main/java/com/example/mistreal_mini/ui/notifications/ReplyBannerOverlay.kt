package com.example.mistreal_mini.ui.notifications

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.notifications.PendingBanner

/**
 * App-wide heads-up card, rendered as a sibling after the NavHost in
 * MainActivity (same precedent as the Guardian SOS AlertDialog) so it
 * survives navigation to any screen. Deliberately a non-blocking card, not
 * a full-screen Dialog — that severity is reserved for the Guardian alert.
 *
 * AiReply gets a reply row (mic + "Reply"); InboundDm never does, per spec —
 * just read-aloud/play and tap-to-navigate.
 */
@Composable
fun ReplyBannerOverlay(
    pending: PendingBanner?,
    isSpeaking: Boolean,
    onDismiss: () -> Unit,
    onPlayPause: (PendingBanner) -> Unit,
    onTapBody: (PendingBanner) -> Unit,
    onVoiceReply: (PendingBanner.AiReply) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = pending != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val banner = pending ?: return@AnimatedVisibility
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                onClick = { onTapBody(banner) }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (banner) {
                                is PendingBanner.AiReply -> banner.trendTitle ?: banner.partnerName
                                is PendingBanner.InboundDm -> "${banner.senderName} · ${banner.platform}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = when (banner) {
                                is PendingBanner.AiReply -> banner.replyPreview
                                is PendingBanner.InboundDm -> banner.contentPreview
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (banner is PendingBanner.AiReply) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(onClick = { onVoiceReply(banner) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Mic, contentDescription = "Voice reply", modifier = Modifier.size(18.dp))
                                }
                                TextButton(onClick = { onTapBody(banner) }) {
                                    Text("Reply", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    IconButton(onClick = { onPlayPause(banner) }) {
                        Icon(
                            if (isSpeaking) Icons.Default.Pause else Icons.Default.VolumeUp,
                            contentDescription = if (isSpeaking) "Pause" else "Play"
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }
    }
}
