package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scribe_notes")
data class ScribeNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val sourcePostId: String?,
    val sourcePlatform: String?,
    val sourceAuthor: String?,
    val sourceContent: String?,
    val aiAnalysis: String,
    val timestamp: Long = System.currentTimeMillis()
)
