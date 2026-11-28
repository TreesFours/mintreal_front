package com.example.mistreal_mini.ui.browser

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Proxy (SOCKS5/HTTP) for the in-app browser only — applied per-WebView via
 * androidx.webkit's ProxyController (BrowserViewModel.applyProxyFromSettings),
 * not a system-wide VPN. Nothing outside the browser screen is affected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSettingsScreen(
    onBack: () -> Unit,
    viewModel: BrowserSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var type by remember { mutableStateOf(settings.type) }
    var host by remember { mutableStateOf(settings.host) }
    var port by remember { mutableStateOf(settings.port.toString()) }
    var hasLoadedOnce by remember { mutableStateOf(false) }

    // Seed the editable fields from stored settings exactly once they arrive
    // (StateFlow starts at the default BrowserProxySettings() before the
    // DataStore read completes) — not on every emission, or the user's own
    // in-progress typing would get clobbered by a redundant re-collection.
    LaunchedEffect(settings, hasLoadedOnce) {
        if (!hasLoadedOnce) {
            type = settings.type
            host = settings.host
            port = settings.port.toString()
            hasLoadedOnce = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browser Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text("Proxy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Routes the in-app browser's traffic through a proxy you control. This only affects the Browser screen — nothing else in the app.",
                style = MaterialTheme.typography.bodySmall,
                color = androidx.compose.ui.graphics.Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("none" to "Off", "http" to "HTTP", "socks5" to "SOCKS5").forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = type == value,
                        onClick = { type = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3)
                    ) { Text(label) }
                }
            }

            if (type != "none") {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Proxy host") },
                    placeholder = { Text("e.g. 10.0.0.5") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { new -> if (new.all { it.isDigit() }) port = new },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { viewModel.save(type, host, port.toIntOrNull() ?: 1080) },
                enabled = type == "none" || host.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}
