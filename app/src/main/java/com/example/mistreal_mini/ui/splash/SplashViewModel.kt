package com.example.mistreal_mini.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.dao.SavedIntelDao
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    private val authRepository: AuthRepository,
    private val chatDao: ChatDao,
    private val savedIntelDao: SavedIntelDao
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
                _statusText.value = "Waking backend..."
                aiRepository.warmupBackend()

                val userId = authRepository.currentUser?.uid ?: "guest"

                _statusText.value = "Loading your stuff..."
                val chatCount = try { chatDao.getUniqueTrendCount(userId) } catch (e: Exception) { 0 }
                val intelCount = try { savedIntelDao.getIntelCount(userId) } catch (e: Exception) { 0 }

                if (chatCount == 0 && intelCount == 0) {
                    _statusText.value = "Welcome! System ready for first deployment..."
                } else {
                    _statusText.value = "Load complete. Synchronization successful..."
                }

                kotlinx.coroutines.delay(1200)
                _isReady.value = true
            } catch (e: Exception) {
                _statusText.value = "Backend awake. Deployment ready..."
                kotlinx.coroutines.delay(1000)
                _isReady.value = true
            }
        }
    }
}
