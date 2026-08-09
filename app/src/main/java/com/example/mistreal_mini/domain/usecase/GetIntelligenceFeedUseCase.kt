package com.example.mistreal_mini.domain.usecase

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.NewsResponse
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetIntelligenceFeedUseCase @Inject constructor(
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper,
    private val preferenceManager: PreferenceManager
) {
    suspend fun getWeather(): Resource<WeatherResponse> {
        return if (preferenceManager.isLocationEnabled.first()) {
            val location = locationHelper.getCurrentLocation()
            if (location != null) {
                val lat = location.latitude
                val lon = location.longitude
                infoRepository.getWeather(lat, lon)
            } else {
                Resource.Error("Location permission denied or unavailable")
            }
        } else {
            // Default fallback
            infoRepository.getWeather(51.5074, -0.1278)
        }
    }

    suspend fun getNews(category: String = "general", country: String = "us"): Resource<NewsResponse> {
        return infoRepository.getNews(category, country)
    }
}
