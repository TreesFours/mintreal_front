package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ScreenRecordingOverlay(
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
        modifier = Modifier.padding(16.dp).wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(12.dp).background(Color.Red, CircleShape))
            
            IconButton(onClick = if (isPaused) onResume else onPause, modifier = Modifier.size(24.dp)) {
                Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.White)
            }
            
            IconButton(onClick = onStop, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Stop, null, tint = Color.Red)
            }
        }
    }
}
