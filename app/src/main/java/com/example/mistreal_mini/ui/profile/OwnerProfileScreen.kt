package com.example.mistreal_mini.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.Review
import com.example.mistreal_mini.data.api.SocialStat
import com.example.mistreal_mini.ui.needs.NeedsViewModel

/**
 * The reviewed person's profile — reached by tapping the owner identity
 * row on a business page. Reviews shown here are already filtered
 * server-side to "effectively published" (reviewRoutes.ts's GET /) — a
 * pending review never reaches this screen, only the reviewee's own
 * PendingReviewsScreen does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerProfileScreen(
    revieweeDeviceId: String,
    ownerName: String?,
    ownerPhotoUrl: String?,
    businessId: String?,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
    needsViewModel: NeedsViewModel = hiltViewModel()
) {
    val reviews by viewModel.reviews.collectAsState()
    val socialStats by viewModel.socialStats.collectAsState()
    var showWriteReview by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(true) }
    var expandedReviewId by remember { mutableStateOf<Int?>(null) }

    val isOwnProfile = revieweeDeviceId == viewModel.myDeviceId

    LaunchedEffect(revieweeDeviceId) {
        viewModel.loadProfile(revieweeDeviceId)
        if (isOwnProfile) needsViewModel.loadMySkills()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ownerName ?: if (isOwnProfile) "My Profile" else "Profile") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            if (!isOwnProfile) {
                ExtendedFloatingActionButton(onClick = { showWriteReview = true }, icon = { Icon(Icons.Default.RateReview, null) }, text = { Text("Leave a review") })
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.DarkGray)) {
                        ownerPhotoUrl?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(ownerName ?: "This user", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }

            if (socialStats.isNotEmpty() && showStats) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        socialStats.forEach { stat -> SocialStatChip(stat) }
                    }
                }
            }

            if (isOwnProfile) {
                item {
                    Text("SKILLS YOU OFFER", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                item { SkillsEditor(needsViewModel) }
            }

            item {
                Text("REVIEWS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }

            if (reviews.isEmpty()) {
                item { Text("No reviews yet.", color = Color.Gray) }
            } else {
                items(reviews) { review ->
                    ReviewCard(
                        review = review,
                        isExpanded = expandedReviewId == review.id,
                        onToggleExpand = { expandedReviewId = if (expandedReviewId == review.id) null else review.id },
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    if (showWriteReview) {
        WriteReviewDialog(
            revieweeDeviceId = revieweeDeviceId,
            businessId = businessId,
            onDismiss = { showWriteReview = false },
            viewModel = viewModel
        )
    }
}

@Composable
private fun SkillsEditor(viewModel: NeedsViewModel) {
    val skills by viewModel.mySkills.collectAsState()
    var newSkill by remember { mutableStateOf("") }

    Column {
        if (skills.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                skills.forEach { skill ->
                    AssistChip(
                        onClick = { viewModel.deleteSkill(skill.id) },
                        label = { Text(skill.skillLabel) },
                        trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(14.dp)) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newSkill,
                onValueChange = { newSkill = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g. electrician") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = {
                if (newSkill.isNotBlank()) {
                    viewModel.addSkill(newSkill)
                    newSkill = ""
                }
            }) { Text("Add") }
        }
        Text(
            "Matches people posting \"I need...\" requests near you — see the Needs screen in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
    }
}

@Composable
private fun SocialStatChip(stat: SocialStat) {
    Column {
        Text(stat.platform.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Row {
            stat.followers?.let { Text("$it followers", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ReviewCard(review: Review, isExpanded: Boolean, onToggleExpand: () -> Unit, viewModel: ReviewViewModel) {
    val threadMessages by viewModel.threadMessages.collectAsState()
    val threadParticipants by viewModel.threadParticipants.collectAsState()
    var replyText by remember { mutableStateOf("") }

    LaunchedEffect(isExpanded) {
        if (isExpanded) viewModel.loadThread(review.id)
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onToggleExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (review.status == "deleted") {
                Text(
                    "Removed — ${review.deletedReason ?: "no reason given"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            } else {
                review.contentText?.let { Text(it) }
                review.voiceUrl?.let { VoiceReviewPlayback(it) }
            }
            Text(review.createdAt ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                threadMessages.forEach { message ->
                    Text(
                        "${if (message.senderDeviceId == review.revieweeDeviceId) "Reviewee" else "Reviewer"}: ${message.contentText}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // Before 3 reviewer/reviewee exchanges, only actual
                // participants (reviewer/reviewee/approved invite) can
                // reply — once publiclyCommentable flips on server-side,
                // anyone who checks this profile can join in.
                if (review.publiclyCommentable || viewModel.myDeviceId in threadParticipants.map { it.deviceId }) {
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
    }
}

@Composable
private fun VoiceReviewPlayback(voiceUrl: String) {
    // Playback for an already-uploaded review's voice note — a plain
    // remote-URL player, distinct from WriteReviewDialog's own recorder.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Voice review", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WriteReviewDialog(
    revieweeDeviceId: String,
    businessId: String?,
    onDismiss: () -> Unit,
    viewModel: ReviewViewModel
) {
    var contentText by remember { mutableStateOf("") }
    var acknowledgedPolicy by remember { mutableStateOf(false) }
    val isRecording by viewModel.isRecording
    val recordingDuration by viewModel.recordingDuration
    val recordedFile by viewModel.recordedFile
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave a review") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Reviews are held for 24h before anyone else can see them, giving the other person a real chance to respond first.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.clickable { acknowledgedPolicy = !acknowledgedPolicy }
                ) {
                    Checkbox(checked = acknowledgedPolicy, onCheckedChange = { acknowledgedPolicy = it })
                    Text(
                        "I understand this must be constructive criticism, not abuse — I can be held legally liable if it isn't.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                OutlinedTextField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write your review...") },
                    enabled = recordedFile == null
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRecording) {
                        Icon(Icons.Default.Mic, null, tint = Color.Red)
                        Text(String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60), modifier = Modifier.padding(horizontal = 8.dp))
                        Button(onClick = { viewModel.stopRecording() }) { Text("Stop") }
                    } else if (recordedFile != null) {
                        IconButton(onClick = { viewModel.playRecording() }) { Icon(Icons.Default.PlayArrow, contentDescription = "Play") }
                        Text("Voice review recorded", modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.deleteRecording() }) { Text("Remove") }
                    } else {
                        OutlinedButton(onClick = { viewModel.startRecording() }, enabled = contentText.isBlank()) {
                            Icon(Icons.Default.Mic, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Record voice review instead")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.submitReview(revieweeDeviceId, businessId, contentText.ifBlank { null }) { success ->
                        if (success) onDismiss()
                    }
                },
                enabled = !isSubmitting && acknowledgedPolicy && (contentText.isNotBlank() || recordedFile != null)
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
