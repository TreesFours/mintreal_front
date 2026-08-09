package com.example.mistreal_mini.ui.dashboard

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
import com.example.mistreal.data.models.PlatformUpdate
import com.example.mistreal.data.models.SocialPost
import com.example.mistreal.data.models.SocialSyncResponse
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.domain.usecase.GetIntelligenceFeedUseCase
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getIntelligenceFeedUseCase: GetIntelligenceFeedUseCase,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val infoRepository: InfoRepository,
    private val sensorManager: SensorManager,
    private val locationHelper: com.example.mistreal_mini.util.LocationHelper,
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

    private val _orientation = mutableStateOf("N")
    val orientation: State<String> = _orientation

    private val _bearing = mutableStateOf(0f)
    val bearing: State<Float> = _bearing

    private val _celestialPositions = mutableStateListOf<com.example.mistreal_mini.data.api.CelestialVectorResponse>()
    val celestialPositions: List<com.example.mistreal_mini.data.api.CelestialVectorResponse> = _celestialPositions

    private val _trackedObjects = mutableStateListOf<TrackedObject>()
    val trackedObjects: List<TrackedObject> = _trackedObjects

    // Sensor Fusion state
    private var accelerometerReading = FloatArray(3)
    private var magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun startSensors() {
        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI)
            Timber.d("🔋 Rotation vector sensor activated")
        } else {
            Timber.w("⚠️ TYPE_ROTATION_VECTOR not available, falling back to Accel+Mag")
            val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (accel != null && mag != null) {
                sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_UI)
                Timber.d("🔋 Accelerometer + magnetometer sensors activated")
            } else {
                Timber.e("❌ Critical sensors missing for compass")
            }
        }
    }

    fun stopSensors() {
        sensorManager.unregisterListener(this)
    }

    fun loadDashboardData(deviceId: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            when (val result = getIntelligenceFeedUseCase.getWeather()) {
                is Resource.Success -> {
                    _weather.value = result.data
                    if (result.data?.summary?.contains("unavailable", ignoreCase = true) == true) {
                        Timber.w("Weather API warning: ${result.data.summary}")
                    }
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
        
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
                val normalizedBearing = (azimuthDegrees + 360) % 360
                Timber.v("🧭 RotationVector Bearing: $normalizedBearing")
                updateBearing(normalizedBearing)
                return
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelerometerReading, 0, accelerometerReading.size)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
            }
            else -> return
        }

        if (SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthDegrees = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            val normalizedBearing = (azimuthDegrees + 360) % 360
            Timber.v("🧭 Accel/Mag Bearing: $normalizedBearing")
            updateBearing(normalizedBearing)
        }
    }

    private fun updateBearing(degree: Float) {
        _bearing.value = degree
        _orientation.value = when {
            degree >= 337.5 || degree < 22.5 -> "N"
            degree >= 22.5 && degree < 67.5 -> "NE"
            degree >= 67.5 && degree < 112.5 -> "E"
            degree >= 112.5 && degree < 157.5 -> "SE"
            degree >= 157.5 && degree < 202.5 -> "S"
            degree >= 202.5 && degree < 247.5 -> "SW"
            degree >= 247.5 && degree < 292.5 -> "W"
            degree >= 292.5 && degree < 337.5 -> "NW"
            else -> "N"
        }
    }

    private val _mapLocation = mutableStateOf<String?>(null)
    val mapLocation: State<String?> = _mapLocation

    fun searchCity(cityName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            // Resolve city to coordinates for the Tactical Map
            val loc = locationHelper.getCoordinates(cityName)
            if (loc != null) {
                _mapLocation.value = "${loc.latitude},${loc.longitude}"
            } else {
                _mapLocation.value = cityName // Fallback to name
            }
            _isLoading.value = false
        }
    }

    fun moveToObject(lat: Double, lon: Double) {
        _mapLocation.value = "$lat,$lon"
    }

    fun pinpointCurrentLocation() {
        viewModelScope.launch {
            _isLoading.value = true
            val loc = locationHelper.getCurrentLocation()
            if (loc != null) {
                val city = locationHelper.getCityName(loc.latitude, loc.longitude)
                _mapLocation.value = city ?: "${loc.latitude}, ${loc.longitude}"
            }
            _isLoading.value = false
        }
    }

    fun fetchCelestialData() {
        viewModelScope.launch {
            val bodies = listOf("10", "199", "299", "399", "499", "599", "699", "799", "899") // Sun, Mercury, Venus, Earth, Mars, Jupiter, Saturn, Uranus, Neptune
            bodies.forEach { id ->
                when (val result = infoRepository.getCelestialVectors(id)) {
                    is Resource.Success -> {
                        result.data?.let { response ->
                            // Convert vector to lat/lon for "Strategic Tactical Overlay"
                            val lat = Math.toDegrees(Math.asin(response.z / 1000.0)).coerceIn(-90.0, 90.0)
                            val lon = Math.toDegrees(Math.atan2(response.y, response.x)).coerceIn(-180.0, 180.0)
                            
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
                                else -> "Body $id"
                            }
                            
                            val type = if(id == "10") "STAR" else "PLANET"
                            
                            _trackedObjects.removeAll { it.id == id }
                            _trackedObjects.add(TrackedObject(id, name, type, lat, lon))
                        }
                    }
                    else -> {}
                }
            }
            // ISS Ground Track Simulation
            _trackedObjects.add(TrackedObject("ISS", "ISS (Zarya)", "SATELLITE", 51.64, -0.12, 420.0, "Tactical Orbital Intel"))
            // Hubble
            _trackedObjects.add(TrackedObject("HST", "Hubble Telescope", "SATELLITE", 28.5, -80.6, 540.0, "Astro Intelligence"))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

data class TrackedObject(
    val id: String,
    val name: String,
    val type: String, // "PLANET", "SATELLITE", "NEO"
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val info: String? = null
)
