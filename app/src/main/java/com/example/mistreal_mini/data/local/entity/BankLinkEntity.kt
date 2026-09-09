package com.example.mistreal_mini.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bank_links")
data class BankLinkEntity(
    @PrimaryKey val id: String, // e.g. "zenith", "gtbank"
    val name: String,
    val url: String,
    val packageId: String?, // For launching the native app if installed
    val iconUrl: String? = null
)
