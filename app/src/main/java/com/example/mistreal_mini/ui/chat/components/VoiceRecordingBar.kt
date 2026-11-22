package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.util.VoicePreset
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
    // Durations in ms, relative to the full recording — applies the trim and
    // replaces recordedFile with the result (see MediaEditorUtil.trimAudio).
    onTrim: (startMs: Long, endMs: Long) -> Unit = { _, _ -> },
    // Applied to the recording right before it's sent (ChatViewModel.sendRecordedVoiceNote) —
    // preview playback here is still the unprocessed clip, since VoiceFxRenderer's
    // decode/re-encode pass only runs once, on send.
    selectedVoicePreset: VoicePreset = VoicePreset.NONE,
    onVoicePresetSelected: (VoicePreset) -> Unit = {}
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                        Column {
                            Text("Voice Preview", style = MaterialTheme.typography.bodyMedium)
                            // Preview playback is still the unprocessed clip (see
                            // selectedVoicePreset's doc below) — this tag is what
                            // will actually go out once sent, so it's shown even
                            // though the preview itself doesn't reflect it yet.
                            if (selectedVoicePreset != VoicePreset.NONE) {
                                Text(
                                    "${selectedVoicePreset.emoji} ${selectedVoicePreset.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
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

            // Voice effect — applied by ChatViewModel right before send, not live.
            if (!isRecording && recordedFile != null) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VoicePreset.entries) { preset ->
                        val isSel = preset == selectedVoicePreset
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable { onVoicePresetSelected(preset) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(preset.emoji, modifier = Modifier.padding(end = 4.dp))
                            Text(
                                preset.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) MaterialTheme.colorScheme.primary else Color.Gray
                            )
                        }
                    }
                }
            }

            // Trim — only meaningful once there's a finished recording with a
            // real duration to carve a range out of.
            if (!isRecording && recordedFile != null && duration > 1) {
                var range by remember(recordedFile) { mutableStateOf(0f..duration.toFloat()) }
                Spacer(modifier = Modifier.height(4.dp))
                RangeSlider(
                    value = range,
                    onValueChange = { range = it },
                    valueRange = 0f..duration.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        String.format("%02d:%02d – %02d:%02d", range.start.toInt() / 60, range.start.toInt() % 60, range.endInclusive.toInt() / 60, range.endInclusive.toInt() % 60),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    TextButton(
                        onClick = { onTrim((range.start * 1000).toLong(), (range.endInclusive * 1000).toLong()) },
                        enabled = range.start > 0f || range.endInclusive < duration.toFloat()
                    ) {
                        Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trim")
                    }
                }
            }
        }
    }
}
