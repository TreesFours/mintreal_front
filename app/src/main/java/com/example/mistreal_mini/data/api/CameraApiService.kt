package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Live-cam discovery feed — /api/cameras. Admin review-queue endpoints are deliberately not exposed to the client; approval happens via a direct API call with ADMIN_API_KEY, same as gameRoutes.ts's catalog-insert precedent. */
interface CameraApiService {

    @POST("api/cameras")
    suspend fun submitCamera(@Body request: SubmitCameraRequest): SubmitCameraResponse

    @GET("api/cameras")
    suspend fun getCameras(): CamerasResponse
}

data class SubmitCameraRequest(
    val deviceId: String,
    val title: String,
    val location: String? = null,
    val streamUrl: String,
    val streamType: String // "youtube" | "hls"
)

data class CameraProfileDto(
    val id: String,
    val title: String,
    val location: String? = null,
    val streamUrl: String,
    val streamType: String,
    val createdAt: String
)

data class SubmitCameraResponse(
    val success: Boolean,
    val camera: CameraProfileDto? = null,
    val error: String? = null
)

data class CamerasResponse(
    val success: Boolean,
    val cameras: List<CameraProfileDto>? = null,
    val error: String? = null
)
