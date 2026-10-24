package com.example.mistreal_mini.ui.dashboard.components.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.api.AdPayload
import com.example.mistreal_mini.data.repository.AdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Polls for and times out ads on its own — previously every caller
 * (MarketScreen, AdSplitScreenHost) ran an identical pair of LaunchedEffects
 * to drive this same polling/impression/timeout loop; centralizing it here
 * means a new caller just observes [dueAd], no copy-pasted effect required.
 */
@HiltViewModel
class AdHostViewModel @Inject constructor(
    private val adRepository: AdRepository
) : ViewModel() {

    private val _dueAd = MutableStateFlow<AdPayload?>(null)
    val dueAd: StateFlow<AdPayload?> = _dueAd

    init {
        viewModelScope.launch {
            while (true) {
                checkForDueAd()
                delay(60_000)
            }
        }
        viewModelScope.launch {
            dueAd.collectLatest { ad ->
                if (ad != null) {
                    trackImpression(ad.id)
                    delay(ad.durationSeconds * 1000L)
                    clearAd()
                }
            }
        }
    }

    fun checkForDueAd() {
        if (_dueAd.value != null) return // already showing one — let it finish its turn
        viewModelScope.launch {
            _dueAd.value = adRepository.getDueAd()
        }
    }

    fun clearAd() {
        _dueAd.value = null
    }

    fun trackImpression(adId: Int) {
        viewModelScope.launch { adRepository.trackAdEvent(adId, "impression") }
    }

    fun trackEngagedClick(adId: Int) {
        viewModelScope.launch { adRepository.trackAdEvent(adId, "engaged_click") }
    }
}
