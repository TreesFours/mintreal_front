package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.util.LinkableText
import com.example.mistreal_mini.ui.util.VideoPlayer
import com.example.mistreal_mini.util.NoteExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun ChatBubble(
    message: ChatMessage,
    viewModel: ChatViewModel,
    onAiInsight: (String) -> Unit,
    onReadAloud: (String, InteractionMode) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope,
    targetLang: String = "English"
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    var isEditing by remember { mutableStateOf(false) }
    var editContent by remember { mutableStateOf(message.content) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = align
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            SelectionContainer {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.provider?.contains("face-swap") == true && (message.type == "image" || message.type == "video")) {
                        Surface(
                            color = Color(0xFFB8860B).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                "AI FACE-EDITED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                color = Color(0xFFB8860B)
                            )
                        }
                    }
                    if (message.type == "video" || message.attachmentUrl?.endsWith(".mp4") == true) {
                        message.attachmentUrl?.let { url ->
                            VideoPlayer(
                                videoUrl = url,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (message.type == "audio") {
                        val audioUri = message.attachmentPaths?.firstOrNull() ?: message.attachmentUrl
                        audioUri?.let { uri ->
                            VoiceNoteBubble(
                                uri = uri,
                                isUser = isUser,
                                textColor = textColor,
                                autoplayUri = viewModel.pendingVoiceNoteAutoplayUri.value,
                                onConsumeAutoplay = { viewModel.consumeVoiceNoteAutoplay(it) },
                                onPlaybackFinished = { viewModel.onVoiceNotePlaybackFinished() }
                            )
                        }
                    }

                    if (message.type == "image") {
                        val attachments = mutableListOf<String>()
                        message.attachmentPaths?.let { attachments.addAll(it) }
                        message.attachmentUrl?.let { attachments.add(it) }
                        
                        if (attachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                attachments.forEach { path ->
                                    AsyncImage(
                                        model = path,
                                        contentDescription = "Image attachment",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    if (isEditing) {
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            trailingIcon = {
                                IconButton(onClick = {
                                    viewModel.updateNote(message, editContent)
                                    isEditing = false
                                }) {
                                    Icon(Icons.Default.Check, null, tint = textColor)
                                }
                            }
                        )
                    } else if (message.content.isNotEmpty()) {
                        val displayContent = message.content.replace(Regex("\\[FILE_REQUEST:.*?\\]"), "📄 Secure File generated and encrypted.")
                        
                        // 🧠 TRUE FEELINGS SUMMARY
                        if (message.trueFeelings != null) {
                            var showFeelings by remember { mutableStateOf(false) }
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Favorite, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("AI SUBJECTIVE STATE DETECTED", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary, fontSize = 8.sp)
                                    }
                                    
                                    if (showFeelings) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = message.trueFeelings,
                                            style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                            color = textColor.copy(alpha = 0.9f)
                                        )
                                        TextButton(onClick = { showFeelings = false }, modifier = Modifier.height(24.dp).align(Alignment.End)) {
                                            Text("HIDE", fontSize = 8.sp, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    } else {
                                        TextButton(onClick = { showFeelings = true }, modifier = Modifier.height(24.dp)) {
                                            Text("VIEW TRUE FEELINGS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                                        }
                                    }
                                }
                            }
                        }

                        if (displayContent.length > 800) {
                            var isExpanded by remember { mutableStateOf(false) }
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = textColor.copy(alpha = 0.1f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.2f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Description, null, tint = textColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("HIGH DENSITY INTEL (${displayContent.length} chars)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = textColor)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isExpanded) displayContent else displayContent.take(300) + "...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColor
                                    )
                                    TextButton(onClick = { isExpanded = !isExpanded }) {
                                        Text(if (isExpanded) "COLLAPSE" else "DECRYPT & VIEW FULL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
                                    }
                                }
                            }
                        } else {
                            LinkableText(
                                text = displayContent,
                                textColor = textColor
                            )
                        }
                    }

                    if (message.type == "social_draft") {
                        var shareToCommunity by remember(message.id) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.padding(top = 8.dp).clickable { shareToCommunity = !shareToCommunity },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = shareToCommunity, onCheckedChange = { shareToCommunity = it })
                            Text("Also share to Mistreal community feed", fontSize = 10.sp, color = textColor)
                        }
                        Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.approveSocialAction(message, shareToCommunity) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Execute", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.discardSocialAction(message) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Discard", fontSize = 10.sp)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isUser && message.mood != null) {
                            MoodBadge(mood = message.mood)
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        var showMenu by remember { mutableStateOf(false) }

                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, "More", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                        
                        var showSendToContactDialog by remember { mutableStateOf(false) }

                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            if (message.type == "image" || message.type == "video") {
                                val imageUri = message.attachmentPaths?.firstOrNull() ?: message.attachmentUrl
                                if (imageUri != null) {
                                    DropdownMenuItem(
                                        text = { Text("Send to Contact") },
                                        onClick = {
                                            showSendToContactDialog = true
                                            showMenu = false
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, null) }
                                    )
                                }
                            }
                            if (!isUser) {
                                val context = LocalContext.current
                                DropdownMenuItem(
                                    text = { Text("Save Note") },
                                    onClick = {
                                        viewModel.saveAsNote(message.content)
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Intel synchronized to Scribe Notes.") }
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Archive") },
                                    onClick = {
                                        NoteExporter.saveAsTxt(context, message.content)
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Intel archived to storage.") }
                                        showMenu = false
                                    },
                                    leadingIcon = { Icon(Icons.Default.Archive, null) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = {
                                    isEditing = true
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                onClick = {
                                    viewModel.deleteMessage(message)
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            )
                        }

                        if (showSendToContactDialog) {
                            val imageUri = message.attachmentPaths?.firstOrNull() ?: message.attachmentUrl
                            if (imageUri != null) {
                                SendToContactDialog(
                                    availablePlatforms = viewModel.availablePlatforms,
                                    isSending = viewModel.isSendingToContact.value,
                                    onDismiss = { showSendToContactDialog = false },
                                    onSend = { platform, targetId, caption ->
                                        viewModel.sendImageToContact(
                                            imageUri = android.net.Uri.parse(imageUri),
                                            platform = platform,
                                            targetId = targetId,
                                            caption = caption
                                        ) { success -> if (success) showSendToContactDialog = false }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { onAiInsight(message.content) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, "Insight", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        val isReadingThis = viewModel.currentlyReadingContent.value == message.content
                        IconButton(
                            onClick = { onReadAloud(message.content, InteractionMode.SINGLE) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (isReadingThis) Icons.Default.VolumeUp else Icons.Default.Hearing,
                                if (isReadingThis) "Stop reading" else "Read aloud",
                                modifier = Modifier.size(14.dp),
                                tint = if (isReadingThis) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.5f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { 
                                viewModel.sendMessage("TRANSLATE to $targetLang: ${message.content}")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Translate, "Translate", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
            val displayName = if (isUser) "OPERATOR" else {
                val partnerPlatform = viewModel.currentChatPartnerPlatform.value
                if (partnerPlatform == "ai") {
                    viewModel.aiCustomName.value.uppercase()
                } else {
                    viewModel.currentChatPartner.value.uppercase()
                }
            }
            
            Text(
                text = displayName, 
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
            if (message.isTrend) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Link, null, modifier = Modifier.size(8.dp), tint = Color.Gray)
            }
        }
    }
}

private data class MoodStyle(val emoji: String, val color: Color)

private val MOOD_STYLES = mapOf(
    "happy" to MoodStyle("😊", Color(0xFFFFC107)),
    "excited" to MoodStyle("🤩", Color(0xFFFF6D00)),
    "neutral" to MoodStyle("😐", Color(0xFF9E9E9E)),
    "curious" to MoodStyle("🤔", Color(0xFF29B6F6)),
    "confused" to MoodStyle("😕", Color(0xFFAB47BC)),
    "frustrated" to MoodStyle("😤", Color(0xFFFF7043)),
    "sad" to MoodStyle("😢", Color(0xFF5C6BC0)),
    "angry" to MoodStyle("😠", Color(0xFFE53935))
)

/**
 * WhatsApp-style voice-note playback: a single persistent [android.media.MediaPlayer]
 * per bubble so play/pause/resume works correctly (pausing doesn't restart from zero).
 * AI replies matching [autoplayUri] play themselves once on first composition, then
 * clear the signal via [onConsumeAutoplay] so scrolling back up doesn't replay them;
 * [onPlaybackFinished] lets hands-free Conversation Mode resume listening afterward.
 */
@Composable
private fun VoiceNoteBubble(
    uri: String,
    isUser: Boolean,
    textColor: Color,
    autoplayUri: String?,
    onConsumeAutoplay: (String) -> Unit,
    onPlaybackFinished: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember(uri) { mutableStateOf(false) }
    val mediaPlayer = remember(uri) { mutableStateOf<android.media.MediaPlayer?>(null) }

    fun togglePlay() {
        val existing = mediaPlayer.value
        if (existing != null) {
            if (isPlaying) {
                existing.pause()
                isPlaying = false
            } else {
                existing.start()
                isPlaying = true
            }
            return
        }
        try {
            mediaPlayer.value = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(context, android.net.Uri.parse(uri))
                setOnCompletionListener {
                    isPlaying = false
                    onPlaybackFinished()
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("VoiceNoteBubble: playback error what=$what extra=$extra")
                    isPlaying = false
                    onPlaybackFinished()
                    true
                }
                prepare()
                start()
            }
            isPlaying = true
        } catch (e: Exception) {
            // A failed voice note must not silently stall hands-free mode forever —
            // onPlaybackFinished() is what resumes the next listen cycle.
            Timber.e(e, "VoiceNoteBubble: failed to start playback")
            isPlaying = false
            onPlaybackFinished()
        }
    }

    LaunchedEffect(uri) {
        if (!isUser && autoplayUri == uri) {
            onConsumeAutoplay(uri)
            togglePlay()
        }
    }

    DisposableEffect(uri) {
        onDispose {
            mediaPlayer.value?.release()
            mediaPlayer.value = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        IconButton(onClick = { togglePlay() }, modifier = Modifier.size(32.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play voice note",
                tint = textColor
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Icon(Icons.Default.GraphicEq, null, tint = textColor.copy(alpha = 0.5f), modifier = Modifier.weight(1f))
    }
}

/** Small colorful per-reply mood indicator, parsed from the AI's [MOOD: ...] tag. */
@Composable
private fun MoodBadge(mood: String) {
    val style = MOOD_STYLES[mood.lowercase()] ?: MoodStyle("💬", Color.Gray)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(style.color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(style.emoji, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            mood.replaceFirstChar { it.uppercase() },
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = style.color
        )
    }
}

@Composable
private fun SendToContactDialog(
    availablePlatforms: List<com.example.mistreal_mini.data.api.SocialPlatformResponse>,
    isSending: Boolean,
    onDismiss: () -> Unit,
    onSend: (platform: String, targetId: String, caption: String) -> Unit
) {
    val connected = availablePlatforms.filter { it.isConnected }
    var selectedPlatform by remember { mutableStateOf(connected.firstOrNull()?.id ?: "") }
    var targetId by remember { mutableStateOf("") }
    var caption by remember { mutableStateOf("") }
    var showPlatformMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send image to contact") },
        text = {
            Column {
                if (connected.isEmpty()) {
                    Text("Connect a platform in Settings first.", color = Color.Gray)
                } else {
                    Box {
                        OutlinedButton(onClick = { showPlatformMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(connected.find { it.id == selectedPlatform }?.name ?: "Select platform")
                        }
                        DropdownMenu(expanded = showPlatformMenu, onDismissRequest = { showPlatformMenu = false }) {
                            connected.forEach { p ->
                                DropdownMenuItem(text = { Text(p.name) }, onClick = { selectedPlatform = p.id; showPlatformMenu = false })
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = targetId,
                        onValueChange = { targetId = it },
                        label = { Text("Contact username/ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        label = { Text("Caption (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(selectedPlatform, targetId.trim(), caption) },
                enabled = !isSending && selectedPlatform.isNotBlank() && targetId.isNotBlank()
            ) {
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
