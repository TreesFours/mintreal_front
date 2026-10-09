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
import com.example.mistreal_mini.ui.dashboard.components.FlowRow
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
    onGuardianAlertsClick: () -> Unit = {},
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
            if (!url.isNullOrBlank()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
                viewModel.clearSocialConnectUrl()
            }
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
            val accordionState = remember { mutableStateOf<String?>("TACTICAL IDENTITY") }
            androidx.compose.runtime.CompositionLocalProvider(LocalSettingsAccordionState provides accordionState) {
            SelectionContainer {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
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

                    // --- SECTION: GOD MODE AI ---
                    SettingsSection(title = "GOD MODE AI", icon = Icons.Default.Bolt) {
                        ProtocolSwitch(
                            title = "God Mode Protocol",
                            desc = "Topmost researcher, zero-mistake fact verification & precision fact pulling.",
                            checked = viewModel.isGodModeEnabled.value,
                            onCheckedChange = { viewModel.setGodModeEnabled(it) }
                        )

                        if (viewModel.isGodModeEnabled.value) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            var tempGodTask by remember { mutableStateOf(viewModel.godModeTask.value) }
                            var tempGodStyle by remember { mutableStateOf(viewModel.godModeStyle.value) }

                            OutlinedTextField(
                                value = tempGodTask,
                                onValueChange = { tempGodTask = it },
                                label = { Text("God Mode Research Task") },
                                placeholder = { Text("e.g. Verify quantum computing benchmarks") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.setGodModeTask(tempGodTask) }) {
                                        Icon(Icons.Default.Check, "Set Task", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = tempGodStyle,
                                onValueChange = { tempGodStyle = it },
                                label = { Text("Explanation Mode (Mode_Human)") },
                                placeholder = { Text("e.g. baby, expert, concise") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                supportingText = { Text("e.g. 'baby' explains deep knowledge in simple terms like to a child.", fontSize = 10.sp, color = Color.Gray) },
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.setGodModeStyle(tempGodStyle) }) {
                                        Icon(Icons.Default.Check, "Set Style", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            )
                        }
                    }

                    // --- SECTION 3: AI MISSION PARAMETERS ---
                    SettingsSection(title = "MISSION PARAMETERS", icon = Icons.Default.Psychology) {
                        ProtocolSwitch(
                            title = "Deep Analysis (High Performance)",
                            desc = "Maximum reasoning depth & exhaustive audit intensity.",
                            checked = viewModel.isDeepAnalysisEnabled.value,
                            onCheckedChange = { viewModel.setDeepAnalysisEnabled(it) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

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
                        // Location Intelligence Switch
                        ProtocolSwitch(
                            title = "Location Intelligence",
                            desc = "Secure local positioning for weather, celestial vectors, and discovery.",
                            checked = localIntelligenceEnabled,
                            onCheckedChange = { enabled ->
                                localIntelligenceEnabled = enabled
                                if (enabled) {
                                    locationPermissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                } else {
                                    viewModel.setLocationEnabled(false)
                                    scope.launch { snackbarHostState.showSnackbar("Location Intelligence Disabled") }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

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

                        // Emergency Contacts — each must confirm/decline via the link sent
                        // to them before they can ever receive real alert content.
                        val emergencyContacts by viewModel.emergencyContacts.collectAsStateWithLifecycle()
                        if (emergencyContacts.isNotEmpty()) {
                            emergencyContacts.forEach { contact ->
                                val statusColor = when (contact.status) {
                                    "confirmed" -> Color(0xFF2E7D32)
                                    "declined" -> Color.Red
                                    else -> Color(0xFFF9A825)
                                }
                                ListItem(
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = {
                                        Text(
                                            "${if (contact.channel == "email") contact.email else contact.platform} · ${contact.status.uppercase()}",
                                            fontSize = 10.sp,
                                            color = statusColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    },
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
                        TextButton(onClick = onGuardianAlertsClick, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("VIEW ALERT HISTORY")
                        }
                    }

                    // --- SECTION 4a: SOCIAL CHANNELS ---
                    SettingsSection(title = "SOCIAL CHANNELS", icon = Icons.Default.Link) {
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
                    }

                    // --- SECTION 4b: BUSINESS ---
                    SettingsSection(title = "BUSINESS", icon = Icons.Default.Storefront) {
                        Button(
                            onClick = onBusinessHubClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f))
                        ) {
                            Icon(Icons.Default.Storefront, null)
                            Spacer(Modifier.width(8.dp))
                            Text("BUSINESS HUB", fontWeight = FontWeight.Bold)
                        }
                    }

                    // --- SECTION 4c: COMMUNITY FEED ---
                    // Off by default; the viewer opts into specific platforms to
                    // see other app users' shared posts.
                    SettingsSection(title = "COMMUNITY FEED", icon = Icons.Default.Public) {
                        Text(
                            "See posts other Mistreal users have chosen to share, from these platforms only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val communityFeedPlatforms by viewModel.communityFeedPlatforms
                        val allPlatforms = viewModel.availablePlatforms.collectAsStateWithLifecycle().value
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            mainAxisSpacing = 8.dp,
                            crossAxisSpacing = 8.dp
                        ) {
                            allPlatforms.forEach { platform ->
                                val isSelected = platform.id in communityFeedPlatforms
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.toggleCommunityFeedPlatform(platform.id) },
                                    label = { Text(platform.name, fontSize = 11.sp) },
                                    leadingIcon = { Text(platform.icon, fontSize = 12.sp) }
                                )
                            }
                        }
                    }

                    // --- SECTION 4d: MISSION DELAY (GHOST MODE) ---
                    SettingsSection(title = "MISSION DELAY (GHOST MODE)", icon = Icons.Default.Timer) {
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
                    }

                    // --- SECTION 4e: VOICE & TRANSLATION ---
                    SettingsSection(title = "VOICE & TRANSLATION", icon = Icons.Default.RecordVoiceOver) {
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

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Speak Out", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "AI replies are read aloud as conversational voice notes, not raw text.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = ttsEnabled,
                                    onCheckedChange = {
                                        ttsEnabled = it
                                        viewModel.setTtsEnabled(it)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Voice Note Autoplay", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        "AI voice-note replies play automatically. Turn off to only play on tap.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                Switch(
                                    checked = viewModel.voiceNoteAutoplay.value,
                                    onCheckedChange = { viewModel.setVoiceNoteAutoplay(it) }
                                )
                            }
                        }
                    }

                    // --- SECTION 4f: COMMAND DECK ---
                    SettingsSection(title = "COMMAND DECK", icon = Icons.Default.Language) {
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

                    Spacer(modifier = Modifier.height(24.dp))
                    ByokSettingsSection(viewModel)

                    Spacer(modifier = Modifier.height(24.dp))
                    ByokVideoSettingsSection(viewModel)

                    Spacer(modifier = Modifier.height(24.dp))
                    MediaGenProviderSection(viewModel, "image_gen", "IMAGE GENERATION PROVIDER", "Our Recommended (Imagen)")

                    Spacer(modifier = Modifier.height(24.dp))
                    MediaGenProviderSection(viewModel, "video_gen", "VIDEO GENERATION PROVIDER", "Our Recommended (Veo)")

                    Spacer(modifier = Modifier.height(24.dp))
                    MarketAlertsSection(viewModel)

                    Spacer(modifier = Modifier.height(24.dp))
                    BankChannelsSection(viewModel)

                    Spacer(modifier = Modifier.height(24.dp))
                    VerifiedFacesSection(viewModel)

                    // Secure Changes Footer
                    Button(
                        onClick = {
                            viewModel.saveSettings(
                                userName,
                                selectedPersona,
                                selectedAudience,
                                calculateDelayMinutes(selectedDelay, customDelayValue, customDelayUnit),
                                viewModel.guardianEnabled.value,
                                aiCustomName = aiCustomName,
                                aiAutoSendEnabled = viewModel.aiAutoSendEnabled.value
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

// One section open at a time, shared across every SettingsSection call in this
// screen — including the ones inside ByokSettingsSection.kt/MediaGenProviderSection,
// since they call this same composable. A CompositionLocal avoids threading an
// expand-state parameter through four separate wrapper functions just for this.
val LocalSettingsAccordionState = compositionLocalOf { mutableStateOf<String?>(null) }

@Composable
fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val expandedState = LocalSettingsAccordionState.current
    val isExpanded = expandedState.value == title

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable {
                expandedState.value = if (isExpanded) null else title
            }
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = Color.Gray
            )
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = isExpanded,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
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

