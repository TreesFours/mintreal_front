package com.example.mistreal_mini.ui.util

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.mistreal_mini.util.SfxHapticPlayer

/**
 * Provided once in MainActivity.setContent (the same Hilt singleton
 * instance app-wide). Defaults to null so Compose previews/tests that
 * don't provide one simply get silent no-op sound/haptic calls.
 */
val LocalSfxHapticPlayer = staticCompositionLocalOf<SfxHapticPlayer?> { null }
