package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

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
    
    @GET("api/social/sync")
    suspend fun syncSocials(@Query("deviceId") deviceId: String?): SocialSyncResponse

    @POST("api/social/action")
    suspend fun performSocialAction(@Body request: SocialActionRequest): SocialActionResponse

    @POST("api/subscribe")
    suspend fun createStripeSession(@Body request: SubscriptionRequest): SubscriptionResponse
}

data class SubscriptionRequest(val tier: String)
data class SubscriptionResponse(val success: Boolean, val url: String?, val error: String?)

data class WeatherResponse(
    val summary: String,
    val rainExpected: Boolean,
    val timeToRain: Int? // in minutes
)

data class NewsResponse(
    val articles: List<Article>
)

data class Article(
    val title: String,
    val description: String,
    val url: String
)

data class SocialSyncResponse(
    val summary: String,
    val platformUpdates: List<PlatformUpdate>,
    val rawContent: String?
)

data class PlatformUpdate(
    val platform: String,
    val count: Int,
    val recentMessage: String?
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
