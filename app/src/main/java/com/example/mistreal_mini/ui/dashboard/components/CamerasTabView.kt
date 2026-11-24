package com.example.mistreal_mini.ui.dashboard.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.api.CameraProfileDto
import com.example.mistreal_mini.ui.dashboard.CamerasViewModel
import com.example.mistreal_mini.ui.util.CameraStreamPlayer
import kotlinx.coroutines.launch

/**
 * Live-cam discovery feed — a plain vertical scroll of approved camera
 * cards, not a swipe-pager (no generic vertical-pager abstraction exists
 * anywhere in this codebase yet; building one is its own scoped effort).
 * Only YouTube and HLS streams are ever shown here — the only two formats
 * confirmed playable in this app (see CameraStreamPlayer.kt).
 */
@Composable
fun CamerasTabView(
    snackbarHostState: SnackbarHostState,
    viewModel: CamerasViewModel = hiltViewModel()
) {
    val cameras by viewModel.cameras.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showSubmitDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.loadCameras() }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { error ->
            coroutineScope.launch { snackbarHostState.showSnackbar(error) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Cameras", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { showSubmitDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Submit a Camera")
                }
            }
            Text(
                "Submitted cameras are reviewed before they appear here.",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )

            if (isLoading && cameras.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (cameras.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No live cameras yet — be the first to submit one.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(cameras, key = { it.id }) { camera -> CameraCard(camera) }
                }
            }
        }
    }

    if (showSubmitDialog) {
        SubmitCameraDialog(
            isSubmitting = viewModel.isSubmitting.collectAsStateWithLifecycle().value,
            onDismiss = { showSubmitDialog = false },
            onSubmit = { title, location, streamUrl, streamType ->
                viewModel.submitCamera(title, location, streamUrl, streamType) { success ->
                    if (success) {
                        showSubmitDialog = false
                        coroutineScope.launch { snackbarHostState.showSnackbar("Submitted — it'll appear once reviewed.") }
                    }
                }
            }
        )
    }
}

@Composable
private fun CameraCard(camera: CameraProfileDto) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        CameraStreamPlayer(
            streamUrl = camera.streamUrl,
            streamType = camera.streamType,
            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(camera.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        if (!camera.location.isNullOrBlank()) {
            Text(camera.location, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun SubmitCameraDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, location: String?, streamUrl: String, streamType: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var streamUrl by remember { mutableStateOf("") }
    var streamType by remember { mutableStateOf("youtube") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit a Camera") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = streamType == "youtube", onClick = { streamType = "youtube" }, label = { Text("YouTube") })
                    FilterChip(selected = streamType == "hls", onClick = { streamType = "hls" }, label = { Text("HLS (.m3u8)") })
                }
                OutlinedTextField(
                    value = streamUrl, onValueChange = { streamUrl = it },
                    label = { Text(if (streamType == "youtube") "YouTube URL" else "HLS stream URL (.m3u8)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Only YouTube and HLS (.m3u8) streams can be played — RTSP and other formats aren't supported.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank() && streamUrl.isNotBlank() && !isSubmitting,
                onClick = { onSubmit(title, location.ifBlank { null }, streamUrl, streamType) }
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
