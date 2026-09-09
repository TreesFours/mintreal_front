package com.example.mistreal_mini.ui.dashboard

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.repository.AuthRepository
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.data.repository.SensorRepository
import com.example.mistreal_mini.domain.usecase.GetIntelligenceFeedUseCase
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getIntelligenceFeedUseCase: GetIntelligenceFeedUseCase,
    private val infoRepository: InfoRepository,
    private val authRepository: AuthRepository,
    private val sensorRepository: SensorRepository,
    private val preferenceManager: com.example.mistreal_mini.data.local.PreferenceManager,
    val locationHelper: LocationHelper
) : ViewModel() {

    private val _weather = mutableStateOf<WeatherResponse?>(null)
    val weather: State<WeatherResponse?> = _weather

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _bearing = mutableStateOf(0f)
    val bearing: State<Float> = _bearing

    private val _orientation = mutableStateOf("N")
    val orientation: State<String> = _orientation

    private val _compassAccuracy = mutableStateOf(0)
    val compassAccuracy: State<Int> = _compassAccuracy

    private val _isCompassCalibrated = mutableStateOf(false)
    val isCompassCalibrated: State<Boolean> = _isCompassCalibrated

    private val _isCalibrationWizardVisible = mutableStateOf(false)
    val isCalibrationWizardVisible: State<Boolean> = _isCalibrationWizardVisible

    private val _calibrationProgress = mutableStateOf(0f)
    val calibrationProgress: State<Float> = _calibrationProgress

    private val _compassSupported = mutableStateOf(true)
    val compassSupported: State<Boolean> = _compassSupported

    val currentPersona = preferenceManager.aiPersona

    init {
        startSensorCollection()
    }

    private fun startSensorCollection() {
        viewModelScope.launch {
            sensorRepository.getOrientationFlow().collect { data ->
                _bearing.value = data.bearing
                _orientation.value = data.orientation
            }
        }
        viewModelScope.launch {
            sensorRepository.getAccuracyFlow().collect { accuracy ->
                _compassAccuracy.value = accuracy
                _isCompassCalibrated.value = accuracy >= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM
                
                if (_isCalibrationWizardVisible.value) {
                    _calibrationProgress.value = when (accuracy) {
                        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 1f
                        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.7f
                        android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.4f
                        else -> _calibrationProgress.value
                    }
                }
            }
        }
    }

    fun startSensors() {
        // Lifecycle management is now handled by Flow collection in viewModelScope
    }

    fun stopSensors() {
        // Flows will automatically be cancelled when viewModelScope is cleared
    }

    fun loadDashboardData(deviceId: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            val loc = locationHelper.getCurrentLocation()
            if (loc != null && deviceId != null) {
                infoRepository.updateUserSettings(
                    deviceId = deviceId,
                    userName = null,
                    aiPersona = null,
                    aiAudience = null,
                    autoReplyDelay = null,
                    guardianEnabled = null,
                    emergencyContacts = null
                )
                
                when (val result = getIntelligenceFeedUseCase.getWeather()) {
                    is Resource.Success -> _weather.value = result.data
                    else -> {}
                }
            }
            _isLoading.value = false
        }
    }

    fun toggleCalibrationWizard(visible: Boolean) {
        _isCalibrationWizardVisible.value = visible
        if (visible) _calibrationProgress.value = 0f
    }

    suspend fun fetchWeatherSummaryFor(place: String): String? {
        val geocoder = android.location.Geocoder(context)
        val addresses = try {
            geocoder.getFromLocationName(place, 1)
        } catch (e: Exception) {
            null
        }

        val addr = addresses?.firstOrNull() ?: return null
        return when (val result = infoRepository.getWeather(addr.latitude, addr.longitude)) {
            is Resource.Success -> result.data?.summary
            else -> null
        }
    }
}

