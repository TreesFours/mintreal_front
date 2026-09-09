package com.example.mistreal_mini.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MediaToolSelector(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onVideo: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("OPTIC INTEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.Default.PhotoCamera, "PHOTO", onPhoto)
                ToolButton(Icons.Default.Videocam, "VIDEO", onVideo)
            }
        }
    }
}

@Composable
fun CaptureToolSelector(
    onDismiss: () -> Unit,
    onScreenshot: () -> Unit,
    onScreenRecord: () -> Unit
) {
    Surface(
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("VISUAL SYNC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(Icons.Default.Screenshot, "STILL", onScreenshot)
                ToolButton(Icons.Default.RadioButtonChecked, "RECORD", onScreenRecord)
            }
        }
    }
}

@Composable
fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }.padding(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
