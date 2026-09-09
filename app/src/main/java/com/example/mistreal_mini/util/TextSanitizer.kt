package com.example.mistreal_mini.util

object TextSanitizer {
    /**
     * Removes markdown symbols and extra whitespace for natural-sounding TTS.
     */
    fun sanitizeForTts(text: String): String {
        return text
            .replace(Regex("\\[.*?\\]"), "") // Remove tactical tags like [AI_MARKER] or [Astro]
            .replace(Regex("[*#_~`>]"), "") // Remove markdown
            .replace(Regex("\\(.*?\\)"), "") // Remove parentheses content (often citation or secondary info)
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
}
