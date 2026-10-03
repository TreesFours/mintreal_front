package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import javax.inject.Inject

class HandleDistressUseCase @Inject constructor(
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper
) {
    /**
     * [broadcastToSocials] defaults false: the automatic audio-spike-detection
     * trigger (VoiceService) must never silently post a public SOS to real
     * social media on a false positive. Only the manual SOS button sets this
     * true, and only after the user explicitly confirms.
     */
    suspend operator fun invoke(
        deviceId: String,
        distressSignature: String = "Audio Spike Detected",
        broadcastToSocials: Boolean = false
    ): Resource<com.example.mistreal_mini.data.api.EmergencyAlertResponse> {
        val location = locationHelper.getCurrentLocation()
        return infoRepository.sendEmergencyAlert(
            deviceId = deviceId,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            distressSignature = distressSignature,
            broadcastToSocials = broadcastToSocials
        )
    }
}
