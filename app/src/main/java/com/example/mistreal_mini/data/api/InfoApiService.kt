package com.example.mistreal_mini.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.mistreal_mini.data.model.SocialSyncResponse

interface InfoApiService {
    @GET("api/weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("deviceId") deviceId: String? = null,
        @Query("firebaseUid") firebaseUid: String? = null
    ): WeatherResponse

    @GET("api/news")
    suspend fun getNews(
        @Query("category") category: String?,
        @Query("location") location: String?,
        @Query("firebaseUid") firebaseUid: String? = null,
        @Query("fastLoad") fastLoad: Boolean? = false
    ): NewsResponse

    @PATCH("api/social/community-preferences")
    suspend fun setCommunityFeedPreferences(@Body request: CommunityPreferencesRequest): SocialActionResponse

    @GET("api/feed/youtube")
    suspend fun getYoutubeVideos(): YoutubeFeedResponse

    @GET("api/intel/location-photo")
    suspend fun getLocationPhoto(
        @Query("label") label: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): LocationPhotoResponse

    @POST("api/intel/pin")
    suspend fun pinIntel(@Body request: PinIntelRequest): Map<String, Boolean>

    @POST("api/intel/unpin")
    suspend fun unpinIntel(@Body request: PinIntelRequest): Map<String, Boolean>
    
    @GET("api/social/platforms")
    suspend fun getAvailablePlatforms(@Query("deviceId") deviceId: String?): List<SocialPlatformResponse>

    @GET("api/social/sync")
    suspend fun syncSocials(@Query("deviceId") deviceId: String?): SocialSyncResponse

    @POST("api/social/disconnect/{platform}")
    suspend fun disconnectPlatform(
        @Path("platform") platform: String,
        @Body request: Map<String, String>
    ): SocialDisconnectResult

    @POST("api/social/action")
    suspend fun performSocialAction(@Body request: SocialActionRequest): SocialActionResponse

    @POST("api/social/init-connection")
    suspend fun initiateConnection(@Body request: Map<String, String>): Map<String, Any>

    @POST("api/user/settings")
    suspend fun updateUserSettings(@Body request: UserSettingsRequest): SocialActionResponse

    @GET("api/user/settings")
    suspend fun getUserSettings(@Query("deviceId") deviceId: String?): UserSettingsResponse

    @GET("api/config")
    suspend fun getAppConfig(): AppConfigResponse

    @POST("api/payment/verify")
    suspend fun verifyPayment(@Body request: PaymentVerifyRequest): PaymentVerifyResponse

    @GET("api/payment/my-addons")
    suspend fun getMyAddons(@Query("deviceId") deviceId: String): MyAddonsResponse

    @POST("api/user/location")
    suspend fun updateLocation(@Body request: LocationRequest): SocialActionResponse

    @POST("api/emergency/alerts")
    suspend fun fireEmergencyAlert(@Body request: FireEmergencyAlertRequest): EmergencyAlertFireResponse

    @GET("api/emergency/alerts")
    suspend fun getEmergencyAlerts(@Query("deviceId") deviceId: String): EmergencyAlertsListResponse

    @POST("api/emergency/alerts/{id}/resolve")
    suspend fun resolveEmergencyAlert(@Path("id") id: Int, @Body body: Map<String, String>): EmergencyActionResponse

    @PATCH("api/emergency/alerts/{id}/audio")
    suspend fun attachSosAudio(@Path("id") id: Int, @Body request: AttachSosAudioRequest): EmergencyActionResponse

    @GET("api/emergency/contacts")
    suspend fun getEmergencyContacts(@Query("deviceId") deviceId: String): EmergencyContactsListResponse

    @POST("api/emergency/contacts")
    suspend fun addEmergencyContact(@Body request: AddEmergencyContactRequest): EmergencyContactResponse

    @DELETE("api/emergency/contacts/{id}")
    suspend fun deleteEmergencyContact(@Path("id") id: Int, @Query("deviceId") deviceId: String): EmergencyActionResponse

    @POST("api/email/send")
    suspend fun sendEmail(@Body request: SendEmailRequest): SendEmailResponse

    @POST("api/business/register")
    suspend fun registerBusiness(@Body request: RegisterBusinessRequest): SocialActionResponse

    @GET("api/business/search")
    suspend fun searchBusinessesRemote(
        @Query("category") category: String?,
        @Query("city") city: String?
    ): BusinessSearchResponse

    @GET("api/business/{businessId}")
    suspend fun getBusinessDetail(@Path("businessId") businessId: String): BusinessDetailResponse

