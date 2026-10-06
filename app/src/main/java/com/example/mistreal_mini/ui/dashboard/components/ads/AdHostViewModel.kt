package com.example.mistreal_mini.ui.dashboard.components.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.api.AdPayload
import com.example.mistreal_mini.data.repository.AdRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdHostViewModel @Inject constructor(
    private val adRepository: AdRepository
) : ViewModel() {

    private val _dueAd = MutableStateFlow<AdPayload?>(null)
    val dueAd: StateFlow<AdPayload?> = _dueAd

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
