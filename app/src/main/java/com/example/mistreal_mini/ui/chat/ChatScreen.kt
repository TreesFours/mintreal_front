package com.example.mistreal_mini.ui.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.ui.chat.components.*
import com.example.mistreal_mini.ui.util.AiInsightPopup
import com.example.mistreal_mini.ui.util.NukeIcon
import com.example.mistreal_mini.util.ScreenshotHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

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
    val currentChatPartner by viewModel.currentChatPartner
    val pagedMessages = viewModel.pagedMessages.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    val isRecording by viewModel.isRecording
    val recordedFile by viewModel.recordedFile
    val recordingDuration by viewModel.recordingDuration
    val isPlayingBack by viewModel.isPlayingBack
    
    val bearing by viewModel.bearing
    val orientation by viewModel.orientation

    LaunchedEffect(messages.size, pagedMessages.itemCount) {
        if (viewModel.isSocialChat.value) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        } else {
            if (pagedMessages.itemCount > 0) {
                listState.animateScrollToItem(0)
            }
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


    var videoUri by remember { mutableStateOf<Uri?>(null) }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success && videoUri != null) {
            viewModel.addPendingAttachment(videoUri!!)
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

    fun captureVideo() {
        val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        videoUri = uri
        videoLauncher.launch(uri)
    }

    val isScribing by viewModel.isScribing
    val scribeText by viewModel.scribeText.collectAsStateWithLifecycle()

    LaunchedEffect(scribeText) {
        if (scribeText.isNotBlank()) {
            textState = scribeText
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Platform/AI Logo
                        val platform = viewModel.currentChatPartnerPlatform.value
                        val logoIcon = when(platform.lowercase()) {
                            "ai" -> Icons.Default.SmartToy
                            "whatsapp" -> Icons.Default.Chat
                            "twitter", "x" -> Icons.Default.Public
                            "linkedin" -> Icons.Default.Business
                            "facebook" -> Icons.Default.Facebook
                            "instagram" -> Icons.Default.PhotoCamera
                            else -> Icons.Default.Link
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(logoIcon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            val partnerText = viewModel.currentTrendTitle.value ?: if (platform == "ai") viewModel.aiCustomName.value else currentChatPartner
                            Text(
                                text = partnerText.uppercase(), 
                                style = MaterialTheme.typography.labelLarge, 
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(viewModel.currentChatPartnerStatus.value, 
                                    style = MaterialTheme.typography.labelSmall, 
                                    color = if (viewModel.currentChatPartnerStatus.value == "Active") Color.Green else Color.Gray,
                                    fontSize = 8.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$orientation | ${bearing.toInt()}°",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 8.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    Box {
                        val unreadCount by viewModel.totalUnreadCount
                        val infiniteTransition = rememberInfiniteTransition(label = "drawer_hint")
                        
                        // 🚀 DRAWER HINT ANIMATION: Subtle pulse when unread messages exist
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (unreadCount > 0) 1.15f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        IconButton(
                            onClick = { showContactList = true },
                            modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
                        ) { 
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Menu, 
                                    contentDescription = "Contacts",
                                    tint = if (unreadCount > 0) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                )
                                
                                if (unreadCount > 0) {
                                    // 🚀 DRAWER PULL HINT: Subtle arrow animation
                                    val offsetX by infiniteTransition.animateFloat(
                                        initialValue = 0f,
                                        targetValue = 10f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "arrow_offset"
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight, 
                                        null, 
                                        modifier = Modifier.padding(start = 24.dp).offset(x = offsetX.dp).size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        
                        if (unreadCount > 0) {
                            Badge(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                                containerColor = Color.Red
                            ) {
                                Text(unreadCount.toString(), color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                },
                actions = {
                    if (viewModel.currentTrendTitle.value != null) {
                        IconButton(onClick = { viewModel.exitTrend() }) { 
                            Icon(Icons.Default.Close, "Exit Trend", tint = Color.Red) 
                        }
                    }
                    IconButton(onClick = onDashboardClick) { 
                        Icon(Icons.Default.Psychology, "Intelligence Hub", tint = MaterialTheme.colorScheme.primary) 
                    }
                    IconButton(onClick = { viewModel.refreshSocialContacts() }) { Icon(Icons.Default.Sync, "Sync") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
                    IconButton(onClick = { showNukeConfirm = true }) { NukeIcon() }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )
        },
        bottomBar = {
            if (isRecording || recordedFile != null) {
                VoiceRecordingBar(
                    isRecording = isRecording,
                    duration = recordingDuration,
                    recordedFile = recordedFile,
                    isPlayingBack = isPlayingBack,
                    onStopRecording = { viewModel.stopRecording() },
                    onDelete = { viewModel.deleteRecording() },
                    onPlay = { viewModel.playRecording() },
                    onSend = { viewModel.sendVoiceMessage() },
                    onCancel = { viewModel.cancelRecording() }
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
                                screenshotUri = ScreenshotHelper.captureAndSave(activity)
                            }
                        }
                    },
                    onScreenRecordClick = {
                        viewModel.startScreenRecord()
                        val recordIntent = Intent(context, com.example.mistreal_mini.service.recording.ScreenRecordService::class.java).apply {
                            action = "START"
                        }
                        context.startForegroundService(recordIntent)
                    },
                    onCameraClick = { captureImage() },
                    onVideoClick = { captureVideo() },
                    onFileClick = { filePickerLauncher.launch("*/*") },
                    onVoiceClick = { viewModel.startRecording() },
                    onConversationClick = { viewModel.startHandsFreeLoop(textState) },
                    onScribeClick = {
                        if (isScribing) viewModel.stopScribe() else viewModel.startScribe()
                    },
                    isScribing = isScribing,
                    onClearScribe = { textState = "" },
                    onSaveScribe = { 
                        viewModel.sendMessage(textState)
                        textState = ""
                        viewModel.stopScribe()
                    },
                    onDraftClick = if (viewModel.isSocialChat.value) { 
                        { viewModel.draftSocialReply(textState); textState = "" } 
                    } else null,
                    isLoading = isLoading,
                    pendingAttachments = viewModel.pendingAttachments,
                    onRemoveAttachment = { viewModel.removePendingAttachment(it) },
                    isSceneMode = viewModel.isSceneMode.value,
                    onToggleSceneMode = { viewModel.toggleSceneMode(it) },
                    isAutoReplyEnabled = viewModel.guardianEnabled.value,
                    onToggleAutoReply = { viewModel.setGuardianEnabled(it) }
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
                reverseLayout = !viewModel.isSocialChat.value,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val isTopHalf = offset.y < size.height / 2
                                coroutineScope.launch {
                                    if (isTopHalf) {
                                        listState.animateScrollToItem(if (viewModel.isSocialChat.value) 0 else pagedMessages.itemCount)
                                    } else {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            },
                            onTap = { focusManager.clearFocus() }
                        )
                    }
            ) {
                if (viewModel.isSocialChat.value) {
                    if (messages.isEmpty() && !isLoading) {
                        item {
                            EmptyChatState(
                                title = "No messages with $currentChatPartner",
                                subtitle = "Start a conversation to see it here."
                            )
                        }
                    }
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
                            coroutineScope = coroutineScope,
                            targetLang = viewModel.defaultTranslationLang.value
                        )
                    }
                } else {
                    if (pagedMessages.itemCount == 0 && !isLoading) {
                        item {
                            EmptyChatState(
                                title = "Welcome to Mistreal",
                                subtitle = "Deploy your first intelligence query or start a chat."
                            )
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    items(count = pagedMessages.itemCount) { index ->
                        val msg = pagedMessages[index]
                        if (msg != null) {
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
                                coroutineScope = coroutineScope,
                                targetLang = viewModel.defaultTranslationLang.value
                            )
                        }
                    }
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
            

            if (showContactList) {
                ContactListDrawer(
                    viewModel = viewModel,
                    onClose = { showContactList = false }
                )
            }

            if (viewModel.isScreenRecording.value) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 100.dp), contentAlignment = Alignment.TopCenter) {
                    ScreenRecordingOverlay(
                        isPaused = viewModel.isRecordingPaused.value,
                        onPause = { viewModel.pauseScreenRecord() },
                        onResume = { viewModel.resumeScreenRecord() },
                        onStop = { viewModel.stopScreenRecord() }
                    )
                }
            }
        }
    }
}
