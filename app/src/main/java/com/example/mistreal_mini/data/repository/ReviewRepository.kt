package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CreateReviewRequest
import com.example.mistreal_mini.data.api.DeviceIdBody
import com.example.mistreal_mini.data.api.Review
import com.example.mistreal_mini.data.api.ReviewApiService
import com.example.mistreal_mini.data.api.ReviewInvite
import com.example.mistreal_mini.data.api.ReviewInviteRequest
import com.example.mistreal_mini.data.api.ReviewInviteRespondRequest
import com.example.mistreal_mini.data.api.ReviewReplyRequest
import com.example.mistreal_mini.data.api.ReviewThreadMessage
import com.example.mistreal_mini.data.api.ReviewThreadParticipant
import com.example.mistreal_mini.data.api.SocialStat
import com.example.mistreal_mini.util.DeviceIdProvider
import com.example.mistreal_mini.util.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ReviewThread(val messages: List<ReviewThreadMessage>, val participants: List<ReviewThreadParticipant>)

/**
 * Backs the review/reputation feature — a review about a user (shown from
 * their business page), held back from everyone but the reviewee until a
 * notice period passes (see reviewRoutes.ts), with a reply thread and
 * mutual-approval 3rd-party invites.
 */
@Singleton
class ReviewRepository @Inject constructor(
    private val api: ReviewApiService,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId

    suspend fun createReview(
        revieweeDeviceId: String,
        businessId: String?,
        contentText: String?,
        voiceUri: Uri?
    ): Resource<Review> {
        return try {
            val voiceBase64 = voiceUri?.let { FileUtil.uriToBase64(context, it) }
            val voiceMimeType = voiceUri?.let { FileUtil.getMimeType(context, it) }
            val response = api.createReview(
                CreateReviewRequest(
                    reviewerDeviceId = deviceId,
                    firebaseUid = authRepository.currentUser?.uid,
                    revieweeDeviceId = revieweeDeviceId,
                    businessId = businessId,
                    contentText = contentText,
                    voiceBase64 = voiceBase64,
                    voiceMimeType = voiceMimeType
                )
            )
            if (response.success && response.review != null) Resource.Success(response.review)
            else Resource.Error(response.error ?: "Failed to submit review")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to submit review")
        }
    }

    suspend fun getPendingReviews(): Resource<List<Review>> {
        return try {
            val response = api.getPendingReviews(deviceId)
            if (response.success) Resource.Success(response.reviews ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load pending reviews")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load pending reviews")
        }
    }

    suspend fun getReviews(revieweeDeviceId: String): Resource<List<Review>> {
        return try {
            val response = api.getReviews(revieweeDeviceId)
            if (response.success) Resource.Success(response.reviews ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load reviews")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load reviews")
        }
    }

    suspend fun hideReview(reviewId: Int): Resource<Unit> {
        return try {
            val response = api.hideReview(reviewId, DeviceIdBody(deviceId))
            if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to hide review")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to hide review")
        }
    }

    /** Permanent, unlike hideReview — requires a reason, shown as a tombstone in place of the original content from then on (see Review.deletedReason). */
    suspend fun deleteReview(reviewId: Int, reason: String): Resource<Review> {
        return try {
            val response = api.deleteReview(reviewId, com.example.mistreal_mini.data.api.DeleteReviewRequest(deviceId, reason))
            if (response.success && response.review != null) Resource.Success(response.review)
            else Resource.Error(response.error ?: "Failed to delete review")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to delete review")
        }
    }

    suspend fun getReviewThread(reviewId: Int): Resource<ReviewThread> {
        return try {
            val response = api.getReviewThread(reviewId)
            if (response.success) Resource.Success(ReviewThread(response.messages ?: emptyList(), response.participants ?: emptyList()))
            else Resource.Error(response.error ?: "Failed to load thread")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load thread")
        }
    }

    suspend fun replyToReview(reviewId: Int, contentText: String): Resource<ReviewThreadMessage> {
        return try {
            val response = api.replyToReview(reviewId, ReviewReplyRequest(deviceId, contentText))
            if (response.success && response.message != null) Resource.Success(response.message)
            else Resource.Error(response.error ?: "Failed to send reply")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send reply")
        }
    }

    suspend fun inviteToReviewThread(reviewId: Int, invitedDeviceId: String): Resource<ReviewInvite> {
        return try {
            val response = api.inviteToReviewThread(reviewId, ReviewInviteRequest(deviceId, invitedDeviceId))
            if (response.success && response.invite != null) Resource.Success(response.invite)
            else Resource.Error(response.error ?: "Failed to send invite")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to send invite")
        }
    }

    suspend fun respondToReviewInvite(reviewId: Int, inviteId: Int, approve: Boolean): Resource<Unit> {
        return try {
            val response = api.respondToReviewInvite(reviewId, inviteId, ReviewInviteRespondRequest(deviceId, approve))
            if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to respond to invite")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to respond to invite")
        }
    }

    suspend fun getSocialStats(targetDeviceId: String): Resource<List<SocialStat>> {
        return try {
            val response = api.getSocialStats(targetDeviceId)
            if (response.success) Resource.Success(response.stats ?: emptyList())
            else Resource.Error(response.error ?: "Stats unavailable")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Stats unavailable")
        }
    }

    fun myDeviceId(): String = deviceId
}
