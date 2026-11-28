package com.example.mistreal_mini.ui.browser

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.example.mistreal_mini.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager
) : ViewModel() {

    // null = not yet attempted, true = applied (or deliberately "none"),
    // false = a proxy was configured but the device's WebView doesn't
    // support PROXY_OVERRIDE (older WebView build) — surfaced in the UI
    // rather than silently browsing unproxied when the user expects otherwise.
    private val _proxyApplied = MutableStateFlow<Boolean?>(null)
    val proxyApplied: StateFlow<Boolean?> = _proxyApplied.asStateFlow()

    fun applyProxyFromSettings() {
        viewModelScope.launch {
            val type = preferenceManager.browserProxyType.first()
            if (type == "none") {
                clearProxy()
                _proxyApplied.value = true
                return@launch
            }
            val host = preferenceManager.browserProxyHost.first()
            val port = preferenceManager.browserProxyPort.first()
            if (host.isBlank()) {
                _proxyApplied.value = true
                return@launch
            }
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                _proxyApplied.value = false
                return@launch
            }
            val scheme = if (type == "socks5") "socks5" else "http"
            val config = ProxyConfig.Builder()
                .addProxyRule("$scheme://$host:$port")
                .build()
            // setProxyOverride(config, executor, listener) — executor runs
            // the completion Runnable synchronously (no background thread
            // needed for this), listener is a no-op "done" callback.
            ProxyController.getInstance().setProxyOverride(config, { command -> command.run() }, {})
            _proxyApplied.value = true
        }
    }

    private fun clearProxy() {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
            ProxyController.getInstance().clearProxyOverride({ command -> command.run() }, {})
        }
    }
}
