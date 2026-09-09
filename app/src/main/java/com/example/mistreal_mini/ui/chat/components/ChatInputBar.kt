package com.example.mistreal_mini.ui.chat.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onScreenshotClick: () -> Unit,
    onScreenRecordClick: () -> Unit,
    onCameraClick: () -> Unit,
    onVideoClick: () -> Unit,
    onFileClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onConversationClick: () -> Unit,
    onScribeClick: () -> Unit,
    isLoading: Boolean,
    isScribing: Boolean = false,
    onClearScribe: () -> Unit = {},
    onSaveScribe: () -> Unit = {},
    onDraftClick: (() -> Unit)? = null,
    pendingAttachments: List<Uri> = emptyList(),
    onRemoveAttachment: (Uri) -> Unit = {},
    isSceneMode: Boolean = false,
    onToggleSceneMode: (Boolean) -> Unit = {},
    isAutoReplyEnabled: Boolean = false,
    onToggleAutoReply: (Boolean) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
    var showMediaTools by remember { mutableStateOf(false) }
    var showCaptureTools by remember { mutableStateOf(false) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }

    Surface(
        tonalElevation = 8.dp, 
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
    Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(pendingAttachments) { index, uri ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(44.dp)) {
                                val isImage = context.contentResolver.getType(uri)?.startsWith("image") == true
                                val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
                                
                                if (isImage || isVideo) {
                                    Box(modifier = Modifier.fillMaxSize().clickable { previewUri = uri }) {
                                        AsyncImage(
                                            model = uri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        if (isVideo) {
                                            Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(24.dp))
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveAttachment(uri) },
                                    modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                            if (isSceneMode) {
                                val label = when(index) {
                                    0 -> "START"
                                    1 -> "END"
                                    else -> "EXTRA"
                                }
                                Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (isSceneMode) {
                if (pendingAttachments.isEmpty()) {
                    Text(
                        "⚠️ TACTICAL WARNING: No optic frames attached. Video fidelity may be low.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                } else {
                    Text(
                        "💡 TIP: Attach at least 2 frames (Start/End) for maximum video stability.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                if (text.isBlank()) {
                    Text(
                        "⚠️ PROMPT REQUIRED: Video models need specific generation instructions.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Yellow,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }

            if (text.length > 500) {
                Text(
                    "Signal Density High: ${text.length} chars. Consider attaching as file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconTint = MaterialTheme.colorScheme.primary
                
                IconButton(onClick = { showCaptureTools = !showCaptureTools }, modifier = Modifier.size(30.dp)) { 
                    Icon(if(showCaptureTools) Icons.Default.Close else Icons.Default.LensBlur, "Visual Sync", tint = iconTint, modifier = Modifier.size(16.dp)) 
                }
                
                IconButton(onClick = { showMediaTools = !showMediaTools }, modifier = Modifier.size(30.dp)) { 
                    Icon(if(showMediaTools) Icons.Default.Close else Icons.Default.PhotoCamera, "Optic Intel", tint = iconTint, modifier = Modifier.size(16.dp)) 
                }
                
                IconButton(onClick = onFileClick, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.AttachFile, "Data Package", tint = iconTint, modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onVoiceClick, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Mic, "Voice Protocol", tint = iconTint, modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onConversationClick, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.VoiceChat, "Conversation Mode", tint = iconTint, modifier = Modifier.size(16.dp)) }
                IconButton(onClick = onScribeClick, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.HistoryEdu, "Scribe Alpha", tint = if (isScribing) Color.Green else iconTint, modifier = Modifier.size(16.dp)) }
                
                if (onDraftClick != null) {
                    IconButton(onClick = onDraftClick) {
                        Icon(Icons.Default.AutoFixHigh, "Draft AI", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Ghost Responder (Auto-Reply) Toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("GHOST", style = MaterialTheme.typography.labelSmall, fontSize = 7.sp, color = if(isAutoReplyEnabled) Color.Green else Color.Gray)
                    Switch(
                        checked = isAutoReplyEnabled,
                        onCheckedChange = onToggleAutoReply,
                        modifier = Modifier.scale(0.5f).height(24.dp),
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Green)
                    )
                    Text("Guardian Active", style = MaterialTheme.typography.labelSmall, fontSize = 6.sp, color = Color.Gray)
                }

                IconButton(onClick = { onToggleSceneMode(!isSceneMode) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Movie, 
                        "Scene Mode", 
                        modifier = Modifier.size(18.dp),
                        tint = if (isSceneMode) MaterialTheme.colorScheme.primary else Color.Gray
                    )
                }
            }

            if (isScribing) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Scribe Active...", style = MaterialTheme.typography.labelSmall, color = Color.Green)
                    Row {
                        TextButton(onClick = onClearScribe) { Text("CLEAR TRANSCRIPT", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onSaveScribe, colors = ButtonDefaults.buttonColors(containerColor = Color.Green.copy(alpha = 0.2f))) { 
                            Text("SAVE & SEND", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) 
                        }
                    }
                }
            }

            if (showMediaTools) {
                MediaToolSelector(
                    onDismiss = { showMediaTools = false },
                    onPhoto = { onCameraClick(); showMediaTools = false },
                    onVideo = { onVideoClick(); showMediaTools = false }
                )
            }

            if (showCaptureTools) {
                CaptureToolSelector(
                    onDismiss = { showCaptureTools = false },
                    onScreenshot = { onScreenshotClick(); showCaptureTools = false },
                    onScreenRecord = { onScreenRecordClick(); showCaptureTools = false }
                )
            }
            
            if (text.length > 500) {
                Text(
                    "Signal Density High: ${text.length} chars. Consider attaching as file.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text, 
                    onValueChange = { 
                        if (it.length > 2000) {
                            // Automatically convert to attachment if extremely long
                            // For now, just allow it but we could trigger a special "File" state
                            onTextChange(it)
                        } else {
                            onTextChange(it)
                        }
                    }, 
                    modifier = Modifier.weight(1f), 
                    placeholder = { Text("Enter command...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, 
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank() && !isLoading) {
                            onSend()
                            focusManager.clearFocus()
                        }
                    })
                )
                Spacer(modifier = Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = { if (text.isNotBlank() && !isLoading) onSend() },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (text.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    contentColor = if (text.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, "Transmit")
                    }
                }
            }
        }
    }

    if (previewUri != null) {
        AlertDialog(
            onDismissRequest = { previewUri = null },
            title = { Text("Attachment Preview") },
            text = {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    AsyncImage(
                        model = previewUri,
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                Button(onClick = { previewUri = null }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    onRemoveAttachment(previewUri!!)
                    previewUri = null 
                }) {
                    Text("CANCEL / DISCARD", color = Color.Red)
                }
            }
        )
    }
}
