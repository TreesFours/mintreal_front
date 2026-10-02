package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A minichat the user has explicitly marked to never auto-expire, regardless of inactivity. */
@Entity(tableName = "pinned_trends")
data class PinnedTrendEntity(
    @PrimaryKey val trendTitle: String,
    val pinnedAt: Long = System.currentTimeMillis()
)
