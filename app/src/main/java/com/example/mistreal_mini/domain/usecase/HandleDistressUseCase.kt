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
     * There is no public broadcast to connected social platforms anywhere
     * in this flow, client or server — the old 30-day "escalate to the
     * owner's own disconnected accounts" sweep this comment used to
     * describe was removed (see index.ts's own note on why: it helped no
     * one, since it posted to the MISSING person's accounts, not a
     * rescuer's). Visibility to non-contacts now works differently: a live,
     * read-time tiered check (nextCommunityBroadcastAt/fullDetailAt) on
     * GET /nearby-sos, not a one-time side effect fired from here. Fires
     * the alert immediately with just location rather than waiting on the
     * SOS ambient-audio recording (up to 120s) to finish first — the caller
     * attaches that audio afterward via InfoRepository.attachSosAudio once
     * it's actually done recording.
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
