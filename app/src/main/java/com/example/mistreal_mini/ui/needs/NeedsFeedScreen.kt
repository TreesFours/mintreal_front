package com.example.mistreal_mini.ui.needs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.api.NeedPost
import kotlinx.coroutines.flow.collectLatest

/**
 * "Matches for your skills" (from UserSkill tags on your own profile,
 * ranked by proximity to your last-known location) above a general
 * "Nearby needs" browse list — distinct from the Community Feed (that's
 * opt-in re-posted social content) and from Review timelines (reputation,
 * not transactions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeedsFeedScreen(onBack: () -> Unit, viewModel: NeedsViewModel = hiltViewModel()) {
    val matches by viewModel.matches.collectAsState()
    val nearbyNeeds by viewModel.nearbyNeeds.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    var showPostDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.loadMatches()
        viewModel.loadNearbyNeeds()
    }
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Needs") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showPostDialog = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Post a need") })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (matches.isNotEmpty()) {
                item {
                    Text("MATCHES FOR YOUR SKILLS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
                items(matches) { need -> NeedCard(need, onClose = { viewModel.closeNeed(need.id) }) }
                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            item {
                Text("NEARBY NEEDS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
            if (nearbyNeeds.isEmpty()) {
                item { Text("Nothing posted nearby yet.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
            } else {
                items(nearbyNeeds) { need -> NeedCard(need, onClose = { viewModel.closeNeed(need.id) }) }
            }
        }
    }

    if (showPostDialog) {
        PostNeedDialog(
            isPosting = isPosting,
            onDismiss = { showPostDialog = false },
            onPost = { skillLabel, description ->
                viewModel.postNeed(skillLabel, description) { success -> if (success) showPostDialog = false }
            }
        )
    }
}

@Composable
private fun NeedCard(need: NeedPost, onClose: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(need.skillLabel.replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Bold)
                need.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                need.distanceKm?.let { Text("%.1f km away".format(it), style = MaterialTheme.typography.labelSmall, color = Color.Gray) }
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close this need") }
        }
    }
}

@Composable
private fun PostNeedDialog(isPosting: Boolean, onDismiss: () -> Unit, onPost: (String, String?) -> Unit) {
    var skillLabel by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post a need") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = skillLabel,
                    onValueChange = { skillLabel = it },
                    label = { Text("What do you need? (e.g. \"electrician\")") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Details (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Uses your current location so nearby people offering this skill can find it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPost(skillLabel, description.ifBlank { null }) },
                enabled = !isPosting && skillLabel.isNotBlank()
            ) {
                if (isPosting) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Post")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
