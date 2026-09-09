package com.example.mistreal_mini.ui.dashboard

data class IntelLogEntry(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val type: String, // "PIN", "SEARCH", "CIRCLE"
    val timestamp: Long
)

data class TacticalCircle(
    val latitude: Double,
    val longitude: Double,
    val radius: Double
)

sealed class CitySearchResult {
    object Success : CitySearchResult()
    object Ambiguous : CitySearchResult()
    object NotFound : CitySearchResult()
}
