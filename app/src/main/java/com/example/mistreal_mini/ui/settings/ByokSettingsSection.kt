package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private val PROVIDER_OPTIONS = listOf(
    "openai" to "OpenAI",
    "anthropic" to "Anthropic",
    "gemini" to "Google Gemini (own key)",
    "openai_compatible" to "Custom (OpenAI-compatible)"
)

/**
 * Bring-your-own-key AI provider configuration. The key is write-only: once
 * saved, only a "configured" status (provider/model) is ever shown back —
 * never the key value itself.
 */
@Composable
fun ByokSettingsSection(viewModel: SettingsViewModel) {
    val status by viewModel.byokStatus.collectAsState()
    val isSaving by viewModel.isSavingByok.collectAsState()

    var providerType by remember { mutableStateOf(PROVIDER_OPTIONS[0].first) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    SettingsSection(title = "AI PROVIDER (BRING YOUR OWN KEY)", icon = Icons.Default.Key) {
        if (status?.configured == true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Configured: ${PROVIDER_OPTIONS.find { it.first == status?.providerType }?.second ?: status?.providerType}",
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!status?.modelName.isNullOrBlank()) {
                        Text(status?.modelName ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                TextButton(onClick = { viewModel.clearByokKey() }, enabled = !isSaving) {
                    Text("REMOVE", color = Color.Red)
                }
            }
        } else {
            Text("Not configured — your chats use the app's built-in models.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box {
            OutlinedButton(onClick = { providerMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(PROVIDER_OPTIONS.find { it.first == providerType }?.second ?: providerType)
            }
            DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                PROVIDER_OPTIONS.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        providerType = id
                        providerMenuExpanded = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (providerType == "openai_compatible") {
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("Base URL (e.g. https://api.groq.com/openai/v1)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it },
            label = { Text("Model name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.saveByokKey(providerType, apiKey, baseUrl.ifBlank { null }, modelName.ifBlank { null })
                apiKey = "" // never keep the raw key in on-screen state after submit
            },
            enabled = !isSaving && apiKey.length >= 10,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
            else Text("SAVE KEY")
        }

        Spacer(modifier = Modifier.height(16.dp))

        ProtocolSwitch(
            "Let AI auto-send its own drafted social replies",
            "Only applies to confident drafts in a connected DM chat. Off by default.",
            viewModel.aiAutoSendEnabled.value,
            { viewModel.setAiAutoSendEnabled(it) }
        )
    }
}

private val VIDEO_PROVIDER_OPTIONS = listOf(
    "runway" to "Runway",
    "custom" to "Custom (your own endpoint)"
)

/**
 * Separate BYOK slot for video EDITING (background/subject change on an
 * existing recorded video) — distinct from the text provider above. Gemini/
 * Veo don't offer this capability, so it only works once a provider is
 * configured here; the AI Edit button on a video attachment explains this
 * inline too.
 */
@Composable
fun ByokVideoSettingsSection(viewModel: SettingsViewModel) {
    val status by viewModel.byokVideoStatus.collectAsState()
    val isSaving by viewModel.isSavingByokVideo.collectAsState()

    var providerType by remember { mutableStateOf(VIDEO_PROVIDER_OPTIONS[0].first) }
    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("") }
    var modelName by remember { mutableStateOf("") }
    var providerMenuExpanded by remember { mutableStateOf(false) }

    SettingsSection(title = "VIDEO EDITING PROVIDER (BRING YOUR OWN KEY)", icon = Icons.Default.Key) {
        Text(
            "Gemini/Veo can generate new video, but can't edit an existing recorded video's background/subject — that needs a dedicated video-editing provider here (e.g. Runway).",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Gray
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (status?.configured == true) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Configured: ${VIDEO_PROVIDER_OPTIONS.find { it.first == status?.providerType }?.second ?: status?.providerType}",
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!status?.modelName.isNullOrBlank()) {
                        Text(status?.modelName ?: "", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                TextButton(onClick = { viewModel.clearByokVideoKey() }, enabled = !isSaving) {
                    Text("REMOVE", color = Color.Red)
                }
            }
        } else {
            Text("Not configured — AI video editing is unavailable until you add a provider.", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box {
            OutlinedButton(onClick = { providerMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(VIDEO_PROVIDER_OPTIONS.find { it.first == providerType }?.second ?: providerType)
            }
            DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                VIDEO_PROVIDER_OPTIONS.forEach { (id, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = {
                        providerType = id
                        providerMenuExpanded = false
                    })
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API Key") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = { Text("Endpoint URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = modelName,
            onValueChange = { modelName = it },
            label = { Text("Model name (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                viewModel.saveByokVideoKey(providerType, apiKey, baseUrl.ifBlank { null }, modelName.ifBlank { null })
                apiKey = ""
            },
            enabled = !isSaving && apiKey.length >= 10 && baseUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
            else Text("SAVE VIDEO PROVIDER")
        }
    }
}
