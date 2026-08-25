package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "social_contacts")
data class SocialContactEntity(
    @PrimaryKey val contactId: String,
    val platform: String,
    val name: String,
    val avatarUrl: String?,
    val lastInteractionTime: Long,
    val isEmergency: Boolean = false,
    val platformUserId: String // Added for official Zernio ID mapping
)
