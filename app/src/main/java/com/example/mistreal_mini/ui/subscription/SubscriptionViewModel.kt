package com.example.mistreal_mini.ui.subscription

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AddonDefinition
import com.example.mistreal_mini.data.repository.BillingRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.DeviceIdProvider
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
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) : ViewModel() {

    private val deviceId: String
        get() = deviceIdProvider.deviceId

    // Decided once per process, not per add-on — a device's install source
    // doesn't change at runtime. Play-sourced installs must use Play
    // Billing; everything else uses Stripe (see BillingRail.kt).
    val isPlayStoreInstall: Boolean = com.example.mistreal_mini.data.repository.BillingRail.isPlayStoreInstall(context)

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
                is Resource.Success -> {
                    val catalog = result.data?.addons ?: emptyList()
                    // A non-Play install can't use an add-on with no Stripe
                    // price configured — hide it rather than offer a
                    // checkout that can't succeed.
                    _addons.value = if (isPlayStoreInstall) catalog else catalog.filter { it.stripeAvailable }
                }
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
        if (isPlayStoreInstall) {
            billingRepository.launchBillingFlow(activity, addon.playProductId)
        } else {
            billingRepository.launchStripeCheckout(activity, addon.id)
        }
    }

    /**
     * Neither rail lets the app cancel a subscription silently in-app —
     * Play Billing's client library has no such API by design, and Stripe
     * cancellation is likewise pushed to its own hosted portal. Unchecking
     * here always deep-links out: Play Store's manage-subscriptions page
     * for a Play install, Stripe's customer billing portal otherwise.
     */
    fun openCancelSubscription(activity: Activity, addon: AddonDefinition) {
        if (isPlayStoreInstall) {
            val uri = Uri.parse("https://play.google.com/store/account/subscriptions?sku=${addon.playProductId}&package=${context.packageName}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            billingRepository.launchStripePortal(activity)
        }
    }

    /** Re-polls my-addons — called when the screen resumes, since neither
     * a Stripe checkout nor the Stripe portal hands control back to the app
     * with a result callback the way Play Billing does. */
    fun refreshAddonStatus() {
        loadCatalogAndStatus()
    }
}
