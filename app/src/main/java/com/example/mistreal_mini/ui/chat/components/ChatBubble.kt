package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
                        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.approveSocialAction(message) },
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
                        var showMenu by remember { mutableStateOf(false) }
                        
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, "More", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                        
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { onAiInsight(message.content) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, "Insight", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        IconButton(
                            onClick = { onReadAloud(message.content, InteractionMode.SINGLE) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Hearing, "Audio", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
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
