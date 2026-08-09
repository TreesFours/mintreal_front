package com.example.mistreal_mini.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NukeIcon(modifier: Modifier = Modifier) {
    // 🤯 Professional "Mind Blown / Nuclear" Custom Icon
    // Using a layered approach to create the "Colorful Explosion" feel of the emoji
    Box(
        modifier = modifier
            .size(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // 1. The "Explosion" Blast (Colorful Background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFEB3B), // Yellow core
                            Color(0xFFFF9800), // Orange mid
                            Color(0xFFF44336), // Red edge
                            Color.Transparent
                        )
                    )
                )
        )

        // 2. The "Fire" / Mushroom Cloud top
        Icon(
            imageVector = Icons.Default.Whatshot,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-4).dp)
        )

        // 3. The "Head" (Face) at the bottom
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = null,
            tint = Color(0xFF5D4037), // Brownish face color
            modifier = Modifier
                .size(18.dp)
                .align(Alignment.BottomCenter)
        )
    }
}
