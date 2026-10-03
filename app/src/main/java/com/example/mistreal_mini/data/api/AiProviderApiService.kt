package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AiProviderApiService {
    @POST("api/ai-provider/key")
    suspend fun saveByokKey(@Body request: SaveByokKeyRequest): ByokStatusResponse

    @POST("api/ai-provider/clear")
    suspend fun clearByokKey(@Body request: ClearByokKeyRequest): ByokStatusResponse

    @GET("api/ai-provider/status")
    suspend fun getByokStatus(@Query("deviceId") deviceId: String): ByokStatusResponse

    // Separate BYOK slot for video editing — see userModel.ts byokVideo* fields.
    @POST("api/ai-provider/video-key")
    suspend fun saveByokVideoKey(@Body request: SaveByokKeyRequest): ByokStatusResponse

    @POST("api/ai-provider/video-clear")
    suspend fun clearByokVideoKey(@Body request: ClearByokKeyRequest): ByokStatusResponse

    @GET("api/ai-provider/video-status")
    suspend fun getByokVideoStatus(@Query("deviceId") deviceId: String): ByokStatusResponse

    // Saved image/video GENERATION provider configs — a user can add several
    // and switch which is active, unlike the single-slot BYOK calls above.
    @POST("api/ai-provider/media-configs")
    suspend fun addMediaProviderConfig(@Body request: MediaProviderConfigRequest): MediaProviderConfigResponse

    @GET("api/ai-provider/media-configs")
    suspend fun getMediaProviderConfigs(
        @Query("deviceId") deviceId: String,
        @Query("capability") capability: String
    ): MediaProviderConfigListResponse

    @POST("api/ai-provider/media-configs/activate")
    suspend fun activateMediaProviderConfig(@Body request: MediaProviderActivateRequest): MediaProviderActivateResponse

    @DELETE("api/ai-provider/media-configs/{id}")
    suspend fun deleteMediaProviderConfig(
        @Path("id") id: Int,
        @Query("deviceId") deviceId: String
    ): ByokStatusResponse
}

data class MediaProviderConfigRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val capability: String, // "image_gen" | "video_gen"
    val label: String,
    val providerType: String,
    val apiKey: String,
    val baseUrl: String,
    val modelName: String? = null
)

data class MediaProviderConfigResponse(
    val success: Boolean,
    val error: String? = null,
    val config: MediaProviderConfigSummary? = null
)

data class MediaProviderConfigSummary(
    val id: Int,
    val label: String,
    val providerType: String,
    val modelName: String? = null
)

data class MediaProviderConfigListResponse(
    val success: Boolean,
    val error: String? = null,
    val activeConfigId: Int? = null,
    val configs: List<MediaProviderConfigSummary>? = null
)

data class MediaProviderActivateRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val capability: String,
    val configId: Int? = null // null = "Our Recommended"
)

data class MediaProviderActivateResponse(
    val success: Boolean,
    val error: String? = null,
    val activeConfigId: Int? = null
)

data class SaveByokKeyRequest(
    val deviceId: String,
    val firebaseUid: String? = null,
    val providerType: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val modelName: String? = null
)

data class ClearByokKeyRequest(
    val deviceId: String,
    val firebaseUid: String? = null
)

data class ByokStatusResponse(
    val success: Boolean,
    val configured: Boolean = false,
    val providerType: String? = null,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val error: String? = null
)
