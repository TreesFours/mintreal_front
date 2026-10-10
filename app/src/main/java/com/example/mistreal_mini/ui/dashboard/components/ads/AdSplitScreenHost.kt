package com.example.mistreal_mini.ui.dashboard.components.ads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.mistreal_mini.data.api.AdPayload
import com.example.mistreal_mini.ui.util.VideoPlayer
import kotlinx.coroutines.delay

/**
 * Wraps the social feed with a non-interruptive ad pane — the feed fills the
 * full width normally, and only narrows to make room on the side when an ad
 * is actually due (per-device paced, round-robin across businesses — see
 * adService.ts). Tapping the ad pane expands it to a one-shot fullscreen
 * play; the feed underneath is never paused or obscured either way.
 */
@Composable
fun AdSplitScreenHost(
    content: @Composable () -> Unit
) {
    val adHostViewModel: AdHostViewModel = hiltViewModel()
    val dueAd by adHostViewModel.dueAd.collectAsState()
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            adHostViewModel.checkForDueAd()
            delay(60_000)
        }
    }

    LaunchedEffect(dueAd) {
        dueAd?.let { ad ->
            adHostViewModel.trackImpression(ad.id)
            delay(ad.durationSeconds * 1000L)
            adHostViewModel.clearAd()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(if (dueAd != null) 0.76f else 1f)) {
            content()
        }
        dueAd?.let { ad ->
            Box(
                modifier = Modifier
                    .weight(0.24f)
                    .fillMaxHeight()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        adHostViewModel.trackEngagedClick(ad.id)
                        isFullscreen = true
                    }
            ) {
                AdMediaView(ad = ad, modifier = Modifier.fillMaxSize())
                Text(
                    ad.caption ?: "Sponsored",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }

    if (isFullscreen && dueAd != null) {
        val ad = dueAd!!
        Dialog(onDismissRequest = { isFullscreen = false }) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                AdMediaView(ad = ad, modifier = Modifier.fillMaxSize())
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                    ad.caption?.let { Text(it, color = Color.White, style = MaterialTheme.typography.titleMedium) }
                    if (!ad.targetUrl.isNullOrBlank()) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ad.targetUrl))
                            )
                        }) { Text(ad.ctaLabel) }
                    }
                }
            }
        }
    }
}

@Composable
fun AdMediaView(ad: AdPayload, modifier: Modifier = Modifier) {
    if (ad.mediaType == "video" && !ad.videoUrl.isNullOrBlank()) {
        VideoPlayer(videoUrl = ad.videoUrl, modifier = modifier)
    } else {
        val images = ad.imageUrls.orEmpty()
        if (images.isEmpty()) return
        var index by remember(ad.id) { mutableIntStateOf(0) }
        LaunchedEffect(ad.id) {
            val perImageMs = (ad.durationSeconds * 1000L / images.size).coerceAtLeast(800L)
            while (true) {
                delay(perImageMs)
                index = (index + 1) % images.size
            }
        }
        AsyncImage(
            model = images[index],
            contentDescription = ad.caption,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
