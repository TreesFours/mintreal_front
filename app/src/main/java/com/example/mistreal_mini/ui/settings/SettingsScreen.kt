package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.scale
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
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
    var selectedAudience by remember { mutableStateOf("None") }
    var customPersonaText by remember { mutableStateOf("") }
    var selectedDelay by remember { mutableStateOf("") }
    var customDelayValue by remember { mutableStateOf("15") }
    var customDelayUnit by remember { mutableStateOf("m") }
    var localIntelligenceEnabled by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(true) }
    var sttEnabled by remember { mutableStateOf(true) }
    var showSocialAuth by remember { mutableStateOf(false) }

    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val defaultPersonas = listOf("Shadow", "Oracle", "Companion", "Standard", "None")
    val defaultAudiences = listOf("None", "General Public", "Tactical & Operations", "Technical / Developer", "Casual & Friendly", "Executive / Professional")
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
        selectedAudience = viewModel.getAiAudience()
        localIntelligenceEnabled = viewModel.isLocationEnabled()
        ttsEnabled = viewModel.isTtsEnabled()
        sttEnabled = viewModel.isSttEnabled()
        val currentDelay = viewModel.getAutoReplyDelay()
        selectedDelay = when(currentDelay) {
            0 -> "None"
            15 -> "15m"
            60 -> "1h"
            1440 -> "1d"
            else -> {
                customDelayValue = currentDelay.toString()
                customDelayUnit = "m"
                "Custom"
            }
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

                        // Supportive Truth-Teller & Therapist Toggle
                        var isTruthTellerEnabled by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            isTruthTellerEnabled = viewModel.isSupportiveTruthTellerEnabled.value
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Supportive Truth-Teller & Therapist", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Honest objective truth paired with empathetic guidance.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isTruthTellerEnabled,
                                    onCheckedChange = { 
                                        isTruthTellerEnabled = it
                                        viewModel.setSupportiveTruthTellerEnabled(it)
                                    }
                                )
                            }
                            
                            if (isTruthTellerEnabled) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                Text("Advanced Protocols", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                
                                // Wellness Shield
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("Wellness Shield (Stress/Progress Monitoring)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = viewModel.isWellnessShieldEnabled.value,
                                        onCheckedChange = { viewModel.setWellnessShieldEnabled(it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                                
                                // Proactive Nudge
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("Proactive Nudge (24h Check-in)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = viewModel.isProactiveNudgeEnabled.value,
                                        onCheckedChange = { viewModel.setProactiveNudgeEnabled(it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                                
                                // Intelligence Spark
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("Intelligence Spark (Natural Feed Suggestions)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    Switch(
                                        checked = viewModel.isIntelligenceSparkEnabled.value,
                                        onCheckedChange = { viewModel.setIntelligenceSparkEnabled(it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
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

                    // AI Audience
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Audience", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp)
                            ) {
                                items(defaultAudiences) { audience ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        RadioButton(selected = (audience == selectedAudience), onClick = { selectedAudience = audience })
                                        Text(text = audience, modifier = Modifier.padding(start = 8.dp).weight(1f))
                                        if (audience == selectedAudience) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Creative Labs
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Creative Labs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Persistent Scene Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Always show Start/End frame slots in the attachment bar.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            }
                            Switch(
                                checked = viewModel.isPersistentSceneModeEnabled.value,
                                onCheckedChange = { viewModel.setPersistentSceneModeEnabled(it) }
                            )
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

                    // ⏳ Mission Delay (Auto-Reply)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mission Delay (Auto-Reply)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text("Hold incoming social replies for:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var showDelayPicker by remember { mutableStateOf(false) }
                        val delayOptions = listOf("None", "15m", "1h", "1d", "Custom")
                        
                        OutlinedCard(
                            onClick = { showDelayPicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (selectedDelay == "Custom") "$customDelayValue$customDelayUnit (Custom)" else selectedDelay, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.KeyboardArrowDown, null)
                            }
                        }
                        
                        if (selectedDelay == "Custom") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = customDelayValue,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) customDelayValue = it },
                                    label = { Text("Value") },
                                    modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                )
                                var showUnitPicker by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(onClick = { showUnitPicker = true }, modifier = Modifier.fillMaxWidth()) {
                                        Text(when(customDelayUnit) { "s" -> "Seconds"; "m" -> "Minutes"; "h" -> "Hours"; else -> "Minutes" })
                                    }
                                    DropdownMenu(expanded = showUnitPicker, onDismissRequest = { showUnitPicker = false }) {
                                        listOf("s", "m", "h").forEach { unit ->
                                            DropdownMenuItem(
                                                text = { Text(when(unit) { "s" -> "Seconds"; "m" -> "Minutes"; "h" -> "Hours"; else -> unit }) },
                                                onClick = { customDelayUnit = unit; showUnitPicker = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (showDelayPicker) {
                            AlertDialog(
                                onDismissRequest = { showDelayPicker = false },
                                title = { Text("Select Delay") },
                                text = {
                                    Column {
                                        delayOptions.forEach { opt ->
                                            TextButton(onClick = { selectedDelay = opt; showDelayPicker = false }, modifier = Modifier.fillMaxWidth()) {
                                                Text(opt, textAlign = androidx.compose.ui.text.style.TextAlign.Start, modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showDelayPicker = false }) { Text("Cancel") } }
                            )
                        }
                    }

                    // 🚨 Emergency Contacts Management
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Emergency Protocols", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        val emergencyContacts = viewModel.emergencyContacts
                        if (emergencyContacts.isNotEmpty()) {
                            emergencyContacts.forEach { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = { Text("${contact.type}: ${contact.value}") },
                                    trailingContent = {
                                        IconButton(onClick = { 
                                            viewModel.removeEmergencyContact(contact)
                                        }) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f)) }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                )
                            }
                        } else {
                            Text("No emergency contacts added.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var showAddDialog by remember { mutableStateOf(false) }
                        if (showAddDialog) {
                            AddEmergencyContactDialog(
                                viewModel = viewModel,
                                onDismiss = { showAddDialog = false }
                            )
                        }

                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                        ) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Emergency Contact")
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
                                                IconButton(onClick = { 
                                                    viewModel.disconnectSocial(platform.id)
                                                }) {
                                                    Icon(Icons.Default.LinkOff, "Disconnect", tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                                }
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
                                "Custom" -> {
                                    val value = customDelayValue.toIntOrNull() ?: 15
                                    when(customDelayUnit) {
                                        "s" -> 0 // Backend currently rounds to minutes, we'd need to update it for seconds
                                        "m" -> value
                                        "h" -> value * 60
                                        else -> value
                                    }
                                }
                                else -> 15
                            }
                            viewModel.saveSettings(
                                userName, 
                                finalPersona,
                                selectedAudience,
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

@Composable
fun AddEmergencyContactDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualPhone by remember { mutableStateOf("") }
    
    val socialContacts by viewModel.recentSocialContacts.collectAsState(initial = emptyList())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Emergency Contact") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Socials", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Phone", modifier = Modifier.padding(8.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (selectedTab == 0) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search linked contacts...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        val filtered = socialContacts.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        if (filtered.isEmpty()) {
                            item { Text("No linked contacts found.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
                        } else {
                            items(filtered) { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = { Text(contact.platform) },
                                    modifier = Modifier.clickable { 
                                        viewModel.addEmergencyContact(com.example.mistreal_mini.data.api.EmergencyContact(
                                            name = contact.name,
                                            type = "social",
                                            value = "${contact.platform}:${contact.contactId}"
                                        ))
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualName,
                            onValueChange = { manualName = it },
                            label = { Text("Contact Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualPhone,
                            onValueChange = { manualPhone = it },
                            label = { Text("Phone Number") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                        )
                        Button(
                            onClick = {
                                if (manualName.isNotBlank() && manualPhone.isNotBlank()) {
                                    viewModel.addEmergencyContact(com.example.mistreal_mini.data.api.EmergencyContact(
                                        name = manualName,
                                        type = "phone",
                                        value = manualPhone
                                    ))
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualName.isNotBlank() && manualPhone.isNotBlank()
                        ) {
                            Text("Secure Emergency Contact")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
