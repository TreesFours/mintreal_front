package com.example.mistreal_mini.util

object TextSanitizer {
    /**
     * Removes markdown symbols and extra whitespace for natural-sounding TTS.
     */
    fun sanitizeForTts(text: String): String {
        return text
            .replace(Regex("[*#_~`>]"), "") // Remove markdown
            .replace(Regex("\\[.*?\\]\\(.*?\\)"), "") // Remove links
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
}
