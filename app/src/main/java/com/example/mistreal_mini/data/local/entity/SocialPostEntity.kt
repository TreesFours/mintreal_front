package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "social_posts")
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
    val fetchedAt: Long
)
