package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun VoiceRecordingBar(
    isRecording: Boolean,
    duration: Int,
    recordedFile: File?,
    isPlayingBack: Boolean,
    onStopRecording: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isRecording) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red)
                Text(
                    text = String.format("%02d:%02d", duration / 60, duration % 60),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Button(onClick = onStopRecording) {
                    Text("Stop")
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
                
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlay) {
                        Icon(if (isPlayingBack) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                    Text("Voice Preview", style = MaterialTheme.typography.bodyMedium)
                }
                
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
