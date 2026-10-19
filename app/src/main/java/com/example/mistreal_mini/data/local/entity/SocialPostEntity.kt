package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Covers getCachedPosts' ORDER BY and deleteOlderThan's WHERE clause (see
// Migration 16->17) — previously a full table scan on both.
@Entity(
    tableName = "social_posts",
    indices = [Index(value = ["fetchedAt"], name = "index_social_posts_fetchedAt")]
)
data class SocialPostEntity(
    @PrimaryKey val id: String,
    val platform: String,
    val author: String,
    val content: String,
    val timestamp: String,
    val type: String,
    val imageUrl: String?,
    val videoUrl: String?,
    val likes: Int?,
    val commentsCount: Int?,
    val sourceUrl: String?,
    val platformIcon: String,
    val platformColor: String,
    val platformDisplayName: String?,
    val isLikedByUser: Boolean = false,
    val isBookmarked: Boolean = false,
    val isCommunityPost: Boolean = false,
    val authorDeviceId: String? = null,
    val fetchedAt: Long
)
