package com.example.mistreal_mini.data.api

import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface AiApiService {
    @Multipart
    @POST("api/chat")
    suspend fun sendMessage(
        @Part("prompt") prompt: RequestBody,
        @Part("provider") provider: RequestBody,
        @Part("history") history: RequestBody,
        @Part("deviceId") deviceId: RequestBody?,
        @Part("firebaseUid") firebaseUid: RequestBody?,
        @Part images: List<MultipartBody.Part>?,
        @Part("imageRoles") imageRoles: RequestBody?,
        @Part audio: MultipartBody.Part?,
        @Part video: MultipartBody.Part?
    ): ChatResponse

    @GET("api/models")
    suspend fun getAvailableModels(@Query("deviceId") deviceId: String?): List<AiModelResponse>

    @GET("api/health")
    suspend fun warmupBackend(): retrofit2.Response<Void>
}


data class AiModelResponse(
    val id: String,
    val name: String,
    val provider: String,
    val isProOnly: Boolean,
    val price: String,
    val quota: String? = null,
    val health: Int? = null,
    // Nullable: Gson bypasses the constructor on deserialization, so an absent
    // JSON field lands as null here regardless of a non-null backend type —
    // callers must fall back to the old id-substring heuristic when this is null
    // (e.g. an older cached response), not assume it's always present.
    val capabilities: ModelCapabilities? = null
)

data class ModelCapabilities(
    val text: Boolean = true,
    val imageGen: Boolean = false,
    val videoGen: Boolean = false,
    val voice: Boolean = false
)
