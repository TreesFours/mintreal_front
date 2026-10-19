package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A minichat where the user has turned on delayed AI replies (paced by the Mission Delay/Ghost Mode minutes setting). Presence = enabled, same convention as pinned_trends. */
@Entity(tableName = "delayed_trends")
data class DelayedTrendEntity(
    @PrimaryKey val trendTitle: String,
    val enabledAt: Long = System.currentTimeMillis()
)
