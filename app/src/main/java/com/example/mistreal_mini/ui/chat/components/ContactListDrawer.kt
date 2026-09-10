package com.example.mistreal_mini.ui.chat.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.ui.chat.ChatViewModel

@Composable
fun ContactListDrawer(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ai") }
    var searchPlatformQuery by remember { mutableStateOf("") }
    val contacts by viewModel.socialContacts
    val unreadItems by viewModel.unreadMessages
    
    val recentContacts by viewModel.recentContacts.collectAsState(initial = emptyList())
    val emergencyContactsState by viewModel.emergencyContacts.collectAsState(initial = emptyList())
    
    var rollingOffset by remember { mutableIntStateOf(0) }
    
    val availablePlatforms = viewModel.availablePlatforms

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(75.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        CategoryIcon(Icons.Default.Shield, "SOS", selectedCategory == "emergency") { 
                            selectedCategory = "emergency"
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
                        CategoryIcon(Icons.Default.Psychology, "AI", selectedCategory == "ai") { 
                            selectedCategory = "ai"
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (rollingOffset > 0) {
                            IconButton(onClick = { rollingOffset-- }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                        }
                        
                        // Use dynamic platforms if available, otherwise fallback to recent
                        val platformsToShow = if (availablePlatforms.isNotEmpty()) {
                            availablePlatforms.filter { it.isConnected }.map { it.id }
                        } else {
                            recentContacts.map { it.platform }.distinct()
                        }
                        
                        platformsToShow.drop(rollingOffset).take(5).forEach { platform ->
                            val platformUnread = unreadItems.count { it.platform.lowercase() == platform.lowercase() }
                            val icon = when(platform.lowercase()) {
                                "whatsapp" -> Icons.Default.Chat
                                "instagram" -> Icons.Default.CameraAlt
                                "twitter", "x" -> Icons.Default.Public
                                "linkedin" -> Icons.Default.Business
                                "facebook" -> Icons.Default.Facebook
                                else -> Icons.Default.Link
                            }
                            CategoryIcon(
                                icon = icon,
                                label = platform.take(4),
                                isSelected = selectedCategory == platform,
                                badgeCount = platformUnread
                            ) {
                                selectedCategory = platform
                                viewModel.fetchContacts(platform)
                            }
                        }
                        
                        if (platformsToShow.size > rollingOffset + 5) {
                            IconButton(onClick = { rollingOffset++ }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        CategoryIcon(
                            icon = Icons.Default.AllInbox, 
                            label = "Inbox", 
                            isSelected = selectedCategory == "unread",
                            badgeCount = unreadItems.size
                        ) { 
                            selectedCategory = "unread"
                            viewModel.fetchUnread()
                        }
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                        Text(
                            text = selectedCategory.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        if (selectedCategory != "ai" && selectedCategory != "unread" && selectedCategory != "emergency") {
                            OutlinedTextField(
                                value = searchPlatformQuery,
                                onValueChange = { 
                                    searchPlatformQuery = it
                                    if (it.length >= 3) viewModel.searchContacts(selectedCategory, it)
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                placeholder = { Text("Search...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LazyColumn {
                            if (selectedCategory == "unread") {
                                items(unreadItems) { item ->
                                    UnreadListItem(item) { 
                                        viewModel.switchChat(item.sender, item.platform)
                                        onClose()
                                    }
                                }
                            } else if (selectedCategory == "emergency") {
                                items(emergencyContactsState) { contact ->
                                    ListItem(
                                        headlineContent = { Text(contact.name) },
                                        leadingContent = { Icon(Icons.Default.ContactPhone, null, tint = Color.Red) },
                                        modifier = Modifier.clickable { 
                                            viewModel.switchChat(contact.name, contact.platform)
                                            onClose()
                                        }
                                    )
                                }
                            } else if (selectedCategory == "ai") {
                                val categorizedModels = viewModel.categorizedModels.value
                                val sortedCategories = listOf("COMMAND CENTER", "GLOBAL OVERLORD", "OPTIC INTEL", "GHOST PROTOCOL", "OPEN INTELLIGENCE")
                                
                                sortedCategories.forEach { category ->
                                    val models = categorizedModels[category] ?: emptyList()
                                    if (models.isNotEmpty()) {
                                        item {
                                            ModelCategoryAccordion(
                                                title = category,
                                                models = models,
                                                selectedModelId = viewModel.selectedProvider.value,
                                                isPro = viewModel.isPro.value,
                                                onModelSelect = { model ->
                                                    viewModel.setProvider(model.id)
                                                    viewModel.switchChat(model.name, "ai")
                                                    onClose()
                                                }
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(contacts) { contact ->
                                    ContactListItem(contact) {
                                        viewModel.switchChat(contact.name, contact.platform)
                                        onClose()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        content = {}
    )
}

@Composable
fun ModelCategoryAccordion(
    title: String,
    models: List<com.example.mistreal_mini.data.api.AiModelResponse>,
    selectedModelId: String,
    isPro: Boolean,
    onModelSelect: (com.example.mistreal_mini.data.api.AiModelResponse) -> Unit
) {
    var isExpanded by remember { mutableStateOf(title == "COMMAND CENTER") }
    
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Surface(
            onClick = { isExpanded = !isExpanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title, 
                    style = MaterialTheme.typography.labelMedium, 
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            // Internal scrolling for large lists, limited height to ~6 items
            Box(modifier = Modifier.heightIn(max = 300.dp)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
                ) {
                    items(models) { model ->
                        val isLocked = model.isProOnly && !isPro
                        val isSelected = selectedModelId == model.id
                        
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = model.name,
                                            color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Quota Health Bar
                                            val health = model.health ?: 100
                                            val barColor = when {
                                                health > 80 -> Color.Green
                                                health > 40 -> Color.Yellow
                                                else -> Color.Red
                                            }
                                            Box(modifier = Modifier.width(40.dp).height(2.dp).background(Color.Gray.copy(alpha = 0.3f))) {
                                                Box(modifier = Modifier.fillMaxWidth(health / 100f).fillMaxHeight().background(barColor))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(model.quota ?: "", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
                                        }
                                    }
                                    if (isLocked) {
                                        Icon(Icons.Default.Lock, null, modifier = Modifier.size(14.dp), tint = Color.Gray)
                                    }
                                }
                            },
                            selected = isSelected,
                            onClick = { if (!isLocked) onModelSelect(model) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
