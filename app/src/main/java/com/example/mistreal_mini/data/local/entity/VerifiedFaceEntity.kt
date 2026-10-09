package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A face registered for use as a swap target — the core safeguard for the
 * face-swap feature. [imagePath] must point at a photo captured live via the
 * in-app camera at registration time (never a gallery pick), so a face can
 * only enter this list if whoever it belongs to was physically present and
 * chose to go through the capture flow themselves. Stored device-local only
 * (no backend sync) — biometric-adjacent data stays off the server.
 */
@Entity(tableName = "verified_faces")
data class VerifiedFaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)
