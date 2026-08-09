// app/src/main/java/com/example/mistreal/domain/usecase/GetSocialPostsUseCase.kt
package com.example.mistreal.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal.data.models.SocialSyncResponse
import com.example.mistreal.data.repository.SocialRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetSocialPostsUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    operator fun invoke(deviceId: String): Flow<Resource<SocialSyncResponse>> = flow {
        emit(Resource.Loading())
        
        try {
            val result = socialRepository.getSocialPosts(deviceId)
            result.onSuccess { response ->
                emit(Resource.Success(response))
            }.onFailure { error ->
                emit(Resource.Error(error.message ?: "Failed to fetch social posts"))
            }
        } catch (e: Exception) {
            emit(Resource.Error(e.message ?: "Unknown error"))
        }
    }
}

class ConnectSocialPlatformUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    /**
     * Professional Generic Entry Point
     */
    suspend operator fun invoke(deviceId: String, platform: String) {
        socialRepository.initiateConnect(deviceId, platform)
    }
}

class DisconnectSocialPlatformUseCase @Inject constructor(
    private val socialRepository: SocialRepository
) {
    suspend operator fun invoke(deviceId: String, platform: String): Result<Map<String, Any>> {
        return socialRepository.disconnectPlatform(deviceId, platform)
    }
}
