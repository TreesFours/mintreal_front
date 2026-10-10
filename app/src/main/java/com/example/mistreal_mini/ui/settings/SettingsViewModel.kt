package com.example.mistreal_mini.ui.settings

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.example.mistreal_mini.data.api.EmergencyContact
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.domain.usecase.UpdateUserSettingsUseCase
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.SocialPlatformResponse
import com.example.mistreal_mini.data.local.dao.SocialContactDao
import com.example.mistreal_mini.data.local.entity.SocialContactEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val infoRepository: InfoRepository,
    private val socialContactDao: SocialContactDao,
    private val bankDao: com.example.mistreal_mini.data.local.dao.BankDao,
    private val marketRepository: com.example.mistreal_mini.data.repository.MarketRepository,
    private val verifiedFaceRepository: com.example.mistreal_mini.data.repository.VerifiedFaceRepository,
    private val updateUserSettingsUseCase: UpdateUserSettingsUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _socialConnectUrl = MutableStateFlow<String?>(null)
    val socialConnectUrl = _socialConnectUrl.asStateFlow()

    private val _availablePlatforms = MutableStateFlow<List<SocialPlatformResponse>>(emptyList())
    val availablePlatforms = _availablePlatforms.asStateFlow()

    private val _isLoadingPlatforms = MutableStateFlow(false)
    val isLoadingPlatforms = _isLoadingPlatforms.asStateFlow()

    private val _isConnectingSocial = MutableStateFlow(false)
    val isConnectingSocial = _isConnectingSocial.asStateFlow()

    private val _connectingPlatform = MutableStateFlow<String?>(null)
    val connectingPlatform = _connectingPlatform.asStateFlow()

    private val _socialConnectionSuccess = MutableSharedFlow<String>()
    val socialConnectionSuccess = _socialConnectionSuccess.asSharedFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _freePlatformLimit = MutableStateFlow(1)
    val freePlatformLimit = _freePlatformLimit.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress = _syncProgress.asStateFlow()

    private val _syncMessage = MutableStateFlow("")
    val syncMessage = _syncMessage.asStateFlow()

    val isPro = preferenceManager.isPro

    private val _guardianEnabled = mutableStateOf(false)
    val guardianEnabled: State<Boolean> = _guardianEnabled

    private val _aiAutoSendEnabled = mutableStateOf(false)
    val aiAutoSendEnabled: State<Boolean> = _aiAutoSendEnabled

    private val _voiceNoteAutoplay = mutableStateOf(true)
    val voiceNoteAutoplay: State<Boolean> = _voiceNoteAutoplay

    private val _communityFeedPlatforms = mutableStateOf<List<String>>(emptyList())
    val communityFeedPlatforms: State<List<String>> = _communityFeedPlatforms

    fun toggleCommunityFeedPlatform(platform: String) {
        val current = _communityFeedPlatforms.value
        val updated = if (platform in current) current - platform else current + platform
        _communityFeedPlatforms.value = updated
        viewModelScope.launch {
            preferenceManager.setCommunityFeedPlatforms(updated)
            infoRepository.setCommunityFeedPreferences(updated)
        }
    }

    private val _byokStatus = MutableStateFlow<com.example.mistreal_mini.data.api.ByokStatusResponse?>(null)
    val byokStatus = _byokStatus.asStateFlow()

    private val _isSavingByok = MutableStateFlow(false)
    val isSavingByok = _isSavingByok.asStateFlow()

    // Backend-synced now (was purely local in-memory before — never even
    // reloaded on screen reopen). Each contact must confirm/decline via the
    // link sent to them before `status` leaves "pending"; see
    // emergencyRoutes.ts.
    private val _emergencyContacts = MutableStateFlow<List<EmergencyContact>>(emptyList())
    val emergencyContacts: StateFlow<List<EmergencyContact>> = _emergencyContacts.asStateFlow()

    private val _isSavingEmergencyContact = MutableStateFlow(false)
    val isSavingEmergencyContact: StateFlow<Boolean> = _isSavingEmergencyContact.asStateFlow()

    val recentSocialContacts = socialContactDao.getRecentContacts()
    val allBankLinks = bankDao.getAllBanks()

    private val _availableVoices = MutableStateFlow<List<Voice>>(emptyList())
    val availableVoices = _availableVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName = _selectedVoiceName.asStateFlow()

    private val _customPersonas = MutableStateFlow<List<String>>(emptyList())
    val customPersonas = _customPersonas.asStateFlow()

    private val _customAudiences = MutableStateFlow<List<String>>(emptyList())
    val customAudiences = _customAudiences.asStateFlow()

    private val _isSupportiveTruthTellerEnabled = mutableStateOf(false)
    val isSupportiveTruthTellerEnabled: State<Boolean> = _isSupportiveTruthTellerEnabled

    private val _isWellnessShieldEnabled = mutableStateOf(false)
    val isWellnessShieldEnabled: State<Boolean> = _isWellnessShieldEnabled

    private val _isProactiveNudgeEnabled = mutableStateOf(false)
    val isProactiveNudgeEnabled: State<Boolean> = _isProactiveNudgeEnabled

    private val _isIntelligenceSparkEnabled = mutableStateOf(false)
    val isIntelligenceSparkEnabled: State<Boolean> = _isIntelligenceSparkEnabled

    private val _isPersistentSceneModeEnabled = mutableStateOf(false)
    val isPersistentSceneModeEnabled: State<Boolean> = _isPersistentSceneModeEnabled

    private val _isDeepAnalysisEnabled = mutableStateOf(false)
    val isDeepAnalysisEnabled: State<Boolean> = _isDeepAnalysisEnabled

    private val _isGodModeEnabled = mutableStateOf(false)
    val isGodModeEnabled: State<Boolean> = _isGodModeEnabled

    private val _godModeTask = mutableStateOf("")
    val godModeTask: State<String> = _godModeTask

    private val _godModeStyle = mutableStateOf("Standard")
    val godModeStyle: State<String> = _godModeStyle

    private val gson = Gson()

    init {
        fetchEmergencyContacts()
        fetchAddonStatus()
        viewModelScope.launch {
            preferenceManager.guardianEnabled.collect { _guardianEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.aiAutoSendEnabled.collect { _aiAutoSendEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.communityFeedPlatforms.collect { _communityFeedPlatforms.value = it }
        }
        viewModelScope.launch {
            preferenceManager.voiceNoteAutoplay.collect { _voiceNoteAutoplay.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isSupportiveTruthTellerEnabled.collect { _isSupportiveTruthTellerEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isWellnessShieldEnabled.collect { _isWellnessShieldEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isProactiveNudgeEnabled.collect { _isProactiveNudgeEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isIntelligenceSparkEnabled.collect { _isIntelligenceSparkEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isPersistentSceneModeEnabled.collect { _isPersistentSceneModeEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isDeepAnalysisEnabled.collect { _isDeepAnalysisEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.isGodModeEnabled.collect { _isGodModeEnabled.value = it }
        }
        viewModelScope.launch {
            preferenceManager.godModeTask.collect { _godModeTask.value = it }
        }
        viewModelScope.launch {
            preferenceManager.godModeStyle.collect { _godModeStyle.value = it }
        }
        viewModelScope.launch {
            preferenceManager.customPersonas.collect { json ->
                val type = object : TypeToken<List<String>>() {}.type
                _customPersonas.value = try {
                    gson.fromJson(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
        viewModelScope.launch {
            preferenceManager.customAudiences.collect { json ->
                val type = object : TypeToken<List<String>>() {}.type
                _customAudiences.value = try {
                    gson.fromJson(json, type) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    // Drives the boot screen's "Loading your settings..." line — true once every
    // await below has settled (success or not), not just once kicked off.
    private val _settingsBootLoaded = mutableStateOf(false)
    val settingsBootLoaded: State<Boolean> = _settingsBootLoaded

    // Network-backed loads deferred out of init{} — see ChatViewModel.onAuthenticated()
    // for why: these used to fire before the splash/auth/biometric gate resolved.
    fun onAuthenticated() {
        viewModelScope.launch {
            kotlinx.coroutines.coroutineScope {
                launch { fetchByokStatusAwait() }
                launch { fetchByokVideoStatusAwait() }
                launch { fetchMediaProviderConfigsAwait("image_gen") }
                launch { fetchMediaProviderConfigsAwait("video_gen") }
                launch { fetchAppConfigAwait() }
            }
            _settingsBootLoaded.value = true
        }
        fetchMarketWatchlist()
        fetchMarketAlerts()
        fetchBankChannels()
    }

    private fun fetchAppConfig() {
        viewModelScope.launch { fetchAppConfigAwait() }
    }

    private suspend fun fetchAppConfigAwait() {
        when (val result = infoRepository.getAppConfig()) {
            is Resource.Success -> {
                result.data?.let { config ->
                    _freePlatformLimit.value = config.freePlatformLimit
                }
            }
            else -> {}
        }
    }

    fun setGuardianEnabled(enabled: Boolean) {
        _guardianEnabled.value = enabled
        viewModelScope.launch {
            preferenceManager.setGuardianEnabled(enabled)
        }
    }

    fun setAiAutoSendEnabled(enabled: Boolean) {
        _aiAutoSendEnabled.value = enabled
        viewModelScope.launch {
            preferenceManager.setAiAutoSendEnabled(enabled)
        }
    }

    fun setVoiceNoteAutoplay(enabled: Boolean) {
        _voiceNoteAutoplay.value = enabled
        viewModelScope.launch {
            preferenceManager.setVoiceNoteAutoplay(enabled)
        }
    }

    fun fetchByokStatus() {
        viewModelScope.launch { fetchByokStatusAwait() }
    }

    private suspend fun fetchByokStatusAwait() {
        when (val result = infoRepository.getByokStatus()) {
            is Resource.Success -> _byokStatus.value = result.data
            else -> {}
        }
    }

    fun saveByokKey(providerType: String, apiKey: String, baseUrl: String?, modelName: String?) {
        viewModelScope.launch {
            _isSavingByok.value = true
            when (val result = infoRepository.saveByokKey(providerType, apiKey, baseUrl, modelName)) {
                is Resource.Success -> {
                    _byokStatus.value = result.data
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to save AI provider key")
                else -> {}
            }
            _isSavingByok.value = false
        }
    }

    fun clearByokKey() {
        viewModelScope.launch {
            _isSavingByok.value = true
            when (val result = infoRepository.clearByokKey()) {
                is Resource.Success -> {
                    _byokStatus.value = com.example.mistreal_mini.data.api.ByokStatusResponse(success = true, configured = false)
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to remove AI provider key")
                else -> {}
            }
            _isSavingByok.value = false
        }
    }

    // --- Separate BYOK slot for video editing (see InfoRepository for why) ---

    private val _byokVideoStatus = MutableStateFlow<com.example.mistreal_mini.data.api.ByokStatusResponse?>(null)
    val byokVideoStatus = _byokVideoStatus.asStateFlow()

    private val _isSavingByokVideo = MutableStateFlow(false)
    val isSavingByokVideo = _isSavingByokVideo.asStateFlow()

    fun fetchByokVideoStatus() {
        viewModelScope.launch { fetchByokVideoStatusAwait() }
    }

    private suspend fun fetchByokVideoStatusAwait() {
        when (val result = infoRepository.getByokVideoStatus()) {
            is Resource.Success -> _byokVideoStatus.value = result.data
            else -> {}
        }
    }

    fun saveByokVideoKey(providerType: String, apiKey: String, baseUrl: String?, modelName: String?) {
        viewModelScope.launch {
            _isSavingByokVideo.value = true
            when (val result = infoRepository.saveByokVideoKey(providerType, apiKey, baseUrl, modelName)) {
                is Resource.Success -> {
                    _byokVideoStatus.value = result.data
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to save video provider key")
                else -> {}
            }
            _isSavingByokVideo.value = false
        }
    }

    fun clearByokVideoKey() {
        viewModelScope.launch {
            _isSavingByokVideo.value = true
            when (val result = infoRepository.clearByokVideoKey()) {
                is Resource.Success -> {
                    _byokVideoStatus.value = com.example.mistreal_mini.data.api.ByokStatusResponse(success = true, configured = false)
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to remove video provider key")
                else -> {}
            }
            _isSavingByokVideo.value = false
        }
    }

    // --- Saved image/video GENERATION provider configs (multi, switchable) ---
    // Separate from the single-slot BYOK sections above: a user can save
    // several providers per capability and pick which is active, or "Our
    // Recommended" (null configId) to use Imagen/Veo.

    private val _imageGenConfigs = MutableStateFlow<com.example.mistreal_mini.data.api.MediaProviderConfigListResponse?>(null)
    val imageGenConfigs = _imageGenConfigs.asStateFlow()

    private val _videoGenConfigs = MutableStateFlow<com.example.mistreal_mini.data.api.MediaProviderConfigListResponse?>(null)
    val videoGenConfigs = _videoGenConfigs.asStateFlow()

    private val _isSavingMediaProvider = MutableStateFlow(false)
    val isSavingMediaProvider = _isSavingMediaProvider.asStateFlow()

    fun fetchMediaProviderConfigs(capability: String) {
        viewModelScope.launch { fetchMediaProviderConfigsAwait(capability) }
    }

    private suspend fun fetchMediaProviderConfigsAwait(capability: String) {
        when (val result = infoRepository.getMediaProviderConfigs(capability)) {
            is Resource.Success -> {
                if (capability == "image_gen") _imageGenConfigs.value = result.data else _videoGenConfigs.value = result.data
            }
            else -> {}
        }
    }

    fun addMediaProviderConfig(capability: String, label: String, providerType: String, apiKey: String, baseUrl: String, modelName: String?) {
        viewModelScope.launch {
            _isSavingMediaProvider.value = true
            when (val result = infoRepository.addMediaProviderConfig(capability, label, providerType, apiKey, baseUrl, modelName)) {
                is Resource.Success -> {
                    fetchMediaProviderConfigs(capability)
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to save provider")
                else -> {}
            }
            _isSavingMediaProvider.value = false
        }
    }

    fun activateMediaProviderConfig(capability: String, configId: Int?) {
        viewModelScope.launch {
            when (val result = infoRepository.activateMediaProviderConfig(capability, configId)) {
                is Resource.Success -> fetchMediaProviderConfigs(capability)
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to switch provider")
                else -> {}
            }
        }
    }

    fun deleteMediaProviderConfig(capability: String, id: Int) {
        viewModelScope.launch {
            when (val result = infoRepository.deleteMediaProviderConfig(id)) {
                is Resource.Success -> fetchMediaProviderConfigs(capability)
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to remove provider")
                else -> {}
            }
        }
    }

    // --- Market Watch & Alerts ---
    private val _marketWatchlist = MutableStateFlow<List<com.example.mistreal_mini.data.api.MarketQuote>>(emptyList())
    val marketWatchlist = _marketWatchlist.asStateFlow()

    private val _marketAlerts = MutableStateFlow<List<com.example.mistreal_mini.data.api.MarketAlert>>(emptyList())
    val marketAlerts = _marketAlerts.asStateFlow()

    private val _isSavingMarketAlert = MutableStateFlow(false)
    val isSavingMarketAlert = _isSavingMarketAlert.asStateFlow()

    fun fetchMarketWatchlist() {
        viewModelScope.launch {
            when (val result = marketRepository.getWatchlist()) {
                is Resource.Success -> _marketWatchlist.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    fun fetchMarketAlerts() {
        viewModelScope.launch {
            when (val result = marketRepository.getAlerts()) {
                is Resource.Success -> _marketAlerts.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    fun addMarketAlert(symbol: String, assetClass: String, direction: String, targetPrice: Double) {
        viewModelScope.launch {
            _isSavingMarketAlert.value = true
            when (val result = marketRepository.createAlert(symbol, assetClass, direction, targetPrice)) {
                is Resource.Success -> {
                    fetchMarketAlerts()
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to create alert")
                else -> {}
            }
            _isSavingMarketAlert.value = false
        }
    }

    fun removeMarketAlert(id: Int) {
        viewModelScope.launch {
            when (val result = marketRepository.deleteAlert(id)) {
                is Resource.Success -> fetchMarketAlerts()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to remove alert")
                else -> {}
            }
        }
    }

    private val _selectedChartSymbol = MutableStateFlow<String?>(null)
    val selectedChartSymbol = _selectedChartSymbol.asStateFlow()

    private val _chartCandles = MutableStateFlow<List<com.example.mistreal_mini.data.api.MarketCandle>>(emptyList())
    val chartCandles = _chartCandles.asStateFlow()

    private val _isLoadingChart = MutableStateFlow(false)
    val isLoadingChart = _isLoadingChart.asStateFlow()

    fun openChart(symbol: String, assetClass: String) {
        _selectedChartSymbol.value = symbol
        _chartCandles.value = emptyList()
        viewModelScope.launch {
            _isLoadingChart.value = true
            when (val result = marketRepository.getCandles(symbol, assetClass)) {
                is Resource.Success -> _chartCandles.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Chart unavailable")
                else -> {}
            }
            _isLoadingChart.value = false
        }
    }

    fun closeChart() {
        _selectedChartSymbol.value = null
    }

    // --- Contact Your Bank ---
    private val _bankChannels = MutableStateFlow<List<com.example.mistreal_mini.data.api.BankChannel>>(emptyList())
    val bankChannels = _bankChannels.asStateFlow()

    fun fetchBankChannels() {
        viewModelScope.launch {
            when (val result = infoRepository.getBankChannels()) {
                is Resource.Success -> _bankChannels.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    // --- Verified Faces (face-swap safeguard) ---
    val verifiedFaces = verifiedFaceRepository.allFaceUris.stateIn(
        viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList()
    )

    private val _isRegisteringFace = MutableStateFlow(false)
    val isRegisteringFace = _isRegisteringFace.asStateFlow()

    fun registerFace(label: String, liveCaptureUri: android.net.Uri) {
        viewModelScope.launch {
            _isRegisteringFace.value = true
            when (val result = verifiedFaceRepository.register(label, liveCaptureUri)) {
                is com.example.mistreal_mini.data.repository.VerifiedFaceRepository.RegisterResult.Success -> _saveSuccess.emit(Unit)
                is com.example.mistreal_mini.data.repository.VerifiedFaceRepository.RegisterResult.Error -> _errorEvent.emit(result.message)
            }
            _isRegisteringFace.value = false
        }
    }

    fun deleteFace(entity: com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity) {
        viewModelScope.launch { verifiedFaceRepository.delete(entity) }
    }

    fun saveSettings(
        name: String,
        persona: String,
        audience: String,
        delayMinutes: Int,
        guardianEnabled: Boolean? = null,
        aiCustomName: String? = null,
        aiAutoSendEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)

            val result = updateUserSettingsUseCase(
                deviceId = deviceId,
                name = name,
                persona = persona,
                audience = audience,
                delayMinutes = delayMinutes,
                guardianEnabled = guardianEnabled,
                aiAutoSendEnabled = aiAutoSendEnabled
            )
            
            if (result is Resource.Success) {
                preferenceManager.setUserName(name)
                preferenceManager.setAiPersona(persona)
                aiCustomName?.let { preferenceManager.setAiCustomName(it) }
                _saveSuccess.emit(Unit)
            } else {
                _errorEvent.emit((result as Resource.Error).message ?: "Save failed")
            }
            _isSaving.value = false
        }
    }

    fun fetchPlatforms() {
        viewModelScope.launch {
            _isLoadingPlatforms.value = true
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getAvailablePlatforms(deviceId)) {
                is Resource.Success -> {
                    _availablePlatforms.value = result.data ?: emptyList()
                }
                is Resource.Error -> {
                    _errorEvent.emit("Failed to load platforms: ${result.message}")
                }
                else -> {}
            }
            _isLoadingPlatforms.value = false
        }
    }

    fun initiateSocialConnection(platform: String) {
        viewModelScope.launch {
            _isConnectingSocial.value = true
            _connectingPlatform.value = platform
            timber.log.Timber.d("🚀 Initiating social connection for platform: $platform")
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val response = infoRepository.initiateConnection(deviceId, platform)
            if (response is Resource.Success<String> && response.data != null && response.data.isNotBlank()) {
                timber.log.Timber.d("✅ Connection initiated successfully. URL: ${response.data}")
                _socialConnectUrl.value = response.data
            } else {
                val error = response.message ?: "Connection init failed"
                timber.log.Timber.e("❌ Social connection initiation failed: $error")
                if (error.contains("LIMIT_REACHED", ignoreCase = true)) {
                    _errorEvent.emit("PLATFORM_LIMIT_REACHED")
                } else {
                    _errorEvent.emit(error)
                }
            }
            _isConnectingSocial.value = false
            _connectingPlatform.value = null
        }
    }

    fun clearSocialConnectUrl() {
        _socialConnectUrl.value = null
    }

    fun disconnectSocial(platform: String) {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.disconnectPlatform(deviceId, platform)) {
                is Resource.Success<Boolean> -> {
                    fetchPlatforms()
                    _saveSuccess.emit(Unit)
                }
                is Resource.Error<Boolean> -> {
                    _errorEvent.emit("Failed to disconnect $platform: ${result.message}")
                }
                else -> {}
            }
        }
    }

    fun onSocialConnectionResult(platform: String, success: Boolean) {
        viewModelScope.launch {
            if (success) {
                _isSyncing.value = true
                _syncMessage.value = "Initializing Secure Sync..."
                _syncProgress.value = 0.2f
                
                try {
                    val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                    
                    // 🔄 Step 1: Trigger Cloud Sync First
                    _syncMessage.value = "Fetching Latest Cloud Data..."
                    _syncProgress.value = 0.4f
                    infoRepository.syncSocials(deviceId)
                    
                    // 📥 Step 2: Fetch and Map Local Contacts
                    _syncMessage.value = "Mapping Social Graph..."
                    _syncProgress.value = 0.7f
                    val normalizedPlatform = platform.lowercase()
                    val contactsResponse = infoRepository.getContacts(deviceId, normalizedPlatform)
                    
                    if (contactsResponse is Resource.Success) {
                        val entities = contactsResponse.data?.map { contact ->
                            SocialContactEntity(
                                contactId = "${normalizedPlatform}_${contact.id}", // 🛡️ Prevent Collision
                                platform = normalizedPlatform,
                                name = contact.name,
                                avatarUrl = contact.avatar,
                                lastInteractionTime = System.currentTimeMillis(),
                                isEmergency = false,
                                platformUserId = contact.id
                            )
                        } ?: emptyList()
                        
                        // Clean up any old contacts without prefix to avoid confusion
                        socialContactDao.deleteByPlatform(normalizedPlatform)
                        socialContactDao.upsertAll(entities)
                    }

                    _syncMessage.value = "Finalizing..."
                    _syncProgress.value = 1.0f
                } catch (e: Exception) {
                    _errorEvent.emit("Sync failed: ${e.message}")
                } finally {
                    _isSyncing.value = false
                }
                
                fetchPlatforms()
                _socialConnectionSuccess.emit(platform)
            }
        }
    }

    suspend fun getUserName() = preferenceManager.userName.first()
    suspend fun getAiPersona() = preferenceManager.aiPersona.first()
    suspend fun getAiCustomName() = preferenceManager.aiCustomName.first()
    suspend fun getAiAudience() = preferenceManager.aiAudience.first()
    suspend fun getAutoReplyDelay() = preferenceManager.autoReplyDelay.first()
    suspend fun isLocationEnabled() = preferenceManager.isLocationEnabled.first()
    suspend fun isTtsEnabled() = preferenceManager.isTtsEnabled.first()
    suspend fun isSttEnabled() = preferenceManager.isSttEnabled.first()
    suspend fun getThemeMode() = preferenceManager.themeMode.first()
    suspend fun getDefaultTranslationLang() = preferenceManager.defaultTranslationLang.first()

    fun setDefaultTranslationLang(lang: String) {
        viewModelScope.launch {
            preferenceManager.setDefaultTranslationLang(lang)
        }
    }

    fun loadVoices(tts: TextToSpeech?) {
        viewModelScope.launch {
            val voices = tts?.voices?.toList() ?: emptyList()
            _availableVoices.value = voices
            _selectedVoiceName.value = preferenceManager.ttsVoiceName.first()
        }
    }

    fun setSelectedVoice(voice: Voice) {
        viewModelScope.launch {
            _selectedVoiceName.value = voice.name
            preferenceManager.setTtsVoiceName(voice.name)
        }
    }

    fun addCustomPersona(persona: String) {
        viewModelScope.launch {
            val newList = _customPersonas.value.toMutableList()
            if (!newList.contains(persona)) {
                newList.add(persona)
                _customPersonas.value = newList
                preferenceManager.setCustomPersonas(gson.toJson(newList))
                
                // 🚀 Immediate Selection & Save
                val currentName = preferenceManager.userName.first()
                val currentAudience = preferenceManager.aiAudience.first()
                val currentDelay = preferenceManager.autoReplyDelay.first()
                saveSettings(currentName, persona, currentAudience, currentDelay)
            }
        }
    }

    fun deleteCustomPersona(persona: String) {
        viewModelScope.launch {
            val newList = _customPersonas.value.toMutableList()
            if (newList.remove(persona)) {
                _customPersonas.value = newList
                preferenceManager.setCustomPersonas(gson.toJson(newList))
            }
        }
    }

    fun addCustomAudience(audience: String) {
        viewModelScope.launch {
            val newList = _customAudiences.value.toMutableList()
            if (!newList.contains(audience)) {
                newList.add(audience)
                _customAudiences.value = newList
                preferenceManager.setCustomAudiences(gson.toJson(newList))
                
                // 🚀 Immediate Selection & Save
                val currentName = preferenceManager.userName.first()
                val currentPersona = preferenceManager.aiPersona.first()
                val currentDelay = preferenceManager.autoReplyDelay.first()
                saveSettings(currentName, currentPersona, audience, currentDelay)
            }
        }
    }

    fun deleteCustomAudience(audience: String) {
        viewModelScope.launch {
            val newList = _customAudiences.value.toMutableList()
            if (newList.remove(audience)) {
                _customAudiences.value = newList
                preferenceManager.setCustomAudiences(gson.toJson(newList))
            }
        }
    }

    fun setLocationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setLocationEnabled(enabled)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            preferenceManager.setThemeMode(mode)
        }
    }

    fun setTtsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setTtsEnabled(enabled)
        }
    }

    fun setSttEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setSttEnabled(enabled)
        }
    }

    fun setSupportiveTruthTellerEnabled(enabled: Boolean) {
        _isSupportiveTruthTellerEnabled.value = enabled
        viewModelScope.launch {
            preferenceManager.setSupportiveTruthTellerEnabled(enabled)
        }
    }

    fun setWellnessShieldEnabled(enabled: Boolean) {
        _isWellnessShieldEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setWellnessShieldEnabled(enabled) }
    }

    fun setProactiveNudgeEnabled(enabled: Boolean) {
        _isProactiveNudgeEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setProactiveNudgeEnabled(enabled) }
    }

    fun setIntelligenceSparkEnabled(enabled: Boolean) {
        _isIntelligenceSparkEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setIntelligenceSparkEnabled(enabled) }
    }

    fun setPersistentSceneModeEnabled(enabled: Boolean) {
        _isPersistentSceneModeEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setPersistentSceneModeEnabled(enabled) }
    }

    fun setDeepAnalysisEnabled(enabled: Boolean) {
        _isDeepAnalysisEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setDeepAnalysisEnabled(enabled) }
    }

    fun setGodModeEnabled(enabled: Boolean) {
        _isGodModeEnabled.value = enabled
        viewModelScope.launch { preferenceManager.setGodModeEnabled(enabled) }
    }

    fun setGodModeTask(task: String) {
        _godModeTask.value = task
        viewModelScope.launch { preferenceManager.setGodModeTask(task) }
    }

    fun setGodModeStyle(style: String) {
        val lower = style.lowercase()
        val forbidden = listOf("racist", "hate", "supremacist", "bigot", "nazi")
        if (forbidden.any { lower.contains(it) }) {
            viewModelScope.launch { _errorEvent.emit("Style rejected: Must adhere to non-discriminatory principles.") }
            return
        }
        _godModeStyle.value = style
        viewModelScope.launch { preferenceManager.setGodModeStyle(style) }
    }

    fun saveRandomFreq(freq: String) {
        viewModelScope.launch {
            // Logic to be implemented in PreferenceManager or a worker
        }
    }

    private val _hasSmsAddon = MutableStateFlow(false)
    val hasSmsAddon: StateFlow<Boolean> = _hasSmsAddon.asStateFlow()

    fun fetchAddonStatus() {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getMyAddons(deviceId)) {
                is Resource.Success -> _hasSmsAddon.value = result.data?.contains("sms_notifications") == true
                else -> {}
            }
        }
    }

    fun fetchEmergencyContacts() {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            when (val result = infoRepository.getEmergencyContacts(deviceId)) {
                is Resource.Success -> _emergencyContacts.value = result.data ?: emptyList()
                else -> {}
            }
        }
    }

    /** Sends the confirm/decline invite — the contact must accept before any real alert ever reaches them. */
    fun addEmergencyContact(name: String, channel: String, platform: String? = null, platformContactId: String? = null, email: String? = null, phoneNumber: String? = null) {
        viewModelScope.launch {
            _isSavingEmergencyContact.value = true
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = infoRepository.addEmergencyContact(deviceId, name, channel, platform, platformContactId, email, phoneNumber)
            _isSavingEmergencyContact.value = false
            if (result is Resource.Success) {
                fetchEmergencyContacts()
            }
        }
    }

    fun removeEmergencyContact(contact: EmergencyContact) {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val result = infoRepository.deleteEmergencyContact(contact.id, deviceId)
            if (result is Resource.Success) {
                _emergencyContacts.value = _emergencyContacts.value.filter { it.id != contact.id }
            }
        }
    }

    fun addBankLink(name: String, url: String, packageId: String?) {
        viewModelScope.launch {
            bankDao.insertBank(com.example.mistreal_mini.data.local.entity.BankLinkEntity(
                id = "bank_${System.currentTimeMillis()}",
                name = name,
                url = url,
                packageId = packageId
            ))
        }
    }

    fun deleteBank(bank: com.example.mistreal_mini.data.local.entity.BankLinkEntity) {
        viewModelScope.launch {
            bankDao.deleteBank(bank)
        }
    }

    private val _closeAppEvent = MutableSharedFlow<Unit>()
    val closeAppEvent = _closeAppEvent.asSharedFlow()

    fun launchBank(bank: com.example.mistreal_mini.data.local.entity.BankLinkEntity) {
        val intent = if (bank.packageId != null) {
            context.packageManager.getLaunchIntentForPackage(bank.packageId)
        } else null
        
        if (intent != null) {
            context.startActivity(intent)
        } else {
            val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(bank.url))
            browserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(browserIntent)
        }
        
        // 🔒 Request app closure
        viewModelScope.launch { _closeAppEvent.emit(Unit) }
    }
}
