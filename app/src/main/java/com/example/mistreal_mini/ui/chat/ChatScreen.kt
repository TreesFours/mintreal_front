package com.example.mistreal_mini.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalFocusManager
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.*
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.service.VoiceService
import com.example.mistreal_mini.util.ScreenshotHelper
import com.example.mistreal_mini.util.VoiceRecorder
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onSubscribeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onDashboardClick: () -> Unit
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf("") }
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val selectedProvider by viewModel.selectedProvider
    val currentChatPartner by viewModel.currentChatPartner
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current

    // 📜 Auto-scroll to bottom when new messages arrive
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
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val voiceRecorder = remember { VoiceRecorder(context) }
    var isRecording by remember { mutableStateOf(false) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordingDuration by remember { mutableIntStateOf(0) }
    var isPlayingBack by remember { mutableStateOf(false) }

    // 📸 Camera / 📁 File Picker
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && cameraUri != null) {
            viewModel.sendAttachment(cameraUri!!, "image")
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let { viewModel.sendAttachment(it, "file") } }

    fun captureImage() {
        val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraUri = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0
            while (isRecording) {
                delay(1000)
                recordingDuration++
            }
        }
    }

    if (screenshotUri != null) {
        AlertDialog(
            onDismissRequest = { screenshotUri = null },
            title = { Text("Preview Screenshot") },
            text = {
                AsyncImage(
                    model = screenshotUri,
                    contentDescription = "Screenshot Preview",
                    modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            confirmButton = {
                Button(onClick = { 
                    viewModel.sendAttachment(screenshotUri!!)
                    screenshotUri = null 
                }) { Text("Send") }
            },
            dismissButton = {
                TextButton(onClick = { screenshotUri = null }) { Text("Cancel") }
            }
        )
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
                            Text(currentChatPartner, style = MaterialTheme.typography.titleMedium)
                            Text("Active", style = MaterialTheme.typography.labelSmall, color = Color.Green)
                        }
                    }
                },
                actions = {
                    // Provider Switcher
                    var showProviders by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showProviders = true }) { Icon(Icons.Default.SmartToy, "Provider") }
                        DropdownMenu(expanded = showProviders, onDismissRequest = { showProviders = false }) {
                            viewModel.availableProviders.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider) },
                                    onClick = { viewModel.setProvider(provider); showProviders = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDashboardClick) { Icon(Icons.Default.Dashboard, "Dashboard") }
                    IconButton(onClick = { viewModel.syncSocials() }) { Icon(Icons.Default.Sync, "Sync") }
                    IconButton(onClick = onSettingsClick) { Icon(Icons.Default.Settings, "Settings") }
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
                            viewModel.sendAttachment(Uri.fromFile(it), "audio")
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
                    onScreenshotClick = { screenshotUri = ScreenshotHelper.captureAndSave(context as Activity) },
                    onCameraClick = { captureImage() },
                    onFileClick = { filePickerLauncher.launch("*/*") },
                    onVoiceClick = { 
                        isRecording = true
                        recordedFile = voiceRecorder.startRecording()
                    },
                    isLoading = isLoading
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(messages) { msg -> ChatBubble(msg) }
                if (isLoading) item { TypingIndicator() }
            }

            if (showContactList) {
                ModalNavigationDrawer(
                    drawerContent = {
                        ModalDrawerSheet {
                            Text("Contacts", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
                            NavigationDrawerItem(label = { Text("AI Assistant") }, selected = currentChatPartner == "AI", onClick = { viewModel.switchChat("AI"); showContactList = false })
                            HorizontalDivider()
                            Text("Social Friends", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.labelSmall)
                            // Mock friends for now
                            NavigationDrawerItem(label = { Text("Sarah (Twitter)") }, selected = false, onClick = { viewModel.switchChat("Sarah"); showContactList = false })
                        }
                    },
                    content = {}
                )
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val context = LocalContext.current
    var transcription by remember { mutableStateOf<String?>(null) }
    var isTranscribing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = align) {
        Surface(
            color = color.copy(alpha = 0.9f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 280.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                if (message.type == "image" && message.attachmentPath != null) {
                    AsyncImage(
                        model = message.attachmentPath,
                        contentDescription = "Image attachment",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (message.type == "audio") {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Audio")
                            Text("Voice Message", modifier = Modifier.padding(start = 8.dp))
                            
                            Spacer(modifier = Modifier.weight(1f))
                            
                            if (isTranscribing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                IconButton(
                                    onClick = {
                                        isTranscribing = true
                                        // Mocking the transcription result for the demo
                                        androidx.core.content.ContextCompat.getMainExecutor(context).execute {
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                transcription = "Transcribed: I'm interested in the new social sync features."
                                                isTranscribing = false
                                            }, 1500)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = "Transcribe", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        transcription?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isUser) Color.White.copy(alpha = 0.7f) else Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                } else if (message.type == "social_draft") {
                    Column(modifier = Modifier.padding(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (message.socialMetadata?.platform == "Twitter") Icons.Default.Public else Icons.Default.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Draft for ${message.socialMetadata?.platform ?: "Social"}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isUser) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(message.content, color = if (isUser) Color.White else Color.Black)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { viewModel.discardSocialAction(message) }) {
                                Text("Discard", color = if (isUser) Color.White.copy(alpha = 0.7f) else Color.Gray)
                            }
                            Button(
                                onClick = { viewModel.approveSocialAction(message) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isUser) Color.White else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isUser) MaterialTheme.colorScheme.primary else Color.White
                                )
                            ) {
                                Text("Approve & Post")
                            }
                        }
                    }
                } else if (message.type == "file") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, contentDescription = "File")
                        Text("Document Shared", modifier = Modifier.padding(start = 8.dp))
                    }
                } else if (message.content.isNotEmpty()) {
                    Text(message.content, color = if (isUser) Color.White else Color.Black)
                }
            }
        }
        Text(text = if (isUser) "You" else message.provider.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(4.dp))
    }
}

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
                    Text("Voice Message Preview", style = MaterialTheme.typography.bodyMedium)
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

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onScreenshotClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFileClick: () -> Unit,
    onVoiceClick: () -> Unit,
    isLoading: Boolean
) {
    Surface(
        tonalElevation = 4.dp, 
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface, // Ensure solid background
        modifier = Modifier.navigationBarsPadding() // Prevent overlap with system nav bar
    ) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
            // Action Buttons Row - Now on top
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onScreenshotClick) { Icon(Icons.Default.Screenshot, "Screenshot") }
                IconButton(onClick = onCameraClick) { Icon(Icons.Default.PhotoCamera, "Camera") }
                IconButton(onClick = onFileClick) { Icon(Icons.Default.AttachFile, "File") }
                IconButton(onClick = onVoiceClick) { Icon(Icons.Default.Mic, "Voice") }
            }
            
            // Text Input Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = text, 
                    onValueChange = onTextChange, 
                    modifier = Modifier.weight(1f), 
                    placeholder = { Text("Type...") },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, 
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Use a Box to handle the "enabled" state for FAB manually if needed, 
                // but standard FAB doesn't have an enabled parameter, it just takes an onClick.
                // We will use IconButton or simply wrap FAB.
                Box {
                    FloatingActionButton(
                        onClick = { if (text.isNotBlank() && !isLoading) onSend() },
                        modifier = Modifier.size(48.dp),
                        containerColor = if (text.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary else Color.Gray,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }
}

@Composable
fun TypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(8.dp)
    ) {
        val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(android.R.drawable.stat_notify_chat)) // Generic placeholder
        val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
        
        Text(
            "AI is thinking...", 
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
