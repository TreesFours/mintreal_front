package com.example.mistreal_mini.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.SelectionContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit = {}, 
    onConnectionsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    billingViewModel: com.example.mistreal_mini.ui.subscription.SubscriptionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var userName by remember { mutableStateOf("") }
    var selectedPersona by remember { mutableStateOf("") }
    var customPersonaText by remember { mutableStateOf("") }
    var selectedDelay by remember { mutableStateOf("") }
    var localIntelligenceEnabled by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(true) }
    var sttEnabled by remember { mutableStateOf(true) }
    var showSocialAuth by remember { mutableStateOf(false) }

    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val defaultPersonas = listOf("Shadow", "Oracle", "Companion", "Standard")
    val customPersonas by viewModel.customPersonas.collectAsStateWithLifecycle()
    var showRandomFreqDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // TTS Reference for loading voices
    val tts = remember { 
        var ttsObj: android.speech.tts.TextToSpeech? = null
        ttsObj = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                viewModel.loadVoices(ttsObj)
            }
        }
        ttsObj
    }

    LaunchedEffect(Unit) {
        userName = viewModel.getUserName()
        selectedPersona = viewModel.getAiPersona()
        localIntelligenceEnabled = viewModel.isLocationEnabled()
        ttsEnabled = viewModel.isTtsEnabled()
        sttEnabled = viewModel.isSttEnabled()
        val currentDelay = viewModel.getAutoReplyDelay()
        selectedDelay = when(currentDelay) {
            0 -> "None"
            15 -> "15m"
            60 -> "1h"
            1440 -> "1d"
            else -> "15m"
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            localIntelligenceEnabled = true
            viewModel.setLocationEnabled(true)
            scope.launch { snackbarHostState.showSnackbar("Location Intelligence Secured") }
        } else {
            localIntelligenceEnabled = false
            viewModel.setLocationEnabled(false)
            scope.launch { snackbarHostState.showSnackbar("Location permission required for local data") }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collectLatest {
            snackbarHostState.showSnackbar("Changes Secured Successfully")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvent.collectLatest { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.socialConnectUrl.collectLatest { url ->
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        }
    }

    if (showRandomFreqDialog) {
        AlertDialog(
            onDismissRequest = { showRandomFreqDialog = false },
            title = { Text("Randomize Frequency") },
            text = { Text("How often should the AI cycle through personas?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.saveRandomFreq("often")
                    showRandomFreqDialog = false 
                }) { Text("Often (2 Days)") }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.saveRandomFreq("rarely")
                    showRandomFreqDialog = false 
                }) { Text("Rarely (2+ Days)") }
            }
        )
    }

    if (showSocialAuth) {
        SocialAuthScreen(
            onBack = { showSocialAuth = false },
            onUpgradeClick = onUpgradeClick,
            viewModel = viewModel
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Agent Settings") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            },
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
        ) { padding ->
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Identity
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { if (it.length <= 20) userName = it },
                            label = { Text("Display Name") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                        )
                    }

                    // AI Persona
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Persona", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Selectable List with Inner Scroll & Delete
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            val allPersonas: List<String> = defaultPersonas + customPersonas + listOf("Random")
                            LazyColumn(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                items(allPersonas) { persona ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(selected = (persona == selectedPersona), onClick = { selectedPersona = persona })
                                        Text(text = persona, modifier = Modifier.padding(start = 8.dp).weight(1f))
                                        
                                        if (persona == "Random" && selectedPersona == "Random") {
                                            IconButton(onClick = { showRandomFreqDialog = true }) {
                                                Icon(Icons.Default.Settings, "Random Freq", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        // Delete button for custom personas
                                        if (persona in customPersonas) {
                                            IconButton(onClick = { 
                                                viewModel.deleteCustomPersona(persona)
                                                if (selectedPersona == persona) selectedPersona = "Standard"
                                            }) {
                                                Icon(Icons.Default.Delete, "Delete", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = customPersonaText,
                            onValueChange = { customPersonaText = it },
                            label = { Text("Custom Persona Name or Behavior...") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { selectedPersona = customPersonaText },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                            ) {
                                Text("Set")
                            }
                            Button(
                                onClick = { 
                                    if (customPersonaText.isNotBlank()) {
                                        viewModel.addCustomPersona(customPersonaText)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Add")
                            }
                        }
                    }

                    // Voice Preferences
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Voice & Language", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // TTS Language/Voice Picker
                        var showVoicePicker by remember { mutableStateOf(false) }
                        val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
                        val selectedVoiceName by viewModel.selectedVoiceName.collectAsStateWithLifecycle()

                        OutlinedCard(
                            onClick = { showVoicePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("AI Language & Tone", style = MaterialTheme.typography.labelMedium)
                                    Text(selectedVoiceName ?: "Default System Voice", style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                        }

                        if (showVoicePicker) {
                            AlertDialog(
                                onDismissRequest = { showVoicePicker = false },
                                title = { Text("Select Voice") },
                                text = {
                                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                        items(availableVoices) { voice ->
                                            TextButton(
                                                onClick = { 
                                                    viewModel.setSelectedVoice(voice)
                                                    showVoicePicker = false 
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(voice.name, textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("Close") } }
                            )
                        }
                    }

                    // Location Intelligence
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Location Intelligence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Local Weather & News", modifier = Modifier.weight(1f))
                                Switch(
                                    checked = localIntelligenceEnabled,
                                    onCheckedChange = { isChecked ->
                                        if (isChecked) {
                                            locationPermissionLauncher.launch(arrayOf(
                                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                                            ))
                                        } else {
                                            localIntelligenceEnabled = false
                                            viewModel.setLocationEnabled(false)
                                        }
                                    }
                                )
                            }
                            
                            // 📡 System Location Check
                            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                            
                            if (localIntelligenceEnabled && !isGpsEnabled) {
                                Text(
                                    text = "⚠️ Please enable device location (GPS) manually in system settings for full intelligence.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // Connected Accounts
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Connected Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        
                        val connectedPlatforms = viewModel.availablePlatforms.collectAsStateWithLifecycle().value.filter { it.isConnected }
                        
                        if (connectedPlatforms.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 150.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.padding(4.dp)) {
                                    items(connectedPlatforms) { platform ->
                                        ListItem(
                                            headlineContent = { Text(platform.name, style = MaterialTheme.typography.bodyMedium) },
                                            leadingContent = { Text(platform.icon, fontSize = 18.sp) },
                                            trailingContent = { 
                                                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                                            },
                                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text("No accounts linked.", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { showSocialAuth = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Text("Link New Connection")
                        }
                    }

                    Button(
                        onClick = { 
                            val finalPersona = if (selectedPersona == "Custom") customPersonaText else selectedPersona
                            val delayMinutes = when(selectedDelay) {
                                "None" -> 0
                                "15m" -> 15
                                "1h" -> 60
                                "1d" -> 1440
                                else -> 15
                            }
                            viewModel.saveSettings(
                                userName, 
                                finalPersona,
                                delayMinutes, 
                                viewModel.guardianEnabled.value,
                                viewModel.emergencyContacts.toList()
                            )
                        },
                        enabled = !isSaving && userName.length >= 3,
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeDefaults.Medium
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Secure Changes")
                        }
                    }
                }
            }
        }
    }
}
