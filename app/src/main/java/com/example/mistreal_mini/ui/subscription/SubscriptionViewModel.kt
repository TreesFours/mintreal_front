package com.example.mistreal_mini.ui.subscription

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.SubscriptionRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val api: InfoApiService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    fun subscribe(tier: String) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                val response = api.createStripeSession(SubscriptionRequest(tier))
                if (response.success && response.url != null) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(response.url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                // Handle error (show toast or log)
            } finally {
                _isLoading.value = false
            }
        }
    }
}
