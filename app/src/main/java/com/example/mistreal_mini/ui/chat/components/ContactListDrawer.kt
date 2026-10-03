package com.example.mistreal_mini.ui.chat.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.data.model.PlatformBrandColors
import com.example.mistreal_mini.ui.chat.ChatViewModel

@Composable
fun ContactListDrawer(
    viewModel: ChatViewModel,
    onClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("ai") }
    var modelTab by remember { mutableStateOf(0) } // 0 = Free, 1 = Premium
    var searchPlatformQuery by remember { mutableStateOf("") }
    var showSosConfirm by remember { mutableStateOf(false) }
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
                                "tiktok" -> Icons.Default.MusicNote
                                "youtube" -> Icons.Default.PlayCircle
                                else -> Icons.Default.Link
                            }
                            CategoryIcon(
                                icon = icon,
                                label = platform.take(4),
                                isSelected = selectedCategory == platform,
                                badgeCount = platformUnread,
                                tintColor = PlatformBrandColors.forPlatform(platform)
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
                            icon = Icons.Default.Email,
                            label = "Email",
                            isSelected = selectedCategory == "email"
                        ) {
                            selectedCategory = "email"
                            viewModel.clearActiveEmailThread()
                            viewModel.fetchEmailContacts()
                        }
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
                        
                        if (selectedCategory != "ai" && selectedCategory != "unread" && selectedCategory != "emergency" && selectedCategory != "email") {
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
                            } else if (selectedCategory == "email") {
                                item {
                                    EmailPanel(viewModel)
                                }
                            } else if (selectedCategory == "emergency") {
                                item {
                                    val isSending = viewModel.isSendingSos.value
                                    Button(
                                        onClick = { showSosConfirm = true },
                                        enabled = !isSending,
                                        modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 12.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                                    ) {
                                        if (isSending) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Sos, null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("SEND SOS", color = Color.White, fontWeight = FontWeight.Black)
                                        }
                                    }
                                    Text(
                                        "Emails your emergency contacts and posts an alert to your connected social accounts.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                }
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

                                item {
                                    TabRow(selectedTabIndex = modelTab, modifier = Modifier.padding(bottom = 4.dp)) {
                                        Tab(
                                            selected = modelTab == 0,
                                            onClick = { modelTab = 0 },
                                            text = { Text("FREE", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        )
                                        Tab(
                                            selected = modelTab == 1,
                                            onClick = { modelTab = 1 },
                                            text = { Text("PREMIUM", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                }

                                sortedCategories.forEach { category ->
                                    // Free tab shows only unlocked models; Premium tab stays visible to
                                    // free users too (with lock icons) so they can see what's available
                                    // to upgrade into, rather than only ever seeing what they already have.
                                    val models = (categorizedModels[category] ?: emptyList())
                                        .filter { if (modelTab == 0) !it.isProOnly else it.isProOnly }
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
                                if (contacts.isEmpty()) {
                                    item {
                                        // Most platforms (LinkedIn/Instagram/Facebook especially)
                                        // don't let third-party apps browse a full connections
                                        // list by policy — this isn't necessarily broken, search
                                        // is the actual way to find someone new to message.
                                        Column(modifier = Modifier.padding(top = 24.dp)) {
                                            Text(
                                                "No conversations yet on ${selectedCategory.replaceFirstChar { it.uppercase() }}.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "Search above by name to find someone to message — most platforms don't let apps browse your full connections list.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray.copy(alpha = 0.8f)
                                            )
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
            }
        },
        content = {}
    )

    if (showSosConfirm) {
        AlertDialog(
            onDismissRequest = { showSosConfirm = false },
            icon = { Icon(Icons.Default.Sos, null, tint = Color.Red) },
            title = { Text("Send SOS alert?") },
            text = {
                Text("This will email your emergency contacts AND post a public SOS status update to every social account you've connected. This can't be undone once sent.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSosConfirm = false
                        viewModel.triggerManualSos()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("SEND SOS", fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSosConfirm = false }) { Text("Cancel") }
            }
        )
    }
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
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = model.name,
                                                modifier = Modifier.weight(1f, fill = false),
                                                color = if (isLocked) Color.Gray else MaterialTheme.colorScheme.onSurface,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1
                                            )
                                            model.capabilities?.let { caps ->
                                                if (caps.imageGen) {
                                                    Icon(Icons.Default.Image, "Generates images", modifier = Modifier.padding(start = 4.dp).size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                                                }
                                                if (caps.videoGen) {
                                                    Icon(Icons.Default.Movie, "Generates video", modifier = Modifier.padding(start = 4.dp).size(12.dp), tint = MaterialTheme.colorScheme.secondary)
                                                }
                                                if (caps.voice) {
                                                    Icon(Icons.Default.Mic, "Supports voice", modifier = Modifier.padding(start = 4.dp).size(12.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                }
                                            }
                                        }
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

/**
 * Compose-and-send email panel (drawer "EMAIL" category) — send-only, no
 * inbox sync. Shows saved addresses (people this device has emailed before)
 * when nothing's open; opening one shows the sent-message thread with a
 * reply box; "+ New Email" composes to a fresh address.
 */
@Composable
private fun EmailPanel(viewModel: ChatViewModel) {
    val contacts = viewModel.emailContacts
    val thread = viewModel.activeEmailThread
    val isSending = viewModel.isSendingEmail.value

    var activeAddress by remember { mutableStateOf<String?>(null) }
    var isComposingNew by remember { mutableStateOf(false) }
    var toEmailInput by remember { mutableStateOf("") }
    var toNameInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }

    fun resetCompose() {
        isComposingNew = false
        toEmailInput = ""
        toNameInput = ""
        subjectInput = ""
        bodyInput = ""
    }

    when {
        activeAddress != null -> {
            val address = activeAddress!!
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { activeAddress = null; viewModel.clearActiveEmailThread() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                    Column {
                        Text(address, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text("Sent mail — not a synced inbox", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                thread.forEach { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(msg.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg.body, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(msg.timestamp, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = subjectInput,
                    onValueChange = { subjectInput = it },
                    placeholder = { Text("Subject", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = bodyInput,
                    onValueChange = { bodyInput = it },
                    placeholder = { Text("Write a reply...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        viewModel.sendEmail(address, null, subjectInput.ifBlank { "(no subject)" }, bodyInput) { success ->
                            if (success) { subjectInput = ""; bodyInput = "" }
                        }
                    },
                    enabled = !isSending && bodyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send") }
                }
            }
        }
        isComposingNew -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { resetCompose() }) { Icon(Icons.Default.Close, "Cancel") }
                    Text("New Email", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = toEmailInput, onValueChange = { toEmailInput = it },
                    placeholder = { Text("To (email address)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = toNameInput, onValueChange = { toNameInput = it },
                    placeholder = { Text("Name (optional)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = subjectInput, onValueChange = { subjectInput = it },
                    placeholder = { Text("Subject", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = bodyInput, onValueChange = { bodyInput = it },
                    placeholder = { Text("Message", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        val addr = toEmailInput.trim()
                        viewModel.sendEmail(addr, toNameInput.ifBlank { null }, subjectInput, bodyInput) { success ->
                            if (success) {
                                activeAddress = addr
                                resetCompose()
                            }
                        }
                    },
                    enabled = !isSending && toEmailInput.contains("@") && subjectInput.isNotBlank() && bodyInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else { Icon(Icons.AutoMirrored.Filled.Send, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Send") }
                }
            }
        }
        else -> {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { isComposingNew = true },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Email")
                }
                if (contacts.isEmpty()) {
                    Text(
                        "No sent emails yet. Compose one above to get started.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                } else {
                    contacts.forEach { c ->
                        ListItem(
                            headlineContent = { Text(c.toName ?: c.toEmail, fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(c.lastSubject ?: "", maxLines = 1) },
                            leadingContent = { Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                activeAddress = c.toEmail
                                viewModel.openEmailThread(c.toEmail)
                            }
                        )
                    }
                }
            }
        }
    }
}
