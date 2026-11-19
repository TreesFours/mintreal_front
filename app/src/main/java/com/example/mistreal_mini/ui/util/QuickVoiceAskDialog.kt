package com.example.mistreal_mini.ui.util

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.VoiceRecordingBar
import com.example.mistreal_mini.util.VoiceRecorder
import java.io.File

/**
 * Lightweight alternative to [AiInsightPopup] — a quick voice question with
 * no message history, no text input, just record → send. The reply surfaces
 * separately as an [AiReplyBubble], not inside this dialog. Own local
 * [VoiceRecorder] instance, same pattern [AiInsightPopup] already uses for
 * its own voice path — this never touches [ChatViewModel]'s recording
 * state, so it can't collide with a recording already happening elsewhere.
 */
@Composable
fun QuickVoiceAskDialog(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onSent: () -> Unit
) {
    val context = LocalContext.current
    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var isPlayingBack by remember { mutableStateOf(false) }

    val galleryLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { viewModel.addPendingAttachment(it) }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDuration++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (isRecording) voiceRecorder.stopRecording() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ask AI (Voice & Attachments)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AddPhotoAlternate, "Attach photo", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                Spacer(modifier = Modifier.height(16.dp))

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
                            onSent()
                        }
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Tap to record your question", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        FloatingActionButton(
                            onClick = { isRecording = true; recordedFile = voiceRecorder.startRecording() },
                            containerColor = MaterialTheme.colorScheme.primary
                        ) {
                            Icon(Icons.Default.Mic, "Start recording")
                        }
                    }
                }
            }
        }
    }
}
