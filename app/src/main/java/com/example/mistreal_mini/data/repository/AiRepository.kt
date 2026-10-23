package com.example.mistreal_mini.data.repository

import android.content.Context
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.data.api.AiApiService
import com.example.mistreal_mini.data.api.AiModelResponse
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.dao.PinnedTrendDao
import com.example.mistreal_mini.data.local.dao.DelayedTrendDao
import com.example.mistreal_mini.data.local.entity.ChatEntity
import com.example.mistreal_mini.data.local.entity.DelayedTrendEntity
import com.example.mistreal_mini.data.local.entity.PinnedTrendEntity
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.model.ChatResponse
import com.example.mistreal_mini.util.DeviceIdProvider
import com.google.gson.Gson
import timber.log.Timber
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val pinnedTrendDao: PinnedTrendDao,
    private val delayedTrendDao: DelayedTrendDao,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId
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

    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: "guest"

    // Returns the generated row id — needed when revealAt is set, so the
    // caller can schedule RevealDelayedReplyWorker against this exact row.
    // Existing callers that don't need the id simply ignore the return value.
    suspend fun saveMessage(message: ChatMessage, revealAt: Long? = null): Long {
        return chatDao.insertMessage(ChatEntity.fromChatMessage(currentUserId, message, revealAt))
    }

    suspend fun saveEntity(entity: ChatEntity): Long {
        return chatDao.insertMessage(entity)
    }

    suspend fun clearRevealAt(messageId: Long) {
        chatDao.clearRevealAt(messageId)
    }

    fun getDelayEnabledTrendTitles(): Flow<List<String>> = delayedTrendDao.getEnabledTitles()

    suspend fun setTrendDelayEnabled(trendTitle: String, enabled: Boolean) {
        if (enabled) delayedTrendDao.enable(DelayedTrendEntity(trendTitle)) else delayedTrendDao.disable(trendTitle)
    }

    suspend fun clearHistory() {
        chatDao.nukeChat(currentUserId)
    }

    suspend fun deleteNonTrendMessages(userId: String) {
        chatDao.deleteNonTrendMessages(userId)
    }

    fun getPinnedTrendTitles(): Flow<List<String>> = pinnedTrendDao.getPinnedTitles()

    suspend fun isTrendPinned(trendTitle: String): Boolean = pinnedTrendDao.isPinned(trendTitle)

    suspend fun setTrendPinned(trendTitle: String, pinned: Boolean) {
        if (pinned) pinnedTrendDao.pin(PinnedTrendEntity(trendTitle)) else pinnedTrendDao.unpin(trendTitle)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPagedMessagesFlow(): Flow<PagingData<ChatMessage>> {
        val userId = currentUserId
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { chatDao.getPagedMessages(userId) }
        ).flow.map { pagingData ->
            pagingData.map { it.toChatMessage() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getPagedTrendMessagesFlow(trendTitle: String): Flow<PagingData<ChatMessage>> {
        val userId = currentUserId
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { chatDao.getPagedTrendMessages(userId, trendTitle) }
        ).flow.map { pagingData ->
            pagingData.map { it.toChatMessage() }
        }
    }

    suspend fun sendMessage(
        prompt: String,
        provider: String,
        history: List<ChatMessage>,
        deviceId: String?,
        images: List<MultipartBody.Part>?,
        audio: MultipartBody.Part?,
        video: MultipartBody.Part? = null,
        // Parallel to `images`, by position — e.g. ["start", "end", "character"].
        // Lets the backend know which uploaded image plays which role for
        // keyframe-conditioned generation, without needing separate named
        // multipart fields per role (those require a backend allowlist change
        // every time a new role is added; this doesn't).
        imageRoles: List<String>? = null,
        // Architectural Render's confirm step — set only when the user tapped
        // "Looks Right" on the understanding card (see ChatViewModel.confirmArchUnderstanding).
        confirmArchRender: Boolean = false,
        archUnderstanding: String? = null,
        retryCount: Int = 0
    ): Resource<ChatResponse> = safeApiCall("Network failure. Check your connection.") {
        val gson = Gson()
        val historyJson = gson.toJson(history)
        val firebaseUid = authRepository.currentUser?.uid
        val imageRolesJson = imageRoles?.let { gson.toJson(it) }

        val response = api.sendMessage(
            prompt = prompt.toRequestBody("text/plain".toMediaTypeOrNull()),
            provider = provider.toRequestBody("text/plain".toMediaTypeOrNull()),
            history = historyJson.toRequestBody("application/json".toMediaTypeOrNull()),
            deviceId = this@AiRepository.deviceId.toRequestBody("text/plain".toMediaTypeOrNull()),
            firebaseUid = firebaseUid?.toRequestBody("text/plain".toMediaTypeOrNull()),
            images = images,
            imageRoles = imageRolesJson?.toRequestBody("application/json".toMediaTypeOrNull()),
            audio = audio,
            video = video,
            confirmArchRender = confirmArchRender.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
            archUnderstanding = archUnderstanding?.toRequestBody("application/json".toMediaTypeOrNull())
        )

        if (response.success) {
            Resource.Success(response)
        } else {
            // 🛠️ Handle 429 Too Many Requests with exponential backoff
            val isRateLimit = response.error?.contains("429") == true || response.error?.contains("RATE_LIMIT") == true

            if (isRateLimit && retryCount < 3) {
                val waitTime = 2000L * (retryCount + 1)
                kotlinx.coroutines.delay(waitTime)
                return@safeApiCall sendMessage(prompt, provider, history, deviceId, images, audio, video, imageRoles, confirmArchRender, archUnderstanding, retryCount + 1)
            }

            if (retryCount < 2 && (response.error?.contains("Timeout") == true || response.error?.contains("503") == true)) {
                kotlinx.coroutines.delay(2000L * (retryCount + 1))
                return@safeApiCall sendMessage(prompt, provider, history, deviceId, images, audio, video, imageRoles, confirmArchRender, archUnderstanding, retryCount + 1)
            }

            // Final error message for the user if all retries fail
            val finalError = if (isRateLimit)
                "AI is catching its breath (Rate Limit). Please wait a few seconds."
                else (response.error ?: "Unknown error")

            Resource.Error(finalError)
        }
    }

    suspend fun getAvailableModels(deviceId: String?): Resource<List<AiModelResponse>> = safeApiCall("Failed to fetch models") {
        Resource.Success(api.getAvailableModels(this@AiRepository.deviceId))
    }

    suspend fun warmupBackend(): Boolean {
        return try {
            val response = api.warmupBackend()
            response.isSuccessful || response.code() == 404 // 404 or success means server is awake/responding
        } catch (e: Exception) {
            Timber.w(e, "AiRepository: warmupBackend failed")
            false
        }
    }
}
