package com.example.mistreal_mini.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Only two stream types are accepted anywhere in the live-cam discovery
 * feed (CameraProfile.streamType, enforced server-side in cameraRoutes.ts)
 * because only two are actually confirmed playable in this app: a YouTube
 * embed (YoutubePlayerWebView, works today), and HLS via ExoPlayer — which
 * only became reliable once media3-exoplayer-hls was added as a dependency
 * for this feature; DefaultMediaSourceFactory auto-detects a .m3u8 URL and
 * delegates to it, so the existing VideoPlayer composable needs no changes.
 * RTSP/MJPEG are deliberately not supported — nothing in this app can play
 * them (confirmed: a prior hardcoded camera list's RTSP entry never worked).
 */
@Composable
fun CameraStreamPlayer(streamUrl: String, streamType: String, modifier: Modifier = Modifier) {
    when (streamType) {
        "youtube" -> {
            val videoId = extractYoutubeId(streamUrl)
            if (videoId != null) {
                YoutubePlayerWebView(videoId = videoId, modifier = modifier)
            } else {
                UnplayableStreamPlaceholder(modifier)
            }
        }
        "hls" -> VideoPlayer(videoUrl = streamUrl, modifier = modifier)
        else -> UnplayableStreamPlaceholder(modifier)
    }
}

@Composable
private fun UnplayableStreamPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        Text("Can't play this stream", color = Color.White)
    }
}

// Mirrors FeedPostCard.kt's private extractYoutubeId exactly — not shared
// since that one's private to its own file and this regex is short enough
// not to be worth threading a new shared-util export through just for this.
private fun extractYoutubeId(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val regex = "(?:youtube(?:-nocookie)?\\.com/(?:[^/]+/.+/|(?:v|e(?:mbed)?)/|.*[?&]v=)|youtu\\.be/)([^\"&?/\\s]{11})".toRegex()
    return regex.find(url)?.groupValues?.get(1) ?: if (url.length == 11) url else null
}
