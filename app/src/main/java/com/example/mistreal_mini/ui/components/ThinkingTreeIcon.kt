package com.example.mistreal_mini.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Thinking Tree Iconography (🌳) — replaces generic "Mistreal Tactical" text.
 * Features an animated pulsing/glow effect whenever the AI is thinking or processing.
 */
@Composable
fun ThinkingTreeIcon(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_tree")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(if (isThinking) scale else 1.0f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🌳",
            fontSize = (size.value * 0.75f).sp,
            fontWeight = FontWeight.Bold
        )
    }
}
