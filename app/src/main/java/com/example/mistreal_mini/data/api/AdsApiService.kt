package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Zernio Ads integration — mounted at /api/ad-campaigns, deliberately NOT
 * /api/ads (already taken by the unrelated self-promotion ad-serving
 * system, AdApiService/AdHostViewModel). Every call here can 403 with
 * "ADS_ADDON_REQUIRED" until Zernio's own Ads add-on is enabled on the
 * workspace — an external prerequisite this app can't verify or flip
 * itself; see backend/src/routes/adsRoutes.ts for the full honest flag.
 */
interface AdsApiService {

    @POST("api/ad-campaigns/connect")
    suspend fun connectAdAccount(@Body request: ConnectAdAccountRequest): AdsConnectResponse

    @POST("api/ad-campaigns/campaigns")
    suspend fun createCampaign(@Body request: CreateCampaignRequest): CampaignResponse

    @PUT("api/ad-campaigns/campaigns/{id}")
    suspend fun updateCampaign(@Path("id") id: String, @Body request: UpdateCampaignRequest): CampaignResponse

    @DELETE("api/ad-campaigns/campaigns/{id}")
    suspend fun deleteCampaign(@Path("id") id: String): GenericSuccessResponse

    @GET("api/ad-campaigns/campaigns")
    suspend fun getCampaigns(
        @Query("deviceId") deviceId: String,
        @Query("businessId") businessId: String,
        @Query("platform") platform: String
    ): CampaignListResponse

    @GET("api/ad-campaigns/campaigns/{id}/analytics")
    suspend fun getCampaignAnalytics(@Path("id") id: String): CampaignAnalyticsResponse
}

data class ConnectAdAccountRequest(
    val deviceId: String,
    val businessId: String,
    val platform: String
)

data class AdsConnectResponse(
    val success: Boolean,
    val connectUrl: String? = null,
    val error: String? = null
)

data class AdCampaign(
    val id: String,
    val name: String? = null,
    val status: String? = null,
    val budget: Double? = null,
    val objective: String? = null,
    val platform: String? = null
)

data class CreateCampaignRequest(
    val deviceId: String,
    val businessId: String,
    val platform: String,
    val name: String,
    val budget: Double,
    val objective: String
)

data class UpdateCampaignRequest(
    val status: String? = null,
    val budget: Double? = null
)

data class CampaignResponse(
    val success: Boolean,
    val campaign: AdCampaign? = null,
    val error: String? = null
)

data class CampaignListResponse(
    val success: Boolean,
    val campaigns: List<AdCampaign>? = null,
    val error: String? = null
)

data class CampaignAnalyticsResponse(
    val success: Boolean,
    // Deliberately a raw map, not a typed shape — Zernio's real analytics
    // response fields weren't independently re-verified this session (see
    // zernioAdapter.ts's own ads-section comment); showing whatever keys
    // come back avoids pretending to a schema that might not match.
    val analytics: Map<String, Any?>? = null,
    val error: String? = null
)
