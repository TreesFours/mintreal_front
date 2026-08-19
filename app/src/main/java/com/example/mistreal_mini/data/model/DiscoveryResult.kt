package com.example.mistreal_mini.data.model

data class DiscoveryResult(
    val name: String,
    val address: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val rating: String? = null,
    val priceRange: String? = null
)
