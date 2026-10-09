package com.example.mistreal_mini.ui.dashboard

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CelestialVectorResponse
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

    private var autoRefreshJob: Job? = null

    fun fetchCelestialData() {
        viewModelScope.launch { refreshOnce() }
    }

    // Called from the Celestial/Orbital screen's lifecycle so positions genuinely
    // update while it's open, instead of only on a manual refresh tap — one
    // fetch cycle (11 sequential JPL Horizons calls) is slow enough that we wait
    // for it to finish before scheduling the next, rather than firing on a raw timer.
    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshOnce()
                delay(60_000)
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private suspend fun refreshOnce() {
        val loc = locationHelper.getCurrentLocation()
        // Earth/Uranus/Neptune/Pluto were already being fetched by a separate
        // 30-min background worker (CelestialWorker) but the results were
        // discarded rather than ever stored/surfaced — tracking them here
        // directly instead, alongside the original seven bodies.
        val bodies = listOf("10", "199", "299", "399", "499", "599", "699", "799", "899", "301", "999") // Sun, Merc, Venus, Earth, Mars, Jup, Sat, Uranus, Neptune, Moon, Pluto
        _celestialPositions.clear()
        bodies.forEach { body ->
            val result = infoRepository.getCelestialVectors(body, loc?.latitude, loc?.longitude)
            // The backend returns HTTP 200 even when JPL Horizons is down (so Retrofit
            // never throws), with success:false and every numeric field null — check
            // that flag explicitly rather than trusting the Resource wrapper alone,
            // otherwise a failed body would render with fabricated-looking defaults.
            val data = (result as? Resource.Success)?.data
            if (data?.success == true) {
                _celestialPositions.add(data)
            } else {
                _celestialPositions.add(
                    CelestialVectorResponse(
                        success = false,
                        body = body,
                        name = BODY_NAMES[body] ?: body,
                        status = "Unavailable"
                    )
                )
            }
        }
        updateTrackedObjects()
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
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
                    status = pos.status ?: "Unavailable",
                    orientation = pos.orientation ?: "N/A",
                    distEarth = pos.distEarth ?: "N/A",
                    distSun = pos.distSun ?: "N/A",
                    description = pos.description ?: "Live position data is temporarily unavailable.",
                    relativeToMoon = pos.relativeToMoon ?: "",
                    isLive = pos.success
                )
            )
        }
    }

    companion object {
        private val BODY_NAMES = mapOf(
            "10" to "Sun", "199" to "Mercury", "299" to "Venus", "399" to "Earth",
            "499" to "Mars", "599" to "Jupiter", "699" to "Saturn", "799" to "Uranus",
            "899" to "Neptune", "301" to "Moon", "999" to "Pluto"
        )
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
    val relativeToMoon: String,
    val isLive: Boolean = true
)
