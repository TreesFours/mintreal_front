package com.example.mistreal_mini.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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

    private val _uniqueTrends = mutableStateListOf<ChatMessage>()
    val uniqueTrends: List<ChatMessage> = _uniqueTrends

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

    val pagedMessages: Flow<PagingData<ChatMessage>> = repository.getPagedMessagesFlow()
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
        fetchAvailableModels()
        fetchAvailablePlatforms()
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
        _messages.clear()
        observeMessages()
    }

    fun exitTrend() {
        _isSocialChat.value = false
        _currentTrendTitle.value = null
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

    fun toggleHandsFree(active: Boolean) {
        _isHandsFreeActive.value = active
        if (!active) {
            _isListening.value = false
            voiceManager.stop()
            context.stopService(Intent(context, com.example.mistreal_mini.service.VoiceService::class.java))
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

                // OPTIC INTEL: Vision/Video/Flash
                id.contains("vision") || id.contains("flash") || id.contains("video") || id.contains("kling") -> "OPTIC INTEL"

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
            val result = sendMessageUseCase(
                context = context,
                prompt = "Draft a reply or analysis for $targetName about: $prompt",
                persona = _currentPersona.value,
                history = _messages.takeLast(5),
                provider = _selectedProvider.value,
                deviceId = deviceId
            )

            if (result is Resource.Success) {
                val draftMsg = ChatMessage(
                    role = "assistant", 
                    content = result.data?.content ?: "", 
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
            _isLoading.value = false
        }
    }

    fun fetchContacts(platform: String) {
        if (platform == "ai") return
        viewModelScope.launch {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getContacts(deviceId, platform)) {
                is Resource.Success -> {
                    val rawContacts = result.data ?: emptyList()
                    _socialContacts.value = rawContacts
                    
                    val entities = rawContacts.map { c ->
                        SocialContactEntity(
                            contactId = c.id,
                            platform = c.platform,
                            name = c.name,
                            avatarUrl = c.avatar,
                            lastInteractionTime = System.currentTimeMillis(),
                            isEmergency = false,
                            platformUserId = c.id
                        )
                    }
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
        val modelSupportsVideo = modelId.contains("video") || modelId.contains("kling") || modelId.contains("luma") || modelId.contains("runway")
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

    fun approveSocialAction(draft: ChatMessage) {
        viewModelScope.launch {
            _isLoading.value = true
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            val metadata = draft.socialMetadata
            
            val result = infoRepository.performSocialAction(
                deviceId = deviceId,
                type = metadata?.type ?: "Post",
                platform = metadata?.platform ?: "Twitter",
                content = draft.content,
                targetId = metadata?.targetId ?: "self"
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
    }

    fun clearPendingAttachments() {
        _pendingAttachments.clear()
    }

    fun sendMessage(text: String, overrideAttachments: List<Uri>? = null, attachmentType: String = "text", trendTitle: String? = null) {
        val attachmentUris = overrideAttachments ?: _pendingAttachments.toList()
        
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
        
        performChatRequest(text, imageUris, audioUri, deviceId, isVoiceRequest, activeTrend)
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

    fun performChatRequest(prompt: String, imageUris: List<Uri>?, audioUri: Uri?, deviceId: String?, isVoiceRequest: Boolean = false, trendTitle: String? = null) {
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
                provider = _selectedProvider.value,
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

                        val feelingsRegex = Regex("\\[TRUE_FEELINGS: (.*?)\\]", RegexOption.DOT_MATCHES_ALL)
                        val feelingsMatch = feelingsRegex.find(content)
                        val trueFeelings = feelingsMatch?.groupValues?.get(1)
                        val cleanContent = content
                            .replace(blueprintRegex, "")
                            .replace(markerRegex, "")
                            .replace(feelingsRegex, "")
                            .trim()

                        val assistantMsg = ChatMessage(
                            role = "assistant", 
                            content = cleanContent, 
                            provider = response.provider,
                            isTrend = trendTitle != null,
                            trendTitle = trendTitle,
                            trueFeelings = trueFeelings
                        )
                        repository.saveMessage(assistantMsg)
                        
                        if (_isTtsEnabled.value && (_isHandsFreeActive.value || isVoiceRequest)) {
                            val speechContent = TextSanitizer.sanitizeForTts(cleanContent)
                            voiceManager.speak(speechContent) {
                                if (_isHandsFreeActive.value) {
                                    viewModelScope.launch { startListeningLoop() }
                                }
                            }
                        }
                    }
                }
                is Resource.Error -> {
                    _messages.add(ChatMessage(role = "assistant", content = "Mission Delayed: ${result.message}", provider = "system"))
                    _errorEvents.emit("Chat error: ${result.message}")
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    private suspend fun startListeningLoop() {
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
            viewModelScope.launch {
                voiceManager.speak("Detecting possible distress. Sending your location to emergency contacts.")
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                handleDistressUseCase(deviceId)
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
            fetchAvailablePlatforms()
            fetchUnread()
            listOf("instagram", "linkedin", "twitter", "x", "facebook", "whatsapp").forEach {
                fetchContacts(it)
            }
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            syncSocialsUseCase(deviceId)
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
