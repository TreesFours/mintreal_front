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
import com.example.mistreal_mini.data.api.EmergencyContact
import com.example.mistreal_mini.ui.settings.SettingsViewModel

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
                                        viewModel.addEmergencyContact(EmergencyContact(
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        Button(
                            onClick = {
                                if (manualName.isNotBlank() && manualPhone.isNotBlank()) {
                                    viewModel.addEmergencyContact(EmergencyContact(
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
