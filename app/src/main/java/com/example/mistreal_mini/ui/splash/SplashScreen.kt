/** 🛡️ AI SYSTEM PROTOCOL 🛡️
 * SOURCE OF TRUTH: master_system_map.artifact.md
 * 
 * 🚀 FUNCTIONAL PIPELINE:
 * [Input]  <- App Start Event / Initialization
 * [Process] <- Renders animated Psychology icon and motto; triggers 3.5s delay
 * [Output] -> Calls onTimeout() to trigger navigation to Auth/Main flow
 *
 * ⚠️ MANDATORY: Never delete history. Only ADD updates/fixes to the Master Map table.
 */
package com.example.mistreal_mini.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val statusText by viewModel.statusText.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()

    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 1500),
        label = "Alpha"
    )
    val scaleAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1.2f else 1f,
        animationSpec = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
        label = "Scale"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // 🛡️ AI NOTE: If you overhaul or fix logic here, log it in the "History & Notes" column of the Master Map.
    LaunchedEffect(key1 = isReady) {
        startAnimation = true
        if (isReady) {
            delay(500)
            onTimeout()
        }
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
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Psychology,
                contentDescription = "Mistreal Logo",
                modifier = Modifier
                    .size(100.dp)
                    .scale(scaleAnim.value * if (isReady) 1f else pulseScale)
                    .alpha(alphaAnim.value),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "MISTREAL",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.alpha(alphaAnim.value)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Text(
                text = "\"If I'm powerful, I will make all what I want available to all like air.\"",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    lineHeight = 24.sp
                ),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(alphaAnim.value)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .alpha(alphaAnim.value)
            )

            if (!isReady) {
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(200.dp)
                        .height(2.dp)
                        .alpha(alphaAnim.value),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// 🛡️ AI CHECKPOINT: Did you update the Master System Map? If not, do it now.