    @POST("api/business/{businessId}/owner-photo")
    suspend fun uploadOwnerPhoto(@Path("businessId") businessId: String, @Body request: UploadOwnerPhotoRequest): UploadOwnerPhotoResponse

    @GET("api/business/{businessId}/can-share")
    suspend fun canShareBusiness(@Path("businessId") businessId: String, @Query("deviceId") deviceId: String): CanShareResponse

    @GET("api/ads/business/{businessId}")
    suspend fun getAdForBusiness(@Path("businessId") businessId: String): BusinessAdResponse

    @POST("api/meetups")
    suspend fun proposeMeetup(@Body request: ProposeMeetupRequest): MeetupResponse

    @GET("api/meetups")
    suspend fun getMeetups(@Query("deviceId") deviceId: String): MeetupsListResponse

    @POST("api/meetups/{id}/confirm")
    suspend fun confirmMeetup(@Path("id") id: Int, @Body request: ConfirmMeetupRequest): MeetupConfirmResponse

    @Multipart
    @POST("api/ads")
    suspend fun createAd(
        @Part("deviceId") deviceId: RequestBody,
        @Part("firebaseUid") firebaseUid: RequestBody?,
        @Part("businessId") businessId: RequestBody,
        @Part("mediaType") mediaType: RequestBody,
        @Part("durationSeconds") durationSeconds: RequestBody,
        @Part("caption") caption: RequestBody?,
        @Part("targetUrl") targetUrl: RequestBody?,
        @Part("ctaLabel") ctaLabel: RequestBody?,
        @Part video: MultipartBody.Part?,
        @Part images: List<MultipartBody.Part>?
    ): CreateAdResponse

    @GET("api/ads/due")
    suspend fun getDueAd(@Query("deviceId") deviceId: String): AdDueResponse

    @POST("api/ads/{adId}/track")
    suspend fun trackAdEvent(@Path("adId") adId: Int, @Body request: AdEventRequest): SocialActionResponse

    @GET("api/email/history")
    suspend fun getEmailHistory(
        @Query("deviceId") deviceId: String,
        @Query("toEmail") toEmail: String
    ): EmailHistoryResponse

    @GET("api/email/contacts")
    suspend fun getEmailContacts(@Query("deviceId") deviceId: String): EmailContactsResponse

    @GET("api/social/contacts")
    suspend fun getContacts(
        @Query("deviceId") deviceId: String,
        @Query("platform") platform: String,
        @Query("search") search: String? = null
    ): ContactsResponse

    @POST("api/social/contacts/auto-reply")
    suspend fun setContactAutoReply(@Body request: ContactAutoReplyRequest): SocialActionResponse

    @GET("api/social/unread")
    suspend fun getUnreadMessages(@Query("deviceId") deviceId: String): UnreadResponse

    @GET("api/social/history")
    suspend fun getSocialHistory(
        @Query("deviceId") deviceId: String,
        @Query("platform") platform: String,
        @Query("targetId") targetId: String
    ): SocialHistoryResponse

    @GET("api/celestial/vectors")
    suspend fun getCelestialVectors(
        @Query("bodyId") bodyId: String,
        @Query("lat") lat: Double?,
        @Query("lon") lon: Double?
    ): CelestialVectorResponse

    @GET("api/banks/channels")
    suspend fun getBankChannels(@Query("deviceId") deviceId: String): BankChannelsResponse

    @GET("api/discovery/nearby")
    suspend fun getNearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Double,
        @Query("category") category: String
    ): DiscoveryNearbyResponse

    @Multipart
    @POST("api/social/youtube/upload")
    suspend fun uploadYoutubeVideo(
        @Part("deviceId") deviceId: RequestBody,
        @Part("title") title: RequestBody,
        @Part("description") description: RequestBody?,
        @Part("privacyStatus") privacyStatus: RequestBody,
        @Part video: MultipartBody.Part
    ): YoutubeUploadResponse

    @GET("api/social/youtube/status")
    suspend fun getYoutubeConnectStatus(@Query("deviceId") deviceId: String): YoutubeStatusResponse
}

data class YoutubeUploadResponse(
    val success: Boolean,
    val videoId: String? = null,
    val url: String? = null,
    val error: String? = null
)

data class YoutubeStatusResponse(
    val success: Boolean,
    val connected: Boolean = false,
    val error: String? = null
)

data class DiscoveryNearbyResponse(
    val results: List<com.example.mistreal_mini.data.model.DiscoveryResult>,
    val succeeded: Boolean = true
)

data class BankWhatsapp(val number: String, val prefilledMessage: String? = null)
data class BankFacebook(val pageId: String)

