package com.example.mistreal_mini.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.*
import com.example.mistreal_mini.data.model.SocialMetadata
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.local.dao.SocialContactDao
import com.example.mistreal_mini.data.local.entity.SocialContactEntity
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatRequest
import com.example.mistreal_mini.data.model.ChatResponse
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.data.repository.TacticalRepository
import com.example.mistreal_mini.data.repository.SensorRepository
import com.example.mistreal_mini.domain.usecase.HandleDistressUseCase
import com.example.mistreal_mini.domain.usecase.SendMessageUseCase
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import com.example.mistreal_mini.util.FileUtil
import com.example.mistreal_mini.util.VoiceManager
import com.example.mistreal_mini.util.VoiceRecorder
import com.example.mistreal_mini.util.TextSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AiRepository,
    private val infoRepository: InfoRepository,
    private val preferenceManager: PreferenceManager,
    private val socialContactDao: SocialContactDao,
    private val voiceManager: VoiceManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val handleDistressUseCase: HandleDistressUseCase,
    private val tacticalRepository: TacticalRepository,
    private val sensorRepository: SensorRepository,
    private val scribeRepository: com.example.mistreal_mini.data.repository.ScribeRepository,
    private val scribeManager: com.example.mistreal_mini.util.ScribeManager,
    private val voiceRecorder: com.example.mistreal_mini.util.VoiceRecorder,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        // Shared tag parsers — both performChatRequest() and draftSocialReply()
        // must strip these before anything is displayed or (for social drafts)
        // potentially sent as a real message.
        private val feelingsRegex = Regex("\\[TRUE_FEELINGS: (.*?)\\]", RegexOption.DOT_MATCHES_ALL)
        private val moodRegex = Regex("\\[MOOD:\\s*(\\w+)\\]", RegexOption.IGNORE_CASE)
        private val socialAutosendRegex = Regex("\\[SOCIAL_AUTOSEND:\\s*true\\]", RegexOption.IGNORE_CASE)
    }

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    private val _selectedProvider = mutableStateOf(savedStateHandle.get<String>("selectedProvider") ?: "dynamic")
    val selectedProvider: State<String> = _selectedProvider

    private val _availableProviders = mutableStateListOf<AiModelResponse>()
    val availableProviders: List<AiModelResponse> = _availableProviders

    // 🛡️ Categorized Models for Tactical Drawer
    private val _categorizedModels = mutableStateOf<Map<String, List<AiModelResponse>>>(emptyMap())
    val categorizedModels: State<Map<String, List<AiModelResponse>>> = _categorizedModels

    // Drives the drawer's 3rd "Custom" tab — only shown once the user has
    // actually configured at least one BYOK text/video/image/video-gen provider.
    private val _hasCustomProvider = mutableStateOf(false)
    val hasCustomProvider: State<Boolean> = _hasCustomProvider

    private val _customProviderSummary = mutableStateOf<List<String>>(emptyList())
    val customProviderSummary: State<List<String>> = _customProviderSummary

    private val _currentPersona = mutableStateOf(savedStateHandle.get<String>("currentPersona") ?: "Shadow")
    val currentPersona: State<String> = _currentPersona

    private val _aiCustomName = mutableStateOf("Shadow AI")
    val aiCustomName: State<String> = _aiCustomName

    private val _currentChatPartner = mutableStateOf(savedStateHandle.get<String>("currentChatPartner") ?: "AI")
    val currentChatPartner: State<String> = _currentChatPartner

    private val _currentChatPartnerStatus = mutableStateOf("Active")
    val currentChatPartnerStatus: State<String> = _currentChatPartnerStatus

    private val _currentChatPartnerPlatform = mutableStateOf("ai")
    val currentChatPartnerPlatform: State<String> = _currentChatPartnerPlatform

    private val _isPro = mutableStateOf(false)
    val isPro: State<Boolean> = _isPro

    private val _isHandsFreeActive = mutableStateOf(false)
    val isHandsFreeActive: State<Boolean> = _isHandsFreeActive

    private val _isListening = mutableStateOf(false)
    val isListening: State<Boolean> = _isListening

    private val _guardianEnabled = mutableStateOf(false)
    val guardianEnabled: State<Boolean> = _guardianEnabled

    private val _isTtsEnabled = mutableStateOf(true)
    val isTtsEnabled: State<Boolean> = _isTtsEnabled

    private val _voiceNoteAutoplay = mutableStateOf(true)
    val voiceNoteAutoplay: State<Boolean> = _voiceNoteAutoplay

    // Set right after an AI voice-note file is created (when autoplay is on) so the
    // bubble that owns that exact Uri knows to play itself once, then clears it —
    // avoids replaying old voice notes whenever the list recomposes/scrolls.
    private val _pendingVoiceNoteAutoplayUri = mutableStateOf<String?>(null)
    val pendingVoiceNoteAutoplayUri: State<String?> = _pendingVoiceNoteAutoplayUri

    fun consumeVoiceNoteAutoplay(uri: String) {
        if (_pendingVoiceNoteAutoplayUri.value == uri) {
            _pendingVoiceNoteAutoplayUri.value = null
        }
    }

    fun onVoiceNotePlaybackFinished() {
        if (_isHandsFreeActive.value) {
            viewModelScope.launch { startListeningLoop() }
        }
    }

    private val _isSttEnabled = mutableStateOf(true)
    val isSttEnabled: State<Boolean> = _isSttEnabled

    private val _socialContacts = mutableStateOf<List<SocialContact>>(emptyList())
    val socialContacts: State<List<SocialContact>> = _socialContacts

    private val _unreadMessages = mutableStateOf<List<UnreadItem>>(emptyList())
    val unreadMessages: State<List<UnreadItem>> = _unreadMessages

    private val _availablePlatforms = mutableStateListOf<SocialPlatformResponse>()
    val availablePlatforms: List<SocialPlatformResponse> = _availablePlatforms

    private val _bearing = mutableStateOf(0f)
    val bearing: State<Float> = _bearing

    private val _orientation = mutableStateOf("N")
    val orientation: State<String> = _orientation

    private val _isSocialChat = mutableStateOf(false)
    val isSocialChat: State<Boolean> = _isSocialChat

    val recentContacts = socialContactDao.getRecentContacts()
    val emergencyContacts = socialContactDao.getEmergencyContacts()

    private val _activeSocialContact = mutableStateOf<SocialContact?>(null)
    val activeSocialContact: State<SocialContact?> = _activeSocialContact

    private val _currentTrendTitle = mutableStateOf<String?>(null)
    val currentTrendTitle: State<String?> = _currentTrendTitle

    private val _pendingAttachments = mutableStateListOf<Uri>()
    val pendingAttachments: List<Uri> = _pendingAttachments

    // Per-segment instructions attached to a pending video, keyed by that attachment's
    // Uri (set via the full-size attachment editor's "Split into 6" tool).
    private val _attachmentSegmentNotes = mutableStateMapOf<Uri, Map<Int, String>>()
    val attachmentSegmentNotes: Map<Uri, Map<Int, String>> = _attachmentSegmentNotes

    private val _uniqueTrends = mutableStateListOf<ChatMessage>()
    val uniqueTrends: List<ChatMessage> = _uniqueTrends

    // Minichats the user has pinned so HistoryWorker's 4-day staleness purge never
    // touches them, regardless of how long it's been since the last message.
    private val _pinnedTrendTitles = mutableStateListOf<String>()
    val pinnedTrendTitles: List<String> = _pinnedTrendTitles

    fun toggleTrendPinned(trendTitle: String) {
        viewModelScope.launch {
            val nowPinned = !_pinnedTrendTitles.contains(trendTitle)
            repository.setTrendPinned(trendTitle, nowPinned)
        }
    }

    private val _isSceneMode = mutableStateOf(false)
    val isSceneMode: State<Boolean> = _isSceneMode

    private val _isScribing = mutableStateOf(false)
    val isScribing: State<Boolean> = _isScribing

    private val _defaultTranslationLang = mutableStateOf("English")
    val defaultTranslationLang: State<String> = _defaultTranslationLang

    private val _persistentSceneMode = mutableStateOf(false)

    // Voice Recording State
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _recordingDuration = mutableIntStateOf(0)
    val recordingDuration: State<Int> = _recordingDuration

    private val _recordedFile = mutableStateOf<File?>(null)
    val recordedFile: State<File?> = _recordedFile

    private val _isPlayingBack = mutableStateOf(false)
    val isPlayingBack: State<Boolean> = _isPlayingBack

    private val _scribeText = MutableStateFlow("")
    val scribeText = _scribeText.asStateFlow()

    private var recordingTimerJob: Job? = null

    // Switches the underlying paged source with the active trend so a minichat
    // shows its own messages instead of always rendering the main chat's query.
    private val _activeTrendForPaging = MutableStateFlow<String?>(null)
    val pagedMessages: Flow<PagingData<ChatMessage>> = _activeTrendForPaging
        .flatMapLatest { trend ->
            if (trend == null) repository.getPagedMessagesFlow() else repository.getPagedTrendMessagesFlow(trend)
        }
        .cachedIn(viewModelScope)

    fun getTrendMessages(title: String): Flow<List<ChatMessage>> {
        return repository.getTrendMessages(title)
    }

    init {
        viewModelScope.launch {
            preferenceManager.aiPersona.collect { _currentPersona.value = it }
        }
        viewModelScope.launch {
            preferenceManager.aiCustomName.collect { _aiCustomName.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isPro.collect { _isPro.value = it }
        }
        viewModelScope.launch {
            preferenceManager.guardianEnabled.collect { _guardianEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isTtsEnabled.collect { _isTtsEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.voiceNoteAutoplay.collect { _voiceNoteAutoplay.value = it }
        }
        viewModelScope.launch {
            repository.getPinnedTrendTitles().collect {
                _pinnedTrendTitles.clear()
                _pinnedTrendTitles.addAll(it)
            }
        }
        viewModelScope.launch {
            preferenceManager.isSttEnabled.collect { _isSttEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isPersistentSceneModeEnabled.collect { 
                _persistentSceneMode.value = it
                updateSceneMode()
            }
        }
        viewModelScope.launch {
            preferenceManager.defaultTranslationLang.collect { _defaultTranslationLang.value = it }
        }
        viewModelScope.launch {
            voiceManager.transcripts.collect { transcript ->
                onHandsFreeTranscript(transcript)
            }
        }
        viewModelScope.launch {
            scribeManager.results.collect { transcript ->
                _scribeText.value = transcript
            }
        }
        
        viewModelScope.launch {
            sensorRepository.getOrientationFlow().collect { data ->
                _bearing.value = data.bearing
                _orientation.value = data.orientation
            }
        }

        observeMessages()
        observeUniqueTrends()
    }

    // Network-backed loads deferred out of init{} — they used to fire the
    // instant this ViewModel was constructed at the Activity's top level,
    // which happened before the splash/auth/biometric gate ever resolved.
    // MainActivity now calls this only once full authentication succeeds.
    fun onAuthenticated() {
        fetchAvailableModels()
        fetchAvailablePlatforms()
        fetchHasCustomProvider()
    }

    private fun observeUniqueTrends() {
        viewModelScope.launch {
            repository.getUniqueTrends().collect { trends ->
                _uniqueTrends.clear()
                _uniqueTrends.addAll(trends)
            }
        }
    }

    private var messagesJob: Job? = null

    private fun observeMessages() {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            if (_isSocialChat.value) {
                return@launch
            }

            repository.getAllMessages().collect { allMsgs ->
                val currentTitle = _currentTrendTitle.value

                val filtered = if (currentTitle == null) {
                    allMsgs.filter { !it.isTrend }
                } else {
                    allMsgs.filter { it.isTrend && it.trendTitle == currentTitle }
                }

                _messages.clear()
                _messages.addAll(filtered)

                Timber.d("📬 Chat Update: ${filtered.size} messages synced (Trend: $currentTitle)")
            }
        }
    }

    fun loadTrend(title: String) {
        _isSocialChat.value = false
        _currentTrendTitle.value = title
        _activeTrendForPaging.value = title
        _messages.clear()
        observeMessages()
    }

    fun exitTrend() {
        _isSocialChat.value = false
        _currentTrendTitle.value = null
        _activeTrendForPaging.value = null
        _messages.clear()
        observeMessages()
    }

    fun toggleTts(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setTtsEnabled(enabled) }
    }

    fun toggleStt(enabled: Boolean) {
        _isSttEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setSttEnabled(enabled) }
    }

    fun setGuardianEnabled(enabled: Boolean) {
        _guardianEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setGuardianEnabled(enabled) }
    }

    /**
     * Per-minichat Ghost Responder override for a social DM thread — distinct from
     * the global [guardianEnabled] master switch. Auto-reply for a given contact
     * only actually fires when BOTH are on, so flipping the master switch never
     * silently starts auto-replying to every contact at once.
     */
    fun toggleActiveContactAutoReply() {
        val contact = _activeSocialContact.value ?: return
        val newValue = !contact.autoReplyEnabled
        _activeSocialContact.value = contact.copy(autoReplyEnabled = newValue)
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val result = infoRepository.setContactAutoReply(deviceId, contact.platform, contact.id, newValue)
            if (result is Resource.Error) {
                _activeSocialContact.value = contact // roll back
                _errorEvents.emit("Couldn't update auto-reply for ${contact.name}: ${result.message}")
            }
        }
    }

    fun setVoiceNoteAutoplay(enabled: Boolean) {
        _voiceNoteAutoplay.value = enabled
        viewModelScope.launch { preferenceManager.setVoiceNoteAutoplay(enabled) }
    }

    // Voice Recording Logic
    fun startRecording() {
        if (!_isSttEnabled.value) {
            viewModelScope.launch { _errorEvents.emit("Voice input disabled in settings") }
            return
        }
        _isRecording.value = true
        _recordedFile.value = voiceRecorder.startRecording()
        
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            _recordingDuration.intValue = 0
            while (_isRecording.value) {
                kotlinx.coroutines.delay(1000)
                _recordingDuration.intValue++
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        voiceRecorder.stopRecording()
        recordingTimerJob?.cancel()
    }

    fun deleteRecording() {
        _recordedFile.value?.let { voiceRecorder.deleteRecording(it) }
        _recordedFile.value = null
        _isRecording.value = false
        recordingTimerJob?.cancel()
    }

    fun playRecording() {
        _recordedFile.value?.let {
            _isPlayingBack.value = true
            voiceRecorder.playRecording(it) { _isPlayingBack.value = false }
        }
    }

    fun sendVoiceMessage() {
        _recordedFile.value?.let {
            handleAudioInput(Uri.fromFile(it))
        }
        _recordedFile.value = null
    }

    fun handleAudioInput(uri: Uri?) {
        _isListening.value = false
        if (uri != null) {
            sendMessage("", listOf(uri), "audio")
        } else if (_isHandsFreeActive.value) {
            viewModelScope.launch {
                voiceManager.speak("I couldn't hear that. Are you still there?") {
                    viewModelScope.launch { startListeningLoop() }
                }
            }
        }
    }

    fun cancelRecording() {
        deleteRecording()
    }

    // --- SOS ambient audio capture ---
    // Pressing SOS (manual or auto-triggered) starts recording ambient audio as
    // evidence of the situation, independent of the chat voice-note recorder's
    // state. Capped at a fixed duration rather than requiring a manual stop,
    // since this fires during an emergency and must not depend on further input.
    private val _isSosRecording = mutableStateOf(false)
    val isSosRecording: State<Boolean> = _isSosRecording
    private var sosRecordingFile: File? = null
    private var sosRecordingTimeoutJob: Job? = null
    private val SOS_RECORDING_MAX_DURATION_MS = 120_000L

    private fun startSosAudioCapture() {
        if (_isSosRecording.value || _isRecording.value) return // don't fight the chat recorder for the mic
        val file = voiceRecorder.startRecording() ?: return
        sosRecordingFile = file
        _isSosRecording.value = true
        sosRecordingTimeoutJob?.cancel()
        sosRecordingTimeoutJob = viewModelScope.launch {
            kotlinx.coroutines.delay(SOS_RECORDING_MAX_DURATION_MS)
            stopSosAudioCapture()
        }
    }

    private fun stopSosAudioCapture() {
        if (!_isSosRecording.value) return
        sosRecordingTimeoutJob?.cancel()
        voiceRecorder.stopRecording()
        _isSosRecording.value = false
        val recorded = sosRecordingFile
        sosRecordingFile = null
        if (recorded != null && recorded.exists() && recorded.length() > 0) {
            viewModelScope.launch {
                val saved = try {
                    val sosDir = java.io.File(context.filesDir, "sos_recordings").apply { if (!exists()) mkdirs() }
                    val dest = java.io.File(sosDir, recorded.name)
                    recorded.copyTo(dest, overwrite = true)
                    recorded.delete()
                    dest
                } catch (e: Exception) {
                    Timber.e(e, "Failed to persist SOS audio evidence")
                    null
                }
                if (saved != null) {
                    _errorEvents.emit("SOS audio evidence recorded (${saved.name})")
                }
            }
        }
    }

    fun fetchAvailablePlatforms() {
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getAvailablePlatforms(deviceId)) {
                is Resource.Success -> {
                    _availablePlatforms.clear()
                    result.data?.let { platforms ->
                        _availablePlatforms.addAll(platforms)
                    }
                }
                else -> {}
            }
        }
    }

    fun fetchHasCustomProvider() {
        viewModelScope.launch {
            val byokStatus = (infoRepository.getByokStatus() as? Resource.Success)?.data
            val byokVideoStatus = (infoRepository.getByokVideoStatus() as? Resource.Success)?.data
            val imageConfigs = (infoRepository.getMediaProviderConfigs("image_gen") as? Resource.Success)?.data?.configs.orEmpty()
            val videoConfigs = (infoRepository.getMediaProviderConfigs("video_gen") as? Resource.Success)?.data?.configs.orEmpty()

            val summary = buildList {
                if (byokStatus?.configured == true) add("💬 Text — ${byokStatus.providerType}${byokStatus.modelName?.let { " ($it)" } ?: ""}")
                if (byokVideoStatus?.configured == true) add("🎬 Video Edit — ${byokVideoStatus.providerType}${byokVideoStatus.modelName?.let { " ($it)" } ?: ""}")
                imageConfigs.forEach { add("🖼️ Image Gen — ${it.label}") }
                videoConfigs.forEach { add("📹 Video Gen — ${it.label}") }
            }
            _customProviderSummary.value = summary
            _hasCustomProvider.value = summary.isNotEmpty()
        }
    }

    fun toggleHandsFree(active: Boolean) {
        _isHandsFreeActive.value = active
        if (!active) {
            _isListening.value = false
            voiceManager.stop()
            context.stopService(Intent(context, com.example.mistreal_mini.service.VoiceService::class.java))
            // A voice note already queued to autoplay must not fire after stop —
            // its onPlaybackFinished would otherwise resume the listen loop.
            _pendingVoiceNoteAutoplayUri.value = null
        }
    }

    fun fetchAvailableModels() {
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            when (val result = repository.getAvailableModels(deviceId)) {
                is Resource.Success -> {
                    val providers = result.data.orEmpty()
                    _availableProviders.clear()
                    
                    _availableProviders.add(AiModelResponse("dynamic", "Dynamic (Best Fit)", "mistreal", false, "Free"))
                    
                    val filteredProviders = if (_isPro.value) {
                        providers 
                    } else {
                        providers.filter { it.price == "Free" || it.price.lowercase() == "free" }
                    }
                    _availableProviders.addAll(filteredProviders)

                    if (filteredProviders.isEmpty() && !_isPro.value) {
                        _errorEvents.emit("Free tier has limited AI access. Upgrade to Premium for all models.")
                    }

                    if (filteredProviders.none { it.id == _selectedProvider.value } && _selectedProvider.value != "dynamic") {
                        _selectedProvider.value = "dynamic"
                    }

                    // 🛡️ Trigger Tactical Categorization
                    groupModels(filteredProviders)
                }
                is Resource.Error -> {
                    _availableProviders.clear()
                    _availableProviders.add(AiModelResponse("dynamic", "Dynamic (Best Fit)", "mistreal", false, "Free"))
                    _selectedProvider.value = "dynamic"
                    _errorEvents.emit(result.message ?: "Could not reach the AI backend.")
                }
                else -> {}
            }
        }
    }

    private fun groupModels(models: List<AiModelResponse>) {
        val groups = mutableMapOf<String, MutableList<AiModelResponse>>()
        
        // 1. COMMAND CENTER (Always at top)
        groups["COMMAND CENTER"] = mutableListOf(
            AiModelResponse("dynamic", "Mistreal Dynamic", "mistreal", false, "Free", "Optimized", 100)
        )

        models.forEach { model ->
            val id = model.id.toLowerCase()
            val category = when {
                // GLOBAL OVERLORD: Large models
                id.contains("gpt-4") || id.contains("claude-3") || id.contains("llama-3.1-405b") || 
                id.contains("grok") || id.contains("deepseek") || id.contains("llama-3.1-70b") ||
                (id.contains("gemini") && id.contains("pro") && !id.contains("flash")) -> "GLOBAL OVERLORD"

                // OPTIC INTEL: Vision/Video/Flash. Prefer real backend capability
                // metadata over id-substring guessing; fall back to the old
                // heuristic only when capabilities weren't provided (rollout safety).
                model.capabilities?.let { it.videoGen || it.voice || it.imageGen } == true -> "OPTIC INTEL"
                model.capabilities == null &&
                    (id.contains("vision") || id.contains("flash") || id.contains("video") || id.contains("kling")) -> "OPTIC INTEL"

                // GHOST PROTOCOL: Fast/Mini
                id.contains("mini") || id.contains("haiku") || id.contains("8b") || id.contains("instant") -> "GHOST PROTOCOL"

                // OPEN INTELLIGENCE: Everything else (Llama, Mistral, etc.)
                else -> "OPEN INTELLIGENCE"
            }
            
            groups.getOrPut(category) { mutableListOf() }.add(model)
        }
        
        _categorizedModels.value = groups
    }

    fun switchChat(partner: String, platform: String = "ai") {
        _currentChatPartner.value = partner
        _currentChatPartnerPlatform.value = platform
        
        if (platform == "ai") {
            _isSocialChat.value = false
            _activeSocialContact.value = null
            _currentChatPartnerStatus.value = "Active"
            _messages.clear()
            observeMessages()
        } else {
            _isSocialChat.value = true
            viewModelScope.launch {
                val contact = _socialContacts.value.find { it.name == partner && it.platform == platform }
                contact?.let {
                    socialContactDao.upsert(
                        SocialContactEntity(
                            contactId = it.id,
                            platform = it.platform,
                            name = it.name,
                            avatarUrl = it.avatar,
                            lastInteractionTime = System.currentTimeMillis(),
                            isEmergency = false,
                            platformUserId = it.id
                        )
                    )
                }
            }
            
            val contact = _socialContacts.value.find { it.name == partner && it.platform == platform }
            _activeSocialContact.value = contact
            _currentChatPartnerStatus.value = if (contact?.unreadCount ?: 0 > 0) "New Message" else "Active"
            fetchSocialHistory(partner, platform)
        }
    }

    private fun fetchSocialHistory(partner: String, platform: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val contact = _socialContacts.value.find { it.name == partner && it.platform == platform }
            
            if (contact != null) {
                when (val result = infoRepository.getSocialHistory(deviceId, platform, contact.id)) {
                    is Resource.Success<List<SocialHistoryMessage>> -> {
                        _messages.clear()
                        result.data?.forEach { msg ->
                            _messages.add(
                                ChatMessage(
                                    role = if (msg.direction == "incoming") "user" else "assistant",
                                    content = msg.text ?: "",
                                    type = msg.attachments?.firstOrNull()?.type ?: "text",
                                    attachmentUrl = msg.attachments?.firstOrNull()?.url,
                                    provider = platform,
                                    socialMetadata = SocialMetadata(
                                        type = "Direct Message",
                                        platform = platform,
                                        targetId = contact.id
                                    )
                                )
                            )
                        }
                        if (_messages.isEmpty()) {
                            _messages.add(ChatMessage(role = "assistant", content = "No previous messages with $partner.", provider = "system"))
                        }
                    }
                    is Resource.Error<List<SocialHistoryMessage>> -> {
                        _errorEvents.emit("Failed to fetch history: ${result.message}")
                    }
                    else -> {}
                }
            } else {
                _messages.clear()
                _messages.add(ChatMessage(role = "system", content = "Could not find contact info for $partner.", provider = "system"))
            }
            _isLoading.value = false
        }
    }

    fun draftSocialReply(prompt: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val contact = _activeSocialContact.value
            val isSpaceIntel = _currentTrendTitle.value == "Galactic Intelligence"
            val isMapIntel = _currentTrendTitle.value?.startsWith("Tactical Map:") == true

            val targetName = when {
                contact != null -> contact.name
                isSpaceIntel -> "the Solar System viewer"
                isMapIntel -> "the Tactical Map"
                else -> "the user"
            }

            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            // Server-confirmed value — never trust a client-only flag for a feature
            // that can send real messages on the user's behalf.
            val autoSendEnabled = if (contact != null) {
                val settingsResult = infoRepository.getUserSettings(deviceId)
                (settingsResult as? Resource.Success)?.data?.aiAutoSendEnabled == true
            } else false

            val result = sendMessageUseCase(
                context = context,
                prompt = "Draft a reply or analysis for $targetName about: $prompt",
                persona = _currentPersona.value,
                history = _messages.takeLast(5),
                provider = _selectedProvider.value,
                deviceId = deviceId,
                isSocialAutosendContext = contact != null && autoSendEnabled
            )

            if (result is Resource.Success) {
                val rawContent = result.data?.content ?: ""
                val wantsAutosend = socialAutosendRegex.containsMatchIn(rawContent)
                val cleanContent = rawContent
                    .replace(feelingsRegex, "")
                    .replace(moodRegex, "")
                    .replace(socialAutosendRegex, "")
                    .trim()

                var autosent = false
                if (contact != null && autoSendEnabled && wantsAutosend) {
                    val sendResult = infoRepository.performSocialAction(
                        deviceId = deviceId,
                        type = "Direct Message",
                        platform = contact.platform,
                        content = cleanContent,
                        targetId = contact.id
                    )
                    if (sendResult is Resource.Success) {
                        autosent = true
                        _messages.add(ChatMessage(
                            role = "assistant",
                            content = "Auto-sent to ${contact.name}: \"$cleanContent\"",
                            provider = "system"
                        ))
                    } else {
                        _errorEvents.emit("Auto-send failed, review manually: ${(sendResult as? Resource.Error)?.message}")
                    }
                }

                if (!autosent) {
                    val draftMsg = ChatMessage(
                        role = "assistant",
                        content = cleanContent,
                        provider = "ai_draft",
                        type = "social_draft",
                        isTrend = _currentTrendTitle.value != null,
                        trendTitle = _currentTrendTitle.value,
                        socialMetadata = contact?.let {
                            SocialMetadata(
                                platform = it.platform,
                                type = "Direct Message",
                                targetId = it.id
                            )
                        }
                    )
                    _messages.add(draftMsg)
                }
            }
            _isLoading.value = false
        }
    }

    fun fetchContacts(platform: String) {
        if (platform == "ai") return
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val normalizedPlatform = platform.lowercase()
            when (val result = infoRepository.getContacts(deviceId, normalizedPlatform)) {
                is Resource.Success -> {
                    val rawContacts = result.data ?: emptyList()
                    _socialContacts.value = rawContacts
                    
                    val entities = rawContacts.map { c ->
                        SocialContactEntity(
                            contactId = "${normalizedPlatform}_${c.id}", // 🛡️ Prefixed ID
                            platform = normalizedPlatform,
                            name = c.name,
                            avatarUrl = c.avatar,
                            lastInteractionTime = System.currentTimeMillis(),
                            isEmergency = false,
                            platformUserId = c.id
                        )
                    }
                    // Clean up legacy non-prefixed records for this platform
                    socialContactDao.deleteByPlatform(normalizedPlatform)
                    socialContactDao.upsertAll(entities)
                }
                else -> {}
            }
        }
    }

    fun evictFromHotSlot(contactId: String) {
        viewModelScope.launch {
            socialContactDao.updateInteractionTime(contactId, 0L)
        }
    }

    fun searchContacts(platform: String, query: String) {
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.searchContacts(deviceId, platform, query)) {
                is Resource.Success<List<SocialContact>> -> _socialContacts.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    private val _totalUnreadCount = mutableIntStateOf(0)
    val totalUnreadCount: State<Int> = _totalUnreadCount

    fun fetchUnread() {
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getUnreadMessages(deviceId)) {
                is Resource.Success -> {
                    _unreadMessages.value = result.data ?: emptyList()
                    _totalUnreadCount.intValue = _unreadMessages.value.size
                }
                else -> {}
            }
        }
    }

    fun setProvider(provider: String) {
        _selectedProvider.value = provider
        updateSceneMode()
    }

    private fun updateSceneMode() {
        val modelId = _selectedProvider.value.lowercase()
        val selectedModel = _availableProviders.find { it.id.lowercase() == modelId }
        val modelSupportsVideo = selectedModel?.capabilities?.let { it.videoGen } ?: run {
            // No capability metadata (older cached response) — fall back to the old heuristic.
            modelId.contains("video") || modelId.contains("kling") || modelId.contains("luma") || modelId.contains("runway")
        }
        _isSceneMode.value = _persistentSceneMode.value || modelSupportsVideo
    }

    fun toggleSceneMode(enabled: Boolean) {
        _isSceneMode.value = enabled
    }

    private val _isScreenRecording = mutableStateOf(false)
    val isScreenRecording: State<Boolean> = _isScreenRecording

    private val _isRecordingPaused = mutableStateOf(false)
    val isRecordingPaused: State<Boolean> = _isRecordingPaused

    fun startScreenRecord() {
        _isScreenRecording.value = true
        // Service handles logic
    }

    fun pauseScreenRecord() {
        _isRecordingPaused.value = true
    }

    fun resumeScreenRecord() {
        _isRecordingPaused.value = false
    }

    fun stopScreenRecord() {
        _isScreenRecording.value = false
        _isRecordingPaused.value = false
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
            _messages.clear()
            _uniqueTrends.clear()
        }
    }

    fun deleteTrend(title: String) {
        viewModelScope.launch {
            repository.deleteTrend(title)
            if (_currentTrendTitle.value == title) {
                _messages.clear()
            }
        }
    }

    fun nukeMainChat() {
        viewModelScope.launch {
            val uid = repository.currentUserId
            repository.deleteNonTrendMessages(uid)
        }
    }

    fun clearSessionMessages() {
        _messages.clear()
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            message.id?.let { id ->
                repository.deleteMessage(id)
                _messages.remove(message)
            }
        }
    }

    fun updateNote(message: ChatMessage, newContent: String) {
        viewModelScope.launch {
            message.id?.let { id ->
                repository.updateMessage(id, newContent)
                val index = _messages.indexOf(message)
                if (index != -1) {
                    _messages[index] = message.copy(content = newContent)
                }
            }
        }
    }

    fun approveSocialAction(draft: ChatMessage, shareToCommunity: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val metadata = draft.socialMetadata

            val result = infoRepository.performSocialAction(
                deviceId = deviceId,
                type = metadata?.type ?: "Post",
                platform = metadata?.platform ?: "Twitter",
                content = draft.content,
                targetId = metadata?.targetId ?: "self",
                shareToCommunity = shareToCommunity
            )
            if (result is Resource.Success) {
                _messages.add(ChatMessage(role = "assistant", content = "Action Approved & Executed on ${metadata?.platform ?: "platform"}.", provider = "system"))
            } else {
                _errorEvents.emit("Action failed: ${(result as Resource.Error).message}")
            }
            _isLoading.value = false
        }
    }

    fun discardSocialAction(draft: ChatMessage) {
        _messages.add(ChatMessage(role = "assistant", content = "Action Discarded.", provider = "system"))
    }

    fun addPendingAttachment(uri: Uri) {
        if (!_pendingAttachments.contains(uri)) {
            _pendingAttachments.add(uri)
        }
    }

    fun removePendingAttachment(uri: Uri) {
        _pendingAttachments.remove(uri)
        _attachmentSegmentNotes.remove(uri)
    }

    fun replacePendingAttachment(old: Uri, new: Uri) {
        val index = _pendingAttachments.indexOf(old)
        if (index != -1) {
            _pendingAttachments[index] = new
        } else {
            _pendingAttachments.add(new)
        }
        _attachmentSegmentNotes.remove(old)?.let { _attachmentSegmentNotes[new] = it }
    }

    fun setSegmentNotes(uri: Uri, notes: Map<Int, String>) {
        if (notes.isEmpty()) _attachmentSegmentNotes.remove(uri) else _attachmentSegmentNotes[uri] = notes
    }

    fun clearPendingAttachments() {
        _pendingAttachments.forEach { _attachmentSegmentNotes.remove(it) }
        _pendingAttachments.clear()
    }

    /**
     * Routes a generation/edit request to the right backend regardless of which
     * "model" is currently selected in the drawer — the user shouldn't have to
     * manually switch to Imagen/Veo/the image-edit model just because their
     * message happens to ask for one. Heuristic, not ML: keyword-based, same
     * spirit as the rest of this app's capability detection (model-id substring
     * matching etc.) — it can misfire on ambiguous phrasing, but errs toward
     * respecting an explicit model choice over guessing. Returns null when no
     * override applies (use whatever's already selected).
     */
    private val EDIT_VERBS = listOf("edit", "change the background", "change background", "replace the background", "replace background", "remove the", "turn it into", "turn this into", "turn the", "make it look like", "make the background")
    private val IMAGE_GEN_PHRASES = listOf("generate an image", "generate a picture", "generate a photo", "create an image", "create a picture", "draw me", "draw a picture", "make me an image", "make an image of")
    private val VIDEO_GEN_PHRASES = listOf("generate a video", "create a video", "make a video of", "make me a video")

    private fun detectAutoRoutedProvider(text: String, attachmentUris: List<Uri>, attachmentType: String): String? {
        val explicitlySelected = setOf("imagen-3.0-generate-002", "veo-2.0-generate-001", "gemini-2.5-flash-image", "byok-video-edit")
        if (_selectedProvider.value in explicitlySelected) return null // respect an explicit manual choice

        val lower = text.lowercase()
        val hasImageAttachment = attachmentUris.isNotEmpty() && (attachmentType == "image" ||
            attachmentUris.any { context.contentResolver.getType(it)?.startsWith("image") == true })
        val hasVideoAttachment = attachmentUris.isNotEmpty() && (attachmentType == "video" ||
            attachmentUris.any { context.contentResolver.getType(it)?.startsWith("video") == true })

        return when {
            hasImageAttachment && EDIT_VERBS.any { lower.contains(it) } -> "gemini-2.5-flash-image"
            hasVideoAttachment && EDIT_VERBS.any { lower.contains(it) } -> "byok-video-edit"
            attachmentUris.isEmpty() && IMAGE_GEN_PHRASES.any { lower.contains(it) } -> "imagen-3.0-generate-002"
            attachmentUris.isEmpty() && VIDEO_GEN_PHRASES.any { lower.contains(it) } -> "veo-2.0-generate-001"
            else -> null
        }
    }

    fun sendMessage(rawText: String, overrideAttachments: List<Uri>? = null, attachmentType: String = "text", trendTitle: String? = null) {
        val attachmentUris = overrideAttachments ?: _pendingAttachments.toList()
        val autoRoutedProvider = detectAutoRoutedProvider(rawText, attachmentUris, attachmentType)

        // Fold any per-segment instructions (from the attachment editor's "Split into 6"
        // tool) into the outgoing text so the AI gets segment-by-segment guidance
        // instead of only a single whole-video prompt.
        val segmentNotesBlock = attachmentUris.mapNotNull { uri ->
            _attachmentSegmentNotes[uri]?.takeIf { it.isNotEmpty() }?.entries
                ?.sortedBy { it.key }
                ?.joinToString("\n") { (segment, note) -> "Segment ${segment + 1}: $note" }
        }.joinToString("\n\n")
        val text = if (segmentNotesBlock.isNotBlank()) {
            "$rawText\n\n[Segment-specific instructions]\n$segmentNotesBlock"
        } else rawText

        // ⚠️ SCENE MODE VALIDATION
        if (_isSceneMode.value && text.isBlank()) {
            viewModelScope.launch { _errorEvents.emit("Video generation requires a descriptive prompt.") }
            return
        }

        if (text.isBlank() && attachmentUris.isEmpty()) return

        val isVoiceRequest = attachmentType == "audio"

        val activeTrend = if (trendTitle?.startsWith("Tactical Map:") == true || trendTitle?.startsWith("MAP_INTEL:") == true) {
             val rawLoc = trendTitle.replace("Tactical Map:", "").replace("MAP_INTEL:", "").trim()
             if (rawLoc.contains(",")) "MAP_INTEL: COORDINATES" else "MAP_INTEL: $rawLoc"
        } else {
             trendTitle ?: _currentTrendTitle.value
        }

        val isTrend = activeTrend != null

        var enhancedText = text
        val polygon = tacticalRepository.tacticalPolygon.value
        if (polygon != null && activeTrend != null && activeTrend.startsWith("MAP_INTEL:")) {
            enhancedText = "[TACTICAL_PERIMETER: $polygon]\n$text"
        }

        if (_isSocialChat.value && _activeSocialContact.value != null) {
            viewModelScope.launch {
                val contact = _activeSocialContact.value!!
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                val result = infoRepository.performSocialAction(
                    deviceId = deviceId,
                    type = "Direct Message",
                    platform = contact.platform,
                    content = text,
                    targetId = contact.id
                )
                if (result is Resource.Success) {
                    _messages.add(ChatMessage(role = "user", content = text, provider = "you"))
                } else {
                    _errorEvents.emit("Failed to send message: ${(result as Resource.Error).message}")
                }
            }
            clearPendingAttachments()
            return
        }

        val userMessage = ChatMessage(
            role = "user", 
            content = text, 
            type = if (attachmentUris.isNotEmpty()) attachmentType else "text",
            attachmentPaths = attachmentUris.map { it.toString() },
            provider = _selectedProvider.value,
            isTrend = isTrend,
            trendTitle = activeTrend
        )
        
        _messages.add(userMessage)

        viewModelScope.launch {
            preferenceManager.setLastInteractionTime(System.currentTimeMillis())
            val uid = repository.currentUserId
            val entity = com.example.mistreal_mini.data.local.entity.ChatEntity.fromChatMessage(uid, userMessage)
            repository.saveEntity(entity)
        }
        
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val imageUris = if (attachmentType == "image" || attachmentUris.isNotEmpty()) attachmentUris else null
        val audioUri = if (attachmentType == "audio" && attachmentUris.isNotEmpty()) attachmentUris[0] else null

        if (autoRoutedProvider != null) {
            val label = when (autoRoutedProvider) {
                "gemini-2.5-flash-image" -> "image editing"
                "byok-video-edit" -> "video editing"
                "imagen-3.0-generate-002" -> "image generation"
                "veo-2.0-generate-001" -> "video generation"
                else -> autoRoutedProvider
            }
            viewModelScope.launch { _errorEvents.emit("Routed to $label for this request.") }
        }

        performChatRequest(text, imageUris, audioUri, deviceId, isVoiceRequest, activeTrend, autoRoutedProvider)
        clearPendingAttachments()
    }

    fun sendAttachments(uris: List<Uri>, type: String = "image") {
        sendMessage("", uris, type)
    }

    fun syncSocials() {
        _isLoading.value = true
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            when (val result = syncSocialsUseCase(deviceId)) {
                is Resource.Success<com.example.mistreal_mini.data.model.SocialSyncResponse> -> {
                    if (result.data?.summary == "CONNECTION_REQUIRED") {
                        _messages.add(ChatMessage(role = "assistant", content = "I don't have access to your social accounts yet. Please go to Settings and connect your profiles so I can sync your data.", provider = "system"))
                    } else {
                        _messages.add(ChatMessage(role = "assistant", content = "Sync Complete: ${result.data?.summary}", provider = "system"))
                    }
                }
                is Resource.Error<com.example.mistreal_mini.data.model.SocialSyncResponse> -> {
                    if (result.message?.contains("CONNECTION_REQUIRED", ignoreCase = true) == true) {
                        _messages.add(ChatMessage(role = "assistant", content = "It looks like your social accounts aren't connected. Head over to Settings to link them.", provider = "system"))
                    } else {
                        _messages.add(ChatMessage(role = "assistant", content = "Error syncing: ${result.message}", provider = "error"))
                        _errorEvents.emit("Sync failed: ${result.message}")
                    }
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun performChatRequest(prompt: String, imageUris: List<Uri>?, audioUri: Uri?, deviceId: String?, isVoiceRequest: Boolean = false, trendTitle: String? = null, providerOverride: String? = null) {
        _isLoading.value = true
        viewModelScope.launch {
            val history = _messages.takeLast(10).toList()
            val activePersona = if (_currentPersona.value == "None") "" else _currentPersona.value
            
            val systemPromptOverlay = if (_aiCustomName.value.isNotBlank()) {
                "IDENTITY_OVERRIDE: Your designation is '${_aiCustomName.value}'. Always identify yourself by this name if asked."
            } else ""

            val result = sendMessageUseCase(
                context = context,
                prompt = if (systemPromptOverlay.isNotBlank()) "$systemPromptOverlay\n\n$prompt" else prompt,
                persona = activePersona,
                history = history,
                provider = providerOverride ?: _selectedProvider.value,
                deviceId = deviceId,
                imageUris = imageUris,
                audioUri = audioUri,
                isSceneMode = _isSceneMode.value
            )
            
            when (result) {
                is Resource.Success -> {
                    result.data?.let { response ->
                        val content = response.content
                        
                        val blueprintRegex = Regex("\\[AI_BLUEPRINT: (.*?)\\]")
                        blueprintRegex.find(content)?.let { match ->
                            val geoJson = match.groupValues[1]
                            tacticalRepository.addAiBlueprint(geoJson)
                        }
                        
                        val markerRegex = Regex("\\[AI_MARKER: (.*?), (.*?), (.*?)\\]")
                        markerRegex.findAll(content).forEach { match ->
                            try {
                                val lat = match.groupValues[1].toDouble()
                                val lon = match.groupValues[2].toDouble()
                                val label = match.groupValues[3]
                                tacticalRepository.addPin(lat, lon, "AI: $label")
                            } catch (e: Exception) {}
                        }

                        val feelingsMatch = feelingsRegex.find(content)
                        val trueFeelings = feelingsMatch?.groupValues?.get(1)

                        val mood = moodRegex.find(content)?.groupValues?.get(1)?.lowercase()

                        val cleanContent = content
                            .replace(blueprintRegex, "")
                            .replace(markerRegex, "")
                            .replace(feelingsRegex, "")
                            .replace(moodRegex, "")
                            .replace(socialAutosendRegex, "")
                            .trim()

                        val assistantMsg = ChatMessage(
                            role = "assistant",
                            content = cleanContent,
                            provider = response.provider,
                            isTrend = trendTitle != null,
                            trendTitle = trendTitle,
                            trueFeelings = trueFeelings,
                            mood = mood
                        )
                        repository.saveMessage(assistantMsg)

                        // AI-generated media comes back as a separate message (not an
                        // attachment on the text reply) so it renders through the same
                        // ChatBubble image/video paths as a user-sent attachment would.
                        response.generatedImageBase64?.let { base64 ->
                            val uri = com.example.mistreal_mini.util.MediaEditorUtil.saveBase64Image(
                                context, base64, response.generatedImageMimeType
                            )
                            if (uri != null) {
                                repository.saveMessage(
                                    ChatMessage(
                                        role = "assistant",
                                        content = "",
                                        type = "image",
                                        attachmentPaths = listOf(uri.toString()),
                                        provider = response.provider,
                                        isTrend = trendTitle != null,
                                        trendTitle = trendTitle
                                    )
                                )
                            } else {
                                _errorEvents.emit("AI generated an image but it couldn't be saved.")
                            }
                        }
                        // A custom image-gen provider may return a URL instead of base64
                        // (see aiService.ts) — render it the same way video already does.
                        response.generatedImageUrl?.let { imageUrl ->
                            repository.saveMessage(
                                ChatMessage(
                                    role = "assistant",
                                    content = "",
                                    type = "image",
                                    attachmentPaths = listOf(imageUrl),
                                    provider = response.provider,
                                    isTrend = trendTitle != null,
                                    trendTitle = trendTitle
                                )
                            )
                        }
                        response.generatedVideoUrl?.let { videoUrl ->
                            repository.saveMessage(
                                ChatMessage(
                                    role = "assistant",
                                    content = "",
                                    type = "video",
                                    attachmentPaths = listOf(videoUrl),
                                    provider = response.provider,
                                    isTrend = trendTitle != null,
                                    trendTitle = trendTitle
                                )
                            )
                        }

                        // WhatsApp-style voice notes: a spoken reply (either a single
                        // voice-note send, or a turn in hands-free Conversation Mode)
                        // is rendered to a file and shown as a replayable bubble instead
                        // of only ever being spoken once through the speaker.
                        if (_isTtsEnabled.value && (_isHandsFreeActive.value || isVoiceRequest)) {
                            val speechContent = TextSanitizer.sanitizeForTts(cleanContent)
                            val outputFile = java.io.File(context.cacheDir, "ai_voice_note_${System.currentTimeMillis()}.wav")
                            val synthesized = voiceManager.synthesizeToFile(speechContent, outputFile)
                            if (synthesized) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", outputFile
                                ).toString()
                                if (_voiceNoteAutoplay.value) {
                                    _pendingVoiceNoteAutoplayUri.value = uri
                                }
                                repository.saveMessage(
                                    ChatMessage(
                                        role = "assistant",
                                        content = "",
                                        type = "audio",
                                        attachmentPaths = listOf(uri),
                                        provider = response.provider,
                                        isTrend = trendTitle != null,
                                        trendTitle = trendTitle
                                    )
                                )
                                // Hands-free mode only resumes listening after the note is
                                // actually heard — handled by onVoiceNotePlaybackFinished(),
                                // called from the bubble once playback completes. If autoplay
                                // is off, the loop simply pauses until the user taps play.
                            } else if (_isHandsFreeActive.value) {
                                // Fallback: speak it directly rather than leave the loop stuck.
                                voiceManager.speak(speechContent) {
                                    viewModelScope.launch { startListeningLoop() }
                                }
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    // The main chat/minichat view renders from pagedMessages (Room-backed),
                    // not the in-memory _messages list — that list is only ever shown in
                    // social-DM-chat mode. Writing only to _messages here meant a failed AI
                    // request fired the error snackbar but the bubble itself never appeared
                    // anywhere in the regular chat transcript.
                    val errorMsg = ChatMessage(
                        role = "assistant",
                        content = "Mission Delayed: ${result.message}",
                        provider = "system",
                        isTrend = trendTitle != null,
                        trendTitle = trendTitle
                    )
                    if (_isSocialChat.value) {
                        _messages.add(errorMsg)
                    } else {
                        repository.saveMessage(errorMsg)
                    }
                    _errorEvents.emit("Chat error: ${result.message}")
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private suspend fun startListeningLoop() {
        // Defense in depth against the TTS-stop race: even if a stale onComplete
        // callback slips through, never resume the mic once the user has stopped.
        if (!_isHandsFreeActive.value) return
        _isListening.value = true
        val intent = Intent(context, com.example.mistreal_mini.service.VoiceService::class.java).apply {
            action = com.example.mistreal_mini.service.VoiceService.ACTION_RESUME_LISTENING
        }
        context.startForegroundService(intent)
    }

    private fun onHandsFreeTranscript(transcript: String) {
        _isListening.value = false
        if (_isHandsFreeActive.value) {
            sendMessage(transcript)
        }
    }


    fun onDistressDetected() {
        if (_guardianEnabled.value) {
            startSosAudioCapture()
            viewModelScope.launch {
                voiceManager.speak("Detecting possible distress. Sending your location to emergency contacts and posting an alert to your connected social accounts.")
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                // Also broadcasts publicly now, by explicit choice — accepted tradeoff
                // is a false-positive audio trigger can post a public SOS.
                handleDistressUseCase(deviceId, broadcastToSocials = true)
                stopSosAudioCapture()
            }
        }
    }

    private val _isSendingSos = mutableStateOf(false)
    val isSendingSos: State<Boolean> = _isSendingSos

    /** Manual SOS button in the drawer — always broadcasts (user already confirmed via dialog). */
    fun triggerManualSos() {
        if (_isSendingSos.value) return
        _isSendingSos.value = true
        startSosAudioCapture()
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = handleDistressUseCase(deviceId, distressSignature = "Manual SOS triggered", broadcastToSocials = true)
            _isSendingSos.value = false
            stopSosAudioCapture()
            when (result) {
                is Resource.Success -> {
                    val data = result.data
                    val platforms = data?.broadcastPlatforms ?: emptyList()
                    val summary = buildString {
                        append("SOS sent — ${data?.emailsSent ?: 0}/${data?.emailContactsTotal ?: 0} contacts emailed")
                        if (platforms.isNotEmpty()) append(", posted to ${platforms.joinToString(", ")}")
                        val failures = data?.broadcastFailures ?: emptyList()
                        if (failures.isNotEmpty()) append(" (failed: ${failures.joinToString(", ")})")
                    }
                    _errorEvents.emit(summary)
                }
                is Resource.Error -> _errorEvents.emit("SOS failed: ${result.message}")
                else -> {}
            }
        }
    }

    // --- Compose & send email (drawer "EMAIL" category) — send-only, no inbox sync ---

    private val _emailContacts = mutableStateListOf<com.example.mistreal_mini.data.api.EmailContactSummary>()
    val emailContacts: List<com.example.mistreal_mini.data.api.EmailContactSummary> = _emailContacts

    private val _activeEmailThread = mutableStateListOf<com.example.mistreal_mini.data.api.EmailMessage>()
    val activeEmailThread: List<com.example.mistreal_mini.data.api.EmailMessage> = _activeEmailThread

    private val _isSendingEmail = mutableStateOf(false)
    val isSendingEmail: State<Boolean> = _isSendingEmail

    fun fetchEmailContacts() {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = infoRepository.getEmailContacts(deviceId)
            if (result is Resource.Success) {
                _emailContacts.clear()
                _emailContacts.addAll(result.data ?: emptyList())
            }
        }
    }

    fun openEmailThread(toEmail: String) {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = infoRepository.getEmailHistory(deviceId, toEmail)
            _activeEmailThread.clear()
            if (result is Resource.Success) {
                _activeEmailThread.addAll(result.data ?: emptyList())
            }
        }
    }

    fun clearActiveEmailThread() {
        _activeEmailThread.clear()
    }

    fun sendEmail(toEmail: String, toName: String?, subject: String, body: String, onResult: (Boolean) -> Unit) {
        if (toEmail.isBlank() || subject.isBlank() || body.isBlank()) {
            viewModelScope.launch { _errorEvents.emit("Address, subject and message are all required.") }
            onResult(false)
            return
        }
        _isSendingEmail.value = true
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = infoRepository.sendEmail(deviceId, toEmail, toName, subject, body)
            _isSendingEmail.value = false
            when (result) {
                is Resource.Success -> {
                    _errorEvents.emit("Email sent to $toEmail")
                    fetchEmailContacts()
                    openEmailThread(toEmail)
                    onResult(true)
                }
                is Resource.Error -> {
                    _errorEvents.emit("Email failed: ${result.message}")
                    onResult(false)
                }
                else -> onResult(false)
            }
        }
    }

    private val _isSendingToContact = mutableStateOf(false)
    val isSendingToContact: State<Boolean> = _isSendingToContact

    /**
     * Delivers an image message (e.g. an AI-edited picture) as a real DM on a
     * connected platform — e.g. WhatsApp/Facebook — via Zernio. The image only
     * exists locally as a Uri, so it's base64-encoded and handed to the backend,
     * which briefly hosts it at a public URL for Zernio to fetch (see
     * mediaStore.ts) since DM APIs take a media URL, not inline bytes.
     */
    fun sendImageToContact(imageUri: Uri, platform: String, targetId: String, caption: String, onResult: (Boolean) -> Unit) {
        _isSendingToContact.value = true
        viewModelScope.launch {
            val base64 = FileUtil.uriToBase64(context, imageUri)
            if (base64 == null) {
                _isSendingToContact.value = false
                _errorEvents.emit("Couldn't read that image.")
                onResult(false)
                return@launch
            }
            val mimeType = FileUtil.getMimeType(context, imageUri) ?: "image/jpeg"
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = infoRepository.performSocialAction(
                deviceId = deviceId,
                type = "Direct Message",
                platform = platform,
                content = caption,
                targetId = targetId,
                mediaBase64 = base64,
                mediaMimeType = mimeType
            )
            _isSendingToContact.value = false
            when (result) {
                is Resource.Success -> {
                    _errorEvents.emit("Sent to $targetId on $platform")
                    onResult(true)
                }
                is Resource.Error -> {
                    _errorEvents.emit("Send failed: ${result.message}")
                    onResult(false)
                }
                else -> onResult(false)
            }
        }
    }

    private val _isEditingVideo = mutableStateOf(false)
    val isEditingVideo: State<Boolean> = _isEditingVideo

    /**
     * AI video editing (background/subject change on an EXISTING recorded video,
     * not generation) — only works if the user has configured their own video
     * provider (Runway/custom) in Settings, since Gemini/Veo don't offer this.
     * Bypasses the generic sendMessage()/performChatRequest() attachment-type
     * routing deliberately — this is a narrow, isolated media operation, not a
     * normal chat turn with mood/feelings/autosend tag parsing.
     */
    fun editVideoWithAi(videoUri: Uri, instruction: String, onResult: (Boolean) -> Unit) {
        if (instruction.isBlank()) {
            viewModelScope.launch { _errorEvents.emit("Describe what to change first.") }
            onResult(false)
            return
        }
        _isEditingVideo.value = true
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = sendMessageUseCase(
                context = context,
                prompt = instruction,
                persona = "",
                history = emptyList(),
                provider = "byok-video-edit",
                deviceId = deviceId,
                videoUri = videoUri
            )
            _isEditingVideo.value = false
            when (result) {
                is Resource.Success -> {
                    val videoUrl = result.data?.generatedVideoUrl
                    if (videoUrl != null) {
                        repository.saveMessage(
                            ChatMessage(
                                role = "assistant",
                                content = "",
                                type = "video",
                                attachmentPaths = listOf(videoUrl),
                                provider = "byok-video-edit",
                                isTrend = _currentTrendTitle.value != null,
                                trendTitle = _currentTrendTitle.value
                            )
                        )
                        onResult(true)
                    } else {
                        _errorEvents.emit("Video editing provider returned no video.")
                        onResult(false)
                    }
                }
                is Resource.Error -> {
                    _errorEvents.emit("Video edit failed: ${result.message}")
                    onResult(false)
                }
                else -> onResult(false)
            }
        }
    }

    private val _isEditingImage = mutableStateOf(false)
    val isEditingImage: State<Boolean> = _isEditingImage

    /**
     * AI image editing (background/subject change on an EXISTING picture) via
     * our own Gemini backend — unlike video editing, this doesn't need a BYOK
     * provider, it's always available. Provider id must match the backend's
     * IMAGE_EDIT_MODEL_ID (aiService.ts).
     */
    fun editImageWithAi(imageUri: Uri, instruction: String, onResult: (Boolean) -> Unit) {
        if (instruction.isBlank()) {
            viewModelScope.launch { _errorEvents.emit("Describe what to change first.") }
            onResult(false)
            return
        }
        _isEditingImage.value = true
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = sendMessageUseCase(
                context = context,
                prompt = instruction,
                persona = "",
                history = emptyList(),
                provider = "gemini-2.5-flash-image",
                deviceId = deviceId,
                imageUris = listOf(imageUri)
            )
            _isEditingImage.value = false
            when (result) {
                is Resource.Success -> {
                    val base64 = result.data?.generatedImageBase64
                    if (base64 != null) {
                        val uri = com.example.mistreal_mini.util.MediaEditorUtil.saveBase64Image(
                            context, base64, result.data?.generatedImageMimeType
                        )
                        if (uri != null) {
                            repository.saveMessage(
                                ChatMessage(
                                    role = "assistant",
                                    content = "",
                                    type = "image",
                                    attachmentPaths = listOf(uri.toString()),
                                    provider = "gemini-2.5-flash-image",
                                    isTrend = _currentTrendTitle.value != null,
                                    trendTitle = _currentTrendTitle.value
                                )
                            )
                            onResult(true)
                        } else {
                            _errorEvents.emit("Edited image couldn't be saved.")
                            onResult(false)
                        }
                    } else {
                        _errorEvents.emit("Image editing returned no image.")
                        onResult(false)
                    }
                }
                is Resource.Error -> {
                    _errorEvents.emit("Image edit failed: ${result.message}")
                    onResult(false)
                }
                else -> onResult(false)
            }
        }
    }

    fun saveSettings(name: String, persona: String, delayMinutes: Int, guardianEnabled: Boolean? = null, contacts: List<EmergencyContact>? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            
            val result = infoRepository.updateUserSettings(
                deviceId = deviceId, 
                userName = name, 
                aiPersona = persona, 
                aiAudience = null,
                autoReplyDelay = delayMinutes,
                guardianEnabled = guardianEnabled,
                emergencyContacts = contacts
            )
            
            if (result is Resource.Success) {
                preferenceManager.setUserName(name)
                preferenceManager.setAiPersona(persona)
                preferenceManager.setAutoReplyDelay(delayMinutes)
                guardianEnabled?.let { preferenceManager.setGuardianEnabled(it) }
            } else {
                _errorEvents.emit("Failed to secure changes: ${(result as Resource.Error).message}")
            }
            _isLoading.value = false
        }
    }

    fun secureAsScribe(sourcePost: com.example.mistreal_mini.data.model.SocialPost?, analysis: String) {
        viewModelScope.launch {
            scribeRepository.saveNote(
                sourcePostId = sourcePost?.id,
                platform = sourcePost?.platform,
                author = sourcePost?.author,
                content = sourcePost?.content,
                analysis = analysis
            )
        }
    }

    fun saveAsNote(content: String) {
        viewModelScope.launch {
            val noteMsg = ChatMessage(
                role = "assistant",
                content = content,
                type = "scribe",
                provider = "system"
            )
            repository.saveMessage(noteMsg)
            
            // Also save to Scribe Repository for the persistent archive
            scribeRepository.saveNote(
                sourcePostId = "intel_${System.currentTimeMillis()}",
                content = content,
                analysis = "Manual archive from intelligence hub."
            )
        }
    }

    fun startHandsFreeLoop(text: String) {
        toggleHandsFree(true)
        val intent = Intent(context, com.example.mistreal_mini.service.VoiceService::class.java).apply {
            action = com.example.mistreal_mini.service.VoiceService.ACTION_START_GUARDIAN
        }
        context.startForegroundService(intent)
        
        val cleanText = TextSanitizer.sanitizeForTts(text)
        voiceManager.speak(cleanText) {
            viewModelScope.launch { startListeningLoop() }
        }
    }

    fun startRadioMode(text: String) {
        toggleHandsFree(false)
        val index = _messages.indexOfFirst { it.content == text }
        if (index != -1) {
            val feed = _messages.subList(index, _messages.size).map { TextSanitizer.sanitizeForTts(it.content) }
            val intent = Intent(context, com.example.mistreal_mini.service.VoiceService::class.java).apply {
                action = com.example.mistreal_mini.service.VoiceService.ACTION_START_RADIO
                putStringArrayListExtra(com.example.mistreal_mini.service.VoiceService.EXTRA_FEED, ArrayList(feed))
            }
            context.startForegroundService(intent)
        }
    }

    fun readNextInRadio(index: Int) {
        if (index < _messages.size) {
            val msg = _messages[index]
            val cleanText = TextSanitizer.sanitizeForTts(msg.content)
            voiceManager.speak(cleanText) {
                viewModelScope.launch { readNextInRadio(index + 1) }
            }
        }
    }

    fun refreshSocialContacts() {
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

            // 🔄 Step 1: Sync with Zernio Cloud First
            syncSocialsUseCase(deviceId)

            // 📥 Step 2: Refresh local metadata, then pull contacts only for platforms
            // the user actually has connected (previously a hardcoded 6-platform list
            // that silently excluded TikTok and anything else not in it).
            val platformsResult = infoRepository.getAvailablePlatforms(deviceId)
            if (platformsResult is Resource.Success) {
                _availablePlatforms.clear()
                platformsResult.data?.let { _availablePlatforms.addAll(it) }
            }
            fetchUnread()
            _availablePlatforms.filter { it.isConnected }.forEach {
                fetchContacts(it.id)
            }
        }
    }

    fun startScribe() {
        _isScribing.value = true
        scribeManager.startScribing()
    }

    fun stopScribe() {
        _isScribing.value = false
        scribeManager.stopScribing()
    }

    fun saveAsNote(message: ChatMessage) {
        saveAsNote(message.content)
    }

    fun readAloud(text: String) {
        if (_isTtsEnabled.value) {
            val cleanText = TextSanitizer.sanitizeForTts(text)
            voiceManager.speak(cleanText)
        }
    }
}
