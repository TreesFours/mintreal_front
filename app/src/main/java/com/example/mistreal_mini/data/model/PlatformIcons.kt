package com.example.mistreal_mini.data.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Facebook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Canonical platform -> icon mapping, replacing what used to be six
 * independently-maintained (and mutually inconsistent) `when(platform)`
 * blocks across chat/settings/dashboard. Mirrors PlatformBrandColors'
 * forPlatform() shape; callers keep supplying their own fallback icon
 * for an unrecognized platform via `?:`, same as before.
 */
object PlatformIcons {
    private val ICONS: Map<String, ImageVector> = mapOf(
        "ai" to Icons.Default.SmartToy,
        "whatsapp" to Icons.Default.Chat,
        "twitter" to Icons.Default.Public,
        "x" to Icons.Default.Public,
        "linkedin" to Icons.Default.Business,
        "facebook" to Icons.Default.Facebook,
        "instagram" to Icons.Default.CameraAlt,
        "tiktok" to Icons.Default.MusicNote,
        "youtube" to Icons.Default.PlayCircle
    )

    fun forPlatform(platform: String): ImageVector? = ICONS[platform.lowercase()]
}
