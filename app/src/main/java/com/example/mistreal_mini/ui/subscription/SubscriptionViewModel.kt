package com.example.mistreal_mini.ui.subscription

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AddonDefinition
import com.example.mistreal_mini.data.repository.BillingRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Replaces the old single-tier "Upgrade Now" paywall with a checklist of
 * independent, individually-toggleable add-ons (ai_pro, extra_platforms,
 * sms_notifications, ...) — the catalog itself comes from the backend's
 * env-var-driven addonCatalog.ts via /api/config, so adding/repricing an
 * add-on is a config change, not a client release.
 */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    private val infoRepository: InfoRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _addons = mutableStateOf<List<AddonDefinition>>(emptyList())
    val addons: State<List<AddonDefinition>> = _addons

    private val _activeAddonIds = mutableStateOf<Set<String>>(emptySet())
    val activeAddonIds: State<Set<String>> = _activeAddonIds

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _purchaseSuccess = MutableSharedFlow<String?>()
    val purchaseSuccess = _purchaseSuccess.asSharedFlow()

    init {
        loadCatalogAndStatus()
        viewModelScope.launch {
            billingRepository.purchaseSuccess.collect { addonId ->
                if (addonId != null) _activeAddonIds.value = _activeAddonIds.value + addonId
                _purchaseSuccess.emit(addonId)
            }
        }
        viewModelScope.launch {
            billingRepository.errorEvent.collect { _errorEvent.emit(it) }
        }
    }

    private fun loadCatalogAndStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = infoRepository.getAppConfig()) {
                is Resource.Success -> _addons.value = result.data?.addons ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load pricing")
                else -> {}
            }
            when (val result = infoRepository.getMyAddons(deviceId)) {
                is Resource.Success -> _activeAddonIds.value = (result.data ?: emptyList()).toSet()
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun purchaseAddon(activity: Activity, addon: AddonDefinition) {
        billingRepository.launchBillingFlow(activity, addon.playProductId)
    }

    /**
     * Play Billing's client library deliberately has no API for an app to
     * cancel a subscription itself — unchecking here can only deep-link to
     * Play Store's own manage-subscriptions page for that product, not
     * silently cancel in-app. This matches the screen's own existing copy
     * ("Cancel anytime in Google Play Store").
     */
    fun openCancelSubscription(addon: AddonDefinition) {
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${addon.playProductId}&package=${context.packageName}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
