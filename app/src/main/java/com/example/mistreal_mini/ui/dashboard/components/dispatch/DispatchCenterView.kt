package com.example.mistreal_mini.ui.dashboard.components.dispatch

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.PlatformIcons
import com.example.mistreal_mini.util.FileUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchCenterView(
    deviceId: String,
    connectedSocials: List<String>,
    // Returns (success, errorMessage) — errorMessage is whatever the backend
    // actually said (e.g. "not linked to this profile", "Connect your social
    // profile first") instead of a generic failure the UI used to show no
    // matter the real cause.
    onPostToSocial: suspend (String, String, String, String, Boolean, String?, String?, Boolean) -> Pair<Boolean, String?>,
    // One Zernio call, one Mistreal post record spanning every selected
    // platform — replaces the old per-platform forEach loop below, which
    // created N disconnected posts with no shared comment thread. Returns
    // (success, unlinkedPlatforms, errorMessage) — unlinkedPlatforms lists
    // any selected platform Zernio reports as not actually connected, so the
    // broadcast result can say so instead of silently posting to fewer than
    // requested; errorMessage is the real backend error on outright failure.
    onPostMultiPlatform: suspend (String, String, List<String>, String?, String?) -> Triple<Boolean, List<String>, String?>,
    // "Your Broadcasts" below the composer, and its comment thread — see
    // FeedViewModel.ownPosts/aggregatedComments. A separate surface from the
    // Zernio-synced feed (that still shows a multi-platform post exploded
    // into one item per platform on the read side — a separate, bigger fix),
    // but it's the one place a user can see their own broadcast as the
    // single post it actually is, with its real merged comments.
    ownPosts: List<com.example.mistreal_mini.data.api.PostDto> = emptyList(),
    onLoadOwnPosts: () -> Unit = {},
    aggregatedComments: List<com.example.mistreal_mini.data.api.AggregatedCommentDto> = emptyList(),
    isLoadingComments: Boolean = false,
    onLoadComments: (String) -> Unit = {},
    onAddComment: suspend (String, String) -> Boolean = { _, _ -> false },
    onAdvertiseClick: () -> Unit = {},
    onOpenNeeds: () -> Unit = {},
    snackbarHostState: SnackbarHostState
) {
    var content by remember { mutableStateOf("") }
    var selectedPlatforms by remember(connectedSocials) { mutableStateOf(connectedSocials.toSet()) }
    var postType by remember { mutableStateOf("post") }
    var mistrealOnly by remember { mutableStateOf(false) }
    // Only meaningful when mistrealOnly is off — that toggle already means
    // "community feed only," this means "the real platforms AND the
    // community feed," not one or the other.
    var shareToCommunity by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    // Zernio's own sendAction only ever accepts one media item per post —
    // performSocialAction (InfoRepository.kt) already reflects that single-
    // item shape, so this only ever holds at most one attachment, by design,
    // not a current limitation of this screen.
    var attachedUri by remember { mutableStateOf<Uri?>(null) }
    var attachedMimeType by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val attachLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            attachedUri = uri
            attachedMimeType = FileUtil.getMimeType(context, uri)
        }
    }

    val isTwitterSelected = selectedPlatforms.contains("twitter") || selectedPlatforms.contains("x")
    val charLimit = if (isTwitterSelected) 280 else 2000
    val isCharLimitExceeded = content.length > charLimit

    var commentsSheetPostId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { onLoadOwnPosts() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Illuminated Billboard Ad Banner (1-tap access to Advertise flow)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onAdvertiseClick() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Campaign, "Billboard", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("📢 ADVERTISE YOUR BUSINESS HERE", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("Launch target ads on Market & Social feeds", fontSize = 9.sp, color = Color.Gray)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Needs was only ever reachable from Settings — no link from Comms,
        // where someone composing a broadcast is already in a "post
        // something" mindset and immediate-need posting fits naturally.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenNeeds() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Handyman, "Needs", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("POST AN IMMEDIATE NEED", fontWeight = FontWeight.Black, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Alerts nearby users with the matching skill set", fontSize = 9.sp, color = Color.Gray)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Compact Content Input (Single-Page Layout)
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            label = { Text("Broadcast Content", fontSize = 11.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            placeholder = { Text("Compose intelligence update or social post...", fontSize = 11.sp) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            supportingText = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "${content.length}/$charLimit",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCharLimitExceeded) MaterialTheme.colorScheme.error else Color.Gray,
                        fontWeight = if (isCharLimitExceeded) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 9.sp
                    )
                }
            },
            isError = isCharLimitExceeded
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Media Attachment
        if (attachedUri == null) {
            OutlinedButton(
                onClick = { attachLauncher.launch("image/*,video/*") },
                modifier = Modifier.height(36.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Attach Media", fontSize = 11.sp)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(48.dp)) {
                    if (attachedMimeType?.startsWith("video") == true) {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Videocam, null, modifier = Modifier.size(16.dp))
                        }
                    } else {
                        AsyncImage(
                            model = attachedUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    IconButton(
                        onClick = { attachedUri = null; attachedMimeType = null },
                        modifier = Modifier.size(18.dp).align(Alignment.TopEnd)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(10.dp))
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Attached", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Broadcast Format Chips
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "FORMAT:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = Color.Gray,
                fontSize = 10.sp
            )
            FilterChip(
                selected = postType == "post",
                onClick = { postType = "post" },
                label = { Text("Standard Post", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, null, modifier = Modifier.size(12.dp)) },
                modifier = Modifier.height(32.dp)
            )
            FilterChip(
                selected = postType == "status",
                onClick = { postType = "status" },
                label = { Text("Status / Story", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(12.dp)) },
                modifier = Modifier.height(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mistreal Only toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Mistreal Only", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Post to Mistreal community feed without cross-posting", fontSize = 9.sp, color = Color.Gray)
                }
                val sfx = com.example.mistreal_mini.ui.util.LocalSfxHapticPlayer.current
                Switch(
                    checked = mistrealOnly,
                    onCheckedChange = { sfx?.playToggle(); mistrealOnly = it },
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Target Social Channels with Glowing Brand Colors
        Text(
            text = "TARGET CHANNELS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
            color = if (mistrealOnly) Color.Gray.copy(alpha = 0.4f) else Color.Gray
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (mistrealOnly) {
            Text("Broadcast won't leave Mistreal.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 10.sp)
        } else if (connectedSocials.isEmpty()) {
            Text("No linked social accounts found. Link in Settings to cross-post.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, fontSize = 10.sp)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                connectedSocials.forEach { platform ->
                    val isSelected = selectedPlatforms.contains(platform)
                    val brandColor = com.example.mistreal_mini.data.model.PlatformBrandColors.forPlatform(platform) ?: MaterialTheme.colorScheme.primary
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedPlatforms = if (isSelected) selectedPlatforms - platform else selectedPlatforms + platform
                        },
                        label = { Text(platform.uppercase(), fontWeight = FontWeight.Bold, fontSize = 10.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = PlatformIcons.forPlatform(platform) ?: Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandColor,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "YOUR BROADCASTS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.Gray,
            fontSize = 10.sp
        )
        Spacer(modifier = Modifier.height(4.dp))

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (ownPosts.isEmpty()) {
                item {
                    Text(
                        "Nothing sent yet — your broadcasts will show up here as one post each, even when they go to multiple platforms.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(ownPosts) { post ->
                    val isFailed = post.status == "failed"
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !isFailed) {
                            commentsSheetPostId = post.id
                            onLoadComments(post.id)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                post.content ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                post.platforms.forEach { platform ->
                                    Icon(
                                        imageVector = PlatformIcons.forPlatform(platform) ?: Icons.Default.Link,
                                        contentDescription = platform,
                                        modifier = Modifier.size(14.dp).padding(end = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (isFailed) {
                                    Text("FAILED", color = MaterialTheme.colorScheme.error, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.Article, "View comments", modifier = Modifier.size(14.dp), tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Transmit Action Button
        Button(
            onClick = {
                if (isCharLimitExceeded) {
                    scope.launch { snackbarHostState.showSnackbar("Content exceeds character limit ($charLimit).") }
                } else if (mistrealOnly) {
                    scope.launch {
                        isPosting = true
                        val mediaBase64 = attachedUri?.let { FileUtil.uriToBase64(context, it) }
                        val (success, errorMessage) = onPostToSocial(deviceId, "mistreal", postType, content, true, mediaBase64, attachedMimeType, false)
                        isPosting = false
                        snackbarHostState.showSnackbar(if (success) "Posted to Mistreal community feed!" else "Failed to post: ${errorMessage ?: "unknown error"}")
                        if (success) { content = ""; attachedUri = null; attachedMimeType = null }
                    }
                } else {
                    scope.launch {
                        isPosting = true
                        val mediaBase64 = attachedUri?.let { FileUtil.uriToBase64(context, it) }
                        val (success, unlinked, errorMessage) = onPostMultiPlatform(deviceId, content, selectedPlatforms.toList(), mediaBase64, attachedMimeType)
                        isPosting = false
                        val reached = selectedPlatforms.size - unlinked.size
                        val message = when {
                            success && unlinked.isEmpty() -> "Broadcast transmitted to $reached channels!"
                            success -> "Broadcast transmitted to $reached channels — ${unlinked.joinToString()} isn't actually connected."
                            else -> "Broadcast failed to transmit: ${errorMessage ?: "unknown error"}"
                        }
                        snackbarHostState.showSnackbar(message)
                        if (success) { content = ""; attachedUri = null; attachedMimeType = null; onLoadOwnPosts() }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = (content.isNotBlank() || attachedUri != null || postType == "status") && (mistrealOnly || selectedPlatforms.isNotEmpty()) && !isPosting && !isCharLimitExceeded,
            shape = RoundedCornerShape(12.dp)
        ) {
            if (isPosting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
            } else {
                Icon(Icons.Default.Send, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("TRANSMIT BROADCAST", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
    }

    val openPostId = commentsSheetPostId
    if (openPostId != null) {
        AggregatedCommentsSheet(
            comments = aggregatedComments,
            isLoading = isLoadingComments,
            onDismiss = { commentsSheetPostId = null },
            onSend = { text -> onAddComment(openPostId, text) }
        )
    }
}

/**
 * Merged-across-platforms comment thread for one broadcast — a new, small
 * composable rather than reusing CommentsSidePanel, since that one's typed
 * against SocialComment (author/text fields, single-platform) while this
 * reads AggregatedCommentDto (senderName/content, tagged per-platform) —
 * different enough to keep separate rather than force a shared shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AggregatedCommentsSheet(
    comments: List<com.example.mistreal_mini.data.api.AggregatedCommentDto>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSend: suspend (String) -> Boolean
) {
    var replyText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 480.dp)) {
            Text("Comments (all platforms)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            } else if (comments.isEmpty()) {
                Text("No comments yet on any connected platform.", color = Color.Gray, fontSize = 12.sp)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(comments) { comment ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = PlatformIcons.forPlatform(comment.platform) ?: Icons.Default.Link,
                                    contentDescription = comment.platform,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(comment.senderName ?: "Someone", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("· ${comment.platform}", color = Color.Gray, fontSize = 10.sp)
                            }
                            Text(comment.content ?: "", fontSize = 13.sp, modifier = Modifier.padding(start = 18.dp))
                        }
                    }
                }
            }

            if (sendError) {
                Text(
                    "Couldn't send that reply — try again.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it; sendError = false },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Reply — sends to the connected platform(s) too", fontSize = 11.sp) },
                    singleLine = true,
                    enabled = !isSending
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = replyText
                        if (text.isNotBlank()) {
                            scope.launch {
                                isSending = true
                                sendError = false
                                if (onSend(text)) replyText = "" else sendError = true
                                isSending = false
                            }
                        }
                    },
                    enabled = !isSending && replyText.isNotBlank()
                ) {
                    if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Send, "Send")
                }
            }
        }
    }
}
