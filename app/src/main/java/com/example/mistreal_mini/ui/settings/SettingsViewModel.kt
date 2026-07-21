package com.example.mistreal_mini.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager,
    private val infoRepository: InfoRepository,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _saveSuccess = MutableSharedFlow<Unit>()
    val saveSuccess = _saveSuccess.asSharedFlow()

    private val _socialConnectUrl = MutableSharedFlow<String>()
    val socialConnectUrl = _socialConnectUrl.asSharedFlow()

    fun saveSettings(name: String, persona: String, delayMinutes: Int) {
        viewModelScope.launch {
            preferenceManager.setUserName(name)
            preferenceManager.setAiPersona(persona)
            preferenceManager.setAutoReplyDelay(delayMinutes)
            _saveSuccess.emit(Unit)
        }
    }

    fun connectSocial(platform: String) {
        viewModelScope.launch {
            val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            // Note: Adding this to InfoApiService/Repository next
            // For now, using a mock URL to demonstrate the flow
            _socialConnectUrl.emit("https://mistreal-backend.onrender.com/api/social/connect?platform=$platform&deviceId=$deviceId")
        }
    }

    suspend fun getUserName() = preferenceManager.userName.first()
    suspend fun getAiPersona() = preferenceManager.aiPersona.first()
    suspend fun getAutoReplyDelay() = preferenceManager.autoReplyDelay.first()
}
