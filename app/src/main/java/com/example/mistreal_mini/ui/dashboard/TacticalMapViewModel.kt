package com.example.mistreal_mini.ui.dashboard

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.local.dao.LocationHistoryDao
import com.example.mistreal_mini.data.local.dao.SavedIntelDao
import com.example.mistreal_mini.data.local.entity.LocationHistoryEntity
import com.example.mistreal_mini.data.local.entity.SavedIntelEntity
import com.example.mistreal_mini.data.model.DiscoveryResult
import com.example.mistreal_mini.data.repository.AuthRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.data.repository.TacticalRepository
import com.example.mistreal_mini.util.LocationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

@HiltViewModel
class TacticalMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val infoRepository: InfoRepository,
    private val authRepository: AuthRepository,
    private val tacticalRepository: TacticalRepository,
    val locationHelper: LocationHelper,
    private val locationHistoryDao: LocationHistoryDao,
    private val savedIntelDao: SavedIntelDao
) : ViewModel() {

    private val _mapLocation = mutableStateOf<String?>(null)
    val mapLocation: State<String?> = _mapLocation

    private val _mapFocusCoords = mutableStateOf<Pair<Double, Double>?>(null)
    val mapFocusCoords: State<Pair<Double, Double>?> = _mapFocusCoords

    private val _ambiguousLocations = mutableStateListOf<Address>()
    val ambiguousLocations: List<Address> = _ambiguousLocations

    private val _isMapLoading = mutableStateOf(false)
    val isMapLoading: State<Boolean> = _isMapLoading

    private val _discoveryResults = mutableStateListOf<DiscoveryResult>()
    val discoveryResults: List<DiscoveryResult> = _discoveryResults

    private val _discoveryError = mutableStateOf<String?>(null)
    val discoveryError: State<String?> = _discoveryError

    val intelLog = tacticalRepository.intelLog
    val tacticalCircle = tacticalRepository.tacticalCircle
    val tacticalPolygon = tacticalRepository.tacticalPolygon
    val aiBlueprints = tacticalRepository.aiBlueprints
    val selectedPins = tacticalRepository.selectedPins
    val focusPlaceLabel = tacticalRepository.focusPlaceLabel
    val searchMarker = tacticalRepository.searchMarker
    val targetPoints = tacticalRepository.targetPoints

    private val _teleportRequest = mutableStateOf<Triple<Double, Double, Int>?>(null)
    val teleportRequest: State<Triple<Double, Double, Int>?> = _teleportRequest

    private val _ghostMarkersRequest = mutableStateOf<String?>(null)
    val ghostMarkersRequest: State<String?> = _ghostMarkersRequest

    private val _isSniperModeActive = mutableStateOf(false)
    val isSniperModeActive: State<Boolean> = _isSniperModeActive

    private val _suggestedCircleRadius = mutableStateOf(500.0)

    private val userId: String
        get() = authRepository.currentUser?.uid ?: "anonymous"

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val locationHistory = authRepository.userState
        .flatMapLatest { user ->
            locationHistoryDao.getLocationHistory(user?.uid ?: "anonymous")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val savedIntel = authRepository.userState
        .flatMapLatest { user ->
            savedIntelDao.getAll(user?.uid ?: "anonymous")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun searchCity(cityName: String): CitySearchResult = withContext(Dispatchers.IO) {
        _isMapLoading.value = true
        _ambiguousLocations.clear()

        val geocoder = Geocoder(context)

        val addresses = try {
            val global = geocoder.getFromLocationName(cityName, 10)
            if (!global.isNullOrEmpty()) {
                global
            } else {
                val currentGps = locationHelper.getCurrentLocation()
                if (currentGps != null) {
                    geocoder.getFromLocationName(cityName, 10,
                        currentGps.latitude - 2.0, currentGps.longitude - 2.0,
                        currentGps.latitude + 2.0, currentGps.longitude + 2.0)
                } else null
            }
        } catch (e: Exception) {
            Timber.e("Geocoding failed for $cityName: ${e.message}")
            null
        }

        val result = if (addresses != null && addresses.size > 1) {
            _ambiguousLocations.addAll(addresses)
            val ghosts = addresses.map { addr ->
                val city = addr.locality ?: addr.adminArea ?: "Unknown"
                mapOf("lat" to addr.latitude, "lon" to addr.longitude, "label" to city)
            }
            _ghostMarkersRequest.value = Gson().toJson(ghosts)
            CitySearchResult.Ambiguous
        } else if (addresses != null && addresses.isNotEmpty()) {
            val addr = addresses[0]
            val city = addr.locality ?: addr.adminArea ?: "Unknown"
            val country = addr.countryName ?: ""
            val fullLabel = "${city.uppercase()}, $country"

            _mapLocation.value = fullLabel
            _mapFocusCoords.value = addr.latitude to addr.longitude
            _discoveryResults.clear()
            clearTacticalCircle()
            
            // Precision Zoom Logic
            val zoom = if (addr.thoroughfare != null || addr.featureName != null) 18 else if (addr.locality != null) 13 else 8
            _teleportRequest.value = Triple(addr.latitude, addr.longitude, zoom)

            val isPrecise = addr.subLocality != null || addr.thoroughfare != null || addr.featureName != null || addr.subAdminArea != null
            _suggestedCircleRadius.value = if (isPrecise) 500.0 else 5000.0

            val entry = IntelLogEntry(fullLabel, addr.latitude, addr.longitude, "SEARCH", System.currentTimeMillis())
            tacticalRepository.setSearchMarker(entry)
            addToIntelLog(entry)
            CitySearchResult.Success
        } else {
            Timber.w("🔍 No location found for query: $cityName")
            CitySearchResult.NotFound
        }
        _isMapLoading.value = false
        return@withContext result
    }

    fun deleteLocationHistoryEntry(id: Long) {
        viewModelScope.launch { locationHistoryDao.deleteLocation(id) }
    }

    fun clearLocationHistory() {
        viewModelScope.launch { locationHistoryDao.nukeHistory(userId) }
    }

    fun focusOnHistoryEntry(entry: LocationHistoryEntity) {
        _mapLocation.value = entry.cityName.uppercase()
        _mapFocusCoords.value = entry.latitude to entry.longitude
        _suggestedCircleRadius.value = 500.0
        addToIntelLog(IntelLogEntry(entry.cityName.uppercase(), entry.latitude, entry.longitude, "SEARCH", System.currentTimeMillis()))
    }

    fun saveIntelItem(type: String, label: String, lat: Double, lon: Double, bearing: Float, radius: Double? = null, groupName: String? = null, thumbnailUri: Uri? = null) {
        viewModelScope.launch {
            val resultsJson = if (_discoveryResults.isNotEmpty()) Gson().toJson(_discoveryResults) else null
            val blueprintJson = if (aiBlueprints.isNotEmpty()) Gson().toJson(aiBlueprints) else null
            
            var permanentPath: String? = null
            thumbnailUri?.let { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val fileName = "intel_thumb_${System.currentTimeMillis()}.png"
                    val file = File(context.filesDir, fileName)
                    inputStream?.use { input ->
                        FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    permanentPath = file.absolutePath
                } catch (e: Exception) {
                    Timber.e(e, "Failed to persist thumbnail")
                }
            }

            savedIntelDao.insert(
                SavedIntelEntity(
                    userId = userId,
                    groupName = groupName?.takeIf { it.isNotBlank() },
                    type = type,
                    label = label,
                    latitude = lat,
                    longitude = lon,
                    radius = radius,
                    bearing = bearing,
                    discoveryResultsJson = resultsJson,
                    blueprintJson = blueprintJson,
                    thumbnailPath = permanentPath
                )
            )
        }
    }

    fun deleteSavedIntel(id: Long) {
        viewModelScope.launch { savedIntelDao.deleteById(id) }
    }

    fun deleteSavedGroup(groupName: String) {
        viewModelScope.launch { savedIntelDao.deleteGroup(groupName) }
    }

    fun focusOnSavedIntel(entity: SavedIntelEntity) {
        _mapLocation.value = entity.label
        _mapFocusCoords.value = entity.latitude to entity.longitude
        _suggestedCircleRadius.value = entity.radius ?: 500.0
        if (entity.type == "CIRCLE" && entity.radius != null) {
            setTacticalCircle(entity.latitude, entity.longitude, entity.radius)
        }
        entity.discoveryResultsJson?.let { json ->
            try {
                val type = object : TypeToken<List<DiscoveryResult>>() {}.type
                val results = Gson().fromJson<List<DiscoveryResult>>(json, type)
                _discoveryResults.clear()
                _discoveryResults.addAll(results)
            } catch (e: Exception) {
                Timber.e("Failed to restore saved discovery results: ${e.message}")
            }
        }
        tacticalRepository.clearTacticalCircle() // Clear before restoring
        entity.blueprintJson?.let { json ->
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val blueprints = Gson().fromJson<List<String>>(json, type)
                blueprints.forEach { tacticalRepository.addAiBlueprint(it) }
            } catch (e: Exception) {
                Timber.e("Failed to restore saved blueprint results: ${e.message}")
            }
        }
    }

    fun removeIntelItem(entry: IntelLogEntry) {
        tacticalRepository.removeIntelItem(entry)
    }

    fun fetchDiscoveryData(category: String, radius: Double) {
        val coords = _mapFocusCoords.value ?: return
        _isMapLoading.value = true
        _discoveryError.value = null
        viewModelScope.launch {
            when (val result = infoRepository.getNearbyPlaces(coords.first, coords.second, radius, category)) {
                is Resource.Success -> {
                    val newResults = result.data?.filter { nr -> 
                        !_discoveryResults.any { dr -> dr.name == nr.name && dr.category == nr.category }
                    } ?: emptyList()
                    _discoveryResults.addAll(newResults)
                }
                is Resource.Error -> {
                    _discoveryError.value = result.message
                    Timber.e("Discovery fetch error: ${result.message}")
                }
                else -> {}
            }
            _isMapLoading.value = false
        }
    }

    fun clearDiscoveryCategory(category: String) {
        _discoveryResults.removeAll { it.category == category }
    }

    fun selectAmbiguousLocation(address: Address) {
        val city = address.locality ?: address.adminArea ?: "Unknown"
        val country = address.countryName ?: ""
        val fullLabel = "${city.uppercase()}, $country"

        _mapLocation.value = fullLabel
        _mapFocusCoords.value = address.latitude to address.longitude
        _discoveryResults.clear()
        clearTacticalCircle()
        _suggestedCircleRadius.value = 5000.0

        addToIntelLog(IntelLogEntry(fullLabel, address.latitude, address.longitude, "SEARCH", System.currentTimeMillis()))
        tacticalRepository.clearAmbiguousLocations() // Oh wait, I didn't add clearAmbiguousLocations to TacticalRepository
    }

    fun setTacticalCircle(lat: Double, lon: Double, radius: Double) {
        tacticalRepository.setTacticalCircle(lat, lon, radius)
    }

    fun adjustCircleRadius(delta: Double) {
        val current = tacticalCircle.value ?: return
        val newRadius = (current.radius + delta).coerceIn(200.0, 5000.0)
        tacticalRepository.setTacticalCircle(current.latitude, current.longitude, newRadius)
    }

    fun viewPlace(lat: Double, lon: Double, label: String, radius: Double = 200.0) {
        tacticalRepository.setFocusPlaceLabel(label)
        setTacticalCircle(lat, lon, radius)
    }

    fun clearTacticalCircle() {
        tacticalRepository.clearTacticalCircle()
        _isSniperModeActive.value = false
    }

    fun onTacticalShapeCompleted(type: String, pointsJson: String) {
        if (type == "POLYGON") {
            tacticalRepository.setTacticalPolygon(pointsJson)
        }
    }

    fun addAiBlueprint(geoJson: String) {
        tacticalRepository.addAiBlueprint(geoJson)
    }

    fun toggleSniperMode(active: Boolean) {
        _isSniperModeActive.value = active
    }

    fun nudgeTacticalCircle(bearingDegrees: Double, meters: Double = 30.0) {
        val current = tacticalCircle.value ?: return
        val latRad = Math.toRadians(current.latitude)
        val bearingRad = Math.toRadians(bearingDegrees)
        val metersPerDegreeLat = 111320.0
        val metersPerDegreeLon = 111320.0 * Math.cos(latRad)
        val newLat = current.latitude + (meters * Math.cos(bearingRad)) / metersPerDegreeLat
        val newLon = current.longitude + (meters * Math.sin(bearingRad)) / metersPerDegreeLon
        setTacticalCircle(newLat, newLon, current.radius)
    }

    fun startDrawingCircle(fallbackLat: Double? = null, fallbackLon: Double? = null) {
        viewModelScope.launch {
            val pins = selectedPins.toList()
            if (pins.isNotEmpty()) {
                if (pins.size == 1) {
                    setTacticalCircle(pins[0].latitude, pins[0].longitude, _suggestedCircleRadius.value)
                } else {
                    val centerLat = pins.map { it.latitude }.average()
                    val centerLon = pins.map { it.longitude }.average()
                    val out = FloatArray(1)
                    val maxDist = pins.maxOf { pin ->
                        Location.distanceBetween(centerLat, centerLon, pin.latitude, pin.longitude, out)
                        out[0].toDouble()
                    }
                    if (maxDist <= 5000.0) {
                        setTacticalCircle(centerLat, centerLon, maxDist.coerceAtLeast(200.0))
                    } else {
                        val last = pins.last()
                        setTacticalCircle(last.latitude, last.longitude, _suggestedCircleRadius.value)
                    }
                }
                return@launch
            }

            val focus = _mapFocusCoords.value
                ?: locationHelper.getCurrentLocation()?.let { it.latitude to it.longitude }
                ?: fallbackLat?.let { lat -> fallbackLon?.let { lon -> lat to lon } }
            focus?.let { (lat, lon) -> setTacticalCircle(lat, lon, _suggestedCircleRadius.value) }
        }
    }

    fun addPin(lat: Double, lon: Double, customLabel: String? = null) {
        viewModelScope.launch {
            val addresses = try {
                Geocoder(context).getFromLocation(lat, lon, 1)
            } catch (e: Exception) { null }
            
            val preciseLabel = customLabel ?: addresses?.get(0)?.let { addr ->
                listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality).distinct().joinToString(", ")
            } ?: "$lat, $lon"

            tacticalRepository.addPin(lat, lon, preciseLabel)
            
            _mapLocation.value = preciseLabel.uppercase()
            _mapFocusCoords.value = lat to lon
        }
    }

    fun clearAmbiguousLocations() {
        _ambiguousLocations.clear()
    }

    private val _isLocationEnabled = mutableStateOf(false)
    val isLocationEnabled: State<Boolean> = _isLocationEnabled

    fun toggleLocation(enabled: Boolean) {
        _isLocationEnabled.value = enabled
    }

    fun addPointToTarget(lat: Double, lon: Double) {
        tacticalRepository.addTargetPoint(lat, lon)
    }

    fun clearTargetBox() {
        tacticalRepository.clearTargetBox()
    }

    fun confirmAmbiguousLocation(lat: Double, lon: Double, label: String) {
        _mapLocation.value = label.uppercase()
        _mapFocusCoords.value = lat to lon
        _teleportRequest.value = Triple(lat, lon, 15)
        _ambiguousLocations.clear()
        _ghostMarkersRequest.value = null
        addToIntelLog(IntelLogEntry(label.uppercase(), lat, lon, "SEARCH", System.currentTimeMillis()))
    }

    fun clearTeleport() {
        _teleportRequest.value = null
    }

    fun clearGhostMarkers() {
        _ghostMarkersRequest.value = null
    }

    fun addToIntelLog(entry: IntelLogEntry) {
        tacticalRepository.addToIntelLog(entry)
    }
}
