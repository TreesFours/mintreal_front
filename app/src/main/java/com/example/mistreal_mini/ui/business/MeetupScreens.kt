package com.example.mistreal_mini.ui.business

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.Meetup
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Proposed from inside a social chat (business or peer-to-peer) — sets a
 * shared location+time plan. Not continuous tracking; the counterparty
 * (who may not have Mistreal at all) accepts/declines via the same
 * no-login web-link pattern the Guardian confirm flow already uses.
 */
@Composable
fun ProposeMeetupDialog(
    businessId: String?,
    counterpartyPlatform: String?,
    counterpartyContactId: String?,
    onDismiss: () -> Unit,
    onProposed: () -> Unit,
    viewModel: MeetupViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val result by viewModel.lastProposalResult.collectAsStateWithLifecycle()

    var addressLabel by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isLocating by remember { mutableStateOf(false) }
    val calendar = remember { Calendar.getInstance() }
    var scheduledLabel by remember { mutableStateOf("Pick date & time") }
    var scheduledSet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(result) {
        if (result is Resource.Success) {
            onProposed()
            viewModel.clearProposalResult()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Propose a Meetup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This isn't tracking — just a shared plan. At the meeting, each of you confirms presence yourself.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                OutlinedTextField(
                    value = addressLabel,
                    onValueChange = { addressLabel = it },
                    label = { Text("Meeting place (e.g. Cafe on 5th)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        isLocating = true
                        scope.launch {
                            val loc = viewModel.getCurrentLocation()
                            isLocating = false
                            if (loc != null) coords = loc.latitude to loc.longitude
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (coords != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary)
                ) {
                    if (isLocating) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                    else Text(if (coords != null) "LOCATION SET" else "USE MY CURRENT LOCATION")
                }
                Button(
                    onClick = {
                        DatePickerDialog(context, { _, y, m, d ->
                            calendar.set(y, m, d)
                            TimePickerDialog(context, { _, h, min ->
                                calendar.set(Calendar.HOUR_OF_DAY, h)
                                calendar.set(Calendar.MINUTE, min)
                                scheduledSet = true
                                scheduledLabel = SimpleDateFormat("EEE MMM d, h:mm a", Locale.getDefault()).format(calendar.time)
                            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(scheduledLabel)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val (lat, lon) = coords ?: return@Button
                    val iso = java.time.Instant.ofEpochMilli(calendar.timeInMillis).toString()
                    viewModel.proposeMeetup(businessId, counterpartyPlatform, counterpartyContactId, lat, lon, addressLabel.ifBlank { null }, iso)
                },
                enabled = !isSubmitting && coords != null && scheduledSet
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Propose")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** "My Meetups" — list + per-meetup presence confirmation (location + photo + outcome). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetupListScreen(onBack: () -> Unit, viewModel: MeetupViewModel = hiltViewModel()) {
    val meetups by viewModel.meetups.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var confirmTarget by remember { mutableStateOf<Meetup?>(null) }

    LaunchedEffect(Unit) { viewModel.fetchMeetups() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MY MEETUPS") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading && meetups.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (meetups.isEmpty()) {
                Text("No meetups proposed yet.", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(meetups) { meetup ->
                        Card(shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(meetup.addressLabel ?: "Meetup #${meetup.id}", fontWeight = FontWeight.Bold)
                                Text(meetup.scheduledAt, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text("Transaction ID: ${meetup.transactionToken}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                Text(meetup.status.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (meetup.status == "accepted" || meetup.status == "proposed") {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { confirmTarget = meetup }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Confirm Presence")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmTarget?.let { meetup ->
        ConfirmMeetupDialog(meetup = meetup, onDismiss = { confirmTarget = null }, viewModel = viewModel)
    }
}

@Composable
private fun ConfirmMeetupDialog(meetup: Meetup, onDismiss: () -> Unit, viewModel: MeetupViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    var isLocating by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf("success") }
    var reason by remember { mutableStateOf("") }
    var review by remember { mutableStateOf("") }
    var buyerVote by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoUri = pendingCameraUri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Presence") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("This captures your current location and a photo as proof, saved permanently for reference.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = outcome == "success", onClick = { outcome = "success" })
                    Text("It went well")
                    Spacer(modifier = Modifier.width(12.dp))
                    RadioButton(selected = outcome == "failed", onClick = { outcome = "failed" })
                    Text("It didn't happen")
                }
                if (outcome == "failed") {
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("What happened?") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OutlinedTextField(value = review, onValueChange = { review = it }, label = { Text("Leave a review (optional)") }, modifier = Modifier.fillMaxWidth())
                    // Only meaningful for a business transaction, and only
                    // actually recorded server-side if this device turns
                    // out to be the buyer's side — harmless no-op otherwise.
                    if (meetup.businessId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Rate this sale:", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { buyerVote = if (buyerVote == "up") null else "up" }) {
                                Icon(Icons.Default.ThumbUp, "Good sale", tint = if (buyerVote == "up") Color(0xFF4CAF50) else Color.Gray)
                            }
                            IconButton(onClick = { buyerVote = if (buyerVote == "down") null else "down" }) {
                                Icon(Icons.Default.ThumbDown, "Bad sale", tint = if (buyerVote == "down") Color.Red else Color.Gray)
                            }
                        }
                    }
                }
                Button(
                    onClick = {
                        val file = File(context.cacheDir, "meetup_proof_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        pendingCameraUri = uri
                        cameraLauncher.launch(uri)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (photoUri != null) Color(0xFF4CAF50) else MaterialTheme.colorScheme.secondary)
                ) {
                    Text(if (photoUri != null) "PHOTO CAPTURED" else "TAKE PROOF PHOTO")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        isLocating = true
                        val loc = viewModel.getCurrentLocation()
                        isLocating = false
                        if (loc == null) return@launch
                        viewModel.confirmMeetup(
                            meetup.id, loc.latitude, loc.longitude, outcome,
                            reason.ifBlank { null }, review.ifBlank { null }, buyerVote, photoUri
                        ) { onDismiss() }
                    }
                },
                enabled = !isSubmitting && !isLocating
            ) {
                if (isSubmitting || isLocating) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Submit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
