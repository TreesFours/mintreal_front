package com.example.mistreal_mini.ui.games.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.mistreal_mini.ui.dashboard.components.ads.AdHostViewModel
import com.example.mistreal_mini.ui.dashboard.components.ads.AdMediaView

/**
 * Deliberately NOT AdSplitScreenHost — that pattern narrows content to 76%
 * width or expands to a fullscreen dialog, which is exactly the "covers the
 * game" experience this was asked to avoid. This is a fixed small strip
 * docked under the game surface, same AdHostViewModel/AdRepository pipeline
 * underneath (checkForDueAd polling, impression/engaged-click tracking),
 * just a far smaller, non-interruptive presentation.
 */
@Composable
fun GameAdStrip(modifier: Modifier = Modifier) {
    val adHostViewModel: AdHostViewModel = hiltViewModel()
    val dueAd by adHostViewModel.dueAd.collectAsState()
    val context = LocalContext.current

    val ad = dueAd ?: return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                adHostViewModel.trackEngagedClick(ad.id)
                if (!ad.targetUrl.isNullOrBlank()) {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ad.targetUrl))
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            AdMediaView(ad = ad, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text("Sponsored", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Text(ad.caption ?: ad.ctaLabel, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}