data class BankChannel(
    val id: String,
    val displayName: String,
    val country: String,
    val whatsapp: BankWhatsapp? = null,
    val facebook: BankFacebook? = null
)

data class BankChannelsResponse(
    val success: Boolean,
    val banks: List<BankChannel> = emptyList(),
    val error: String? = null
)

data class CelestialVectorResponse(
    val success: Boolean,
    val body: String,
    val name: String? = null,
    val azimuth: Double? = null,
    val elevation: Double? = null,
    val orientation: String? = null,
    val distEarth: String? = null,
    val distSun: String? = null,
    val description: String? = null,
    val relativeToMoon: String? = null,
    val status: String? = null
)

data class LocationRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val lat: Double,
    val lon: Double
)

data class CreateAdResponse(
    val success: Boolean,
    val adId: Int? = null,
    val error: String? = null
)

data class AdPayload(
    val id: Int,
    val mediaType: String,
    val videoUrl: String? = null,
    val imageUrls: List<String>? = null,
    val durationSeconds: Int,
    val caption: String? = null,
    val targetUrl: String? = null,
    val ctaLabel: String
)

data class AdDueResponse(
    val success: Boolean,
    val due: Boolean = false,
    val ad: AdPayload? = null,
    val error: String? = null
)

data class AdEventRequest(
    val deviceId: String,
    val eventType: String
)

data class YoutubeVideoResponse(
    val videoId: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String? = null,
    val publishedAt: String? = null,
    val viewCount: Long? = null,
    val likeCount: Long? = null
)

data class YoutubeFeedResponse(
    val success: Boolean,
    val videos: List<YoutubeVideoResponse> = emptyList(),
    val error: String? = null
)

data class CommunityPreferencesRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val platforms: List<String>
)

data class LocationPhotoResponse(
    val success: Boolean,
    val photoUrl: String? = null,
    val error: String? = null
)

data class BusinessPlatformHandle(val platform: String, val handle: String)

data class RegisterBusinessRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val businessId: String,
    val name: String,
    val description: String? = null,
    val logoUrl: String? = null,
    val category: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val connectedPlatforms: List<BusinessPlatformHandle> = emptyList(),
    val ownerName: String? = null,
    // Already-hosted URL carried forward from a prior uploadOwnerPhoto call
    // — register itself never uploads bytes, so a routine re-save can never
    // accidentally clobber this with null.
    val ownerPhotoUrl: String? = null
)

// Server-side mirror of a business — distinct from the local Room
// BusinessEntity (which stays the owner-device's own source of truth for
// edits); this is what makes OTHER users' searches/profile views possible.
data class RemoteBusiness(
    val businessId: String,
    val name: String,
    val description: String? = null,
    val category: String,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val logoUrl: String? = null,
    val connectedPlatforms: List<BusinessPlatformHandle> = emptyList(),
    val confirmedMeetupsCount: Int = 0,
    val ownerName: String? = null,
    val ownerPhotoUrl: String? = null
)

data class BusinessSearchResponse(
    val success: Boolean,
    val businesses: List<RemoteBusiness>? = null,
    val error: String? = null
)

data class BusinessTestimonial(val reviewText: String?, val confirmedAt: String)

data class BusinessDetailResponse(
    val success: Boolean,
    val business: RemoteBusiness? = null,
    val confirmedMeetupsCount: Int = 0,
    val upvotes: Int = 0,
    val downvotes: Int = 0,
    val testimonials: List<BusinessTestimonial>? = null,
    val error: String? = null
)

data class UploadOwnerPhotoRequest(val deviceId: String, val photoBase64: String, val photoMimeType: String)
data class UploadOwnerPhotoResponse(val success: Boolean, val ownerPhotoUrl: String? = null, val error: String? = null)
data class CanShareResponse(val success: Boolean, val canShare: Boolean = false, val error: String? = null)

data class ProposeMeetupRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val businessId: String? = null,
    val counterpartyPlatform: String? = null,
    val counterpartyContactId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val addressLabel: String? = null,
    val scheduledAt: String
)

data class Meetup(
    val id: Int,
    val businessId: String? = null,
    val proposerDeviceId: String,
    val counterpartyPlatform: String? = null,
    val counterpartyContactId: String? = null,
    val latitude: Double,
    val longitude: Double,
    val addressLabel: String? = null,
    val scheduledAt: String,
    val status: String, // "proposed" | "accepted" | "declined" | "completed"
    val transactionToken: String
)

data class MeetupResponse(
    val success: Boolean,
    val meetup: Meetup? = null,
    val error: String? = null
)

