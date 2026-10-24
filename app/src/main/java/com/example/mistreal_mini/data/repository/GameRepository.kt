package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.data.api.GameSummary
import com.example.mistreal_mini.data.api.InfoApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepository @Inject constructor(
    private val api: InfoApiService
) {
    suspend fun listGames(category: String? = null): Resource<List<GameSummary>> =
        safeApiCall("Failed to load games", logTag = "GameRepository.listGames failed") {
            val response = api.listGames(category)
            if (response.success) Resource.Success(response.games)
            else Resource.Error(response.error ?: "Failed to load games")
        }
}
