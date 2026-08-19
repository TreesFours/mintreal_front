package com.example.mistreal_mini.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AiModelResponse
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatRequest
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.domain.usecase.SendMessageUseCase
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import com.example.mistreal_mini.domain.usecase.HandleDistressUseCase
import com.example.mistreal_mini.util.FileUtil
import com.example.mistreal_mini.util.VoiceManager
import com.example.mistreal_mini.util.TextSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: AiRepository,
    private val infoRepository: InfoRepository,
    private val preferenceManager: PreferenceManager,
    private val voiceManager: VoiceManager,
    private val sendMessageUseCase: SendMessageUseCase,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val handleDistressUseCase: HandleDistressUseCase,
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

    private val _currentPersona = mutableStateOf(savedStateHandle.get<String>("currentPersona") ?: "Shadow")
    val currentPersona: State<String> = _currentPersona

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

    private val _socialContacts = mutableStateOf<List<com.example.mistreal_mini.data.api.SocialContact>>(emptyList())
    val socialContacts: State<List<com.example.mistreal_mini.data.api.SocialContact>> = _socialContacts

    private val _unreadMessages = mutableStateOf<List<com.example.mistreal_mini.data.api.UnreadItem>>(emptyList())
    val unreadMessages: State<List<com.example.mistreal_mini.data.api.UnreadItem>> = _unreadMessages

    private val _availablePlatforms = mutableStateListOf<com.example.mistreal_mini.data.api.SocialPlatformResponse>()
    val availablePlatforms: List<com.example.mistreal_mini.data.api.SocialPlatformResponse> = _availablePlatforms

    private val _bearing = mutableStateOf(0f)
    val bearing: State<Float> = _bearing

    private val _orientation = mutableStateOf("N")
    val orientation: State<String> = _orientation

    private val _isSocialChat = mutableStateOf(false)
    val isSocialChat: State<Boolean> = _isSocialChat

    private val _activeSocialContact = mutableStateOf<com.example.mistreal_mini.data.api.SocialContact?>(null)
    val activeSocialContact: State<com.example.mistreal_mini.data.api.SocialContact?> = _activeSocialContact

    private val _currentTrendTitle = mutableStateOf<String?>(null)
    val currentTrendTitle: State<String?> = _currentTrendTitle

    private val _pendingAttachments = mutableStateListOf<Uri>()
    val pendingAttachments: List<Uri> = _pendingAttachments

    private val _uniqueTrends = mutableStateListOf<ChatMessage>()
    val uniqueTrends: List<ChatMessage> = _uniqueTrends

    private var currentOffset = 0
    private val pageSize = 20
    private var isLastPage = false

    init {
        viewModelScope.launch {
            preferenceManager.aiPersona.collect { _currentPersona.value = it }
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
            voiceManager.transcripts.collect { transcript ->
                onHandsFreeTranscript(transcript)
            }
        }

        // Default: Load Main Chat (Global Timeline)
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

    private fun observeMessages() {
        viewModelScope.launch {
            if (_isSocialChat.value) {
                // ... social chat logic
                return@launch
            }

            repository.getAllMessages().collect { allMsgs ->
                val currentTitle = _currentTrendTitle.value
                
                val filtered = if (currentTitle == null) {
                    allMsgs.filter { !it.isTrend }
                } else {
                    allMsgs.filter { it.isTrend && it.trendTitle == currentTitle }
                }
                
                // CRITICAL: Ensure clear and re-add happens atomically in the UI state
                _messages.clear()
                _messages.addAll(filtered)
                
                Timber.d("📬 Chat Update: ${filtered.size} messages synced (Trend: $currentTitle)")
            }
        }
    }

    fun loadTrend(title: String) {
        _currentTrendTitle.value = title
        _messages.clear()
        observeMessages()
    }

    fun exitTrend() {
        _currentTrendTitle.value = null
        _messages.clear()
        observeMessages()
    }

    fun loadMoreMessages() {
        if (isLastPage || _isLoading.value) return
        
        viewModelScope.launch {
            _isLoading.value = true
            val pagedMessages = repository.getPagedMessages(pageSize, currentOffset)
            if (pagedMessages.isEmpty()) {
                isLastPage = true
            } else {
                _messages.addAll(0, pagedMessages)
                currentOffset += pageSize
            }
            _isLoading.value = false
        }
    }

    fun toggleTts(enabled: Boolean) {
        _isTtsEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setTtsEnabled(enabled) }
    }

    fun toggleStt(enabled: Boolean) {
        _isSttEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setSttEnabled(enabled) }
    }

    fun fetchAvailablePlatforms() {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getAvailablePlatforms(deviceId)) {
                is Resource.Success<List<com.example.mistreal_mini.data.api.SocialPlatformResponse>> -> {
                    _availablePlatforms.clear()
                    // Fixed: Show all connected platforms without hardcoded tier filters
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
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = repository.getAvailableModels(deviceId)) {
                is Resource.Success -> {
                    val providers = result.data.orEmpty()
                    _availableProviders.clear()
                    
                    // Always add Dynamic (Best Fit) - available for all tiers
                    _availableProviders.add(AiModelResponse("dynamic", "Dynamic (Best Fit)", "mistreal", false, "Free"))
                    
                    // Filter providers based on tier
                    val filteredProviders = if (_isPro.value) {
                        providers // Premium: show all
                    } else {
                        // Free: only show models marked as free tier
                        providers.filter { it.price == "Free" || it.price.lowercase() == "free" }
                    }
                    _availableProviders.addAll(filteredProviders)

                    if (filteredProviders.isEmpty() && !_isPro.value) {
                        _errorEvents.emit("Free tier has limited AI access. Upgrade to Premium for all models.")
                    }

                    if (filteredProviders.none { it.id == _selectedProvider.value } && _selectedProvider.value != "dynamic") {
                        _selectedProvider.value = "dynamic"
                    }
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
            val contact = _socialContacts.value.find { it.name == partner && it.platform == platform }
            _activeSocialContact.value = contact
            _currentChatPartnerStatus.value = if (contact?.unreadCount ?: 0 > 0) "New Message" else "Active"
            fetchSocialHistory(partner, platform)
        }
    }

    private fun fetchSocialHistory(partner: String, platform: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val contact = _socialContacts.value.find { it.name == partner && it.platform == platform }
            
            if (contact != null) {
                when (val result = infoRepository.getSocialHistory(deviceId, platform, contact.id)) {
                    is Resource.Success<List<com.example.mistreal_mini.data.api.SocialHistoryMessage>> -> {
                        _messages.clear()
                        result.data?.forEach { msg ->
                            _messages.add(
                                ChatMessage(
                                    role = if (msg.direction == "incoming") "user" else "assistant",
                                    content = msg.text ?: "",
                                    type = msg.attachments?.firstOrNull()?.type ?: "text",
                                    attachmentUrl = msg.attachments?.firstOrNull()?.url,
                                    provider = platform,
                                    socialMetadata = com.example.mistreal_mini.data.model.SocialMetadata(
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
                    is Resource.Error<List<com.example.mistreal_mini.data.api.SocialHistoryMessage>> -> {
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

            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
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
                        com.example.mistreal_mini.data.model.SocialMetadata(
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
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getContacts(deviceId, platform)) {
                is Resource.Success -> _socialContacts.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    fun searchContacts(platform: String, query: String) {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            // Query backend for discovery search
            when (val result = infoRepository.searchContacts(deviceId, platform, query)) {
                is Resource.Success<List<com.example.mistreal_mini.data.api.SocialContact>> -> _socialContacts.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    fun fetchUnread() {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getUnreadMessages(deviceId)) {
                is Resource.Success -> _unreadMessages.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    fun setProvider(provider: String) {
        _selectedProvider.value = provider
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
            _messages.clear()
            _uniqueTrends.clear()
            currentOffset = 0
            isLastPage = false
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
            // Delete all non-trend messages for current user
            val uid = repository.currentUserId
            repository.deleteNonTrendMessages(uid)
            // No need to clear _messages if we're leaving the screen, 
            // but we can for safety.
        }
    }

    fun clearSessionMessages() {
        _messages.clear()
        currentOffset = 0
        isLastPage = false
        // No DB deletion, just UI session clear
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
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
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
        if (text.isBlank() && attachmentUris.isEmpty()) return
        
        val isVoiceRequest = attachmentType == "audio"
        
        // 📍 Standardize Map Intel Trend Titles
        val activeTrend = if (trendTitle?.startsWith("Tactical Map:") == true || trendTitle?.startsWith("MAP_INTEL:") == true) {
             val rawLoc = trendTitle.replace("Tactical Map:", "").replace("MAP_INTEL:", "").trim()
             if (rawLoc.contains(",")) "MAP_INTEL: COORDINATES" else "MAP_INTEL: $rawLoc"
        } else {
             trendTitle ?: _currentTrendTitle.value
        }

        val isTrend = activeTrend != null
        
        if (_isSocialChat.value && _activeSocialContact.value != null) {
            viewModelScope.launch {
                val contact = _activeSocialContact.value!!
                val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
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
        
        // 🚀 Fix: Immediate UI Echo - Add to list BEFORE network call
        _messages.add(userMessage)

        viewModelScope.launch {
            val uid = repository.currentUserId
            val entity = com.example.mistreal_mini.data.local.entity.ChatEntity.fromChatMessage(uid, userMessage)
            repository.saveEntity(entity)
        }
        
        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
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
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = syncSocialsUseCase(deviceId)) {
                is Resource.Success<com.example.mistreal.data.models.SocialSyncResponse> -> {
                    if (result.data?.summary == "CONNECTION_REQUIRED") {
                        _messages.add(ChatMessage(role = "assistant", content = "I don't have access to your social accounts yet. Please go to Settings and connect your profiles so I can sync your data.", provider = "system"))
                    } else {
                        _messages.add(ChatMessage(role = "assistant", content = "Sync Complete: ${result.data?.summary}", provider = "system"))
                    }
                }
                is Resource.Error<com.example.mistreal.data.models.SocialSyncResponse> -> {
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
            // Include recent context for AI
            val history = _messages.takeLast(10).toList()
            
            // 🎭 DYNAMIC PERSONA RESOLUTION
            val activePersona = if (_currentPersona.value == "None") "" else _currentPersona.value
            
            val result = sendMessageUseCase(
                context = context,
                prompt = prompt,
                persona = activePersona,
                history = history,
                provider = _selectedProvider.value,
                deviceId = deviceId,
                imageUris = imageUris,
                audioUri = audioUri
            )
            
            when (result) {
                is Resource.Success -> {
                    result.data?.let { response ->
                        val assistantMsg = ChatMessage(
                            role = "assistant", 
                            content = response.content, 
                            provider = response.provider,
                            isTrend = trendTitle != null,
                            trendTitle = trendTitle
                        )
                        repository.saveMessage(assistantMsg)
                        // _messages will be updated via observation
                        
                        // 🎙️ TTS Logic: Respect strict enable/disable switch
                        if (_isTtsEnabled.value && (_isHandsFreeActive.value || isVoiceRequest)) {
                            val cleanContent = TextSanitizer.sanitizeForTts(response.content)
                            voiceManager.speak(cleanContent) {
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

    fun onVoiceReplyRecorded(uri: Uri?) {
        _isListening.value = false
        if (uri != null) {
            sendMessage("", listOf(uri), "audio")
        } else {
            if (_isHandsFreeActive.value) {
                viewModelScope.launch {
                    voiceManager.speak("I couldn't hear that. Are you still there?") {
                        viewModelScope.launch { startListeningLoop() }
                    }
                }
            }
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

    fun saveSettings(name: String, persona: String, delayMinutes: Int, guardianEnabled: Boolean? = null, contacts: List<com.example.mistreal_mini.data.api.EmergencyContact>? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            
            val result = infoRepository.updateUserSettings(
                deviceId = deviceId, 
                userName = name, 
                aiPersona = persona, 
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

    fun readAloud(text: String) {
        if (_isTtsEnabled.value) {
            val cleanText = TextSanitizer.sanitizeForTts(text)
            voiceManager.speak(cleanText)
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
            // Instant data hydration for common platforms
            listOf("instagram", "linkedin", "twitter", "x", "facebook", "whatsapp").forEach {
                fetchContacts(it)
            }
            // Background sync for Feeds and DMs
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            syncSocialsUseCase(deviceId)
        }
    }
}
