package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.data.api.CloudStorageConfig
import kotlinx.coroutines.flow.collectLatest

/**
 * BYOK cloud storage — connect your own WebDAV account (Nextcloud, Box,
 * etc.) and app-generated files (ad media, review voice notes, business
 * photos) go there instead of our Firebase bucket, via the backend's
 * persistToStorage dispatcher. Same multi-slot add/activate/delete pattern
 * as MediaGenProviderSection's AI provider configs (ByokSettingsSection.kt)
 * — only one account active at a time.
 */
@Composable
fun CloudStorageSettingsSection(viewModel: CloudStorageViewModel = hiltViewModel()) {
    val configs by viewModel.configs.collectAsState()
    val files by viewModel.files.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isLoadingFiles by viewModel.isLoadingFiles.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isAdding by remember { mutableStateOf(false) }
    var showFiles by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadConfigs() }
    LaunchedEffect(Unit) {
        viewModel.errorEvent.collectLatest { snackbarHostState.showSnackbar(it) }
    }

    SettingsSection(title = "CLOUD STORAGE (BRING YOUR OWN ACCOUNT)", icon = Icons.Default.Cloud) {
        SnackbarHost(snackbarHostState)

        Text(
            "Connect your own WebDAV storage (Nextcloud, Box, or similar) — app-generated files go there instead of our servers once an account is active below.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (configs.isEmpty()) {
            Text("No cloud storage account connected yet.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        } else {
            configs.forEach { config -> CloudStorageConfigRow(config, viewModel) }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!isAdding) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { isAdding = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ADD ACCOUNT")
                }
                if (configs.any { it.isActive }) {
                    TextButton(onClick = {
                        showFiles = !showFiles
                        if (showFiles) viewModel.loadFiles()
                    }) { Text(if (showFiles) "HIDE FILES" else "SEE FILES") }
                }
            }
        } else {
            val canAddCloudConfig = !isSaving && label.isNotBlank() && baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
            // Shared by the SAVE button and the password field's keyboard Done action.
            val onAddCloudConfig: () -> Unit = {
                viewModel.addConfig(label, baseUrl, username, password)
                label = ""; baseUrl = ""; username = ""; password = ""
                isAdding = false
            }
            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text("Name (e.g. \"My Nextcloud\")") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("Server URL (e.g. https://cloud.example.com/remote.php/dav/files/me)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password / app token") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canAddCloudConfig) onAddCloudConfig() })
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddCloudConfig,
                    enabled = canAddCloudConfig,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSaving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    else Text("SAVE")
                }
                TextButton(onClick = { isAdding = false }) { Text("Cancel") }
            }
        }

        if (showFiles) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            if (isLoadingFiles) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else if (files.isEmpty()) {
                Text("No files yet.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            } else {
                files.forEach { file ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(
                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            null, modifier = Modifier.size(16.dp), tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(file.name, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudStorageConfigRow(config: CloudStorageConfig, viewModel: CloudStorageViewModel) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (config.isActive) {
            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(config.label, fontWeight = if (config.isActive) FontWeight.Bold else FontWeight.Normal)
            Text(config.baseUrl, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        if (!config.isActive) {
            TextButton(onClick = { viewModel.activateConfig(config.id) }) { Text("USE") }
        }
        IconButton(onClick = { viewModel.deleteConfig(config.id) }) {
            Icon(Icons.Default.Delete, "Remove", tint = Color.Red, modifier = Modifier.size(18.dp))
        }
    }
}
