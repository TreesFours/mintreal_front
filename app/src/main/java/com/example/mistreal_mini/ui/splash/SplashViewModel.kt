package com.example.mistreal_mini.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.repository.AiRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Used to run its own full warmup here — backend ping, AI model count,
 * social platform sync — all fired pre-auth from init{}, then BootScreen
 * (deliberately gated to run only after auth) re-fetched the same models
 * and platforms seconds later for real use. Two full network round-trips
 * for the same data, and two near-identical "loading" screens back to
 * back. This screen's only real job now is the brief logo animation;
 * warmupBackend() stays as a fire-and-forget ping (no auth needed, pure
 * latency win — pre-warms Render's cold start while the animation plays
 * instead of waiting for it later) but nothing else is fetched or
 * reported here anymore. Model/platform counts are reported by
 * BootScreen instead, where the real fetch already happens.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            launch {
                try { aiRepository.warmupBackend() } catch (e: Exception) { /* best-effort ping */ }
            }
            delay(1200)
            _isReady.value = true
        }
    }
}
