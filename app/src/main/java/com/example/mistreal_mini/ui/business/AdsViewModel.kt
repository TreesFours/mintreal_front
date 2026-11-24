package com.example.mistreal_mini.ui.business

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AdCampaign
import com.example.mistreal_mini.data.repository.AdsRepository
import com.example.mistreal_mini.data.repository.BusinessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs AdsConnectionsScreen — connect a per-platform ad account (mirrors
 * SocialConnectionsScreen's OAuth-handoff shape, not the BYOK pattern) and
 * manage campaigns for whichever platform's ad account is connected. Every
 * call here can surface "ADS_ADDON_REQUIRED" — Zernio's own Ads add-on
 * toggle on the workspace, which this app has no way to check or flip
 * itself; shown verbatim rather than translated into a generic error so
 * the user knows exactly what's blocking them.
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    private val adsRepository: AdsRepository,
    private val businessRepository: BusinessRepository
) : ViewModel() {

    private val _connectUrl = MutableSharedFlow<String>()
    val connectUrl = _connectUrl.asSharedFlow()

    private val _connectedAdPlatforms = MutableStateFlow<List<String>>(emptyList())
    val connectedAdPlatforms = _connectedAdPlatforms.asStateFlow()

    /**
     * Live source of truth for which ad accounts are actually connected —
     * the list passed in via navigation is only a point-in-time snapshot
     * from whenever the screen was opened, which would otherwise never
     * reflect a platform connected moments ago through the external OAuth
     * handoff. Call again after returning from that handoff to refresh.
     */
    fun loadConnectedAdAccounts(businessId: String) {
        viewModelScope.launch {
            when (val result = businessRepository.getBusinessDetail(businessId)) {
                is Resource.Success -> {
                    _connectedAdPlatforms.value = result.data?.business?.connectedAdAccounts?.map { it.platform } ?: emptyList()
                }
                else -> {} // keep whatever the nav-time snapshot already showed
            }
        }
    }

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()

    private val _campaigns = MutableStateFlow<List<AdCampaign>>(emptyList())
    val campaigns = _campaigns.asStateFlow()

    private val _isLoadingCampaigns = MutableStateFlow(false)
    val isLoadingCampaigns = _isLoadingCampaigns.asStateFlow()

    private val _isCreatingCampaign = MutableStateFlow(false)
    val isCreatingCampaign = _isCreatingCampaign.asStateFlow()

    fun connectAdAccount(businessId: String, platform: String) {
        viewModelScope.launch {
            _isConnecting.value = true
            when (val result = adsRepository.connectAdAccount(businessId, platform)) {
                is Resource.Success -> result.data?.let { _connectUrl.emit(it) }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to start ad account connection")
                else -> {}
            }
            _isConnecting.value = false
        }
    }

    fun loadCampaigns(businessId: String, platform: String) {
        viewModelScope.launch {
            _isLoadingCampaigns.value = true
            when (val result = adsRepository.getCampaigns(businessId, platform)) {
                is Resource.Success -> _campaigns.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load campaigns")
                else -> {}
            }
            _isLoadingCampaigns.value = false
        }
    }

    fun createCampaign(businessId: String, platform: String, name: String, budget: Double, objective: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isCreatingCampaign.value = true
            val result = adsRepository.createCampaign(businessId, platform, name, budget, objective)
            _isCreatingCampaign.value = false
            if (result is Resource.Success) {
                loadCampaigns(businessId, platform)
                onResult(true)
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to create campaign")
                onResult(false)
            }
        }
    }

    fun updateCampaignStatus(businessId: String, platform: String, campaignId: String, status: String) {
        viewModelScope.launch {
            val result = adsRepository.updateCampaignStatus(campaignId, status)
            if (result is Resource.Success) loadCampaigns(businessId, platform)
            else _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to update campaign")
        }
    }
}
