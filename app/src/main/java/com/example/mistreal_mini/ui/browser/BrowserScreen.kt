package com.example.mistreal_mini.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Real in-app browser: address/search bar + back/forward/reload, replacing
 * Chrome Custom Tabs for "journal" article links and anywhere else that
 * previously just handed off to the system browser with zero control.
 * Proxy (SOCKS5/HTTP) is applied per-WebView via androidx.webkit's
 * ProxyController, configured in BrowserSettingsScreen — no system VPN.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    initialUrl: String? = null,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val focusManager = LocalFocusManager.current
    var addressText by remember { mutableStateOf(initialUrl ?: "") }
    var currentUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val proxyApplied by viewModel.proxyApplied.collectAsState()

    LaunchedEffect(Unit) { viewModel.applyProxyFromSettings() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = addressText,
                            onValueChange = { addressText = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Search or enter address") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = {
                                focusManager.clearFocus()
                                webViewRef?.loadUrl(resolveAddressBarInput(addressText))
                            })
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close browser") }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Browser settings") }
                    }
                )
                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            BottomAppBar {
                IconButton(onClick = { webViewRef?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { webViewRef?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (proxyApplied != null) {
                    Text(
                        if (proxyApplied == true) "PROXY ON" else "PROXY ERROR",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
    ) { padding ->
        BrowserWebView(
            modifier = Modifier.padding(padding).fillMaxSize(),
            startUrl = resolveAddressBarInput(initialUrl ?: "https://www.google.com"),
            onWebViewReady = { webViewRef = it },
            onUrlChanged = { url, loading, back, forward ->
                currentUrl = url
                addressText = url
                isLoading = loading
                canGoBack = back
                canGoForward = forward
            }
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun BrowserWebView(
    modifier: Modifier = Modifier,
    startUrl: String,
    onWebViewReady: (WebView) -> Unit,
    onUrlChanged: (url: String, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) -> Unit
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        onUrlChanged(url ?: "", true, view.canGoBack(), view.canGoForward())
                    }
                    override fun onPageFinished(view: WebView, url: String?) {
                        onUrlChanged(url ?: "", false, view.canGoBack(), view.canGoForward())
                    }
                }
                onWebViewReady(this)
                loadUrl(startUrl)
            }
        }
    )
}

/** Bare host/domain-looking text ("example.com", "a search term") gets a
 * scheme or a Google search query; anything already URL-shaped is passed
 * through untouched — same "address bar doubles as search" behavior every
 * mobile browser has. */
private fun resolveAddressBarInput(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return "https://www.google.com"
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    val looksLikeDomain = trimmed.matches(Regex("^[\\w-]+(\\.[\\w-]+)+(/.*)?$")) && !trimmed.contains(" ")
    return if (looksLikeDomain) "https://$trimmed"
    else "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
}
