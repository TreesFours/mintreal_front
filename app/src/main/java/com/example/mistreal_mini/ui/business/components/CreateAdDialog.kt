package com.example.mistreal_mini.ui.business.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage

/**
 * Premium-gated ad creation popup — either a 10s/30s video, or a 6-12 image
 * slideshow spanning the same duration.
 */
@Composable
fun CreateAdDialog(
    isPro: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onUpgradeClick: () -> Unit,
    onSubmit: (mediaType: String, durationSeconds: Int, caption: String, targetUrl: String, videoUri: Uri?, imageUris: List<Uri>) -> Unit
) {
    var mediaType by remember { mutableStateOf("video") } // "video" | "slideshow"
    var duration by remember { mutableIntStateOf(10) }
    var caption by remember { mutableStateOf("") }
    var targetUrl by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { videoUri = it }
    val imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { imageUris = it }

    val isSlideshowValid = imageUris.size in 6..12
    val canSubmit = !isSubmitting && caption.isNotBlank() &&
        (mediaType == "video" && videoUri != null || mediaType == "slideshow" && isSlideshowValid)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 12.dp
        ) {
            Column(modifier = Modifier.padding(24.dp).widthIn(max = 400.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("LAUNCH AN AD", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, null)
                    }
                }

                if (!isPro) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Premium feature", fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text("Ad creation needs at least Premium 1.", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onUpgradeClick, modifier = Modifier.fillMaxWidth()) {
                                Text("UPGRADE")
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(20.dp))
                    Text("FORMAT", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    SegmentedToggle(
                        options = listOf("video" to "Video", "slideshow" to "Slideshow"),
                        selected = mediaType,
                        onSelect = { mediaType = it }
                    )

                    Spacer(Modifier.height(16.dp))
                    Text("DURATION", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(Modifier.height(6.dp))
                    SegmentedToggle(
                        options = listOf(10 to "10s", 30 to "30s"),
                        selected = duration,
                        onSelect = { duration = it }
                    )

                    Spacer(Modifier.height(16.dp))
                    if (mediaType == "video") {
                        Text("VIDEO", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { videoLauncher.launch("video/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            if (videoUri != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Text("Video selected", style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.VideoLibrary, null, tint = Color.Gray)
                                    Text("Tap to choose a ${duration}s video", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                        }
                    } else {
                        Text("IMAGES — 6 TO 12", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(imageUris) { uri ->
                                AsyncImage(
                                    model = uri, contentDescription = null, contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                            item {
                                Box(
                                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { imagesLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.Gray) }
                            }
                        }
                        if (imageUris.isNotEmpty() && !isSlideshowValid) {
                            Spacer(Modifier.height(4.dp))
                            Text("${imageUris.size}/12 — need 6 to 12", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = caption, onValueChange = { caption = it },
                        label = { Text("Caption") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = targetUrl, onValueChange = { targetUrl = it },
                        label = { Text("Link (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = { onSubmit(mediaType, duration, caption, targetUrl, videoUri, imageUris) },
                        enabled = canSubmit,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.RocketLaunch, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("LAUNCH AD", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentedToggle(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 13.sp
                )
            }
        }
    }
}
