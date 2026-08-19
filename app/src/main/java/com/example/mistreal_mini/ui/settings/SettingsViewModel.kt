package com.example.mistreal_mini.ui.settings

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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val infoRepository: InfoRepository,
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

    val isPro = preferenceManager.isPro

    private val _guardianEnabled = mutableStateOf(false)
    val guardianEnabled: State<Boolean> = _guardianEnabled

    private val _emergencyContacts = mutableStateListOf<EmergencyContact>()
    val emergencyContacts: List<EmergencyContact> = _emergencyContacts

    init {
        viewModelScope.launch {
            preferenceManager.guardianEnabled.collect { _guardianEnabled.value = it }
        }
    }

    fun setGuardianEnabled(enabled: Boolean) {
        _guardianEnabled.value = enabled
        viewModelScope.launch {
            preferenceManager.setGuardianEnabled(enabled)
        }
    }

    fun saveSettings(name: String, persona: String, delayMinutes: Int, guardianEnabled: Boolean? = null, contacts: List<com.example.mistreal_mini.data.api.EmergencyContact>? = null) {
        viewModelScope.launch {
            _isSaving.value = true
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            
            val result = updateUserSettingsUseCase(
                deviceId = deviceId, 
                name = name, 
                persona = persona, 
                delayMinutes = delayMinutes,
                guardianEnabled = guardianEnabled,
                contacts = contacts
            )
            
            if (result is Resource.Success) {
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
            // FIXED: Path aligned with backend /api/social/platforms
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

    fun connectSocial(platform: String) {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            // Enhanced with Deep Link Redirect to bypass intermediate dashboard
            val redirectUri = "mistreal://social-connected"
            _socialConnectUrl.emit("https://mistreal-backend.onrender.com/api/social/connect/$platform?deviceId=$deviceId&redirect_uri=$redirectUri")
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
                fetchPlatforms()
                _socialConnectionSuccess.emit(platform)
            }
        }
    }

    suspend fun getUserName() = preferenceManager.userName.first()
    suspend fun getAiPersona() = preferenceManager.aiPersona.first()
    suspend fun getAutoReplyDelay() = preferenceManager.autoReplyDelay.first()
    suspend fun isLocationEnabled() = preferenceManager.isLocationEnabled.first()
    suspend fun isTtsEnabled() = preferenceManager.isTtsEnabled.first()
    suspend fun isSttEnabled() = preferenceManager.isSttEnabled.first()

    private val _availableVoices = MutableStateFlow<List<android.speech.tts.Voice>>(emptyList())
    val availableVoices = _availableVoices.asStateFlow()

    private val _selectedVoiceName = MutableStateFlow<String?>(null)
    val selectedVoiceName = _selectedVoiceName.asStateFlow()

    private val _customPersonas = MutableStateFlow<List<String>>(emptyList())
    val customPersonas = _customPersonas.asStateFlow()

    private val gson = Gson()

    init {
        viewModelScope.launch {
            preferenceManager.guardianEnabled.collect { _guardianEnabled.value = it }
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

    fun setLocationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setLocationEnabled(enabled)
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

    fun saveRandomFreq(freq: String) {
        viewModelScope.launch {
            // Logic to be implemented in PreferenceManager or a worker
            // For now, we'll store it as a string
        }
    }
}
