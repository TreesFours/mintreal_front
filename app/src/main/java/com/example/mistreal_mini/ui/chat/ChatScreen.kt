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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.mistreal_mini.data.model.PlatformIcons
import com.example.mistreal_mini.ui.chat.components.*
import com.example.mistreal_mini.ui.util.AiInsightPopup
import com.example.mistreal_mini.ui.util.NukeIcon
import com.example.mistreal_mini.util.ScreenshotHelper
import com.example.mistreal_mini.util.SocialDeepLinks
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    // Lets a quick-launch entry point (Settings' "AI Render" button) land
    // directly in a specific provider/domain mode rather than whatever the
    // user last had selected — same nav-arg pattern watch_host already uses.
    initialProviderOverride: String? = null,
    onSubscribeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDashboardClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onLiveAiClick: () -> Unit = {}
) {
    LaunchedEffect(initialProviderOverride) {
        initialProviderOverride?.let { viewModel.setProvider(it) }
    }
    val context = LocalContext.current
    val sfx = com.example.mistreal_mini.ui.util.LocalSfxHapticPlayer.current
    val coroutineScope = rememberCoroutineScope()
    var textState by rememberSaveable { mutableStateOf("") }
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
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
    var showProposeMeetup by remember { mutableStateOf(false) }
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
                        val logoIcon = PlatformIcons.forPlatform(platform) ?: Icons.Default.Link
                        
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
                            val pendingEta = viewModel.pendingReplyEtaMillis.value
                            if (pendingEta != null) {
                                val minsLeft = ((pendingEta - System.currentTimeMillis()) / 60_000L + 1).coerceAtLeast(0)
                                Text(
                                    text = "Reply arriving in ~${minsLeft}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
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
                            onClick = { sfx?.playTap(); showContactList = true },
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
                    // Scrollable instead of force-fit inline: 6 icons squeezed into a
                    // fixed-width Row left almost no room for the title (name/logo),
                    // which is why it was wrapping/clipping. Capping the width and
                    // letting it scroll also leaves room to add more tools later.
                    Row(
                        modifier = Modifier
                            .widthIn(max = 130.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // First increment of real Facebook calling: Messenger has no
                        // public "start a call with this user" deep link, so this opens
                        // the native Messenger thread with the contact — the user still
                        // taps Messenger's own call button from there. Deliberately one
                        // button, not separate video/voice ones, since both would do the
                        // exact same thing today; to be improved incrementally.
                        if (viewModel.currentChatPartnerPlatform.value.lowercase() == "facebook" && viewModel.activeSocialContact.value != null) {
                            val contact = viewModel.activeSocialContact.value!!
                            val callContext = LocalContext.current
                            IconButton(onClick = {
                                SocialDeepLinks.openMessenger(contact.id, callContext::startActivity)
                            }) {
                                Icon(Icons.Default.VideoCall, "Open in Messenger to call", tint = Color(0xFF0084FF))
                            }
                        }
                        // In-app Mistreal-to-Mistreal video calling (CallScreen/CallService)
                        // needs the contact's actual Mistreal deviceId — a Zernio social
                        // contact only ever carries a platform-side contactId, with no
                        // mapping back to a deviceId anywhere in this app yet. Shown
                        // greyed-out rather than hidden, so it's visible that the
                        // capability exists, just not reachable for this specific contact
                        // until that cross-reference is built.
                        if (viewModel.isSocialChat.value && viewModel.activeSocialContact.value != null) {
                            IconButton(onClick = {}, enabled = false) {
                                Icon(
                                    Icons.Default.VideoCall,
                                    "Mistreal video call unavailable — this contact isn't verified on Mistreal",
                                    tint = LocalContentColor.current.copy(alpha = 0.3f)
                                )
                            }
                        }
                        // A scheduled in-person meetup only makes sense with a real
                        // person/business on the other end of the chat, not the AI.
                        if (viewModel.isSocialChat.value && viewModel.activeSocialContact.value != null) {
                            IconButton(onClick = { showProposeMeetup = true }) {
                                Icon(Icons.Default.Handshake, "Propose Meetup", tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                        if (viewModel.currentTrendTitle.value != null) {
                            val trendTitle = viewModel.currentTrendTitle.value!!
                            val delayOn = viewModel.delayEnabledTrendTitles.contains(trendTitle)
                            IconButton(onClick = { sfx?.playTap(); viewModel.toggleTrendDelay(trendTitle) }) {
                                Icon(
                                    Icons.Default.Timer,
                                    "Delayed replies ${if (delayOn) "on" else "off"} in this minichat",
                                    tint = if (delayOn) MaterialTheme.colorScheme.primary else LocalContentColor.current.copy(alpha = 0.5f)
                                )
                            }
                            IconButton(onClick = { sfx?.playTap(); viewModel.exitTrend() }) {
                                Icon(Icons.Default.Close, "Exit Trend", tint = Color.Red)
                            }
                        }
                        IconButton(onClick = onArchiveClick) {
                            Icon(Icons.Default.History, "History", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDashboardClick) {
                            Icon(Icons.Default.Psychology, "Intelligence Hub", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onLiveAiClick) {
                            Icon(Icons.Default.Videocam, "Live AI", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.refreshSocialContacts() }) { Icon(Icons.Default.Sync, "Sync") }
                        IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
                        IconButton(onClick = { showNukeConfirm = true }) { NukeIcon() }
                    }
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
                    onSend = { viewModel.sendRecordedVoiceNote() },
                    onTrim = { startMs, endMs -> viewModel.trimRecording(startMs, endMs) }
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
                    onAiVoiceNoteClick = if (viewModel.isSocialChat.value) {
                        { viewModel.startAiVoiceNoteRecording() }
                    } else null,
                    isLoading = isLoading,
                    pendingAttachments = viewModel.pendingAttachments,
                    onRemoveAttachment = { viewModel.removePendingAttachment(it) },
                    onReplaceAttachment = { old, new -> viewModel.replacePendingAttachment(old, new) },
                    segmentNotes = viewModel.attachmentSegmentNotes,
                    onSegmentNotesChanged = { uri, notes -> viewModel.setSegmentNotes(uri, notes) },
                    segmentDrawings = viewModel.attachmentSegmentDrawings,
                    onSegmentDrawingsChanged = { uri, drawings -> viewModel.setSegmentDrawings(uri, drawings) },
                    verifiedFaces = viewModel.verifiedFaces.collectAsStateWithLifecycle().value,
                    onDetectFacesInVideo = { uri -> viewModel.detectFacesInVideo(uri) },
                    onMarkFaceTarget = { frame, box -> viewModel.markFaceTarget(frame, box) },
                    isAiEditingVideo = viewModel.isEditingVideo.value,
                    onAiEditVideo = { uri, faceRef, faceTarget, instruction, cb -> viewModel.editVideoWithAi(uri, faceRef, faceTarget, instruction, cb) },
                    isAiEditingImage = viewModel.isEditingImage.value,
                    onAiEditImage = { uri, extraImages, isFaceSwap, instruction, cb -> viewModel.editImageWithAi(uri, extraImages, isFaceSwap, instruction, cb) },
                    isSynthesizingVoiceOver = viewModel.isSynthesizingVoiceOver.value,
                    onAddVoiceOver = { uri, narration, cb -> viewModel.addVoiceOverToVideo(uri, narration, cb) },
                    isSceneMode = viewModel.isSceneMode.value,
                    onToggleSceneMode = { viewModel.toggleSceneMode(it) },
                    // In a social DM minichat, GHOST controls that specific contact's
                    // auto-reply override; everywhere else it's the global master switch.
                    isAutoReplyEnabled = if (viewModel.isSocialChat.value) {
                        viewModel.activeSocialContact.value?.autoReplyEnabled ?: false
                    } else viewModel.guardianEnabled.value,
                    onToggleAutoReply = {
                        if (viewModel.isSocialChat.value) viewModel.toggleActiveContactAutoReply()
                        else viewModel.setGuardianEnabled(it)
                    },
                    replyingTo = if (viewModel.isSocialChat.value) viewModel.replyingTo.value else null,
                    onCancelReply = { viewModel.setReplyingTo(null) }
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
                    items(messages, key = { it.id ?: it.hashCode() }) { msg ->
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
                            targetLang = viewModel.defaultTranslationLang.value,
                            onAttachmentClick = { uri, isVideo -> viewModel.openAttachmentEditor(uri, isVideo) },
                            // Reply-to-a-specific-message only makes sense in a
                            // real user-to-user chat (more than one possible
                            // thread) — this branch only ever renders when
                            // isSocialChat is true, so it's unconditional here.
                            onReplyClick = { viewModel.setReplyingTo(it) },
                            repliedToPreview = msg.replyToId?.let { id -> messages.find { it.id == id }?.content?.take(80) }
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
                    items(
                        count = pagedMessages.itemCount,
                        key = { index -> pagedMessages.peek(index)?.id ?: index }
                    ) { index ->
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
                                targetLang = viewModel.defaultTranslationLang.value,
                                onAttachmentClick = { uri, isVideo -> viewModel.openAttachmentEditor(uri, isVideo) }
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
                            if (isRecording) "Conversation Mode — Recording… tap to send" else "Conversation Mode — tap to speak",
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

            // Re-edit an already-sent image/video (tapped in ChatBubble) using
            // the exact same editor pre-send attachments get — editImageWithAi/
            // editVideoWithAi always produce a new assistant message with the
            // result, same as they already do today, so there's no in-place
            // mutation of the original sent message to worry about. Crop/
            // discard aren't meaningful for an already-sent message (there's
            // no pending-attachment list to update), so those just close the
            // dialog — only the AI-edit/face-swap/voice-over paths apply here.
            viewModel.editingAttachment.value?.let { (uri, isVideo) ->
                AttachmentEditorDialog(
                    uri = uri,
                    isVideo = isVideo,
                    segmentNotes = emptyMap(),
                    onDismiss = { viewModel.closeAttachmentEditor() },
                    onReplaceAttachment = { viewModel.closeAttachmentEditor() },
                    onDiscardAttachment = { viewModel.closeAttachmentEditor() },
                    onSegmentNotesChanged = {},
                    verifiedFaces = viewModel.verifiedFaces.collectAsStateWithLifecycle().value,
                    onDetectFacesInVideo = { u -> viewModel.detectFacesInVideo(u) },
                    onMarkFaceTarget = { frame, box -> viewModel.markFaceTarget(frame, box) },
                    isSynthesizingVoiceOver = viewModel.isSynthesizingVoiceOver.value,
                    onAddVoiceOver = { u, narration, cb -> viewModel.addVoiceOverToVideo(u, narration, cb) },
                    isAiEditingVideo = viewModel.isEditingVideo.value,
                    onAiEditVideo = { u, faceRef, faceTarget, instruction, cb -> viewModel.editVideoWithAi(u, faceRef, faceTarget, instruction, cb) },
                    isAiEditingImage = viewModel.isEditingImage.value,
                    onAiEditImage = { u, extraImages, isFaceSwap, instruction, cb -> viewModel.editImageWithAi(u, extraImages, isFaceSwap, instruction, cb) }
                )
            }

            // Whatever's currently being iterated on (an image/video just
            // edited, or tapped from an earlier bubble) — one tap to reopen
            // its editor without scrolling back to find it. Same pill
            // template as the Conversation Mode indicator above, stacked
            // just below it rather than overlapping.
            val activeWorkItem = viewModel.activeWorkItem.value
            AnimatedVisibility(
                visible = activeWorkItem != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = if (isHandsFree) 56.dp else 8.dp)
            ) {
                activeWorkItem?.let { (uri, isVideo) ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.clickable { viewModel.openAttachmentEditor(uri, isVideo) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Icon(if (isVideo) Icons.Default.Movie else Icons.Default.Image, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Editing — tap to continue", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.clearActiveWorkItem() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, "Dismiss", tint = MaterialTheme.colorScheme.onSecondary)
                            }
                        }
                    }
                }
            }

            if (showProposeMeetup) {
                val contact = viewModel.activeSocialContact.value
                com.example.mistreal_mini.ui.business.ProposeMeetupDialog(
                    businessId = viewModel.activeBusinessId.value,
                    counterpartyPlatform = contact?.platform,
                    counterpartyContactId = contact?.id,
                    onDismiss = { showProposeMeetup = false },
                    onProposed = { showProposeMeetup = false }
                )
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
