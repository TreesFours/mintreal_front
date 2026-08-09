// app/src/main/java/com/example/mistreal/data/models/SocialPost.kt
package com.example.mistreal.data.models

data class SocialPost(
    val id: String,
    val platform: String, // Dynamic platform ID from backend
    val author: String,
    val content: String,
    val timestamp: String, // ISO 8601 format
    val imageUrl: String? = null,
    val likes: Int? = null,
    val comments: Int? = null,
    val retweets: Int? = null,
    val sourceUrl: String? = null,
    val platformIcon: String, // Dynamic emoji or icon name
    val platformColor: String, // Dynamic Hex Color
    val platformDisplayName: String? = null // Optional override from backend
) {
    /**
     * Professional Dynamic Display Name Logic
     * Prioritizes backend-provided name, falls back to capitalized ID.
     */
    fun fetchDisplayName(): String = platformDisplayName ?: platform.replaceFirstChar { 
        if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
    }
    
    fun getRelativeTime(): String {
        val posted = java.time.Instant.parse(timestamp)
        val now = java.time.Instant.now()
        val diffMinutes = java.time.temporal.ChronoUnit.MINUTES.between(posted, now).toInt()
        
        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "$diffMinutes min ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }
}

data class SocialSyncResponse(
    val summary: String,
    val posts: List<SocialPost>,
    val platformUpdates: List<PlatformUpdate>,
    val platformStatus: Map<String, String>? = null,
    val rawContent: String? = null
)

data class PlatformUpdate(
    val platform: String,
    val count: Int,
    val recentMessage: String? = null,
    val platformIcon: String,
    val platformColor: String,
    val platformDisplayName: String? = null
)

data class SocialAuth(
    val authUrl: String,
    val deviceId: String? = null
)

data class SocialDisconnectRequest(
    val deviceId: String,
    val platform: String
)
