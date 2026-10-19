package com.example.mistreal_mini.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * "I'm here" screen for a comfort meet — both people open this when they
 * believe they're actually together; the backend cross-checks their live
 * locations against each other (meetupRoutes.ts's comfort-ping) rather
 * than trusting either side's self-report, which is what makes this
 * different from the existing business-meetup confirm flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComfortMeetScreen(
    meetupId: Int,
    onBack: () -> Unit,
    viewModel: ComfortMeetViewModel = hiltViewModel()
) {
    val status by viewModel.status
    val isActive by viewModel.isActive
    val isConfirmed by viewModel.isConfirmed

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comfort Meet") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = if (isConfirmed) Color(0xFFE91E63) else Color.Gray,
                modifier = Modifier.size(72.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                status,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (!isConfirmed) {
                Button(
                    onClick = { if (isActive) viewModel.stop() else viewModel.start(meetupId) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text(if (isActive) "Stop checking" else "I'm here", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Both of you need this screen open and nearby for it to confirm — it checks your live location against theirs, not just your own say-so.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            } else {
                Button(onClick = onBack) { Text("Done") }
            }
        }
    }
}
