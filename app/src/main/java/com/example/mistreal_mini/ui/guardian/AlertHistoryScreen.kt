package com.example.mistreal_mini.ui.guardian

import android.media.MediaPlayer
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.api.EmergencyAlert
import timber.log.Timber

/**
 * Previously a fired alert had no durable record at all — just a one-shot
 * snackbar on the triggering device. This lists every EmergencyAlert for
 * this device (active/resolved/escalated) and, per alert, the location and
 * SOS audio evidence that used to be captured and immediately orphaned.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    onBack: () -> Unit,
    viewModel: GuardianViewModel = hiltViewModel()
) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.fetchAlerts() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GUARDIAN ALERT HISTORY") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading && alerts.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (alerts.isEmpty()) {
                Text(
                    "No alerts triggered yet.",
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(alerts) { alert ->
                        AlertCard(alert = alert, onResolve = { viewModel.resolveAlert(alert.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: EmergencyAlert, onResolve: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val statusColor = when (alert.status) {
        "resolved_safe" -> Color(0xFF2E7D32)
        "escalated_public" -> Color(0xFFD32F2F)
        else -> Color(0xFFF9A825)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(alert.distressSignature ?: "SOS Alert", fontWeight = FontWeight.Bold)
                Text(
                    alert.status.replace('_', ' ').uppercase(),
                    color = statusColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(alert.createdAt, color = Color.Gray, style = MaterialTheme.typography.labelSmall)

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                val mapsLink = "https://www.google.com/maps?q=${alert.latitude},${alert.longitude}"
                Text(
                    "Location: $mapsLink",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )

                alert.sosAudioUrl?.let { url ->
                    Spacer(modifier = Modifier.height(12.dp))
                    RemoteAudioPlayer(url = url)
                }

                if (alert.status == "active") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                        Text("I'm safe — resolve this alert")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteAudioPlayer(url: String) {
    var isPlaying by remember(url) { mutableStateOf(false) }
    var mediaPlayer by remember(url) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
            } else {
                try {
                    val player = MediaPlayer()
                    player.setDataSource(url)
                    player.setOnPreparedListener { it.start() }
                    player.setOnCompletionListener {
                        isPlaying = false
                        mediaPlayer?.release()
                        mediaPlayer = null
                    }
                    player.prepareAsync()
                    mediaPlayer = player
                    isPlaying = true
                } catch (e: Exception) {
                    Timber.e(e, "RemoteAudioPlayer: failed to play %s", url)
                    isPlaying = false
                }
            }
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play SOS audio")
        }
        Text("SOS audio evidence", style = MaterialTheme.typography.bodySmall)
    }
}
