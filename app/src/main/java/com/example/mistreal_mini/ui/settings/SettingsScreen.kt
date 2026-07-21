package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var userName by remember { mutableStateOf("User") }
    var selectedPersona by remember { mutableStateOf("Shadow") }
    var selectedDelay by remember { mutableStateOf("15m") }
    val personas = listOf("Shadow", "Oracle", "Companion")
    
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        userName = viewModel.getUserName()
        selectedPersona = viewModel.getAiPersona()
        val currentDelay = viewModel.getAutoReplyDelay()
        selectedDelay = when(currentDelay) {
            0 -> "None"
            15 -> "15m"
            60 -> "1h"
            1440 -> "1d"
            else -> "Custom"
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveSuccess.collectLatest {
            snackbarHostState.showSnackbar("Changes Secured Successfully")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.socialConnectUrl.collectLatest { url ->
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        }
    }

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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 👤 User Identity
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = userName,
                    onValueChange = { userName = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }

            // 🎭 AI Persona
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI Persona", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                personas.forEach { persona ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (persona == selectedPersona),
                            onClick = { selectedPersona = persona }
                        )
                        Text(
                            text = persona,
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // ⏳ Auto-Reply Delay
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Reply Delay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                val delayOptions = listOf("None", "15m", "1h", "1d")
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    delayOptions.forEach { option ->
                        FilterChip(
                            selected = (option == selectedDelay),
                            onClick = { selectedDelay = option },
                            label = { Text(option) }
                        )
                    }
                }
            }

            // 📊 Quota Status
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Monthly Quota", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { 0.4f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "40 / 100 messages used",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 🔗 Connected Accounts
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connected Accounts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                AccountRow("Twitter", Icons.Default.Public, onConnect = { viewModel.connectSocial("twitter") })
                AccountRow("WhatsApp", Icons.Default.Chat, onConnect = { viewModel.connectSocial("whatsapp") })
                AccountRow("Instagram", Icons.Default.CameraAlt, onConnect = { viewModel.connectSocial("instagram") })
            }

            Button(
                onClick = { 
                    val delayMinutes = when(selectedDelay) {
                        "None" -> 0
                        "15m" -> 15
                        "1h" -> 60
                        "1d" -> 1440
                        else -> 15
                    }
                    viewModel.saveSettings(userName, selectedPersona, delayMinutes)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeDefaults.Medium
            ) {
                Text("Secure Changes")
            }
        }
    }
}

@Composable
fun AccountRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onConnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onConnect) {
            Text("Connect")
        }
    }
}
