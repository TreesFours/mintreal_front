package com.example.mistreal_mini.ui.dashboard.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.ChatBubble
import com.example.mistreal_mini.ui.chat.components.InteractionMode

@Composable
fun ArchitectureDesignView(
    chatViewModel: ChatViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val auditMessages by chatViewModel.getTrendMessages("ARCHITECT_AUDIT").collectAsState(initial = emptyList())
    val isLoading by chatViewModel.isLoading
    val coroutineScope = rememberCoroutineScope()

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedImageUri = uri
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "ARCHITECTURAL DESIGN HUB",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Upload floor plans for structural audit & material calculation.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Drop Zone
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clickable { photoLauncher.launch("image/*") },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selectedImageUri == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudUpload, null, tint = MaterialTheme.colorScheme.primary)
                        Text("SELECT FLOOR PLAN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    AsyncImage(
                        model = selectedImageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    IconButton(
                        onClick = { selectedImageUri = null },
                        modifier = Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(0.dp, 12.dp, 0.dp, 12.dp))
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                    }
                }
            }
        }

        if (selectedImageUri != null) {
            Button(
                onClick = {
                    chatViewModel.sendMessage(
                        text = "Perform a complete Design Audit on this plan.",
                        overrideAttachments = listOf(selectedImageUri!!),
                        attachmentType = "image",
                        trendTitle = "ARCHITECT_AUDIT"
                    )
                    selectedImageUri = null
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Analytics, null)
                Spacer(Modifier.width(8.dp))
                Text("RUN TACTICAL AUDIT", fontWeight = FontWeight.Black)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Results Ledger
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (auditMessages.isEmpty() && !isLoading) {
                item {
                    Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active audits found.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            items(auditMessages) { msg ->
                ChatBubble(
                    message = msg,
                    viewModel = chatViewModel,
                    onAiInsight = {},
                    onReadAloud = { text, mode -> chatViewModel.readAloud(text) },
                    snackbarHostState = snackbarHostState,
                    coroutineScope = coroutineScope
                )
            }

            if (isLoading) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                    Text("Architect calculating structure...", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
