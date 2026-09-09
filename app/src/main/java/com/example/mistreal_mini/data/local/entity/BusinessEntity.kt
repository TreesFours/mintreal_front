package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "businesses")
data class BusinessEntity(
    @PrimaryKey val businessId: String,
    val ownerId: String,
    val name: String,
    val description: String?,
    val category: String, // Beach, Market, Street, Sport Centers, etc.
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val logoUrl: String?,
    val ownerImageUrl: String?,
    val verifiedTimestamp: Long,
    val connectedPlatforms: String? // JSON list of {platform, value}
)

@Entity(tableName = "business_items")
data class BusinessItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val businessId: String,
    val name: String,
    val price: String?,
    val imageUrl: String?,
    val timestamp: Long = System.currentTimeMillis()
)
