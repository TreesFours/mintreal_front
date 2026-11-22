package com.example.mistreal_mini.util

object TextSanitizer {
    /**
     * Removes markdown symbols and extra whitespace for natural-sounding TTS.
     */
    fun sanitizeForTts(text: String): String {
        return text
            // Markdown links [label](url) — must run before the generic bracket-tag
            // stripper below, which would otherwise eat "[label]" and leave the
            // url to get removed by the paren-strip too, erasing the link entirely
            // instead of reading its label.
            .replace(Regex("\\[([^\\[\\]]+)]\\(https?://\\S+?\\)"), "$1")
            .replace(Regex("\\[.*?\\]"), "") // Remove tactical tags like [AI_MARKER] or [Astro]
            .replace(Regex("https?://\\S+"), "a link") // Reading a raw URL aloud is jarring
            .replace(Regex("(?m)^\\s*[-*•]\\s+"), "") // Bullet markers — read as run-on prose instead
            .replace(Regex("(?m)^\\s*\\d+[.)]\\s+"), "") // "1. " / "2) " list numbering — same reason;
            // without this, TTS reads numbered lists as "one period, two period, ..." which is
            // exactly the "unnecessary numbers" robotic cadence this sanitizer exists to avoid.
            // Markdown headers — keep the heading text but give it a period, since
            // a heading was never a full sentence and would otherwise run straight
            // into the next line as one unnaturally long sentence.
            .replace(Regex("(?m)^#{1,6}\\s*(.+)$"), "$1.")
            .replace(Regex(":[a-zA-Z_]+:"), "") // Emoji shortcodes (:fire:, :joy:) — read literally otherwise
            .replace(Regex("[\\x{2190}-\\x{21FF}\\x{2300}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{1F000}-\\x{1FFFF}]"), "") // Unicode emoji — most TTS engines either skip inconsistently or read garbage for these
            .replace(Regex("[*#_~`>]"), "") // Remove remaining markdown
            .replace(Regex("\\(.*?\\)"), "") // Remove parentheses content (often citation or secondary info)
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()
    }
}
