package com.example.mistreal_mini.ui.settings

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
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
    private val updateUserSettingsUseCase: UpdateUserSettingsUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _socialConnectUrl = MutableSharedFlow<String>()
    val socialConnectUrl = _socialConnectUrl.asSharedFlow()

    private val _availablePlatforms = MutableStateFlow<List<SocialPlatformResponse>>(emptyList())
    val availablePlatforms = _availablePlatforms.asStateFlow()

    private val _isLoadingPlatforms = MutableStateFlow(false)
    val isLoadingPlatforms = _isLoadingPlatforms.asStateFlow()

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

    private val _emergencyContacts = mutableStateListOf<EmergencyContact>()
    val emergencyContacts: List<EmergencyContact> = _emergencyContacts

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

    private val gson = Gson()

    init {
        viewModelScope.launch {
            preferenceManager.guardianEnabled.collect { _guardianEnabled.value = it }
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
        fetchAppConfig()
    }

    private fun fetchAppConfig() {
        viewModelScope.launch {
            when (val result = infoRepository.getAppConfig()) {
                is Resource.Success -> {
                    result.data?.let { config ->
                        _freePlatformLimit.value = config.freePlatformLimit
                    }
                }
                else -> {}
            }
        }
    }

    fun setGuardianEnabled(enabled: Boolean) {
        _guardianEnabled.value = enabled
        viewModelScope.launch {
            preferenceManager.setGuardianEnabled(enabled)
        }
    }

    fun saveSettings(
        name: String, 
        persona: String, 
        audience: String, 
        delayMinutes: Int, 
        guardianEnabled: Boolean? = null, 
        contacts: List<EmergencyContact>? = null,
        aiCustomName: String? = null
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
                contacts = contacts
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
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            val response = infoRepository.initiateConnection(deviceId, platform)
            if (response is Resource.Success<String> && response.data != null) {
                _socialConnectUrl.emit(response.data)
            } else {
                val error = response.message ?: "Connection init failed"
                if (error.contains("LIMIT_REACHED")) {
                    _errorEvent.emit("PLATFORM_LIMIT_REACHED")
                } else {
                    _errorEvent.emit(error)
                }
            }
        }
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
                _syncMessage.value = "Fetching Profile..."
                _syncProgress.value = 0.2f
                
                try {
                    _syncMessage.value = "Syncing Contacts..."
                    _syncProgress.value = 0.5f
                    val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                    
                    val contactsResponse = infoRepository.getContacts(deviceId, platform)
                    if (contactsResponse is Resource.Success) {
                        val entities = contactsResponse.data?.map { contact ->
                            SocialContactEntity(
                                contactId = contact.id,
                                platform = contact.platform,
                                name = contact.name,
                                avatarUrl = contact.avatar,
                                lastInteractionTime = System.currentTimeMillis(),
                                isEmergency = false,
                                platformUserId = contact.id
                            )
                        } ?: emptyList()
                        socialContactDao.upsertAll(entities)
                    }
                    
                    _syncMessage.value = "Syncing Threads..."
                    _syncProgress.value = 0.8f
                    infoRepository.syncSocials(deviceId)

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

    fun saveRandomFreq(freq: String) {
        viewModelScope.launch {
            // Logic to be implemented in PreferenceManager or a worker
        }
    }

    fun addEmergencyContact(contact: EmergencyContact) {
        if (!_emergencyContacts.any { it.value == contact.value }) {
            _emergencyContacts.add(contact)
        }
    }

    fun removeEmergencyContact(contact: EmergencyContact) {
        _emergencyContacts.remove(contact)
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
