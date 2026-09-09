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
import com.example.mistreal_mini.ui.settings.components.AddEmergencyContactDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.selection.SelectionContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onUpgradeClick: () -> Unit = {}, 
    onConnectionsClick: () -> Unit = {},
    onBusinessHubClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    billingViewModel: com.example.mistreal_mini.ui.subscription.SubscriptionViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    var userName by remember { mutableStateOf("") }
    var aiCustomName by remember { mutableStateOf("") }
    var selectedPersona by remember { mutableStateOf("") }
    var selectedAudience by remember { mutableStateOf("None") }
    var customPersonaText by remember { mutableStateOf("") }
    var customAudienceText by remember { mutableStateOf("") }
    var selectedDelay by remember { mutableStateOf("") }
    var customDelayValue by remember { mutableStateOf("15") }
    var customDelayUnit by remember { mutableStateOf("m") }
    var localIntelligenceEnabled by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(true) }
    var sttEnabled by remember { mutableStateOf(true) }
    var translationLang by remember { mutableStateOf("English") }
    var selectedTheme by remember { mutableStateOf("auto") }
    var showSocialAuth by remember { mutableStateOf(false) }

    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val defaultPersonas = listOf("Shadow", "Oracle", "Architect", "Companion", "Standard", "None")
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
        viewModel.fetchPlatforms()
        userName = viewModel.getUserName()
        aiCustomName = viewModel.getAiCustomName()
        selectedPersona = viewModel.getAiPersona()
        selectedAudience = viewModel.getAiAudience()
        localIntelligenceEnabled = viewModel.isLocationEnabled()
        ttsEnabled = viewModel.isTtsEnabled()
        sttEnabled = viewModel.isSttEnabled()
        translationLang = viewModel.getDefaultTranslationLang()
        selectedTheme = viewModel.getThemeMode()
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
        viewModel.closeAppEvent.collectLatest {
            (context as? android.app.Activity)?.finishAndRemoveTask()
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
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    // --- SECTION 1: OPERATOR & AI IDENTITY ---
                    SettingsSection(title = "TACTICAL IDENTITY", icon = Icons.Default.Badge) {
                        OutlinedTextField(
                            value = userName,
                            onValueChange = { if (it.length <= 20) userName = it },
                            label = { Text("Operator Handle (You)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("${userName.length}/20", textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { 
                                viewModel.saveSettings(userName, selectedPersona, selectedAudience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                focusManager.clearFocus() 
                            })
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = aiCustomName,
                            onValueChange = { if (it.length <= 15) aiCustomName = it },
                            label = { Text("AI Designation (System)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            supportingText = { Text("${aiCustomName.length}/15", textAlign = androidx.compose.ui.text.style.TextAlign.End, modifier = Modifier.fillMaxWidth()) },
                            placeholder = { Text("e.g. Shadow AI") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { 
                                viewModel.saveSettings(userName, selectedPersona, selectedAudience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                focusManager.clearFocus() 
                            })
                        )
                    }

                    // --- SECTION 2: VISUAL ENVIRONMENT ---
                    SettingsSection(title = "VISUAL ENVIRONMENT", icon = Icons.Default.Palette) {
                        Text("Current Atmosphere", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeCard(
                                title = "FIRE", 
                                icon = Icons.Default.Whatshot, 
                                isSelected = selectedTheme == "fire", 
                                onClick = { 
                                    selectedTheme = "fire"
                                    viewModel.setThemeMode("fire") 
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeCard(
                                title = "SAND", 
                                icon = Icons.Default.WbSunny, 
                                isSelected = selectedTheme == "sand", 
                                onClick = { 
                                    selectedTheme = "sand"
                                    viewModel.setThemeMode("sand")
                                },
                                modifier = Modifier.weight(1f)
                            )
                            ThemeCard(
                                title = "AUTO", 
                                icon = Icons.Default.BrightnessAuto, 
                                isSelected = selectedTheme == "auto", 
                                onClick = { 
                                    selectedTheme = "auto"
                                    viewModel.setThemeMode("auto")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "AUTO mode follows your system-wide Dark/Light mode settings.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }

                    // --- SECTION 3: AI MISSION PARAMETERS ---
                    SettingsSection(title = "MISSION PARAMETERS", icon = Icons.Default.Psychology) {
                        Text("Current Behavior Protocol", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Selectable List with Inner Scroll & Delete
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val allPersonas: List<String> = defaultPersonas + customPersonas + listOf("Random")
                            LazyColumn(modifier = Modifier.padding(4.dp)) {
                                items(allPersonas) { persona ->
                                    ListItem(
                                        headlineContent = { Text(persona, fontWeight = if(persona == selectedPersona) FontWeight.Bold else FontWeight.Normal) },
                                        leadingContent = { 
                                            RadioButton(
                                                selected = (persona == selectedPersona), 
                                                onClick = { 
                                                    selectedPersona = persona
                                                    viewModel.saveSettings(userName, persona, selectedAudience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                                }
                                            )
                                        },
                                        trailingContent = {
                                            if (persona == "Random" && selectedPersona == "Random") {
                                                IconButton(onClick = { showRandomFreqDialog = true }) {
                                                    Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                            if (persona in customPersonas) {
                                                IconButton(onClick = { 
                                                    viewModel.deleteCustomPersona(persona)
                                                    if (selectedPersona == persona) selectedPersona = "Standard"
                                                }) {
                                                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable { 
                                            selectedPersona = persona
                                            viewModel.saveSettings(userName, persona, selectedAudience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Column {
                            OutlinedTextField(
                                value = customPersonaText,
                                onValueChange = { customPersonaText = it },
                                label = { Text("Custom Behavior Protocol") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. Aggressive Researcher") },
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (customPersonaText.isNotBlank()) {
                                            viewModel.addCustomPersona(customPersonaText)
                                            selectedPersona = customPersonaText
                                            customPersonaText = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("ADD")
                                }
                                
                                Button(
                                    onClick = {
                                        if (customPersonaText.isNotBlank()) {
                                            selectedPersona = customPersonaText
                                            viewModel.saveSettings(userName, customPersonaText, selectedAudience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("SET")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        Text("Target Audience Bias", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val customAudiences by viewModel.customAudiences.collectAsStateWithLifecycle()
                            val allAudiences = defaultAudiences + customAudiences
                            LazyColumn(modifier = Modifier.padding(4.dp)) {
                                items(allAudiences) { audience ->
                                    ListItem(
                                        headlineContent = { Text(audience, fontWeight = if(audience == selectedAudience) FontWeight.Bold else FontWeight.Normal) },
                                        leadingContent = { 
                                            RadioButton(
                                                selected = (audience == selectedAudience), 
                                                onClick = { 
                                                    selectedAudience = audience
                                                    viewModel.saveSettings(userName, selectedPersona, audience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                                }
                                            )
                                        },
                                        trailingContent = {
                                            if (audience in customAudiences) {
                                                IconButton(onClick = { 
                                                    viewModel.deleteCustomAudience(audience)
                                                    if (selectedAudience == audience) selectedAudience = "None"
                                                }) {
                                                    Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        },
                                        modifier = Modifier.clickable { 
                                            selectedAudience = audience
                                            viewModel.saveSettings(userName, selectedPersona, audience, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Column {
                            OutlinedTextField(
                                value = customAudienceText,
                                onValueChange = { customAudienceText = it },
                                label = { Text("Custom Audience Bias") },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("e.g. Deep Space Experts") },
                                shape = RoundedCornerShape(12.dp)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (customAudienceText.isNotBlank()) {
                                            viewModel.addCustomAudience(customAudienceText)
                                            selectedAudience = customAudienceText
                                            customAudienceText = ""
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("ADD")
                                }
                                
                                Button(
                                    onClick = {
                                        if (customAudienceText.isNotBlank()) {
                                            selectedAudience = customAudienceText
                                            viewModel.saveSettings(userName, selectedPersona, customAudienceText, calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), aiCustomName = aiCustomName)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("SET")
                                }
                            }
                        }
                    }

                    // --- SECTION 3: GUARDIAN & PRIVACY ---
                    SettingsSection(title = "GUARDIAN PROTOCOLS", icon = Icons.Default.Security) {
                        // Supportive Truth-Teller
                        ProtocolSwitch(
                            title = "Supportive Truth-Teller",
                            desc = "Honest objective truth paired with empathetic guidance.",
                            checked = viewModel.isSupportiveTruthTellerEnabled.value,
                            onCheckedChange = { viewModel.setSupportiveTruthTellerEnabled(it) }
                        )

                        if (viewModel.isSupportiveTruthTellerEnabled.value) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(modifier = Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ProtocolSwitch("Wellness Shield", "Stress/Progress Monitoring", viewModel.isWellnessShieldEnabled.value, { viewModel.setWellnessShieldEnabled(it) }, true)
                                ProtocolSwitch("Proactive Nudge", "24h Emotional Check-in", viewModel.isProactiveNudgeEnabled.value, { viewModel.setProactiveNudgeEnabled(it) }, true)
                                ProtocolSwitch("Intelligence Spark", "Contextual Feed Suggestions", viewModel.isIntelligenceSparkEnabled.value, { viewModel.setIntelligenceSparkEnabled(it) }, true)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

                        // Emergency Contacts
                        val emergencyContacts = viewModel.emergencyContacts
                        if (emergencyContacts.isNotEmpty()) {
                            emergencyContacts.forEach { contact ->
                                ListItem(
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = { Text("${contact.type}: ${contact.value}", fontSize = 10.sp) },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.removeEmergencyContact(contact) }) { 
                                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) 
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                )
                            }
                        }
                        
                        var showAddDialog by remember { mutableStateOf(false) }
                        if (showAddDialog) {
                            AddEmergencyContactDialog(
                                viewModel = viewModel,
                                onDismiss = { showAddDialog = false }
                            )
                        }
                        
                        TextButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CONFIGURE EMERGENCY NODES")
                        }
                    }

                    // --- SECTION 4: COMMUNICATIONS HUB ---
                    SettingsSection(title = "COMMUNICATIONS HUB", icon = Icons.Default.Link) {
                        // Social Channels
                        val connectedPlatforms = viewModel.availablePlatforms.collectAsStateWithLifecycle().value.filter { it.isConnected }
                        if (connectedPlatforms.isNotEmpty()) {
                            Text("ACTIVE SOCIAL CHANNELS", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Surface(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                LazyColumn(modifier = Modifier.padding(4.dp)) {
                                    items(connectedPlatforms) { platform ->
                                        ListItem(
                                            headlineContent = { Text(platform.name) },
                                            leadingContent = { Text(platform.icon, fontSize = 18.sp) },
                                            trailingContent = { 
                                                IconButton(onClick = { viewModel.disconnectSocial(platform.id) }) {
                                                    Icon(Icons.Default.LinkOff, null, tint = Color.Red.copy(alpha = 0.6f), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Button(
                            onClick = onConnectionsClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.AddLink, null)
                            Spacer(Modifier.width(8.dp))
                            Text("LINK NEW ACCOUNT", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Mission Delay
                        Text("MISSION DELAY (GHOST MODE)", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        var showDelayPicker by remember { mutableStateOf(false) }
                        var customDelayInput by remember { mutableStateOf(customDelayValue) }
                        
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (selectedDelay == "Custom") "${customDelayInput}m" else selectedDelay, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { showDelayPicker = !showDelayPicker }) {
                                        Icon(if(showDelayPicker) Icons.Default.Close else Icons.Default.Timer, null)
                                    }
                                }
                                
                                if (showDelayPicker) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = customDelayInput,
                                            onValueChange = { if(it.all { char -> char.isDigit() }) customDelayInput = it },
                                            label = { Text("Minutes") },
                                            modifier = Modifier.weight(1f),
                                            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        Button(
                                            onClick = {
                                                val mins = customDelayInput.toIntOrNull() ?: 15
                                                viewModel.saveSettings(userName, selectedPersona, selectedAudience, mins, aiCustomName = aiCustomName)
                                                selectedDelay = "Custom"
                                                customDelayValue = customDelayInput
                                                showDelayPicker = false
                                                focusManager.clearFocus()
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("SET")
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        listOf("None", "15m", "1h", "1d").forEach { preset ->
                                            FilterChip(
                                                selected = selectedDelay == preset,
                                                onClick = { 
                                                    selectedDelay = preset
                                                    val mins = calculateDelayMinutes(preset, "15", "m")
                                                    viewModel.saveSettings(userName, selectedPersona, selectedAudience, mins, aiCustomName = aiCustomName)
                                                    showDelayPicker = false
                                                },
                                                label = { Text(preset) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Voice & Translation
                        Text("VOICE & TRANSLATION", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        var showVoicePicker by remember { mutableStateOf(false) }
                        val selectedVoiceName by viewModel.selectedVoiceName.collectAsStateWithLifecycle()
                        val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()

                        OutlinedCard(onClick = { showVoicePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("AI Voice", style = MaterialTheme.typography.labelSmall)
                                    Text(selectedVoiceName ?: "System Default", style = MaterialTheme.typography.bodyMedium)
                                }
                                Icon(Icons.Default.RecordVoiceOver, null)
                            }
                        }

                        if (showVoicePicker) {
                            AlertDialog(
                                onDismissRequest = { showVoicePicker = false },
                                title = { Text("Select AI Voice") },
                                text = {
                                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                                        LazyColumn {
                                            if (availableVoices.isEmpty()) {
                                                item { Text("No tactical voices found.", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                                            }
                                            items(availableVoices) { voice ->
                                                ListItem(
                                                    headlineContent = { Text(voice.name) },
                                                    supportingContent = { Text(voice.locale.displayName) },
                                                    modifier = Modifier.clickable { 
                                                        viewModel.setSelectedVoice(voice)
                                                        showVoicePicker = false
                                                    },
                                                    trailingContent = {
                                                        if (voice.name == selectedVoiceName) {
                                                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("Close") } }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // External Command Deck
                        Text("COMMAND DECK", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        Button(
                            onClick = { 
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://mistreal-console.com"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.Language, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("EXTERNAL MISSION CONTROL", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Secure Changes Footer
                    Button(
                        onClick = { 
                            viewModel.saveSettings(
                                userName, 
                                selectedPersona,
                                selectedAudience,
                                calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit), 
                                viewModel.guardianEnabled.value,
                                viewModel.emergencyContacts.toList(),
                                aiCustomName = aiCustomName
                            )
                        },
                        enabled = !isSaving && userName.length >= 3,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        else Text("SECURE MISSION SETTINGS", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.2f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
            Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray)
        }
    }
}

@Composable
fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
fun ProtocolSwitch(title: String, desc: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, isSub: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = if(isSub) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(desc, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            modifier = if(isSub) Modifier.scale(0.7f) else Modifier
        )
    }
}

private fun calculateDelayMinutes(selectedDelay: String, customValue: String, customUnit: String): Int {
    return when(selectedDelay) {
        "None" -> 0
        "15m" -> 15
        "1h" -> 60
        "1d" -> 1440
        "Custom" -> {
            val value = customValue.toIntOrNull() ?: 15
            when(customUnit) {
                "s" -> 0
                "m" -> value
                "h" -> value * 60
                else -> value
            }
        }
        else -> 15
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
