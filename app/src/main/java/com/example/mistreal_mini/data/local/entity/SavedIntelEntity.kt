package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_intel")
data class SavedIntelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val groupName: String?, // null = individual/ungrouped save
    val type: String, // "PIN", "SEARCH", "CIRCLE"
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Double? = null, // only for CIRCLE
    val bearing: Float? = null, // compass heading at time of save
    val discoveryResultsJson: String? = null, // Gson-serialized List<DiscoveryResult> found at this point
    val timestamp: Long = System.currentTimeMillis()
)
