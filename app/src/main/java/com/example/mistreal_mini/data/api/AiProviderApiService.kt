package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
}

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
