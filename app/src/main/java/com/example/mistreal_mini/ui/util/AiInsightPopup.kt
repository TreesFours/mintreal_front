package com.example.mistreal_mini.ui.util

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.chat.components.*
import com.example.mistreal_mini.util.VoiceRecorder
import java.io.File

@Composable
fun AiInsightPopup(
    contextText: String,
    onClose: () -> Unit,
    viewModel: ChatViewModel,
    sourcePost: com.example.mistreal_mini.data.model.SocialPost? = null
) {
    val context = LocalContext.current
    var textState by remember { mutableStateOf("") }
    val messages = viewModel.messages
    val isLoading by viewModel.isLoading
    val focusManager = LocalFocusManager.current
    
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f) // Expanded to match red margin
            .pointerInput(Unit) {
                detectTapGestures(onTap = { focusManager.clearFocus() })
            },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Drafting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                
                // Professional Clear Button for Mini Chat
                IconButton(onClick = { viewModel.clearSessionMessages() }) { 
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear Session", tint = Color.Gray)
                }

                IconButton(onClick = { 
                    val lastAiMsg = messages.lastOrNull { it.role == "assistant" }?.content ?: contextText
                    viewModel.secureAsScribe(sourcePost, lastAiMsg)
                    onClose()
                }) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = "Secure Scribe", tint = MaterialTheme.colorScheme.primary)
                }
                
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Quick model switcher — full parity with the main chat drawer's
            // picker (ContactListDrawer.kt), not a flat list: same FREE/PREMIUM
            // tabs, same category accordions (ModelCategoryAccordion, reused
            // directly — it's self-contained, no NavController/drawer-only
            // state needed), same capability icons so visual models
            // (Pollinations/Imagen/Veo/free video) are identifiable here too.
            // Reuses the same ChatViewModel instance the drawer mutates, so
            // switching here applies everywhere immediately.
            var showModelSwitcher by remember { mutableStateOf(false) }
            var modelTab by remember { mutableIntStateOf(0) }
            var expandedModelCategory by remember { mutableStateOf("") }
            val currentModelName = viewModel.availableProviders
                .find { it.id == viewModel.selectedProvider.value }?.name
                ?: "Dynamic (Best Fit)"

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showModelSwitcher = !showModelSwitcher }.padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(currentModelName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                Icon(
                    if (showModelSwitcher) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Switch model", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)
                )
            }

            if (showModelSwitcher) {
                val categorizedModels = viewModel.categorizedModels.value
                val sortedCategories = listOf("COMMAND CENTER", "GLOBAL OVERLORD", "OPTIC INTEL", "GHOST PROTOCOL", "OPEN INTELLIGENCE")
                Column(modifier = Modifier.heightIn(max = 260.dp)) {
                    TabRow(selectedTabIndex = modelTab, modifier = Modifier.padding(bottom = 4.dp)) {
                        Tab(selected = modelTab == 0, onClick = { modelTab = 0 }, text = { Text("FREE", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                        Tab(selected = modelTab == 1, onClick = { modelTab = 1 }, text = { Text("PREMIUM", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                    }
                    LazyColumn {
                        sortedCategories.forEach { category ->
                            val models = (categorizedModels[category] ?: emptyList())
                                .filter { if (modelTab == 0) !it.isProOnly else it.isProOnly }
                            if (models.isNotEmpty()) {
                                item {
                                    ModelCategoryAccordion(
                                        title = category,
                                        models = models,
                                        selectedModelId = viewModel.selectedProvider.value,
                                        isPro = viewModel.isPro.value,
                                        isExpanded = expandedModelCategory == category,
                                        onToggleExpand = {
                                            expandedModelCategory = if (expandedModelCategory == category) "" else category
                                        },
                                        onModelSelect = { model ->
                                            viewModel.setProvider(model.id)
                                            showModelSwitcher = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Context: ${contextText.take(50)}...",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(messages.takeLast(5)) { msg ->
                    ChatBubble(
                        message = msg, 
                        viewModel = viewModel, 
                        onAiInsight = {}, 
                        onReadAloud = { text, mode ->
                            when (mode) {
                                InteractionMode.SINGLE -> viewModel.readAloud(text)
                                InteractionMode.HANDS_FREE -> viewModel.startHandsFreeLoop(text)
                                InteractionMode.RADIO -> viewModel.startRadioMode(text)
                            }
                        }, 
                        snackbarHostState = remember { SnackbarHostState() }, 
                        coroutineScope = rememberCoroutineScope(),
                        targetLang = viewModel.defaultTranslationLang.value
                    )
                }
            }

            if (isRecording) {
                Text("Say \"Yes, send that\" to confirm", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            
            if (isRecording || recordedFile != null) {
                VoiceRecordingBar(
                    isRecording = isRecording,
                    duration = recordingDuration,
                    recordedFile = recordedFile,
                    isPlayingBack = isPlayingBack,
                    onStopRecording = { isRecording = false; voiceRecorder.stopRecording() },
                    onDelete = { recordedFile = null; isRecording = false },
                    onPlay = { recordedFile?.let { voiceRecorder.playRecording(it) { isPlayingBack = false } } },
                    onSend = {
                        recordedFile?.let { viewModel.sendAttachments(listOf(Uri.fromFile(it)), "audio") }
                        recordedFile = null
                    }
                )
            } else {
                ChatInputBar(
                    text = textState, 
                    onTextChange = { textState = it }, 
                    onSend = {
                        focusManager.clearFocus()
                        if (textState.lowercase() == "yes send that") {
                            viewModel.sendMessage("Confirming reply dispatch.")
                        } else {
                            // draftSocialReply (not sendMessage) — this popup is an AI
                            // drafting surface, including when opened inside a real
                            // human-to-human DM thread. sendMessage() in that context
                            // sends the raw prompt as a literal outgoing DM and never
                            // calls the AI at all; draftSocialReply asks the AI and only
                            // ever sends the AI's own cleaned reply, and only if the
                            // user has autosend explicitly enabled.
                            val fullPrompt = "Based on this context: $contextText\n\nUser Question/Instruction: $textState"
                            viewModel.draftSocialReply(fullPrompt)
                        }
                        textState = ""
                    },
                    onScreenshotClick = { },
                    onScreenRecordClick = { },
                    onCameraClick = { },
                    onVideoClick = { },
                    onFileClick = { },
                    onVoiceClick = { isRecording = true; recordedFile = voiceRecorder.startRecording() },
                    onScribeClick = { },
                    isLoading = isLoading,
                    pendingAttachments = emptyList(),
                    onRemoveAttachment = { },
                    isAutoReplyEnabled = viewModel.guardianEnabled.value,
                    onToggleAutoReply = { viewModel.setGuardianEnabled(it) }
                )
            }
        }
    }
}
