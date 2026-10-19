package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Query

interface ReviewApiService {

    @POST("api/reviews")
    suspend fun createReview(@Body request: CreateReviewRequest): ReviewResponse

    @GET("api/reviews/pending")
    suspend fun getPendingReviews(@Query("deviceId") deviceId: String): ReviewListResponse

    @GET("api/reviews")
    suspend fun getReviews(@Query("revieweeDeviceId") revieweeDeviceId: String): ReviewListResponse

    @POST("api/reviews/{id}/hide")
    suspend fun hideReview(@Path("id") id: Int, @Body request: DeviceIdBody): GenericSuccessResponse

    @POST("api/reviews/{id}/delete")
    suspend fun deleteReview(@Path("id") id: Int, @Body request: DeleteReviewRequest): ReviewResponse

    @GET("api/reviews/{id}/thread")
    suspend fun getReviewThread(@Path("id") id: Int): ReviewThreadResponse

    @POST("api/reviews/{id}/reply")
    suspend fun replyToReview(@Path("id") id: Int, @Body request: ReviewReplyRequest): ReviewReplyResponse

    @POST("api/reviews/{id}/invite")
    suspend fun inviteToReviewThread(@Path("id") id: Int, @Body request: ReviewInviteRequest): ReviewInviteResponse

    @POST("api/reviews/{id}/invite/{inviteId}/respond")
    suspend fun respondToReviewInvite(
        @Path("id") id: Int,
        @Path("inviteId") inviteId: Int,
        @Body request: ReviewInviteRespondRequest
    ): GenericSuccessResponse

    @GET("api/social/social-stats")
    suspend fun getSocialStats(@Query("deviceId") deviceId: String): SocialStatsResponse
}

data class DeviceIdBody(val deviceId: String)

data class CreateReviewRequest(
    val reviewerDeviceId: String,
    val firebaseUid: String? = null,
    val revieweeDeviceId: String,
    val businessId: String? = null,
    val contentText: String? = null,
    val voiceBase64: String? = null,
    val voiceMimeType: String? = null
)

data class Review(
    val id: Int,
    val reviewerDeviceId: String,
    val revieweeDeviceId: String,
    val businessId: String? = null,
    // Both null when status == "deleted" — the server strips the original
    // content from every response at that point; deletedReason is what to
    // show instead.
    val contentText: String? = null,
    val voiceUrl: String? = null,
    val status: String, // "pending" | "published" | "hidden" | "deleted"
    val publishAt: String,
    val expiresAt: String? = null,
    val exchangeCount: Int = 0,
    val publiclyCommentable: Boolean = false,
    val deletedReason: String? = null,
    val createdAt: String? = null
)

data class DeleteReviewRequest(val deviceId: String, val reason: String)

data class ReviewResponse(val success: Boolean, val review: Review? = null, val error: String? = null)
data class ReviewListResponse(val success: Boolean, val reviews: List<Review>? = null, val error: String? = null)

data class ReviewThreadMessage(
    val id: Int,
    val reviewId: Int,
    val senderDeviceId: String,
    val contentText: String,
    val createdAt: String
)

data class ReviewThreadParticipant(
    val id: Int,
    val reviewId: Int,
    val deviceId: String,
    val role: String // "reviewer" | "reviewee" | "invited"
)

data class ReviewThreadResponse(
    val success: Boolean,
    val messages: List<ReviewThreadMessage>? = null,
    val participants: List<ReviewThreadParticipant>? = null,
    val error: String? = null
)

data class ReviewReplyRequest(val deviceId: String, val contentText: String)
data class ReviewReplyResponse(val success: Boolean, val message: ReviewThreadMessage? = null, val error: String? = null)

data class ReviewInviteRequest(val deviceId: String, val invitedDeviceId: String)
data class ReviewInvite(
    val id: Int,
    val reviewId: Int,
    val invitedDeviceId: String,
    val proposedByDeviceId: String,
    val status: String // "pending" | "approved" | "declined"
)
data class ReviewInviteResponse(val success: Boolean, val invite: ReviewInvite? = null, val error: String? = null)
data class ReviewInviteRespondRequest(val deviceId: String, val approve: Boolean)

data class SocialStat(
    val platform: String,
    val available: Boolean = false,
    val followers: Int? = null,
    val friends: Int? = null,
    val likes: Int? = null
)

data class SocialStatsResponse(val success: Boolean, val stats: List<SocialStat>? = null, val error: String? = null)
