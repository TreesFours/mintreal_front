package com.example.mistreal_mini.data.model

data class SocialPost(
    val id: String,
    val platform: String,
    val author: String,
    val content: String,
    val timestamp: String,
    val type: String = "post", // "post", "message", "comment", "story", "reel"
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val likes: Int? = null,
    val commentsCount: Int? = null,
    val retweets: Int? = null,
    val sourceUrl: String? = null,
    val platformIcon: String, 
    val platformColor: String, 
    val platformDisplayName: String? = null,
    val comments: List<SocialComment>? = emptyList(),
    val isLikedByUser: Boolean = false,
    val isBookmarked: Boolean = false,
    // From another app user's Community Feed share, not this viewer's own
    // synced content — distinct from everything else above.
    val isCommunityPost: Boolean = false
) {
    fun fetchDisplayName(): String = platformDisplayName ?: platform.replaceFirstChar { 
        if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
    }
    
    fun getRelativeTime(): String {
        return try {
            val posted = java.time.Instant.parse(timestamp)
            val now = java.time.Instant.now()
            val diffMinutes = java.time.temporal.ChronoUnit.MINUTES.between(posted, now).toInt()
            
            when {
                diffMinutes < 1 -> "Just now"
                diffMinutes < 60 -> "$diffMinutes min ago"
                diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
                else -> "${diffMinutes / 1440}d ago"
            }
        } catch (e: Exception) {
            "Recent"
        }
    }
}

data class SocialComment(
    val id: String,
    val author: String,
    val text: String,
    val timestamp: String,
    val likes: Int = 0,
    val replies: List<SocialComment> = emptyList()
)

data class SocialSyncResponse(
    val summary: String,
    val posts: List<SocialPost>,
    val platformUpdates: List<PlatformUpdate>,
    val platformStatus: Map<String, String>? = null,
    val rawContent: String? = null,
    // Diagnostic reasons the feed came back empty/partial (bad Zernio profile,
    // platform returned nothing, sync call failed) — surfaced in the UI instead
    // of silently showing "No social intelligence found" with no explanation.
    val syncWarnings: List<String>? = null
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

data class PlatformCapabilities(
    val supportsFeed: Boolean = true,
    val supportsStories: Boolean = false,
    val supportsReels: Boolean = false,
    val supportsDM: Boolean = true,
    val supportsFollow: Boolean = false,
    val supportsLike: Boolean = true,
    val supportsComments: Boolean = true
)
