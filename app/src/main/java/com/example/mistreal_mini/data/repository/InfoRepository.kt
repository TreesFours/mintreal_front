package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.api.NewsResponse
import com.example.mistreal_mini.data.api.SocialSyncResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfoRepository @Inject constructor(
    private val api: InfoApiService
) {
    suspend fun getWeather(lat: Double, lon: Double): Resource<WeatherResponse> {
        return try {
            Resource.Success(api.getWeather(lat, lon))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Weather error")
        }
    }

    suspend fun getNews(category: String?, location: String?): Resource<NewsResponse> {
        return try {
            Resource.Success(api.getNews(category, location))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "News error")
        }
    }

    suspend fun syncSocials(deviceId: String?): Resource<SocialSyncResponse> {
        return try {
            Resource.Success(api.syncSocials(deviceId))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Social sync error")
        }
    }

    suspend fun performSocialAction(
        deviceId: String,
        type: String,
        platform: String,
        content: String,
        targetId: String,
        delayMinutes: Int? = 0
    ): Resource<Boolean> {
        return try {
            val response = api.performSocialAction(
                com.example.mistreal_mini.data.api.SocialActionRequest(
                    deviceId,
                    com.example.mistreal_mini.data.api.SocialAction(type, platform, content, targetId),
                    delayMinutes
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Action failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Action error")
        }
    }
}