data class MeetupsListResponse(
    val success: Boolean,
    val meetups: List<Meetup>? = null,
    val error: String? = null
)

data class ConfirmMeetupRequest(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val outcome: String, // "success" | "failed"
    val reasonIfFailed: String? = null,
    val reviewText: String? = null,
    // Only honored server-side when this device is the buyer (the
    // proposer) of a businessId-tagged, outcome='success' confirmation.
    val buyerVote: String? = null, // "up" | "down"
    val photoBase64: String? = null,
    val photoMimeType: String? = null
)

data class BusinessAdResponse(val success: Boolean, val ad: AdPayload? = null, val error: String? = null)

data class MeetupConfirmResponse(
    val success: Boolean,
    val error: String? = null
)

// Public auto-escalation to real connected platforms is no longer a client
// choice (see the Guardian plan) — it only ever happens server-side, once,
// after 30 days with zero confirmed-contact response. The client just fires
// the alert; everything after that is backend-owned.
data class FireEmergencyAlertRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val latitude: Double,
    val longitude: Double,
    val distressSignature: String,
    val triggerType: String = "manual", // "manual" | "meetup_panic"
    val sosAudioBase64: String? = null,
    val sosAudioMimeType: String? = null
)

data class EmergencyAlertFireResponse(
    val success: Boolean,
    val error: String? = null,
    val alert: EmergencyAlert? = null,
    val confirmedContactsNotified: Int = 0,
    val confirmedContactsTotal: Int = 0
)

data class EmergencyAlert(
    val id: Int,
    val ownerDeviceId: String,
    val triggerType: String,
    val latitude: Double,
    val longitude: Double,
    val sosAudioUrl: String? = null,
    val distressSignature: String? = null,
    val status: String, // "active" | "resolved_safe" | "escalated_public"
    val createdAt: String,
    val escalateAt: String? = null,
    val resolvedAt: String? = null
)

data class AttachSosAudioRequest(
    val deviceId: String,
    val sosAudioBase64: String,
    val sosAudioMimeType: String
)

data class EmergencyAlertsListResponse(
    val success: Boolean,
    val alerts: List<EmergencyAlert>? = null,
    val error: String? = null
)

data class EmergencyActionResponse(
    val success: Boolean,
    val error: String? = null
)

data class SendEmailRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val toEmail: String,
    val toName: String? = null,
    val subject: String,
    val body: String
)

data class SendEmailResponse(
    val success: Boolean,
    val error: String? = null
)

data class EmailHistoryResponse(
    val success: Boolean,
    val messages: List<EmailMessage>? = null
)

data class EmailMessage(
    val id: Long,
    val toEmail: String,
    val toName: String? = null,
    val subject: String,
    val body: String,
    val timestamp: String
)

data class EmailContactsResponse(
    val success: Boolean,
    val contacts: List<EmailContactSummary>? = null
)

data class EmailContactSummary(
    val toEmail: String,
    val toName: String? = null,
    val lastSubject: String? = null,
    val lastTimestamp: String? = null
)

data class ContactsResponse(val success: Boolean, val contacts: List<SocialContact>)

// Replaces the old one-directional {name, type, value} shape — a contact
// must now explicitly confirm/decline via the emailed/DM'd link before
// `status` ever leaves "pending", and only a "confirmed" contact receives
// real alert content (see emergencyRoutes.ts).
data class EmergencyContact(
    val id: Int = 0,
    val name: String,
    val channel: String, // "platform" | "email" | "sms"
    val platform: String? = null,
    val platformContactId: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val status: String = "pending" // "pending" | "confirmed" | "declined"
)

data class AddEmergencyContactRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val name: String,
    val channel: String,
    val platform: String? = null,
    val platformContactId: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null
)

data class EmergencyContactResponse(
    val success: Boolean,
    val contact: EmergencyContact? = null,
    val error: String? = null
)

data class EmergencyContactsListResponse(
    val success: Boolean,
    val contacts: List<EmergencyContact>? = null,
    val error: String? = null
)

data class SocialContact(
    val id: String,
    val name: String,
    val platform: String,
    val unreadCount: Int,
    val isOnline: Boolean = false,
    val lastSeen: String? = null, // "5 minutes ago", "2 hours ago", etc.
    val statusMessage: String? = null,
    val avatar: String? = null,
    val autoReplyEnabled: Boolean = false
)

data class ContactAutoReplyRequest(
    val deviceId: String,
    val platform: String,
    val contactId: String,
    val enabled: Boolean
)

data class UnreadResponse(val success: Boolean, val unreadItems: List<UnreadItem>)
data class UnreadItem(
    val id: String,
    val sender: String,
    val platform: String,
    val text: String,
    val timestamp: String,
    val isOnline: Boolean = false,
    val lastSeen: String? = null
)

