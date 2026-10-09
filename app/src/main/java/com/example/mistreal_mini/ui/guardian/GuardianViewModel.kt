package com.example.mistreal_mini.ui.guardian

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.EmergencyAlert
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GuardianViewModel @Inject constructor(
    private val infoRepository: InfoRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _alerts = MutableStateFlow<List<EmergencyAlert>>(emptyList())
    val alerts: StateFlow<List<EmergencyAlert>> = _alerts.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Drives the blocking "I'm safe" confirm sheet shown app-wide on open —
    // escalation (see the server-side 30-day sweep) only stops once this
    // resolves to null.
    val activeAlert: StateFlow<EmergencyAlert?> = _alerts
        .map { list -> list.firstOrNull { it.status == "active" } }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, null)

    private fun deviceId() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    fun fetchAlerts() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = infoRepository.getEmergencyAlerts(deviceId())
            _isLoading.value = false
            if (result is Resource.Success) _alerts.value = result.data ?: emptyList()
        }
    }

    fun resolveAlert(alertId: Int) {
        viewModelScope.launch {
            val result = infoRepository.resolveEmergencyAlert(alertId, deviceId())
            if (result is Resource.Success) fetchAlerts()
        }
    }

    fun resolveActiveAlert() {
        activeAlert.value?.let { resolveAlert(it.id) }
    }
}
