package com.example.mistreal_mini.ui.dashboard

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.Article
import com.example.mistreal_mini.data.model.DiscoveryResult
import com.example.mistreal.data.models.PlatformUpdate
import com.example.mistreal.data.models.SocialPost
import com.example.mistreal.data.models.SocialSyncResponse
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.domain.usecase.GetIntelligenceFeedUseCase
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getIntelligenceFeedUseCase: GetIntelligenceFeedUseCase,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val infoRepository: InfoRepository,
    private val sensorManager: SensorManager,
    val locationHelper: com.example.mistreal_mini.util.LocationHelper,
    private val locationHistoryDao: com.example.mistreal_mini.data.local.dao.LocationHistoryDao,
    private val savedIntelDao: com.example.mistreal_mini.data.local.dao.SavedIntelDao,
    private val savedStateHandle: SavedStateHandle
) : ViewModel(), SensorEventListener {

    private val _weather = mutableStateOf<WeatherResponse?>(null)
    val weather: State<WeatherResponse?> = _weather

    private val _newsArticles = mutableStateListOf<Article>()
    val newsArticles: List<Article> = _newsArticles

    private val _socialUpdates = mutableStateListOf<PlatformUpdate>()
    val socialUpdates: List<PlatformUpdate> = _socialUpdates

    private val _socialPosts = mutableStateListOf<SocialPost>()
    val socialPosts: List<SocialPost> = _socialPosts

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    // 🛡️ PROFESSIONAL REFORM: Step 1 - Consolidated Map State
    private val _mapState = mutableStateOf(TacticalMapState())
    val mapState: State<TacticalMapState> = _mapState

    private val _celestialPositions = mutableStateListOf<com.example.mistreal_mini.data.api.CelestialVectorResponse>()
    val celestialPositions: List<com.example.mistreal_mini.data.api.CelestialVectorResponse> = _celestialPositions

    private val _trackedObjects = mutableStateListOf<TrackedObject>()
    val trackedObjects: List<TrackedObject> = _trackedObjects

    // 📍 Discovery & History State
    val locationHistory = locationHistoryDao.getLocationHistory("current_user") // Simplified UID logic
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedIntel = savedIntelDao.getAll("current_user")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _discoveryResults = mutableStateListOf<DiscoveryResult>()
    val discoveryResults: List<DiscoveryResult> = _discoveryResults

    // 🛰️ Set when the last discovery fetch failed (e.g. Overpass mirrors all down) —
    // lets the UI say "search failed, try again" instead of falsely claiming "not found."
    private val _discoveryError = mutableStateOf<String?>(null)
    val discoveryError: State<String?> = _discoveryError

    private val _intelLog = mutableStateListOf<IntelLogEntry>()
    val intelLog: List<IntelLogEntry> = _intelLog

    private val _tacticalCircle = mutableStateOf<TacticalCircle?>(null)
    val tacticalCircle: State<TacticalCircle?> = _tacticalCircle

    // 🎯 Label for whatever the circle is currently centered on — set when "View" is
    // tapped on a specific discovery result, so the map can mark exactly which place
    // the satellite spotlight is showing, not just an unlabeled circle.
    private val _focusPlaceLabel = mutableStateOf<String?>(null)
    val focusPlaceLabel: State<String?> = _focusPlaceLabel

    private val _isCalibrationWizardVisible = mutableStateOf(false)
    val isCalibrationWizardVisible: State<Boolean> = _isCalibrationWizardVisible

    private val _calibrationProgress = mutableStateOf(0f)
    val calibrationProgress: State<Float> = _calibrationProgress

    private val _compassAccuracy = mutableStateOf(SensorManager.SENSOR_STATUS_UNRELIABLE)
    val compassAccuracy: State<Int> = _compassAccuracy

    private val _isLocationEnabled = mutableStateOf(true)
    val isLocationEnabled: State<Boolean> = _isLocationEnabled

    private val _searchMarker = mutableStateOf<IntelLogEntry?>(null)
    val searchMarker: State<IntelLogEntry?> = _searchMarker

    private val _selectedPins = mutableStateListOf<android.location.Location>()

    private val _bearing = mutableStateOf(0f)
    val bearing: State<Float> = _bearing

    private val _orientation = mutableStateOf("N")
    val orientation: State<String> = _orientation

    private val _isCompassCalibrated = mutableStateOf(false)
    val isCompassCalibrated: State<Boolean> = _isCompassCalibrated

    // 🧭 True until proven otherwise — set false only if the device genuinely has neither
    // a rotation-vector sensor nor an accelerometer+magnetometer pair, so the UI can say
    // "not supported on this device" instead of showing a wizard that can never succeed.
    private val _compassSupported = mutableStateOf(true)
    val compassSupported: State<Boolean> = _compassSupported

    // Sensor Fusion state
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var lastBearing = 0f
    private var usingRotationVectorPrimary = false
    private val smoothingFactor = 0.15f // 💡 Low-pass filter for smooth compass movement

    fun startSensors() {
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            usingRotationVectorPrimary = true
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
            Timber.d("🔋 Rotation vector sensor activated")

            // 🧭 The fused rotation-vector sensor rarely reports real onAccuracyChanged
            // transitions on many devices (its accuracy callback is manufacturer-dependent
            // and often just never fires again after the first report), which left the
            // calibration wizard permanently stuck. The raw magnetometer's accuracy DOES
            // update reliably as the device moves, so also listen to it — purely for that
            // accuracy signal, NOT for bearing (guarded by usingRotationVectorPrimary below).
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (mag != null) {
                sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                Timber.d("🔋 Magnetometer also activated (accuracy signal only)")
            }
        } else {
            usingRotationVectorPrimary = false
            Timber.w("⚠️ TYPE_ROTATION_VECTOR not available, falling back to Accel+Mag")
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null && mag != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                Timber.d("🔋 Accelerometer + magnetometer sensors activated")
            } else {
                _compassSupported.value = false
                Timber.e("❌ Critical sensors missing for compass — device has no rotation vector and no accel+mag pair")
            }
        }
    }

    fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    fun loadDashboardData(deviceId: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            // 1. Proactive Weather Sync
            val loc = locationHelper.getCurrentLocation()
            if (loc != null && deviceId != null) {
                // Inform backend of current location to update proactive cache
                infoRepository.updateUserSettings(
                    deviceId = deviceId,
                    userName = null,
                    aiPersona = null,
                    aiAudience = null,
                    autoReplyDelay = null,
                    guardianEnabled = null,
                    emergencyContacts = null
                )
                // Actually we should have a dedicated location update call in InfoRepository
                // For now, we fetch weather normally which also updates the backend cache
                when (val result = getIntelligenceFeedUseCase.getWeather()) {
                    is Resource.Success -> {
                        _weather.value = result.data
                    }
                    is Resource.Error -> {
                        _weather.value = WeatherResponse(
                            summary = result.message ?: "Failed to load environment data",
                            location = "Error",
                            rainExpected = false,
                            timeToRain = null
                        )
                    }
                    else -> {}
                }
            }

            // 2. Global Intelligence Feed (Rolling Buffer)
            when (val result = getIntelligenceFeedUseCase.getNews()) {
                is Resource.Success -> {
                    _newsArticles.clear()
                    result.data?.articles?.let { _newsArticles.addAll(it) }
                }
                is Resource.Error -> {
                    Timber.e("News fetch error: ${result.message}")
                }
                else -> {}
            }

            // 3. Social Intelligence Feed
            when (val result = syncSocialsUseCase(deviceId)) {
                is Resource.Success -> {
                    _socialUpdates.clear()
                    _socialPosts.clear()
                    result.data?.platformUpdates?.let { _socialUpdates.addAll(it) }
                    result.data?.posts?.let { _socialPosts.addAll(it) }
                }
                is Resource.Error -> {
                    Timber.e("Social sync error: ${result.message}")
                }
                else -> {}
            }
            _isLoading.value = false
        }
    }

    suspend fun postToSocial(deviceId: String, platform: String, type: String, content: String, targetId: String = "self"): Boolean {
        val result = infoRepository.performSocialAction(
            deviceId = deviceId,
            type = type,
            platform = platform,
            content = content,
            targetId = targetId
        )
        return result is Resource.Success
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        
        val rotationMatrixInternal = FloatArray(9)
        val adjustedRotationMatrix = FloatArray(9)

        // 🧭 Gentle motion feedback while calibrating — capped well below "complete".
        // Real completion is driven by onAccuracyChanged (genuine OS accuracy reports),
        // not by how many sensor events have streamed in.
        if (_isCalibrationWizardVisible.value && _compassAccuracy.value < SensorManager.SENSOR_STATUS_ACCURACY_HIGH) {
            _calibrationProgress.value = (_calibrationProgress.value + 0.003f).coerceAtMost(0.6f)
        }

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrixInternal, event.values)
                
                // 🧭 Remap coordinate system for UPRIGHT (Portrait) usage
                SensorManager.remapCoordinateSystem(
                    rotationMatrixInternal,
                    SensorManager.AXIS_X,
                    SensorManager.AXIS_Z,
                    adjustedRotationMatrix
                )
                
                SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles)
                val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val normalizedBearing = (azimuthDegrees + 360) % 360
                
                updateBearing(normalizedBearing)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            else -> return
        }

        // 🧭 Skip the accel+mag fallback bearing calc when rotation-vector is the primary
        // source — magnetometer is also registered in that case, but only for its
        // accuracy callback, not to double-compute (and fight with) the bearing.
        if (!usingRotationVectorPrimary && event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            SensorManager.getRotationMatrix(rotationMatrixInternal, null, accelerometerReading, magnetometerReading)) {
            
            SensorManager.remapCoordinateSystem(
                rotationMatrixInternal,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                adjustedRotationMatrix
            )
            
            SensorManager.getOrientation(adjustedRotationMatrix, orientationAngles)
            val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalizedBearing = (azimuthDegrees + 360) % 360
            
            updateBearing(normalizedBearing)
        }
    }

    private fun updateBearing(degree: Float) {
        // 🧪 Apply Low-Pass Filter (Smoothing)
        var diff = degree - lastBearing
        if (diff > 180) diff -= 360
        if (diff < -180) diff += 360
        
        val smoothedBearing = (lastBearing + smoothingFactor * diff + 360) % 360
        lastBearing = smoothedBearing

        _bearing.value = smoothedBearing
        _orientation.value = when {
            smoothedBearing >= 337.5 || smoothedBearing < 22.5 -> "N"
            smoothedBearing >= 22.5 && smoothedBearing < 67.5 -> "NE"
            smoothedBearing >= 67.5 && smoothedBearing < 112.5 -> "E"
            smoothedBearing >= 112.5 && smoothedBearing < 157.5 -> "SE"
            smoothedBearing >= 157.5 && smoothedBearing < 202.5 -> "S"
            smoothedBearing >= 202.5 && smoothedBearing < 247.5 -> "SW"
            smoothedBearing >= 247.5 && smoothedBearing < 292.5 -> "W"
            smoothedBearing >= 292.5 && smoothedBearing < 337.5 -> "NW"
            else -> "N"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Timber.d("🧭 onAccuracyChanged: sensor=${sensor?.type} name=${sensor?.name} accuracy=$accuracy")
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD || sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            _compassAccuracy.value = accuracy
            _isCompassCalibrated.value = accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
            if (_isCalibrationWizardVisible.value) {
                // 🧭 Wizard progress now reflects the OS's real accuracy report, not elapsed time.
                _calibrationProgress.value = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 1f
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.7f
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.4f
                    else -> _calibrationProgress.value
                }
            }
            if (!_isCompassCalibrated.value) {
                Timber.w("🧭 Compass needs calibration. Accuracy: $accuracy")
            }
        }
    }

    private val _mapLocation = mutableStateOf<String?>(null)
    val mapLocation: State<String?> = _mapLocation

    // 🛡️ Exact coordinates for wherever the map should focus. Kept separate from
    // _mapLocation (a display label) so the map never has to re-geocode a name or
    // mis-parse a comma-containing label ("City, Country") as raw lat,lon.
    private val _mapFocusCoords = mutableStateOf<Pair<Double, Double>?>(null)
    val mapFocusCoords: State<Pair<Double, Double>?> = _mapFocusCoords

    private val _ambiguousLocations = mutableStateListOf<android.location.Address>()
    val ambiguousLocations: List<android.location.Address> = _ambiguousLocations

    private val _isMapLoading = mutableStateOf(false)
    val isMapLoading: State<Boolean> = _isMapLoading

    // 🎯 Starter radius for the next drawn circle — small/precise by default, bumped to
    // the max spotlight size when the current focus came from a broad (country/region-level)
    // search, since a small circle would be meaningless for something that size.
    private val _suggestedCircleRadius = mutableStateOf(500.0)

    suspend fun searchCity(cityName: String): CitySearchResult {
        _isMapLoading.value = true
        _ambiguousLocations.clear()

        val geocoder = android.location.Geocoder(context)

        // 🌍 Global search is authoritative — a region-biased box was previously tried
        // FIRST and silently forced every query (even "Anchorage Alaska") to match
        // something within ~220km of the user's current GPS, returning nonsense results
        // for anywhere outside that box. Only fall back to region bias (helps disambiguate
        // short, genuinely local names) when the unbiased global search finds nothing.
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
            CitySearchResult.Ambiguous
        } else if (addresses != null && addresses.isNotEmpty()) {
            val addr = addresses[0]
            val city = addr.locality ?: addr.adminArea ?: "Unknown"
            val country = addr.countryName ?: ""
            val fullLabel = "${city.uppercase()}, $country"

            _mapLocation.value = null
            _mapLocation.value = fullLabel
            _mapFocusCoords.value = addr.latitude to addr.longitude
            // 🛡️ New search area — the previous area's discovery labels (and any satellite
            // spotlight circle, e.g. left over from tapping "View" on a result) no longer
            // apply here and were blocking the new search from visually taking effect.
            _discoveryResults.clear()
            clearTacticalCircle()

            // 🎯 Precise (street/POI-level) vs broad (country/region-level) result —
            // a broad match gets the full spotlight cap since a small circle on it is meaningless.
            val isPrecise = addr.subLocality != null || addr.thoroughfare != null || addr.featureName != null || addr.subAdminArea != null
            _suggestedCircleRadius.value = if (isPrecise) 500.0 else 5000.0

            val entry = IntelLogEntry(fullLabel, addr.latitude, addr.longitude, "SEARCH", System.currentTimeMillis())
            _searchMarker.value = entry

            addToIntelLog(fullLabel, addr.latitude, addr.longitude, "SEARCH")
            CitySearchResult.Success
        } else {
            Timber.w("🔍 No location found for query: $cityName")
            CitySearchResult.NotFound
        }
        _isMapLoading.value = false
        return result
    }

    fun deleteLocationHistoryEntry(id: Long) {
        viewModelScope.launch {
            locationHistoryDao.deleteLocation(id)
        }
    }

    fun clearLocationHistory() {
        viewModelScope.launch {
            locationHistoryDao.nukeHistory("current_user")
        }
    }

    fun focusOnHistoryEntry(entry: com.example.mistreal_mini.data.local.entity.LocationHistoryEntity) {
        _mapLocation.value = null
        _mapLocation.value = entry.cityName.uppercase()
        _mapFocusCoords.value = entry.latitude to entry.longitude
        _suggestedCircleRadius.value = 500.0
        addToIntelLog(entry.cityName.uppercase(), entry.latitude, entry.longitude, "SEARCH")
    }

    // 🔖 Saved Intel — bookmark a pin/search/circle (optionally into a named group)
    // for later quick recall, bundling whatever discovery results were found there.
    fun saveIntelItem(type: String, label: String, lat: Double, lon: Double, radius: Double? = null, groupName: String? = null) {
        viewModelScope.launch {
            val resultsJson = if (_discoveryResults.isNotEmpty()) com.google.gson.Gson().toJson(_discoveryResults) else null
            savedIntelDao.insert(
                com.example.mistreal_mini.data.local.entity.SavedIntelEntity(
                    userId = "current_user",
                    groupName = groupName?.takeIf { it.isNotBlank() },
                    type = type,
                    label = label,
                    latitude = lat,
                    longitude = lon,
                    radius = radius,
                    bearing = _bearing.value,
                    discoveryResultsJson = resultsJson
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

    fun focusOnSavedIntel(entity: com.example.mistreal_mini.data.local.entity.SavedIntelEntity) {
        _mapLocation.value = null
        _mapLocation.value = entity.label
        _mapFocusCoords.value = entity.latitude to entity.longitude
        _suggestedCircleRadius.value = entity.radius ?: 500.0
        if (entity.type == "CIRCLE" && entity.radius != null) {
            setTacticalCircle(entity.latitude, entity.longitude, entity.radius)
        }
        entity.discoveryResultsJson?.let { json ->
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<DiscoveryResult>>() {}.type
                val results = com.google.gson.Gson().fromJson<List<DiscoveryResult>>(json, type)
                _discoveryResults.clear()
                _discoveryResults.addAll(results)
            } catch (e: Exception) {
                Timber.e("Failed to restore saved discovery results: ${e.message}")
            }
        }
    }

    fun removeIntelItem(entry: IntelLogEntry) {
        if (_searchMarker.value == entry) _searchMarker.value = null
        _selectedPins.removeAll { it.latitude == entry.latitude && it.longitude == entry.longitude }
        _intelLog.remove(entry)
        // 🛡️ Discovery labels (and any satellite spotlight circle) are scoped to the pin/
        // search that was focused when they were fetched — leaving them on the map after
        // their pin is gone made it look like the clear did nothing.
        _discoveryResults.clear()
        clearTacticalCircle()
    }

    fun toggleLocation(enabled: Boolean) {
        _isLocationEnabled.value = enabled
        // 🛡️ Previously this only flipped the flag — updateGpsLocation's own flyTo was
        // removed earlier (it was firing on every recomposition, fighting with the user's
        // pan/zoom). That left the toggle with no way to actually recenter on GPS anymore.
        // Recenter explicitly, once, right when the user turns it on.
        if (enabled) {
            viewModelScope.launch {
                val gps = locationHelper.getCurrentLocation()
                gps?.let { _mapFocusCoords.value = it.latitude to it.longitude }
            }
        }
    }

    fun adjustCircleRadius(delta: Double) {
        val current = _tacticalCircle.value ?: return
        val newRadius = (current.radius + delta).coerceIn(200.0, 5000.0)
        _tacticalCircle.value = current.copy(radius = newRadius)
        fetchDiscoveryData("Satellite Focus")
    }

    fun setTacticalCircle(lat: Double, lon: Double, radius: Double) {
        _tacticalCircle.value = TacticalCircle(lat, lon, radius)
        fetchDiscoveryData("Satellite Focus")
    }

    // 🎯 Same as setTacticalCircle, but also labels exactly what the spotlight is centered
    // on — used by the SCAN tab's "View" action so the satellite view isn't just an
    // unmarked circle with no indication of which building/place it actually is.
    fun viewPlace(lat: Double, lon: Double, label: String, radius: Double = 200.0) {
        _focusPlaceLabel.value = label
        setTacticalCircle(lat, lon, radius)
    }

    fun clearTacticalCircle() {
        _tacticalCircle.value = null
        _focusPlaceLabel.value = null
    }

    // 🕹️ Small, precise nudges (fixed real-world distance regardless of latitude) instead
    // of relying only on long-press-elsewhere, which risks a big accidental jump.
    fun nudgeTacticalCircle(bearingDegrees: Double, meters: Double = 30.0) {
        val current = _tacticalCircle.value ?: return
        val latRad = Math.toRadians(current.latitude)
        val bearingRad = Math.toRadians(bearingDegrees)
        val metersPerDegreeLat = 111320.0
        val metersPerDegreeLon = 111320.0 * Math.cos(latRad)
        val newLat = current.latitude + (meters * Math.cos(bearingRad)) / metersPerDegreeLat
        val newLon = current.longitude + (meters * Math.sin(bearingRad)) / metersPerDegreeLon
        setTacticalCircle(newLat, newLon, current.radius)
    }

    // 🎯 Drops a ready-to-use starter circle at the current focus point instead of
    // requiring a drag-to-draw gesture on the map (which would fight with pin/zoom).
    // The +/- "ADJUST SCOPE" controls widen/shrink it from there.
    fun startDrawingCircle(fallbackLat: Double? = null, fallbackLon: Double? = null) {
        viewModelScope.launch {
            val pins = _selectedPins.toList()
            if (pins.isNotEmpty()) {
                if (pins.size == 1) {
                    setTacticalCircle(pins[0].latitude, pins[0].longitude, _suggestedCircleRadius.value)
                } else {
                    // Multiple pins: bound them in one circle if they're close together;
                    // if they're too far apart to sensibly share a spotlight, just center
                    // on the most recently placed one instead of drawing a giant, misleading circle.
                    val centerLat = pins.map { it.latitude }.average()
                    val centerLon = pins.map { it.longitude }.average()
                    val out = FloatArray(1)
                    val maxDist = pins.maxOf { pin ->
                        android.location.Location.distanceBetween(centerLat, centerLon, pin.latitude, pin.longitude, out)
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

            // 🎯 "Freelance" placement — nothing pinned, searched, or GPS'd yet: fall back to
            // wherever the map is currently centered on screen (reported by the WebView).
            val focus = _mapFocusCoords.value
                ?: locationHelper.getCurrentLocation()?.let { it.latitude to it.longitude }
                ?: fallbackLat?.let { lat -> fallbackLon?.let { lon -> lat to lon } }
            focus?.let { (lat, lon) -> setTacticalCircle(lat, lon, _suggestedCircleRadius.value) }
        }
    }

    fun toggleCalibrationWizard(visible: Boolean) {
        Timber.d("🧭 Calibration wizard ${if (visible) "opened" else "closed"} — current accuracy=${_compassAccuracy.value}, usingRotationVectorPrimary=$usingRotationVectorPrimary")
        _isCalibrationWizardVisible.value = visible
        if (visible) _calibrationProgress.value = 0f
    }

    fun addPin(lat: Double, lon: Double) {
        viewModelScope.launch {
            val addresses = try {
                android.location.Geocoder(context).getFromLocation(lat, lon, 1)
            } catch (e: Exception) { null }
            
            val preciseLabel = addresses?.get(0)?.let { addr ->
                listOfNotNull(addr.thoroughfare, addr.subLocality, addr.locality).distinct().joinToString(", ")
            } ?: "$lat, $lon"

            val loc = android.location.Location("manual").apply {
                latitude = lat
                longitude = lon
            }
            _selectedPins.add(loc)
            
            // 📜 Add to Intel Log
            addToIntelLog(preciseLabel, lat, lon, "PIN")
            
            // Update header to show precise focus
            _mapLocation.value = preciseLabel.uppercase()
            _mapFocusCoords.value = lat to lon
            _suggestedCircleRadius.value = 500.0

            Timber.d("📍 Manual Pin Added: $preciseLabel. Total pins: ${_selectedPins.size}")
        }
    }

    private fun addToIntelLog(label: String, lat: Double, lon: Double, type: String) {
        _intelLog.add(0, IntelLogEntry(
            label = label,
            latitude = lat,
            longitude = lon,
            type = type,
            timestamp = System.currentTimeMillis()
        ))
        // Limit Intel Log to 50 entries
        if (_intelLog.size > 50) _intelLog.removeAt(_intelLog.size - 1)
    }

    fun clearPins() {
        _selectedPins.clear()
        _discoveryResults.clear()
        clearTacticalCircle()
    }

    fun selectAmbiguousLocation(address: android.location.Address) {
        val name = address.locality ?: address.adminArea ?: address.countryName ?: "Unknown"
        _mapLocation.value = "${name.uppercase()}, ${address.countryCode ?: ""}"
        _mapFocusCoords.value = address.latitude to address.longitude
        _ambiguousLocations.clear()
        // 🛡️ New area — previous area's discovery labels and spotlight circle no longer apply.
        _discoveryResults.clear()
        clearTacticalCircle()
    }

    fun clearAmbiguousLocations() {
        _ambiguousLocations.clear()
    }

    fun moveToObject(lat: Double, lon: Double) {
        viewModelScope.launch {
            _isMapLoading.value = true
            val city = locationHelper.getCityName(lat, lon)
            _mapLocation.value = city ?: "$lat,$lon"
            _mapFocusCoords.value = lat to lon
            _isMapLoading.value = false
        }
    }

    fun pinpointCurrentLocation() {
        viewModelScope.launch {
            _isMapLoading.value = true
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                // 🏙️ Get detailed address
                val addresses = android.location.Geocoder(context).getFromLocation(loc.latitude, loc.longitude, 1)
                val city = addresses?.get(0)?.locality ?: addresses?.get(0)?.adminArea ?: "Unknown Area"
                val street = addresses?.get(0)?.thoroughfare ?: addresses?.get(0)?.subLocality
                val town = addresses?.get(0)?.subAdminArea

                _mapLocation.value = city.uppercase() // Standardize to UPPERCASE
                _mapFocusCoords.value = loc.latitude to loc.longitude
                _suggestedCircleRadius.value = 500.0
                _discoveryResults.clear()
                clearTacticalCircle()

                // 📍 Save to history with details
                locationHistoryDao.insertLocation(
                    com.example.mistreal_mini.data.local.entity.LocationHistoryEntity(
                        userId = "current_user",
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        cityName = city,
                        town = town,
                        street = street,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            _isMapLoading.value = false
        }
    }

    fun clearDiscoveryCategory(category: String) {
        _discoveryResults.removeAll { it.category == category }
    }

    fun fetchDiscoveryData(category: String, explicitRadius: Double? = null) {
        viewModelScope.launch {
            _isMapLoading.value = true
            // 🗂️ Multi-select categories: only replace this category's own results,
            // leave every other currently-active category's results untouched.
            _discoveryResults.removeAll { it.category == category }

            val pointsOfOrigin = mutableListOf<Pair<android.location.Location, String>>()

            // 1. Tactical Circle (Highest Priority)
            val circle = _tacticalCircle.value
            if (circle != null) {
                val l = android.location.Location("circle").apply {
                    latitude = circle.latitude
                    longitude = circle.longitude
                }
                pointsOfOrigin.add(l to "Circle Area")
            }

            // 2. Current Map Focus — use the real resolved coordinates directly, never
            // re-parse/re-geocode the display label (that was silently sending searched
            // "City, Country" labels to (0,0) and re-geocoding bare names on every call).
            val focusCoords = _mapFocusCoords.value
            if (focusCoords != null && circle == null) {
                val l = android.location.Location("focus").apply {
                    latitude = focusCoords.first
                    longitude = focusCoords.second
                }
                pointsOfOrigin.add(l to (_mapLocation.value ?: "Map Focus"))
            }

            // 3. Manual Pins
            _selectedPins.forEach { pin ->
                pointsOfOrigin.add(pin to "Pin Marker")
            }

            if (pointsOfOrigin.isEmpty()) {
                val gps = locationHelper.getCurrentLocation()
                gps?.let { pointsOfOrigin.add(it to "Your Position") }
            }

            // 🔎 Government/civic buildings especially are sparse — 1km often found nothing.
            // Default bumped to 3km; callers (e.g. the INTEL radius slider) can override.
            val radius = (explicitRadius ?: circle?.radius ?: 3000.0).coerceIn(200.0, 5000.0)

            _discoveryError.value = null
            pointsOfOrigin.forEach { (loc, label) ->
                when (val result = infoRepository.getNearbyPlaces(loc.latitude, loc.longitude, radius, category)) {
                    is Resource.Success -> {
                        result.data?.let { _discoveryResults.addAll(it) }
                    }
                    is Resource.Error -> {
                        Timber.e("Discovery fetch error for $label: ${result.message}")
                        _discoveryError.value = result.message
                    }
                    else -> {}
                }
            }
            _isMapLoading.value = false
        }
    }

    // 🌦️ "Ask AI about weather anywhere" — geocodes the named place and fetches its real
    // current conditions, so the AI answers from actual data instead of guessing from
    // training-data memory. Returns null if the place can't be resolved or the lookup fails.
    suspend fun fetchWeatherSummaryFor(placeName: String): String? {
        val geo = locationHelper.getCoordinates(placeName) ?: return null
        val result = infoRepository.getWeather(geo.latitude, geo.longitude)
        val weather = (result as? Resource.Success)?.data ?: return null
        return "Real-time weather data for $placeName: ${weather.summary}"
    }

    fun fetchCelestialData() {
        viewModelScope.launch {
            // Sun, Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune, Moon, Pluto.
            // 🌙 Moon (301) and Pluto (999) added — same standard Horizons major-body ID
            // convention as the rest, so no query-format changes needed. True asteroid
            // tracking (e.g. Ceres) uses a different small-body ID syntax ("1;") that this
            // simple bodyId passthrough doesn't support yet, and artificial satellites
            // (ISS, Starlink, etc.) aren't in JPL Horizons at all — that needs a separate
            // TLE-based data source (e.g. Celestrak) entirely.
            val bodies = listOf("10", "199", "299", "399", "499", "599", "699", "799", "899", "301", "999")
            bodies.forEach { id ->
                when (val result = infoRepository.getCelestialVectors(id)) {
                    is Resource.Success -> {
                        result.data?.let { response ->
                            val name = when(id) {
                                "10" -> "Sun"
                                "199" -> "Mercury"
                                "299" -> "Venus"
                                "399" -> "Earth"
                                "499" -> "Mars"
                                "599" -> "Jupiter"
                                "699" -> "Saturn"
                                "799" -> "Uranus"
                                "899" -> "Neptune"
                                "301" -> "Moon"
                                "999" -> "Pluto"
                                else -> "Body $id"
                            }

                            val type = when (id) {
                                "10" -> "STAR"
                                "301" -> "MOON"
                                else -> "PLANET"
                            }
                            
                            _trackedObjects.removeAll { it.id == id }
                            _trackedObjects.add(TrackedObject(
                                id = id, 
                                name = name, 
                                type = type, 
                                azimuth = response.azimuth ?: 0.0,
                                orientation = response.orientation ?: "N",
                                status = response.status ?: "Unknown"
                            ))
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

data class TrackedObject(
    val id: String,
    val name: String,
    val type: String, // "STAR", "PLANET", "SATELLITE"
    val azimuth: Double,
    val orientation: String,
    val status: String
)

data class IntelLogEntry(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val type: String, // "SEARCH", "PIN"
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
