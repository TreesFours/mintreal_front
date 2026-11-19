package com.example.mistreal_mini.ui.splash

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.dao.SavedIntelDao
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.AuthRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val authRepository: AuthRepository,
    private val infoRepository: InfoRepository,
    private val chatDao: ChatDao,
    private val savedIntelDao: SavedIntelDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _statusText = MutableStateFlow("Initializing system...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        performWarmupSequence()
    }

    private fun performWarmupSequence() {
        viewModelScope.launch {
            try {
                val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "device_default"
                val userId = authRepository.currentUser?.uid ?: "guest"

                // Step 1: Backend Connection Check
                _statusText.value = "Connecting to Mistreal backend..."
                val isAwake = try {
                    aiRepository.warmupBackend()
                } catch (e: Exception) {
                    false
                }

                // Step 2: Query Local Storage (Chats & Intel Logs)
                _statusText.value = "Reading local memory vaults..."
                val chatCount = try { chatDao.getUniqueTrendCount(userId) } catch (e: Exception) { 0 }
                val intelCount = try { savedIntelDao.getIntelCount(userId) } catch (e: Exception) { 0 }

                // Step 3: Fetch Available AI Models
                _statusText.value = "Auditing available AI models..."
                val modelCount = try {
                    val result = aiRepository.getAvailableModels(deviceId)
                    if (result is com.example.mistreal_mini.data.Resource.Success) {
                        result.data.orEmpty().size
                    } else 0
                } catch (e: Exception) { 0 }

                // Step 4: Preload Social Connections & Platform Handles
                try {
                    infoRepository.getAvailablePlatforms(deviceId)
                    infoRepository.syncSocials(deviceId)
                } catch (e: Exception) {
                    // Non-blocking pre-fetch
                }

                // Step 5: Publish Final Synchronized Status
                val backendStatus = if (isAwake) "Online" else "Local Mode"
                if (chatCount == 0 && intelCount == 0) {
                    _statusText.value = "Backend $backendStatus · $modelCount AI Models Ready · First Launch"
                } else {
                    _statusText.value = "Backend $backendStatus · $chatCount Chats · $intelCount Intel Items · $modelCount AI Models"
                }

                kotlinx.coroutines.delay(1200)
                _isReady.value = true
            } catch (e: Exception) {
                _statusText.value = "System Ready · Deployment Initialized"
                kotlinx.coroutines.delay(800)
                _isReady.value = true
            }
        }
    }
}
