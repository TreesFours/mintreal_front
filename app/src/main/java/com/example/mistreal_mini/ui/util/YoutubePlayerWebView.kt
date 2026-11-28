package com.example.mistreal_mini.ui.util

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The only ToS-compliant way to play a YouTube video in this app — YouTube's
 * Data API never hands back a direct streamable URL (and scraping one would
 * violate their terms), so this loads the official IFrame Player embed
 * instead of routing through ExoPlayer/FeedVideoPlayer like a normal video.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubePlayerWebView(videoId: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webChromeClient = WebChromeClient()
                // mediaPlaybackRequiresUserGesture=false above is an Android
                // WebView-level setting — the underlying Chromium engine still
                // enforces its own autoplay policy for audible content inside
                // the loaded page regardless, which silently blocked unmuted
                // autoplay here and left the embed showing a frozen first
                // frame (read as "can't play it directly"). Muted autoplay is
                // universally allowed; the embed's own controls let the user
                // unmute with one tap.
                loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&mute=1")
            }
        }
    )
}
