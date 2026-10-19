package com.example.mistreal_mini.ui.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Non-intrusive AI reply surface for the quick voice-ask flow
 * ([QuickVoiceAskDialog]) — deliberately NOT a [androidx.compose.ui.window.Dialog]/
 * scrim, so it never blocks taps on whatever's underneath it. A plain
 * composable, not a `BoxScope` extension — callers position it themselves
 * via [modifier] (e.g. `Modifier.align(Alignment.BottomEnd)` from inside
 * their own enclosing `Box`), same as any other floating element in this
 * codebase (e.g. InteractiveMapView's FABs). Stays until dismissed — no
 * Snackbar-style auto-timeout.
 */
@Composable
fun AiReplyBubble(
    isLoading: Boolean,
    replyText: String?,
    onReadAloud: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.widthIn(max = 280.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 10.dp,
            shadowElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.Close, "Dismiss", modifier = Modifier.size(16.dp).clickable(onClick = onDismiss)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (isLoading || replyText == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thinking…", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        text = if (isExpanded) replyText else replyText.take(140) + if (replyText.length > 140) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { isExpanded = !isExpanded }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onReadAloud, contentPadding = PaddingValues(0.dp)) {
                        Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Read aloud", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
