package com.example.mistreal_mini.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.ui.chat.ChatViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialConnectionsScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
    settingsViewModel: com.example.mistreal_mini.ui.settings.SettingsViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    val contacts by viewModel.recentContacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val platforms by settingsViewModel.availablePlatforms.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connections") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.syncSocials() }) {
                        Icon(Icons.Default.Sync, "Sync Platforms")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search friends or contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = RoundedCornerShape(12.dp)
            )
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "AVAILABLE PLATFORMS", 
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Black
                    )
                }
                
                items(platforms) { platform ->
                    ListItem(
                        headlineContent = { Text(platform.name) },
                        leadingContent = { Text(platform.icon, fontSize = 24.sp) },
                        trailingContent = {
                            if (platform.isConnected) {
                                Icon(Icons.Default.CheckCircle, null, tint = Color.Green)
                            } else {
                                Button(
                                    onClick = { settingsViewModel.initiateSocialConnection(platform.id) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("CONNECT", fontSize = 10.sp)
                                }
                            }
                        }
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(16.dp)) }

                item {
                    Text(
                        "RECENT CONTACTS", 
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Black
                    )
                }
                
                val filteredContacts = contacts.filter { 
                    it.name.contains(searchQuery, ignoreCase = true) || 
                    it.platform.contains(searchQuery, ignoreCase = true)
                }

                if (filteredContacts.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No connections found.", color = Color.Gray)
                        }
                    }
                }

                items(filteredContacts) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text("${contact.platform.uppercase()} • Active recently", fontSize = 12.sp) },
                        leadingContent = {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Text(contact.name.take(1), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        trailingContent = {
                            IconButton(onClick = { 
                                viewModel.switchChat(contact.name, contact.platform)
                                onBack()
                            }) {
                                Icon(Icons.Default.Chat, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}
