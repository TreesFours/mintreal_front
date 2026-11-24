package com.example.mistreal_mini.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val AD_PLATFORMS = listOf("facebook" to "Meta (Facebook/Instagram)", "google" to "Google", "tiktok" to "TikTok")

/**
 * Structurally copies SocialConnectionsScreen's OAuth-handoff shape (connect
 * button → open the returned URL → done), not the BYOK settings pattern —
 * an ad account is a per-platform external grant, same shape as a social
 * profile connection, just a different Zernio endpoint underneath.
 *
 * Honest flag, surfaced in the UI itself: connecting here and creating a
 * campaign can both still fail with "ADS_ADDON_REQUIRED" until Zernio's own
 * Ads add-on is enabled on the workspace — something only checkable on
 * Zernio's own dashboard, not from this app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdsConnectionsScreen(
    businessId: String,
    connectedAdPlatforms: List<String>,
    onBack: () -> Unit,
    viewModel: AdsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isConnecting by viewModel.isConnecting.collectAsStateWithLifecycle()
    val campaigns by viewModel.campaigns.collectAsStateWithLifecycle()
    val isLoadingCampaigns by viewModel.isLoadingCampaigns.collectAsStateWithLifecycle()
    // Seeded from the nav-time snapshot, then kept live below — a platform
    // connected moments ago through the external OAuth handoff wouldn't
    // otherwise show up without this refresh.
    val liveConnectedAdPlatforms by viewModel.connectedAdPlatforms.collectAsStateWithLifecycle()
    val effectiveConnectedPlatforms = liveConnectedAdPlatforms.ifEmpty { connectedAdPlatforms }
    var selectedPlatform by remember { mutableStateOf(connectedAdPlatforms.firstOrNull()) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadConnectedAdAccounts(businessId)
    }

    LaunchedEffect(selectedPlatform) {
        selectedPlatform?.let { viewModel.loadCampaigns(businessId, it) }
    }

    LaunchedEffect(Unit) {
        viewModel.connectUrl.collect { url ->
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collect { error ->
            android.widget.Toast.makeText(context, "❌ $error", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Business Ads") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                Text(
                    "AD ACCOUNTS",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black
                )
            }
            items(AD_PLATFORMS) { (platformId, label) ->
                val isConnected = effectiveConnectedPlatforms.contains(platformId)
                ListItem(
                    headlineContent = { Text(label, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(if (isConnected) "Connected" else "Not connected") },
                    trailingContent = {
                        if (isConnected) {
                            TextButton(onClick = { selectedPlatform = platformId }) { Text(if (selectedPlatform == platformId) "SELECTED" else "VIEW CAMPAIGNS") }
                        } else {
                            Button(onClick = { viewModel.connectAdAccount(businessId, platformId) }, enabled = !isConnecting, shape = RoundedCornerShape(8.dp)) {
                                Text("CONNECT")
                            }
                        }
                    }
                )
                HorizontalDivider()
            }

            if (selectedPlatform != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Campaigns — $selectedPlatform", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { showCreateDialog = true }) { Text("New Campaign") }
                    }
                }
                if (isLoadingCampaigns) {
                    item { Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else if (campaigns.isEmpty()) {
                    item { Text("No campaigns yet.", color = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.padding(32.dp)) }
                } else {
                    items(campaigns) { campaign ->
                        ListItem(
                            headlineContent = { Text(campaign.name ?: campaign.id, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text("${campaign.status ?: "unknown"} · budget ${campaign.budget ?: "—"} · ${campaign.objective ?: ""}") },
                            trailingContent = {
                                IconButton(onClick = {
                                    val next = if (campaign.status == "active") "paused" else "active"
                                    viewModel.updateCampaignStatus(businessId, selectedPlatform!!, campaign.id, next)
                                }) {
                                    Icon(
                                        if (campaign.status == "active") Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (campaign.status == "active") "Pause campaign" else "Resume campaign"
                                    )
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showCreateDialog && selectedPlatform != null) {
        CreateCampaignDialog(
            isCreating = viewModel.isCreatingCampaign.collectAsStateWithLifecycle().value,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, budget, objective ->
                viewModel.createCampaign(businessId, selectedPlatform!!, name, budget, objective) { success ->
                    if (success) showCreateDialog = false
                }
            }
        )
    }
}

@Composable
private fun CreateCampaignDialog(
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (name: String, budget: Double, objective: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    val objectives = listOf("awareness", "traffic", "conversions")
    var objective by remember { mutableStateOf(objectives.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Ad Campaign") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Campaign name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = budget, onValueChange = { budget = it }, label = { Text("Daily budget (USD)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    objectives.forEach { obj ->
                        FilterChip(selected = objective == obj, onClick = { objective = obj }, label = { Text(obj) })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && budget.toDoubleOrNull() != null && !isCreating,
                onClick = { onCreate(name, budget.toDoubleOrNull() ?: 0.0, objective) }
            ) {
                if (isCreating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp) else Text("Create")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
