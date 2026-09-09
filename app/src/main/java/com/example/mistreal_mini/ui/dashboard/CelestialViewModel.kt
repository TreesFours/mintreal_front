package com.example.mistreal_mini.ui.dashboard

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CelestialVectorResponse
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CelestialViewModel @Inject constructor(
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _celestialPositions = mutableStateListOf<CelestialVectorResponse>()
    val celestialPositions: List<CelestialVectorResponse> = _celestialPositions

    private val _trackedObjects = mutableStateListOf<CelestialObject>()
    val trackedObjects: List<CelestialObject> = _trackedObjects

    fun fetchCelestialData() {
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation()
            val bodies = listOf("10", "199", "299", "499", "599", "699", "301") // Sun, Merc, Venus, Mars, Jup, Sat, Moon
            _celestialPositions.clear()
            bodies.forEach { body ->
                when (val result = infoRepository.getCelestialVectors(body, loc?.latitude, loc?.longitude)) {
                    is Resource.Success -> result.data?.let { _celestialPositions.add(it) }
                    else -> {}
                }
            }
            updateTrackedObjects()
        }
    }

    private fun updateTrackedObjects() {
        _trackedObjects.clear()
        _celestialPositions.forEach { pos ->
            _trackedObjects.add(
                CelestialObject(
                    id = pos.body,
                    name = pos.name ?: pos.body,
                    type = if (pos.body == "301") "SATELLITE" else (if(pos.body == "10") "STAR" else "PLANET"),
                    azimuth = pos.azimuth?.toFloat() ?: 0f,
                    elevation = pos.elevation?.toFloat() ?: 0f,
                    status = pos.status ?: "VISIBLE",
                    orientation = pos.orientation ?: "N/A",
                    distEarth = pos.distEarth ?: "N/A",
                    distSun = pos.distSun ?: "N/A",
                    description = pos.description ?: "",
                    relativeToMoon = pos.relativeToMoon ?: ""
                )
            )
        }
    }
}

data class CelestialObject(
    val id: String,
    val name: String,
    val type: String,
    val azimuth: Float,
    val elevation: Float,
    val status: String,
    val orientation: String,
    val distEarth: String,
    val distSun: String,
    val description: String,
    val relativeToMoon: String
)
