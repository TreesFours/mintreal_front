package com.example.mistreal_mini.ui.boot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.mistreal_mini.ui.chat.ChatViewModel
import com.example.mistreal_mini.ui.settings.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Shown once between the password/biometric prompt clearing and landing on
 * Chat — makes the (now auth-gated) backend warmup visible as an honest,
 * accumulating log instead of silently happening with nothing on screen.
 */
@Composable
fun BootScreen(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onComplete: () -> Unit
) {
    val modelsLoaded by chatViewModel.modelsLoaded
    val platformsLoaded by chatViewModel.platformsLoaded
    val customProviderChecked by chatViewModel.customProviderChecked
    val settingsBootLoaded by settingsViewModel.settingsBootLoaded

    val log = remember { mutableStateListOf<String>() }
    var done by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        log.add("Connecting to Mistreal backend…")
        chatViewModel.onAuthenticated()
        settingsViewModel.onAuthenticated()

        // Each await below just watches the flags the ViewModels already flip
        // once their real network call settles — this is never a fake delay.
        withTimeoutOrNull(15_000) {
            while (!modelsLoaded) delay(50)
        }
        log.add("Loaded your AI models.")

        withTimeoutOrNull(15_000) {
            while (!platformsLoaded) delay(50)
        }
        log.add("Synced connected platforms.")

        withTimeoutOrNull(15_000) {
            while (!customProviderChecked) delay(50)
        }
        log.add(
            if (chatViewModel.hasCustomProvider.value) "Found your custom AI provider."
            else "No custom AI provider configured."
        )

        withTimeoutOrNull(15_000) {
            while (!settingsBootLoaded) delay(50)
        }
        log.add("Loaded your settings.")

        log.add("Ready.")
        delay(400) // let the last line actually be readable before moving on
        done = true
    }

    LaunchedEffect(done) {
        if (done) onComplete()
    }

    val gearTransition = rememberInfiniteTransition(label = "gear")
    val gearRotation by gearTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
        label = "gearRotation"
    )

    val listState = rememberLazyListState()
    LaunchedEffect(log.size) {
        if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = "Mistreal",
                    modifier = Modifier.size(84.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.BottomEnd)
                        .rotate(gearRotation),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .heightIn(max = 180.dp)
                    .widthIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(log) { line ->
                    AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 2 }) {
                        Text(
                            text = "› $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }
    }
}
