package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AiApiService
import com.example.mistreal_mini.data.api.AiModelResponse
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.entity.ChatEntity
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatResponse
import com.google.gson.Gson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val api: AiApiService,
    private val chatDao: ChatDao,
    private val authRepository: AuthRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllMessages(): Flow<List<ChatMessage>> {
        return authRepository.userState.flatMapLatest { user ->
            val uid = user?.uid ?: "guest"
            chatDao.getAllMessages(uid).map { entities ->
                entities.map { it.toChatMessage() }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getTrendMessages(trendTitle: String): Flow<List<ChatMessage>> {
        return authRepository.userState.flatMapLatest { user ->
            val uid = user?.uid ?: "guest"
            chatDao.getTrendMessages(uid, trendTitle).map { entities ->
                entities.map { it.toChatMessage() }
            }
        }
    }

    suspend fun getPagedMessages(limit: Int, offset: Int): List<ChatMessage> {
        val uid = authRepository.currentUser?.uid ?: "guest"
        return chatDao.getPagedMessages(uid, limit, offset).map { it.toChatMessage() }.reversed()
    }

    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: "guest"

    suspend fun saveMessage(message: ChatMessage) {
        chatDao.insertMessage(ChatEntity.fromChatMessage(currentUserId, message))
    }

    suspend fun saveEntity(entity: ChatEntity) {
        chatDao.insertMessage(entity)
    }

    suspend fun clearHistory() {
        chatDao.nukeChat(currentUserId)
    }

    suspend fun deleteNonTrendMessages(userId: String) {
        chatDao.deleteNonTrendMessages(userId)
    }

    suspend fun deleteMessage(id: Long) {
        chatDao.deleteMessageById(id)
    }

    suspend fun updateMessage(id: Long, content: String) {
        chatDao.updateContent(id, content)
    }

    suspend fun deleteTrend(title: String) {
        chatDao.deleteTrend(title)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getUniqueTrends(): Flow<List<ChatMessage>> {
        return authRepository.userState.flatMapLatest { user ->
            val uid = user?.uid ?: "guest"
            chatDao.getUniqueTrends(uid).map { entities ->
                entities.map { it.toChatMessage() }
            }
        }
    }

    suspend fun sendMessage(
        prompt: String,
        provider: String,
        history: List<ChatMessage>,
        deviceId: String?,
        images: List<MultipartBody.Part>?,
        audio: MultipartBody.Part?,
        retryCount: Int = 0
    ): Resource<ChatResponse> {
        return try {
            val gson = Gson()
            val historyJson = gson.toJson(history)
            
            val response = api.sendMessage(
                prompt = prompt.toRequestBody("text/plain".toMediaTypeOrNull()),
                provider = provider.toRequestBody("text/plain".toMediaTypeOrNull()),
                history = historyJson.toRequestBody("application/json".toMediaTypeOrNull()),
                deviceId = deviceId?.toRequestBody("text/plain".toMediaTypeOrNull()),
                images = images,
                audio = audio
            )

            if (response.success) {
                Resource.Success(response)
            } else {
                // 🛠️ Handle 429 Too Many Requests with exponential backoff
                val isRateLimit = response.error?.contains("429") == true || response.error?.contains("RATE_LIMIT") == true
                
                if (isRateLimit && retryCount < 3) {
                    val waitTime = 2000L * (retryCount + 1)
                    kotlinx.coroutines.delay(waitTime)
                    return sendMessage(prompt, provider, history, deviceId, images, audio, retryCount + 1)
                }
                
                if (retryCount < 2 && (response.error?.contains("Timeout") == true || response.error?.contains("503") == true)) {
                    kotlinx.coroutines.delay(2000L * (retryCount + 1))
                    return sendMessage(prompt, provider, history, deviceId, images, audio, retryCount + 1)
                }
                
                // Final error message for the user if all retries fail
                val finalError = if (isRateLimit)
                    "AI is catching its breath (Rate Limit). Please wait a few seconds." 
                    else (response.error ?: "Unknown error")
                
                Resource.Error(finalError)
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network failure. Check your connection.")
        }
    }

    suspend fun getAvailableModels(deviceId: String?): Resource<List<AiModelResponse>> {
        return try {
            Resource.Success(api.getAvailableModels(deviceId))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch models")
        }
    }
}
