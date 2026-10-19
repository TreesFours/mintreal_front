package com.example.mistreal_mini.ui.guardian

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.EmergencyAlert
import com.example.mistreal_mini.data.api.SafeZone
import com.example.mistreal_mini.data.api.SosPreferences
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.DeviceIdProvider
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _safeZone = MutableStateFlow<SafeZone?>(null)
    val safeZone: StateFlow<SafeZone?> = _safeZone.asStateFlow()

    private val _sosPreferences = MutableStateFlow(SosPreferences())
    val sosPreferences: StateFlow<SosPreferences> = _sosPreferences.asStateFlow()

    // Why a resolve attempt failed (no location, no safe zone configured,
    // outside the safe-zone radius) — the confirm sheet shows this instead
    // of silently staying on screen.
    private val _resolveError = MutableSharedFlow<String>()
    val resolveError = _resolveError.asSharedFlow()

    // Drives the blocking "I'm safe" confirm sheet shown app-wide on open —
    // escalation only stops once this resolves to null.
    val activeAlert: StateFlow<EmergencyAlert?> = _alerts
        .map { list -> list.firstOrNull { it.status == "active" } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private fun deviceId() = deviceIdProvider.deviceId

    fun fetchAlerts() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = infoRepository.getEmergencyAlerts(deviceId())
            _isLoading.value = false
            if (result is Resource.Success) _alerts.value = result.data ?: emptyList()
        }
    }

    fun fetchSafeZone() {
        viewModelScope.launch {
            val result = infoRepository.getSafeZone(deviceId())
            if (result is Resource.Success) _safeZone.value = result.data
        }
    }

    fun fetchSosPreferences() {
        viewModelScope.launch {
            val result = infoRepository.getSosPreferences(deviceId())
            if (result is Resource.Success) _sosPreferences.value = result.data ?: SosPreferences()
        }
    }

    /**
     * Required setup step — always sourced from the device's own live fix,
     * never a manually-typed coordinate (no text field exists for this
     * anywhere in the UI), so a safe zone can't be configured remotely.
     */
    fun saveSafeZoneHere(onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location == null) {
                _resolveError.emit("Couldn't get your current location — enable location services and try again.")
                onDone(false)
                return@launch
            }
            val result = infoRepository.setSafeZone(deviceId(), location.latitude, location.longitude)
            if (result is Resource.Success) _safeZone.value = result.data
            onDone(result is Resource.Success)
        }
    }

    fun setSosPreference(
        camera: Boolean? = null,
        microphone: Boolean? = null,
        image: Boolean? = null,
        fullDetailEscalation: Boolean? = null,
        silentMeetupWindowDays: Int? = null,
        communityBroadcastDelayDays: Int? = null,
        fullDetailWindowDays: Int? = null
    ) {
        viewModelScope.launch {
            val result = infoRepository.setSosPreferences(
                deviceId(), camera, microphone, image, fullDetailEscalation,
                silentMeetupWindowDays, communityBroadcastDelayDays, fullDetailWindowDays
            )
            if (result is Resource.Success) _sosPreferences.value = result.data ?: _sosPreferences.value
        }
    }

    /**
     * Fetches a live location first (fail closed — no silent "resolved
     * anyway" if location is unavailable) since the server checks it
     * against the owner's own pre-configured safe zone before accepting.
     */
    fun resolveAlert(alertId: Int) {
        viewModelScope.launch {
            val location = locationHelper.getCurrentLocation()
            if (location == null) {
                _resolveError.emit("Location unavailable — enable location services to resolve this alert.")
                return@launch
            }
            val result = infoRepository.resolveEmergencyAlert(alertId, deviceId(), location.latitude, location.longitude)
            if (result is Resource.Success) {
                fetchAlerts()
            } else {
                _resolveError.emit((result as? Resource.Error)?.message ?: "Failed to resolve alert")
            }
        }
    }

    fun resolveActiveAlert() {
        activeAlert.value?.let { resolveAlert(it.id) }
    }
}
