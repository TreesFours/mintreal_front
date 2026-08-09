package com.example.mistreal_mini.ui.subscription

import android.app.Activity
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AppConfigResponse
import com.example.mistreal_mini.data.repository.BillingRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val billingRepository: BillingRepository,
    private val infoRepository: InfoRepository
) : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _config = mutableStateOf<AppConfigResponse?>(null)
    val config: State<AppConfigResponse?> = _config

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _purchaseSuccess = MutableSharedFlow<Unit>()
    val purchaseSuccess = _purchaseSuccess.asSharedFlow()

    init {
        loadConfig()
        viewModelScope.launch {
            billingRepository.purchaseSuccess.collect { _purchaseSuccess.emit(Unit) }
        }
        viewModelScope.launch {
            billingRepository.errorEvent.collect { _errorEvent.emit(it) }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = infoRepository.getAppConfig()) {
                is Resource.Success -> _config.value = result.data
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load pricing")
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun subscribe(activity: Activity) {
        val currentConfig = _config.value
        if (currentConfig != null) {
            billingRepository.launchBillingFlow(activity, currentConfig.productId)
        } else {
            viewModelScope.launch { _errorEvent.emit("Billing configuration not ready") }
        }
    }
}
