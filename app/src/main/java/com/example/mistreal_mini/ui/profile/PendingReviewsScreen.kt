package com.example.mistreal_mini.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.api.Review

/**
 * The reviewee's own notice inbox — reviews about them that are still
 * held back from everyone else (reviewRoutes.ts's 24h hold-back). Reply
 * or hide here, before anyone else ever sees it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingReviewsScreen(onBack: () -> Unit, viewModel: ReviewViewModel = hiltViewModel()) {
    val pendingReviews by viewModel.pendingReviews.collectAsState()
    var expandedReviewId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { viewModel.loadPendingReviews() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reviews About You") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { padding ->
        if (pendingReviews.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing pending right now.", color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text(
                        "These aren't visible to anyone else yet — you have 24h to read and reply before they publish.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                items(pendingReviews) { review ->
                    PendingReviewCard(
                        review = review,
                        isExpanded = expandedReviewId == review.id,
                        onToggleExpand = { expandedReviewId = if (expandedReviewId == review.id) null else review.id },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingReviewCard(review: Review, isExpanded: Boolean, onToggleExpand: () -> Unit, viewModel: ReviewViewModel) {
    val threadMessages by viewModel.threadMessages.collectAsState()
    var replyText by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (isExpanded) viewModel.loadThread(review.id)
    }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            review.contentText?.let { Text(it) }
            if (review.voiceUrl != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Voice review", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onToggleExpand) { Text(if (isExpanded) "Hide reply" else "Reply") }
                TextButton(onClick = { viewModel.hideReview(review.id) {} }) {
                    Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Hide")
                }
                TextButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp), tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", color = Color.Red)
                }
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                threadMessages.forEach { message ->
                    Text(
                        "${if (message.senderDeviceId == review.revieweeDeviceId) "You" else "Them"}: ${message.contentText}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Reply...") }
                    )
                    IconButton(onClick = {
                        if (replyText.isNotBlank()) {
                            viewModel.sendReply(review.id, replyText)
                            replyText = ""
                        }
                    }) { Icon(Icons.Default.Send, contentDescription = "Send") }
                }
            }
        }
    }

    if (showDeleteDialog) {
        DeleteReviewDialog(
            onDismiss = { showDeleteDialog = false },
            onConfirm = { reason ->
                viewModel.deleteReview(review.id, reason) { showDeleteDialog = false }
            }
        )
    }
}

@Composable
private fun DeleteReviewDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this review?") },
        text = {
            Column {
                Text(
                    "This is permanent — unlike Hide, it won't come back on its own. Everyone will see \"Removed — {your reason}\" in its place.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (reason.isNotBlank()) onConfirm(reason) })
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(reason) }, enabled = reason.isNotBlank()) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
