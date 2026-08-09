package com.example.mistreal_mini.ui.util

import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.ChatInputBar
import com.example.mistreal_mini.ui.chat.VoiceRecordingBar
import com.example.mistreal_mini.util.VoiceRecorder
import java.io.File

@Composable
fun AiInsightPopup(
    contextText: String,
    onClose: () -> Unit,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf("") }
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val focusManager = LocalFocusManager.current
    
    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var isPlayingBack by remember { mutableStateOf(false) }

    // ⏱️ Recording Timer logic
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDuration++
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Expanded to match red margin
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Drafting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                
                // Professional Clear Button for Mini Chat
                IconButton(onClick = { viewModel.clearSessionMessages() }) { 
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear Session", tint = Color.Gray)
                }
                
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            Text(
                text = "Context: ${contextText.take(50)}...",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages.takeLast(5)) { msg ->
                    Text("${msg.role.uppercase()}: ${msg.content}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
            }

            if (isRecording) {
                Text("Say \"Yes, send that\" to confirm", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            
            if (isRecording || recordedFile != null) {
                VoiceRecordingBar(
                    isRecording = isRecording,
                    duration = recordingDuration,
                    recordedFile = recordedFile,
                    isPlayingBack = isPlayingBack,
                    onStopRecording = { isRecording = false; voiceRecorder.stopRecording() },
                    onDelete = { recordedFile = null; isRecording = false },
                    onPlay = { recordedFile?.let { voiceRecorder.playRecording(it) { isPlayingBack = false } } },
                    onSend = {
                        recordedFile?.let { viewModel.sendAttachments(listOf(Uri.fromFile(it)), "audio") }
                        recordedFile = null
                    },
                    onCancel = { recordedFile = null; isRecording = false }
                )
            } else {
                ChatInputBar(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = {
                        focusManager.clearFocus()
                        if (textState.lowercase() == "yes send that") {
                            viewModel.sendMessage("Confirming reply dispatch.")
                        } else {
                            val trendId = "TREND_${System.currentTimeMillis()}"
                            val fullPrompt = "Based on this context: $contextText\n\nUser Question/Instruction: $textState"
                            // Save as Trend in history
                            viewModel.sendMessage(fullPrompt, attachmentType = "trend", trendTitle = "Strategy: ${textState.take(20)}...")
                        }
                        textState = ""
                    },
                    onScreenshotClick = { },
                    onCameraClick = { },
                    onFileClick = { },
                    onVoiceClick = { isRecording = true; recordedFile = voiceRecorder.startRecording() },
                    onScribeClick = { /* Not used in mini-chat */ },
                    isLoading = isLoading,
                    onClearClick = null
                )
            }
        }
    }
}
