package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import javax.inject.Inject

class HandleDistressUseCase @Inject constructor(
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper
) {
    suspend operator fun invoke(deviceId: String): Resource<Boolean> {
        val location = locationHelper.getCurrentLocation()
        return infoRepository.sendEmergencyAlert(
            deviceId = deviceId,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            distressSignature = "Audio Spike Detected"
        )
    }
}
