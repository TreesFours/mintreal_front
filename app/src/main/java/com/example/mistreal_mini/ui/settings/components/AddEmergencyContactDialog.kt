package com.example.mistreal_mini.ui.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.ui.settings.SettingsViewModel

/**
 * Only sends an invite now — the contact must confirm/decline via the
 * link before ever becoming a real, alert-receiving emergency contact (see
 * emergencyRoutes.ts). "Phone" was dropped as a channel: no SMS provider
 * exists anywhere in this app, so those contacts were previously stored but
 * never actually reachable. Email replaces it — a channel the backend can
 * genuinely deliver on via the existing Gmail SMTP sender.
 */
@Composable
fun AddEmergencyContactDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }
    var manualEmail by remember { mutableStateOf("") }

    val socialContacts by viewModel.recentSocialContacts.collectAsState(initial = emptyList())
    val isSaving by viewModel.isSavingEmergencyContact.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Emergency Contact") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    "They'll get a link to confirm or decline — they won't receive any real alert until they do.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(12.dp))
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                        Text("Socials", modifier = Modifier.padding(8.dp))
                    }
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                        Text("Email", modifier = Modifier.padding(8.dp))
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
                                        viewModel.addEmergencyContact(
                                            name = contact.name,
                                            channel = "platform",
                                            platform = contact.platform,
                                            platformContactId = contact.contactId
                                        )
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
                            value = manualEmail,
                            onValueChange = { manualEmail = it },
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )
                        Button(
                            onClick = {
                                if (manualName.isNotBlank() && manualEmail.isNotBlank()) {
                                    viewModel.addEmergencyContact(
                                        name = manualName,
                                        channel = "email",
                                        email = manualEmail
                                    )
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving && manualName.isNotBlank() && manualEmail.isNotBlank()
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                            else Text("Send Confirmation Request")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
