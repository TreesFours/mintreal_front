package com.example.mistreal_mini.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import java.io.File

/**
 * Registers a face as a swap target — the only way a face can enter this
 * list is a live camera capture, right now, by whoever's face it is (see
 * VerifiedFaceRepository). No gallery import, by design.
 */
@Composable
fun VerifiedFacesSection(viewModel: SettingsViewModel) {
    val faces by viewModel.verifiedFaces.collectAsState()
    val isRegistering by viewModel.isRegisteringFace.collectAsState()
    val context = LocalContext.current

    var pendingLabel by remember { mutableStateOf("") }
    var showLabelPrompt by remember { mutableStateOf(false) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && captureUri != null) {
            showLabelPrompt = true
        }
    }

    fun startCapture() {
        val file = File(context.cacheDir, "face_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        captureUri = uri
        cameraLauncher.launch(uri)
    }

    SettingsSection(title = "VERIFIED FACES (FACE SWAP)", icon = Icons.Default.Face) {
        Text(
            "Register a face for AI face-swap edits by capturing it live with the camera — never from a gallery photo. This is what keeps the feature to people who actually chose to use it, not anyone's photo found online.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (faces.isEmpty()) {
            Text("No verified faces yet.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
        } else {
            faces.forEach { (entity, uri) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = entity.label,
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(entity.label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteFace(entity) }) {
                        Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = { startCapture() },
            enabled = !isRegistering,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isRegistering) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
            else {
                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CAPTURE & ADD FACE")
            }
        }
    }

    if (showLabelPrompt && captureUri != null) {
        AlertDialog(
            onDismissRequest = { showLabelPrompt = false; pendingLabel = "" },
            title = { Text("Label this face") },
            text = {
                OutlinedTextField(
                    value = pendingLabel,
                    onValueChange = { pendingLabel = it },
                    placeholder = { Text("e.g. Me") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.registerFace(pendingLabel.ifBlank { "Face" }, captureUri!!)
                        showLabelPrompt = false
                        pendingLabel = ""
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showLabelPrompt = false; pendingLabel = "" }) { Text("Cancel") }
            }
        )
    }
}
