package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AiApiService
import com.example.mistreal_mini.data.model.ChatRequest
import com.example.mistreal_mini.data.model.ChatResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val api: AiApiService
) {
    suspend fun sendMessage(request: ChatRequest): Resource<ChatResponse> {
        return try {
            val response = api.sendMessage(request)
            if (response.success) {
                Resource.Success(response)
            } else {
                Resource.Error(response.error ?: "Unknown error")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "An error occurred")
        }
    }
}
