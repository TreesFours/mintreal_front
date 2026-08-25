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
                _statusText.value = "Waking up backend..."
                aiRepository.warmupBackend()

                val userId = authRepository.currentUser?.uid ?: "guest"

                _statusText.value = "Loading your previous chats..."
                val chatCount = try { chatDao.getUniqueTrendCount(userId) } catch (e: Exception) { 0 }

                _statusText.value = "Loading your intelligence feeds..."
                val intelCount = try { savedIntelDao.getIntelCount(userId) } catch (e: Exception) { 0 }

                _statusText.value = "All systems synchronized ($chatCount chats, $intelCount intelligence feeds ready)..."
                kotlinx.coroutines.delay(1200)
                _isReady.value = true
            } catch (e: Exception) {
                _statusText.value = "Backend awake. Initializing session..."
                kotlinx.coroutines.delay(1000)
                _isReady.value = true
            }
        }
    }
}
