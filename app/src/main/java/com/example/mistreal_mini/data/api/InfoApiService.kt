package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import com.example.mistreal.data.models.SocialSyncResponse

interface InfoApiService {
    @GET("api/weather")
    suspend fun getWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): WeatherResponse

    @GET("api/news")
    suspend fun getNews(
        @Query("category") category: String?,
        @Query("location") location: String?
    ): NewsResponse
    
    @GET("api/social/platforms")
    suspend fun getAvailablePlatforms(@Query("deviceId") deviceId: String?): List<SocialPlatformResponse>

    @GET("api/social/sync")
    suspend fun syncSocials(@Query("deviceId") deviceId: String?): SocialSyncResponse

    @POST("api/social/disconnect/{platform}")
    suspend fun disconnectPlatform(
        @Path("platform") platform: String,
        @Body request: Map<String, String>
    ): Map<String, Boolean>

    @POST("api/social/action")
    suspend fun performSocialAction(@Body request: SocialActionRequest): SocialActionResponse

    @POST("api/user/settings")
    suspend fun updateUserSettings(@Body request: UserSettingsRequest): SocialActionResponse

    @POST("api/subscribe")
    suspend fun createStripeSession(@Body request: SubscriptionRequest): SubscriptionResponse

    @GET("api/config")
    suspend fun getAppConfig(): AppConfigResponse

    @POST("api/payment/verify")
    suspend fun verifyPayment(@Body request: PaymentVerifyRequest): PaymentVerifyResponse

    @POST("api/user/location")
    suspend fun updateLocation(@Body request: LocationRequest): SocialActionResponse

    @POST("api/emergency/alert")
    suspend fun sendEmergencyAlert(@Body request: EmergencyAlertRequest): SocialActionResponse

    @GET("api/social/contacts")
    suspend fun getContacts(
        @Query("deviceId") deviceId: String,
        @Query("platform") platform: String,
        @Query("search") search: String? = null
    ): ContactsResponse

    @GET("api/social/unread")
    suspend fun getUnreadMessages(@Query("deviceId") deviceId: String): UnreadResponse

    @GET("api/social/history")
    suspend fun getSocialHistory(
        @Query("deviceId") deviceId: String,
        @Query("platform") platform: String,
        @Query("targetId") targetId: String
    ): SocialHistoryResponse

    @GET("api/celestial/vectors")
    suspend fun getCelestialVectors(@Query("bodyId") bodyId: String): CelestialVectorResponse

    @GET("api/discovery/nearby")
    suspend fun getNearbyPlaces(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Double,
        @Query("category") category: String
    ): DiscoveryNearbyResponse
}

data class DiscoveryNearbyResponse(
    val results: List<com.example.mistreal_mini.data.model.DiscoveryResult>
)

data class CelestialVectorResponse(
    val success: Boolean,
    val body: String,
    val azimuth: Double? = null,
    val elevation: Double? = null,
    val orientation: String? = null,
    val status: String? = null
)

data class LocationRequest(
    val deviceId: String,
    val lat: Double,
    val lon: Double
)

data class EmergencyAlertRequest(
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val distressSignature: String
)

data class ContactsResponse(val success: Boolean, val contacts: List<SocialContact>)
data class EmergencyContact(
    val name: String,
    val type: String, // "phone", "email", "social"
    val value: String
)

data class SocialContact(
    val id: String,
    val name: String,
    val platform: String,
    val unreadCount: Int,
    val isOnline: Boolean = false,
    val lastSeen: String? = null, // "5 minutes ago", "2 hours ago", etc.
    val statusMessage: String? = null,
    val avatar: String? = null
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

data class AppConfigResponse(
    val proPrice: String,
    val productId: String,
    val freeTrialDays: String
)

data class PaymentVerifyRequest(
    val purchaseToken: String,
    val productId: String
)

data class PaymentVerifyResponse(
    val success: Boolean,
    val message: String?
)

data class UserSettingsRequest(
    val deviceId: String,
    val userName: String?,
    val aiPersona: String?,
    val autoReplyDelay: Int?,
    val guardianEnabled: Boolean? = null,
    val emergencyContacts: List<EmergencyContact>? = null
)

data class SubscriptionRequest(val tier: String)
data class SubscriptionResponse(val success: Boolean, val url: String?, val error: String?)

data class WeatherResponse(
    val summary: String,
    val location: String?,
    val rainExpected: Boolean,
    val timeToRain: Int?, // in minutes
    val rainEventType: String? = "NONE", // "START", "STOP"
    val rainIntensity: Double? = 0.0,
    val moonPhase: String? = null,
    val moonImageUrl: String? = null,
    val planets: String? = null
)

data class NewsResponse(
    val articles: List<Article>
)

data class Article(
    val title: String,
    val description: String?,
    val url: String
)

data class SocialActionRequest(
    val deviceId: String,
    val action: SocialAction,
    val delayMinutes: Int? = 0
)

data class SocialAction(
    val type: String,
    val platform: String,
    val content: String,
    val targetId: String
)

data class SocialActionResponse(
    val success: Boolean,
    val error: String? = null
)

data class SocialPlatformResponse(
    val id: String,
    val name: String,
    val icon: String,
    val isProOnly: Boolean,
    val isConnected: Boolean = false
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
