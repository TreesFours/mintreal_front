package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_history")
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val cityName: String,
    val town: String? = null,
    val street: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
