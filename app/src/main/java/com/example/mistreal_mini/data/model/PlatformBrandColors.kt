package com.example.mistreal_mini.data.model

import androidx.compose.ui.graphics.Color

/**
 * Brand colors for the drawer's platform icons — keep in sync with the
 * `color` values in backend/src/services/socialPlatforms/platformRegistry.ts.
 * Purely cosmetic (selected-state tint); unknown platforms fall back to the
 * theme's primary color rather than a hardcoded gray.
 */
object PlatformBrandColors {
    private val COLORS = mapOf(
        "twitter" to Color(0xFF1DA1F2),
        "x" to Color(0xFF1DA1F2),
        "whatsapp" to Color(0xFF25D366),
        "instagram" to Color(0xFFE4405F),
        "facebook" to Color(0xFF1877F2),
        "discord" to Color(0xFF5865F2),
        "telegram" to Color(0xFF0088CC),
        "reddit" to Color(0xFFFF4500),
        "linkedin" to Color(0xFF0A66C2),
        "tiktok" to Color(0xFF000000),
        "snapchat" to Color(0xFFFFFC00),
        "youtube" to Color(0xFFFF0000),
        "twitch" to Color(0xFF9146FF)
    )

    fun forPlatform(platform: String): Color? = COLORS[platform.lowercase()]
}
