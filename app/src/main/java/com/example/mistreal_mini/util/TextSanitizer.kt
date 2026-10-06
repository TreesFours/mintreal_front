package com.example.mistreal_mini.util

object TextSanitizer {
    /**
     * Removes markdown symbols and extra whitespace for natural-sounding TTS.
     */
    fun sanitizeForTts(text: String): String {
        return text
            .replace(Regex("\\[.*?\\]"), "") // Remove tactical tags like [AI_MARKER] or [Astro]
            .replace(Regex("https?://\\S+"), "a link") // Reading a raw URL aloud is jarring
            .replace(Regex("(?m)^\\s*[-*•]\\s+"), "") // Bullet markers — read as run-on prose instead
            .replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), "") // "1. " / "2) " list numbering — same reason;
            // without this, TTS reads numbered lists as "one period, two period, ..." which is
            // exactly the "unnecessary numbers" robotic cadence this sanitizer exists to avoid.
            .replace(Regex("[*#_~`>]"), "") // Remove markdown
            .replace(Regex("\\(.*?\\)"), "") // Remove parentheses content (often citation or secondary info)
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
}
