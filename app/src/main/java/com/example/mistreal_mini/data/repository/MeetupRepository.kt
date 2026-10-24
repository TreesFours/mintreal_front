package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.data.api.ConfirmMeetupRequest
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.Meetup
import com.example.mistreal_mini.data.api.ProposeMeetupRequest
import com.example.mistreal_mini.util.DeviceIdProvider
import com.example.mistreal_mini.util.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backs the Business-Hub/chat "propose a meetup" flow — deliberately not
 * continuous location tracking, just a shared plan both sides agree to and
 * a one-shot presence confirmation at the meeting itself (see
 * MeetupConfirmation server-side). Shares its escalation path with the
 * Guardian emergency system (a silently-unconfirmed meetup raises a real
 * EmergencyAlert after 10 days — see meetupRoutes.ts's sweepSilentMeetups),
 * rather than being a second, parallel safety mechanism.
 */
@Singleton
class MeetupRepository @Inject constructor(
    private val api: InfoApiService,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId

    suspend fun proposeMeetup(
        businessId: String?,
        counterpartyPlatform: String?,
        counterpartyContactId: String?,
        latitude: Double,
        longitude: Double,
        addressLabel: String?,
        scheduledAtIso: String,
        counterpartyDeviceId: String? = null,
        meetupType: String = "business"
    ): Resource<Meetup> = safeApiCall("Failed to propose meetup") {
        val response = api.proposeMeetup(
            ProposeMeetupRequest(
                deviceId = deviceId,
                firebaseUid = authRepository.currentUser?.uid,
                businessId = businessId,
                counterpartyPlatform = counterpartyPlatform,
                counterpartyContactId = counterpartyContactId,
                counterpartyDeviceId = counterpartyDeviceId,
                meetupType = meetupType,
                latitude = latitude,
                longitude = longitude,
                addressLabel = addressLabel,
                scheduledAt = scheduledAtIso
            )
        )
        if (response.success && response.meetup != null) Resource.Success(response.meetup)
        else Resource.Error(response.error ?: "Failed to propose meetup")
    }

    /** Called repeatedly (every few seconds) while a comfort meet's "I'm here" screen is open on both sides — see meetupRoutes.ts's comfort-ping. */
    suspend fun comfortMeetPing(meetupId: Int, latitude: Double, longitude: Double): Resource<com.example.mistreal_mini.data.api.ComfortMeetPingResponse> =
        safeApiCall("Failed to check proximity") {
            val response = api.comfortMeetPing(meetupId, com.example.mistreal_mini.data.api.ComfortMeetPingRequest(deviceId, latitude, longitude))
            if (response.success) Resource.Success(response) else Resource.Error(response.error ?: "Failed to check proximity")
        }

    suspend fun getComfortMeetCount(): Resource<Int> = safeApiCall("Failed to load count") {
        val response = api.getComfortMeetCount(deviceId)
        if (response.success) Resource.Success(response.count) else Resource.Error(response.error ?: "Failed to load count")
    }

    suspend fun getMyMeetups(): Resource<List<Meetup>> = safeApiCall("Failed to load meetups") {
        val response = api.getMeetups(deviceId)
        if (response.success) Resource.Success(response.meetups ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load meetups")
    }

    suspend fun confirmMeetup(
        meetupId: Int,
        latitude: Double,
        longitude: Double,
        outcome: String,
        reasonIfFailed: String?,
        reviewText: String?,
        buyerVote: String? = null,
        photoUri: Uri?
    ): Resource<Boolean> = safeApiCall("Failed to confirm meetup") {
        val photoBase64 = photoUri?.let { FileUtil.uriToBase64(context, it) }
        val photoMimeType = photoUri?.let { FileUtil.getMimeType(context, it) }
        val response = api.confirmMeetup(
            meetupId,
            ConfirmMeetupRequest(
                deviceId = deviceId,
                latitude = latitude,
                longitude = longitude,
                outcome = outcome,
                reasonIfFailed = reasonIfFailed,
                reviewText = reviewText,
                buyerVote = buyerVote,
                photoBase64 = photoBase64,
                photoMimeType = photoMimeType
            )
        )
        if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to confirm meetup")
    }
}