data class AddonDefinition(
    val id: String,
    val label: String,
    val description: String,
    val priceUsd: String,
    val playProductId: String
)

data class AppConfigResponse(
    val proPrice: String,
    val productId: String,
    val freeTrialDays: String,
    val freePlatformLimit: Int,
    // Nullable: Gson bypasses the constructor on deserialization, so a
    // missing JSON field lands as null regardless of the Kotlin default.
    val addons: List<AddonDefinition>? = null
)

data class PaymentVerifyRequest(
    val deviceId: String,
    val purchaseToken: String,
    val productId: String
)

data class PaymentVerifyResponse(
    val success: Boolean,
    val message: String?,
    val addonId: String? = null,
    val isPro: Boolean = false
)

data class MyAddonsResponse(
    val success: Boolean,
    val addons: List<String>? = null,
    val error: String? = null
)

data class UserSettingsRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val userName: String?,
    val aiPersona: String?,
    val aiAudience: String?,
    val autoReplyDelay: Int?,
    val guardianEnabled: Boolean? = null,
    val aiAutoSendEnabled: Boolean? = null
)

data class UserSettingsResponse(
    val success: Boolean,
    val userName: String? = null,
    val aiPersona: String? = null,
    val autoReplyDelay: Int? = null,
    val guardianEnabled: Boolean? = null,
    val aiAutoSendEnabled: Boolean? = null
)

data class WeatherResponse(
    val summary: String,
    val location: String?,
    val rainExpected: Boolean,
    val timeToRain: Int?,
    val forecast: List<ForecastItem>? = null,
    val moonPhase: String? = null,
    val moonImageUrl: String? = null,
    val planets: String? = null,
    val celestial: CelestialData? = null
)

data class ForecastItem(
    val time: String,
    val temp: String,
    val condition: String
)

data class CelestialData(
    val moon: MoonData,
    val planets: List<PlanetVisibility>
)

data class MoonData(
    val phase: String,
    val imageUrl: String,
    val azimuth: Double,
    val altitude: Double,
    val direction: String
)

data class PlanetVisibility(
    val name: String,
    val id: String,
    val altitude: Double,
    val azimuth: Double,
    val direction: String,
    val isVisible: Boolean,
    val isNearMoon: Boolean
)

data class NewsResponse(
    val articles: List<Article>
)

data class PinIntelRequest(
    val firebaseUid: String,
    val itemTitle: String,
    val itemUrl: String? = null,
    val itemType: String? = null,
    val metadata: Map<String, Any>? = null
)

data class Article(
    val title: String,
    val description: String?,
    val url: String,
    val type: String? = "news",
    val isPinned: Boolean? = false,
    val category: String? = null,
    val timestamp: String? = null
)

// Flat, matching the backend's socialActionSchema exactly — this used to
// nest type/platform/content/targetId under an "action" sub-object, but the
// Zod schema (and the route handler) read those fields at the top level, so
// every social action (like/follow/comment/DM/post) was failing validation
// with a 400 before the handler ever ran.
data class SocialActionRequest(
    val deviceId: String,
    val type: String,
    val platform: String,
    val content: String,
    val targetId: String,
    val mediaBase64: String? = null,
    val mediaMimeType: String? = null,
    val shareToCommunity: Boolean = false
)

data class SocialActionResponse(
    val success: Boolean,
    val error: String? = null
)

data class SocialDisconnectResult(
    val success: Boolean,
    val platform: String? = null,
    val message: String? = null,
    val error: String? = null
)

data class SocialPlatformResponse(
    val id: String,
    val name: String,
    val icon: String,
    val isProOnly: Boolean,
    val isConnected: Boolean = false,
    // Nullable: Gson bypasses the constructor on deserialization, so a missing JSON
    // field lands as null here even though it's typed non-null elsewhere in Kotlin
    // code (same reasoning as SocialPost.comments below) — callers must use
    // `capabilities ?: PlatformCapabilities()`.
    val capabilities: com.example.mistreal_mini.data.model.PlatformCapabilities? = null
)

data class SocialHistoryResponse(
    val success: Boolean,
    val messages: List<SocialHistoryMessage>
)

data class SocialHistoryMessage(
    val id: String,
    val platform: String,
    val direction: String, // "incoming" or "outgoing"
    val text: String?,
    val timestamp: String,
    val attachments: List<SocialAttachment>? = null
)

data class SocialAttachment(
    val type: String, // "image", "video", "file", etc.
    val url: String
)
