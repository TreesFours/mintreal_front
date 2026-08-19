/** 🛡️ AI SYSTEM PROTOCOL 🛡️
 * SOURCE OF TRUTH: master_system_map.artifact.md
 * 
 * 🚀 FUNCTIONAL PIPELINE:
 * [Input]  <- Multi-modal commands (Text, Voice, Image, Files) via Operator
 * [Process] <- Processes natural language, executes social drafts, and manages audio synthesis
 * [Output] -> Encrypted chat records; triggers backend social actions and voice feedback
 *
 * ⚠️ MANDATORY: Never delete history. Only ADD updates/fixes to the Master Map table.
 */
package com.example.mistreal_mini.ui.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.service.VoiceService
import com.example.mistreal_mini.util.ScreenshotHelper
import com.example.mistreal_mini.util.VoiceRecorder
import com.example.mistreal_mini.util.NoteExporter
import com.example.mistreal_mini.ui.util.AiInsightPopup
import com.example.mistreal_mini.ui.util.NukeIcon
import com.example.mistreal_mini.ui.util.LinkableText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import com.example.mistreal_mini.data.api.SocialContact
import com.example.mistreal_mini.data.api.UnreadItem
import androidx.compose.foundation.text.selection.SelectionContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onSubscribeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onArchiveClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var textState by rememberSaveable { mutableStateOf("") }
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val isListening by viewModel.isListening
    val isHandsFree by viewModel.isHandsFreeActive
    val selectedProvider by viewModel.selectedProvider
    val currentChatPartner by viewModel.currentChatPartner
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // 🛡️ AI NOTE: If you overhaul or fix logic here, log it in the "History & Notes" column of the Master Map.
    // Pagination logic
    val isScrollingUp = listState.isScrollInProgress && listState.firstVisibleItemIndex == 0
    LaunchedEffect(isScrollingUp) {
        if (isScrollingUp) {
            viewModel.loadMoreMessages()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.errorEvents.collectLatest { error ->
            snackbarHostState.showSnackbar(error)
        }
    }

    var showContactList by remember { mutableStateOf(false) }
    var showNukeConfirm by remember { mutableStateOf(false) }
    var insightContext by remember { mutableStateOf<String?>(null) }
    var showInsightPopup by remember { mutableStateOf(false) }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var isPlayingBack by remember { mutableStateOf(false) }

    // ⏱️ Recording Timer logic
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordingDuration++
            }
        }
    }
    
    // 📝 Scribe State
    var showScribeResult by remember { mutableStateOf<String?>(null) }
    val scribeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            showScribeResult = spokenText
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            viewModel.addPendingAttachment(cameraUri!!)
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> 
        if (uris.isNotEmpty()) {
            uris.forEach { viewModel.addPendingAttachment(it) }
        }
    }

    LaunchedEffect(screenshotUri) {
        screenshotUri?.let {
            viewModel.addPendingAttachment(it)
            screenshotUri = null
        }
    }

    fun captureImage() {
        val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showContactList = true }) { Icon(Icons.Default.Menu, "Contacts") }
                        Column {
                            val partnerText = viewModel.currentTrendTitle.value ?: currentChatPartner
                            Text(partnerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(viewModel.currentChatPartnerStatus.value, 
                                style = MaterialTheme.typography.labelSmall, 
                                color = if (viewModel.currentChatPartnerStatus.value == "Active") Color.Green else Color.Gray
                            )
                        }
                    }
                },
                actions = {
                    if (viewModel.currentTrendTitle.value != null) {
                        IconButton(onClick = { viewModel.exitTrend() }) { 
                            Icon(Icons.Default.Close, "Exit Trend", tint = Color.Red) 
                        }
                    }
                    IconButton(onClick = onArchiveClick) { Icon(Icons.Default.History, "History") }
                    IconButton(onClick = onDashboardClick) { Icon(Icons.Default.Dashboard, "Dashboard") }
                    IconButton(onClick = { viewModel.syncSocials() }) { Icon(Icons.Default.Sync, "Sync") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { showNukeConfirm = true }) { NukeIcon() }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            )
        },
        bottomBar = {
            if (isRecording || recordedFile != null) {
                VoiceRecordingBar(
                    isRecording = isRecording,
                    duration = recordingDuration,
                    recordedFile = recordedFile,
                    isPlayingBack = isPlayingBack,
                    onStopRecording = {
                        isRecording = false
                        voiceRecorder.stopRecording()
                    },
                    onDelete = {
                        recordedFile?.let { voiceRecorder.deleteRecording(it) }
                        recordedFile = null
                        isRecording = false
                    },
                    onPlay = {
                        recordedFile?.let {
                            isPlayingBack = true
                            voiceRecorder.playRecording(it) { isPlayingBack = false }
                        }
                    },
                    onSend = {
                        recordedFile?.let {
                            viewModel.sendAttachments(listOf(Uri.fromFile(it)), "audio")
                        }
                        recordedFile = null
                    },
                    onCancel = {
                        recordedFile?.let { voiceRecorder.deleteRecording(it) }
                        recordedFile = null
                        isRecording = false
                    }
                )
            } else {
                ChatInputBar(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = { 
                        viewModel.sendMessage(textState)
                        textState = "" 
                        focusManager.clearFocus()
                    },
                    onScreenshotClick = { 
                        (context as? Activity)?.let { activity ->
                            coroutineScope.launch {
                                screenshotUri = com.example.mistreal_mini.util.ScreenshotHelper.captureAndSave(activity)
                            }
                        }
                    },
                    onCameraClick = { captureImage() },
                    onFileClick = { filePickerLauncher.launch("*/*") },
                    onVoiceClick = { 
                        if (viewModel.isSttEnabled.value) {
                            isRecording = true
                            recordedFile = voiceRecorder.startRecording()
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Voice input disabled in settings") }
                        }
                    },
                    onScribeClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        }
                        scribeLauncher.launch(intent)
                    },
                    onDraftClick = if (viewModel.isSocialChat.value) { 
                        { viewModel.draftSocialReply(textState); textState = "" } 
                    } else null,
                    isLoading = isLoading,
                    pendingAttachments = viewModel.pendingAttachments,
                    onRemoveAttachment = { viewModel.removePendingAttachment(it) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            }
        ) {
            LazyColumn(
                state = listState, 
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val isTopHalf = offset.y < size.height / 2
                                coroutineScope.launch {
                                    if (isTopHalf) {
                                        listState.animateScrollToItem(0)
                                    } else {
                                        if (messages.isNotEmpty()) {
                                            listState.animateScrollToItem(messages.size - 1)
                                        }
                                    }
                                }
                            },
                            onTap = { focusManager.clearFocus() }
                        )
                    }
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(messages) { msg -> 
                    ChatBubble(
                        message = msg,
                        viewModel = viewModel,
                        onAiInsight = { text ->
                            insightContext = text
                            showInsightPopup = true
                        },
                        onReadAloud = { text, mode ->
                            when (mode) {
                                InteractionMode.SINGLE -> viewModel.readAloud(text)
                                InteractionMode.HANDS_FREE -> viewModel.startHandsFreeLoop(text)
                                InteractionMode.RADIO -> viewModel.startRadioMode(text)
                            }
                        },
                        snackbarHostState = snackbarHostState,
                        coroutineScope = coroutineScope
                    ) 
                }
                if (isLoading) item { TypingIndicator() }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Quick Scroll FAB
            val showScrollToBottom by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex < messages.size - 10
                }
            }
            
            AnimatedVisibility(
                visible = showScrollToBottom,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 100.dp, end = 16.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    contentColor = Color.White,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, "Bottom")
                }
            }

            if (showInsightPopup) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    AiInsightPopup(
                        contextText = insightContext ?: "",
                        onClose = { showInsightPopup = false },
                        viewModel = viewModel
                    )
                }
            }

            AnimatedVisibility(
                visible = isHandsFree,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 8.dp,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                    ) {
                        Icon(Icons.Default.Mic, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isListening) "Conversation Mode — Listening…" else "Conversation Mode — Active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.toggleHandsFree(false) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "Stop Conversation Mode", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            if (showNukeConfirm) {
                AlertDialog(
                    onDismissRequest = { showNukeConfirm = false },
                    icon = { NukeIcon(Modifier.size(48.dp)) },
                    title = { Text("Total Annihilation?") },
                    text = { Text("This will permanently wipe your chat history and memory. Proceed with caution.") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearChat()
                                showNukeConfirm = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) { Text("Nuke It") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showNukeConfirm = false }) { Text("Abort") }
                    }
                )
            }
            
            // 📝 Scribe Result Dialog
            if (showScribeResult != null) {
                AlertDialog(
                    onDismissRequest = { showScribeResult = null },
                    title = { Text("Speech Transcribed") },
                    text = { 
                        Column {
                            OutlinedTextField(
                                value = showScribeResult!!,
                                onValueChange = { showScribeResult = it },
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Would you like to save this as a local note?", style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    confirmButton = {
                        Row {
                            TextButton(onClick = { 
                                viewModel.saveAsNote(showScribeResult!!)
                                coroutineScope.launch { snackbarHostState.showSnackbar("Note saved to Scribe Records") }
                                showScribeResult = null
                            }) { Text("Secure Note") }
                            
                            Button(onClick = { 
                                viewModel.sendMessage(showScribeResult!!)
                                showScribeResult = null
                            }) { Text("Send to AI") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showScribeResult = null }) { Text("Discard") }
                    }
                )
            }

            if (showContactList) {
                var selectedCategory by remember { mutableStateOf("ai") }
                var searchPlatformQuery by remember { mutableStateOf("") }
                val contacts by viewModel.socialContacts
                val unreadItems by viewModel.unreadMessages

                ModalNavigationDrawer(
                    drawerContent = {
                        ModalDrawerSheet {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // 📱 Platform Sidebar
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(75.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.Top)
                                ) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    CategoryIcon(Icons.Default.Psychology, "AI", selectedCategory == "ai") { 
                                        selectedCategory = "ai"
                                    }
                                    viewModel.availablePlatforms.forEach { platform ->
                                        if (platform.isConnected) {
                                            CategoryIcon(
                                                icon = platform.icon,
                                                label = platform.name.take(4),
                                                isSelected = selectedCategory == platform.id
                                            ) {
                                                selectedCategory = platform.id
                                                searchPlatformQuery = ""
                                                viewModel.fetchContacts(platform.id)
                                            }
                                        }
                                    }
                                    CategoryIcon(Icons.Default.AllInbox, "Inbox", selectedCategory == "unread") { 
                                        selectedCategory = "unread"
                                        viewModel.fetchUnread()
                                    }
                                }

                                // 🔍 Contextual Search & Results
                                Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                                    Text(
                                        text = selectedCategory.replace("_", " ").uppercase(),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    
                                    if (selectedCategory != "ai" && selectedCategory != "unread") {
                                        OutlinedTextField(
                                            value = searchPlatformQuery,
                                            onValueChange = { 
                                                searchPlatformQuery = it
                                                if (it.length >= 3) {
                                                    viewModel.searchContacts(selectedCategory, it)
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                            placeholder = { Text("Search on ${selectedCategory}...", fontSize = 12.sp) },
                                            leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp)) },
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    LazyColumn {
                                        if (selectedCategory == "unread") {
                                            items(unreadItems) { item ->
                                                UnreadListItem(item) { 
                                                    viewModel.switchChat(item.sender, item.platform)
                                                    showContactList = false 
                                                }
                                            }
                                        } else if (selectedCategory == "ai") {
                                            items(viewModel.availableProviders) { model ->
                                                NavigationDrawerItem(
                                                    label = { 
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                            Text(model.name, modifier = Modifier.weight(1f))
                                                            if (model.price != "Free") {
                                                                Badge(
                                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                                                    modifier = Modifier.size(20.dp)
                                                                ) { 
                                                                    Text("PRO", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    selected = viewModel.selectedProvider.value == model.id,
                                                    onClick = { 
                                                        viewModel.setProvider(model.id)
                                                        viewModel.switchChat(model.name, "ai")
                                                        showContactList = false 
                                                    }
                                                )
                                            }
                                        } else {
                                            items(contacts) { contact ->
                                                ContactListItem(contact) {
                                                    viewModel.switchChat(contact.name, contact.platform)
                                                    showContactList = false
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
        }
    }
}

@Composable
fun CategoryIcon(icon: Any, label: String, isSelected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            when (icon) {
                is androidx.compose.ui.graphics.vector.ImageVector -> {
                    Icon(icon, null, tint = if (isSelected) Color.White else Color.Gray, modifier = Modifier.size(24.dp))
                }
                is String -> {
                    // Handle emoji or text - FIXED COLOR logic
                    Text(icon, fontSize = 24.sp, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            label, 
            style = MaterialTheme.typography.labelSmall, 
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}

@Composable
fun ContactListItem(contact: SocialContact, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { 
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(contact.name, modifier = Modifier.weight(1f))
                        // Online status indicator
                        if (contact.isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.Green)
                            )
                        }
                    }
                    // Show last seen or status message
                    contact.lastSeen?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 10.sp)
                    }
                }
                if (contact.unreadCount > 0) {
                    Badge { Text(contact.unreadCount.toString()) }
                }
            }
        },
        selected = false,
        onClick = onClick
    )
}

@Composable
fun UnreadListItem(item: UnreadItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(item.platform) {
                        "whatsapp" -> Icons.Default.Chat
                        "twitter" -> Icons.Default.Public
                        "instagram" -> Icons.Default.CameraAlt
                        "facebook" -> Icons.Default.Facebook
                        else -> Icons.Default.Psychology
                    },
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(item.sender, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                // Online status indicator
                if (item.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color.Green)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(item.text, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            if (item.lastSeen != null) {
                Text(item.lastSeen, style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.7f), fontSize = 9.sp)
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    viewModel: ChatViewModel,
    onAiInsight: (String) -> Unit,
    onReadAloud: (String, InteractionMode) -> Unit,
    snackbarHostState: SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = align
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 300.dp),
            tonalElevation = 2.dp,
            shadowElevation = 2.dp
        ) {
            SelectionContainer {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.type == "image") {
                        val attachments = mutableListOf<String>()
                        message.attachmentPaths?.let { attachments.addAll(it) }
                        message.attachmentUrl?.let { attachments.add(it) }
                        
                        if (attachments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                attachments.forEach { path ->
                                    AsyncImage(
                                        model = path,
                                        contentDescription = "Image attachment",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    
                    if (message.content.isNotEmpty()) {
                        val displayContent = message.content.replace(Regex("\\[FILE_REQUEST:.*?\\]"), "📄 Secure File generated and encrypted.")
                        LinkableText(
                            text = displayContent,
                            textColor = textColor
                        )
                    }

                    if (message.type == "social_draft") {
                        Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.approveSocialAction(message) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Execute", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.discardSocialAction(message) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Discard", fontSize = 10.sp)
                            }
                        }
                    }

                    // --- PROFESSIONAL TACTICAL TOOLBAR ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!isUser) {
                            val context = LocalContext.current
                            IconButton(
                                onClick = { 
                                    viewModel.saveAsNote(message.content)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Intel synchronized to Scribe Notes.") }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.DriveFileRenameOutline, "Note", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                            }
                            Spacer(modifier = Modifier.width(4.dp))

                            IconButton(
                                onClick = { 
                                    com.example.mistreal_mini.util.NoteExporter.saveAsTxt(context, message.content)
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Intel archived to storage.") }
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Archive, "Archive", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { onAiInsight(message.content) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, "Insight", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                        
                        Spacer(modifier = Modifier.width(4.dp))
                        
                        IconButton(
                            onClick = { onReadAloud(message.content, InteractionMode.SINGLE) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Hearing, "Audio", modifier = Modifier.size(14.dp), tint = textColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)) {
            Text(
                text = if (isUser) "OPERATOR" else message.provider.uppercase(), 
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black
            )
            if (message.isTrend) {
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.Default.Link, null, modifier = Modifier.size(8.dp), tint = Color.Gray)
            }
        }
    }
}

enum class InteractionMode { SINGLE, RADIO, HANDS_FREE }

@Composable
fun VoiceRecordingBar(
    isRecording: Boolean,
    duration: Int,
    recordedFile: File?,
    isPlayingBack: Boolean,
    onStopRecording: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isRecording) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red)
                Text(
                    text = String.format("%02d:%02d", duration / 60, duration % 60),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                )
                Button(onClick = onStopRecording) {
                    Text("Stop")
                }
            } else {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                }
                
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPlay) {
                        Icon(if (isPlayingBack) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = "Play")
                    }
                    Text("Voice Preview", style = MaterialTheme.typography.bodyMedium)
                }
                
                FloatingActionButton(
                    onClick = onSend,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onScreenshotClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFileClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onScribeClick: () -> Unit,
    isLoading: Boolean,
    onDraftClick: (() -> Unit)? = null,
    pendingAttachments: List<Uri> = emptyList(),
    onRemoveAttachment: (Uri) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    Surface(
        tonalElevation = 8.dp, 
        shadowElevation = 12.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.navigationBarsPadding(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            // 📎 PENDING ATTACHMENTS PREVIEW
            if (pendingAttachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pendingAttachments) { uri ->
                        Box(modifier = Modifier.size(60.dp)) {
                            val isImage = context.contentResolver.getType(uri)?.startsWith("image") == true
                            if (isImage) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                            IconButton(
                                onClick = { onRemoveAttachment(uri) },
                                modifier = Modifier.size(20.dp).align(Alignment.TopEnd).background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconTint = MaterialTheme.colorScheme.primary
                IconButton(onClick = onScreenshotClick) { Icon(Icons.Default.LensBlur, "Visual Sync", tint = iconTint) }
                IconButton(onClick = onCameraClick) { Icon(Icons.Default.PhotoCamera, "Optic Intel", tint = iconTint) }
                IconButton(onClick = onFileClick) { Icon(Icons.Default.AttachFile, "Data Package", tint = iconTint) }
                IconButton(onClick = onVoiceClick) { Icon(Icons.Default.Mic, "Voice Protocol", tint = iconTint) }
                IconButton(onClick = onScribeClick) { Icon(Icons.Default.HistoryEdu, "Scribe Alpha", tint = iconTint) }
                
                if (onDraftClick != null) {
                    IconButton(onClick = onDraftClick) {
                        Icon(Icons.Default.AutoFixHigh, "Draft AI", tint = MaterialTheme.colorScheme.tertiary)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text, 
                    onValueChange = onTextChange, 
                    modifier = Modifier.weight(1f), 
                    placeholder = { Text("Enter command...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, 
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (text.isNotBlank() && !isLoading) {
                            onSend()
                            focusManager.clearFocus()
                        }
                    })
                )
                Spacer(modifier = Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = { if (text.isNotBlank() && !isLoading) onSend() },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (text.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                    contentColor = if (text.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary else Color.Gray,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.ArrowUpward, "Transmit")
                    }
                }
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            "AI is thinking...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.
