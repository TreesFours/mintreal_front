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
     * Public broadcasting to real connected social platforms is no longer a
     * client-set flag here — it only ever happens server-side, once, after
     * 30 days with zero confirmed-contact response (see the Guardian
     * escalation sweep in index.ts). Fires the alert immediately with just
     * location rather than waiting on the SOS ambient-audio recording (up
     * to 120s) to finish first — the caller attaches that audio afterward
     * via InfoRepository.attachSosAudio once it's actually done recording.
     */
    suspend operator fun invoke(
        deviceId: String,
        distressSignature: String = "Audio Spike Detected",
        triggerType: String = "manual"
    ): Resource<com.example.mistreal_mini.data.api.EmergencyAlertFireResponse> {
        val location = locationHelper.getCurrentLocation()
        return infoRepository.fireEmergencyAlert(
            deviceId = deviceId,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            distressSignature = distressSignature,
            triggerType = triggerType
        )
    }
}
