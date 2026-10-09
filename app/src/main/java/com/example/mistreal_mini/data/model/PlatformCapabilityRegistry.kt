package com.example.mistreal_mini.data.model

/**
 * Client-side mirror of the backend's platformRegistry.ts capability facts, so the
 * feed UI can gate affordances (follow/like/comment buttons) even before the
 * `/api/social/platforms` response has loaded, or if it's missing the field.
 * Keep in sync with backend/src/services/socialPlatforms/platformRegistry.ts.
 */
object PlatformCapabilityRegistry {
    private val FULL_SOCIAL = PlatformCapabilities(
        supportsFeed = true, supportsDM = true, supportsLike = true, supportsComments = true
    )
    private val DM_ONLY = PlatformCapabilities(
        supportsFeed = false, supportsDM = true, supportsLike = false, supportsComments = false
    )
    private val READ_ONLY_FEED = PlatformCapabilities(
        supportsFeed = true, supportsReels = true, supportsDM = false, supportsLike = false, supportsComments = false
    )

    private val DEFINITIONS: Map<String, PlatformCapabilities> = mapOf(
        "twitter" to FULL_SOCIAL.copy(supportsFollow = true),
        "x" to FULL_SOCIAL.copy(supportsFollow = true),
        "whatsapp" to DM_ONLY,
        "instagram" to FULL_SOCIAL.copy(supportsStories = true, supportsReels = true),
        "facebook" to FULL_SOCIAL.copy(supportsStories = true),
        "discord" to FULL_SOCIAL.copy(supportsLike = false),
        "telegram" to DM_ONLY.copy(supportsFeed = true, supportsComments = true),
        "reddit" to FULL_SOCIAL.copy(supportsFollow = true),
        "linkedin" to FULL_SOCIAL,
        "tiktok" to READ_ONLY_FEED,
        "snapchat" to DM_ONLY.copy(supportsFeed = true, supportsStories = true),
        "youtube" to READ_ONLY_FEED.copy(supportsComments = true, supportsNativeUpload = true),
        "twitch" to READ_ONLY_FEED.copy(supportsComments = true)
    )

    fun forPlatform(platform: String): PlatformCapabilities =
        DEFINITIONS[platform.lowercase()] ?: PlatformCapabilities()
}
