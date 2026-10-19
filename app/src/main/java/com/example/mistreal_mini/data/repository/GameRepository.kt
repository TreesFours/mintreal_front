package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.GameSummary
import com.example.mistreal_mini.data.api.InfoApiService
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val api: InfoApiService
) {
    suspend fun listGames(category: String? = null): Resource<List<GameSummary>> {
        return try {
            val response = api.listGames(category)
            if (response.success) Resource.Success(response.games)
            else Resource.Error(response.error ?: "Failed to load games")
        } catch (e: Exception) {
            Timber.e(e, "GameRepository.listGames failed")
            Resource.Error(e.message ?: "Failed to load games")
        }
    }
}
