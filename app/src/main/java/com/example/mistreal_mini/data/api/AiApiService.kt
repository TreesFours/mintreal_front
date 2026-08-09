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
        @Part images: List<MultipartBody.Part>?,
        @Part audio: MultipartBody.Part?
    ): ChatResponse

    @GET("api/models")
    suspend fun getAvailableModels(@Query("deviceId") deviceId: String?): List<AiModelResponse>
}


data class AiModelResponse(
    val id: String,
    val name: String,
    val provider: String,
    val isProOnly: Boolean,
    val price: String
)
