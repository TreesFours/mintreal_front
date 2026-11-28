package com.example.mistreal_mini.ui.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowserProxySettings(val type: String = "none", val host: String = "", val port: Int = 1080)

@HiltViewModel
class BrowserSettingsViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    val settings = combine(
        preferenceManager.browserProxyType,
        preferenceManager.browserProxyHost,
        preferenceManager.browserProxyPort
    ) { type, host, port -> BrowserProxySettings(type, host, port) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BrowserProxySettings())

    fun save(type: String, host: String, port: Int) {
        viewModelScope.launch {
            preferenceManager.setBrowserProxy(type, host, port)
        }
    }
}
