package com.example.mistreal_mini.ui.camera

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.util.FaceGuard

data class ArFilter(
    val id: String,
    val name: String,
    val emoji: String,
    val overlayType: String // "clown", "gradient", "mask", "vintage"
)

private val DEFAULT_FILTERS = listOf(
    ArFilter("normal", "Normal", "📷", "none"),
    ArFilter("clown", "Clown Selfie", "🤡", "clown"),
    ArFilter("neon", "Neon Glow", "🌈", "gradient"),
    ArFilter("cyber", "Cyber Mask", "🤖", "mask"),
    ArFilter("retro", "Retro Grain", "🎞️", "vintage")
)

/**
 * Instagram-Style AR Filter Camera with FaceSwap & Media Effects
 */
@Composable
fun ArFilterCameraScreen(
    onBack: () -> Unit,
    onMediaCaptured: (Uri, Boolean) -> Unit, // (uri, isVideo)
    verifiedFaces: List<Pair<com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity, Uri>> = emptyList()
) {
    var selectedFilter by remember { mutableStateOf(DEFAULT_FILTERS[1]) } // Clown selfie default
    var isRecordingVideo by remember { mutableStateOf(false) }
    var overlayText by remember { mutableStateOf("") }
    var showTextInput by remember { mutableStateOf(false) }
    var showFaceSwapDialog by remember { mutableStateOf(false) }
    var selectedFace by remember { mutableStateOf<Pair<com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity, Uri>?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current

    // Camera Photo Capture Launcher via FileProvider/Cache File
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && capturedUri != null) {
            onMediaCaptured(capturedUri!!, false)
        }
    }

    // Media Gallery Picker Launcher
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video") == true
            onMediaCaptured(uri, isVideo)
        }
    }

    fun launchCameraCapture() {
        val photoFile = java.io.File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile
        )
        capturedUri = uri
        try {
            photoLauncher.launch(uri)
        } catch (e: Exception) {
            galleryLauncher.launch("image/*")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Viewfinder Canvas / Live AR Overlay Placeholder
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    when (selectedFilter.overlayType) {
                        "gradient" -> Color.Magenta.copy(alpha = 0.2f)
                        "vintage" -> Color.DarkGray.copy(alpha = 0.3f)
                        else -> Color.Black
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Live AR Mask / Emoji Overlay
            if (selectedFilter.overlayType != "none") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(selectedFilter.emoji, fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(selectedFilter.name.uppercase(), color = Color.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }

            if (selectedFace != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.VerifiedUser, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "FACESWAP TARGET: ${selectedFace!!.first.label.uppercase()}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (overlayText.isNotBlank()) {
                Text(
                    text = overlayText,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Top Action Bar (Close, Flash, Text, Gallery, Effects)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                Icon(Icons.Default.Close, "Exit", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                    Icon(Icons.Default.PhotoLibrary, "Gallery", tint = Color.White)
                }
                IconButton(onClick = { showTextInput = !showTextInput }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                    Text("Aa", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
                IconButton(onClick = { showFaceSwapDialog = true }, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                    Icon(Icons.Default.AutoAwesome, "AI FaceSwap", tint = if (selectedFace != null) Color.Green else Color.Yellow)
                }
            }
        }

        // Face Swap Picker Dialog
        if (showFaceSwapDialog) {
            AlertDialog(
                onDismissRequest = { showFaceSwapDialog = false },
                title = { Text("AI FaceSwap Identity") },
                text = {
                    Column {
                        Text(
                            "Select a verified identity to target for AI FaceSwap:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (verifiedFaces.isEmpty()) {
                            Text(
                                "No verified face identities registered yet. Capture a selfie in Settings to register your face.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            verifiedFaces.forEach { pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedFace = pair
                                            showFaceSwapDialog = false
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (selectedFace?.first?.id == pair.first.id) Icons.Default.CheckCircle else Icons.Default.Face,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(pair.first.label, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFaceSwapDialog = false }) { Text("Close") }
                },
                dismissButton = {
                    if (selectedFace != null) {
                        TextButton(onClick = { selectedFace = null; showFaceSwapDialog = false }) { Text("Clear Selection") }
                    }
                }
            )
        }

        // Text Input Box Overlay
        if (showTextInput) {
            OutlinedTextField(
                value = overlayText,
                onValueChange = { overlayText = it },
                placeholder = { Text("Add caption...", color = Color.Gray) },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.8f),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White)
            )
        }

        // Bottom Filter Carousel & Shutter Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Selected Filter Name Badge
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text(
                    selectedFilter.name.uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Filter Carousel
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                items(DEFAULT_FILTERS) { filter ->
                    val isSel = filter.id == selectedFilter.id
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(if (isSel) Color.Cyan.copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.5f))
                            .border(2.dp, if (isSel) Color.Cyan else Color.Transparent, CircleShape)
                            .clickable { selectedFilter = filter },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(filter.emoji, fontSize = 24.sp)
                    }
                }
            }

            // Main Shutter Button
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(if (isRecordingVideo) Color.Red else Color.White)
                    .border(4.dp, Color.White, CircleShape)
                    .clickable {
                        launchCameraCapture()
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (isRecordingVideo) Color.Red else Color.White)
                )
            }
        }
    }
}
