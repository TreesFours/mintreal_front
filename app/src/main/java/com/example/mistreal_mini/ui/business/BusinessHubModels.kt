package com.example.mistreal_mini.ui.business

import com.example.mistreal_mini.data.api.SocialPlatformResponse

data class ConnectedPlatformInfo(
    val platform: String,
    val value: String // Phone number, username, etc.
)

data class BusinessHubState(
    val isSeller: Boolean = false,
    val isRegistered: Boolean = false,
    val verifiedToday: Boolean = false,
    val lastVerifiedTime: String? = null
)

val BusinessCategories = listOf(
    "Market",
    "Street",
    "Beach",
    "Sport Centers",
    "Service Center",
    "Tech Hub",
    "Restaurant/Cafe"
)
