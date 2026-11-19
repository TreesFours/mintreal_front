package com.example.mistreal_mini.ui.business

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Previously "Launch Ad" only existed as one small icon buried among 6
 * others inside Tactical Shopfront's inventory screen — no dedicated,
 * discoverable place to advertise at all. This gives it one, reachable
 * directly from Business Hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvertiseScreen(
    onBack: () -> Unit,
    onRegisterBusiness: () -> Unit,
    onUpgradeClick: () -> Unit = {},
    viewModel: BusinessViewModel = hiltViewModel()
) {
    val business by viewModel.myBusiness.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val isCreatingAd by viewModel.isCreatingAd.collectAsStateWithLifecycle()
    val currentAd by viewModel.businessAd.collectAsStateWithLifecycle()
    var showCreateAd by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(business?.businessId) {
        business?.businessId?.let { viewModel.fetchBusinessAd(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.adCreationResult.collect { result ->
            when (result) {
                is com.example.mistreal_mini.data.Resource.Success -> {
                    showCreateAd = false
                    business?.businessId?.let { viewModel.fetchBusinessAd(it) }
                    snackbarHostState.showSnackbar("Ad launched — it'll start cycling into rotation.")
                }
                is com.example.mistreal_mini.data.Resource.Error -> {
                    snackbarHostState.showSnackbar(result.message ?: "Failed to launch ad")
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ADVERTISE") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val currentBusiness = business
        if (currentBusiness == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Storefront, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("You need a registered business to advertise.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRegisterBusiness) { Text("Register a Business") }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(currentBusiness.name.uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))

                val ad = currentAd
                if (ad != null) {
                    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("CURRENT AD", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(ad.caption ?: "(no caption)", fontWeight = FontWeight.Bold)
                            Text("${ad.mediaType} • ${ad.durationSeconds}s", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showCreateAd = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Campaign, null)
                        Spacer(Modifier.width(8.dp))
                        Text("REPLACE AD")
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Campaign, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No active ad yet.", color = Color.Gray)
                    }
                    Button(onClick = { showCreateAd = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.RocketLaunch, null)
                        Spacer(Modifier.width(8.dp))
                        Text("CREATE YOUR FIRST AD")
                    }
                }
            }
        }
    }

    if (showCreateAd) {
        business?.let { biz ->
            com.example.mistreal_mini.ui.business.components.CreateAdDialog(
                isPro = isPro,
                isSubmitting = isCreatingAd,
                onDismiss = { showCreateAd = false },
                onUpgradeClick = { showCreateAd = false; onUpgradeClick() },
                onSubmit = { mediaType, durationSeconds, caption, targetUrl, videoUri, imageUris ->
                    viewModel.createAd(
                        businessId = biz.businessId,
                        mediaType = mediaType,
                        durationSeconds = durationSeconds,
                        caption = caption,
                        targetUrl = targetUrl.takeIf { it.isNotBlank() },
                        ctaLabel = "Learn More",
                        videoUri = videoUri,
                        imageUris = imageUris.takeIf { it.isNotEmpty() }
                    )
                }
            )
        }
    }
}
