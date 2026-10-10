package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.ConfirmMeetupRequest
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.Meetup
import com.example.mistreal_mini.data.api.ProposeMeetupRequest
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
    @ApplicationContext private val context: Context
) {
    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    suspend fun proposeMeetup(
        businessId: String?,
        counterpartyPlatform: String?,
        counterpartyContactId: String?,
        latitude: Double,
        longitude: Double,
        addressLabel: String?,
        scheduledAtIso: String
    ): Resource<Meetup> {
        return try {
            val response = api.proposeMeetup(
                ProposeMeetupRequest(
                    deviceId = deviceId,
                    firebaseUid = authRepository.currentUser?.uid,
                    businessId = businessId,
                    counterpartyPlatform = counterpartyPlatform,
                    counterpartyContactId = counterpartyContactId,
                    latitude = latitude,
                    longitude = longitude,
                    addressLabel = addressLabel,
                    scheduledAt = scheduledAtIso
                )
            )
            if (response.success && response.meetup != null) Resource.Success(response.meetup)
            else Resource.Error(response.error ?: "Failed to propose meetup")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to propose meetup")
        }
    }

    suspend fun getMyMeetups(): Resource<List<Meetup>> {
        return try {
            val response = api.getMeetups(deviceId)
            if (response.success) Resource.Success(response.meetups ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load meetups")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load meetups")
        }
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
    ): Resource<Boolean> {
        return try {
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
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to confirm meetup")
        }
    }
}
