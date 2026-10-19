package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A game the user has favorited in the Games section. */
@Entity(tableName = "pinned_games")
data class PinnedGameEntity(
    @PrimaryKey val gameId: String,
    val pinnedAt: Long = System.currentTimeMillis()
)
