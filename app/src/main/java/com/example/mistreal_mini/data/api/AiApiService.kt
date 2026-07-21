package com.example.mistreal_mini.data.api

import com.example.mistreal_mini.data.model.ChatRequest
import com.example.mistreal_mini.data.model.ChatResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AiApiService {
    @POST("api/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse
}
