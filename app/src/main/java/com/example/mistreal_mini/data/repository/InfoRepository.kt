package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.api.NewsResponse
import com.example.mistreal.data.models.SocialSyncResponse
import com.example.mistreal_mini.data.api.SocialPlatformResponse
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

    suspend fun getAvailablePlatforms(deviceId: String?): Resource<List<SocialPlatformResponse>> {
        return try {
            Resource.Success(api.getAvailablePlatforms(deviceId))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch platforms")
        }
    }

    suspend fun disconnectPlatform(deviceId: String, platform: String): Resource<Boolean> {
        return try {
            val response = api.disconnectPlatform(platform, mapOf("deviceId" to deviceId))
            // The API returns success: true as a Boolean in the map
            if (response["success"] == true) Resource.Success(true)
            else Resource.Error("Platform disconnect failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun syncSocials(deviceId: String?): Resource<SocialSyncResponse> {
        return try {
            Resource.Success(api.syncSocials(deviceId))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Social sync error")
        }
    }

    suspend fun getAppConfig(): Resource<com.example.mistreal_mini.data.api.AppConfigResponse> {
        return try {
            Resource.Success(api.getAppConfig())
        } catch (e: Exception) {
            Resource.Error("Failed to load pricing")
        }
    }

    suspend fun verifyPayment(purchaseToken: String, productId: String): Resource<Boolean> {
        return try {
            val response = api.verifyPayment(com.example.mistreal_mini.data.api.PaymentVerifyRequest(purchaseToken, productId))
            if (response.success) Resource.Success(true)
            else Resource.Error(response.message ?: "Verification failed")
        } catch (e: Exception) {
            Resource.Error("Payment verification error")
        }
    }

    suspend fun updateLocation(deviceId: String, lat: Double, lon: Double): Resource<Boolean> {
        return try {
            val response = api.updateLocation(com.example.mistreal_mini.data.api.LocationRequest(deviceId, lat, lon))
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Location update failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun updateUserSettings(
        deviceId: String,
        userName: String?,
        aiPersona: String?,
        autoReplyDelay: Int?,
        guardianEnabled: Boolean? = null,
        emergencyContacts: List<com.example.mistreal_mini.data.api.EmergencyContact>? = null
    ): Resource<Boolean> {
        return try {
            val response = api.updateUserSettings(
                com.example.mistreal_mini.data.api.UserSettingsRequest(
                    deviceId, userName, aiPersona, autoReplyDelay, guardianEnabled, emergencyContacts
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Failed to update settings")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Update settings error")
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

    suspend fun getContacts(deviceId: String, platform: String): Resource<List<com.example.mistreal_mini.data.api.SocialContact>> {
        return try {
            val response = api.getContacts(deviceId, platform)
            if (response.success) Resource.Success(response.contacts)
            else Resource.Error("Failed to fetch contacts")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun searchContacts(deviceId: String, platform: String, query: String): Resource<List<com.example.mistreal_mini.data.api.SocialContact>> {
        return try {
            val response = api.getContacts(deviceId, platform, query)
            if (response.success) Resource.Success(response.contacts)
            else Resource.Error("Search failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun getUnreadMessages(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.UnreadItem>> {
        return try {
            val response = api.getUnreadMessages(deviceId)
            if (response.success) Resource.Success(response.unreadItems)
            else Resource.Error("Failed to fetch unread messages")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun getSocialHistory(deviceId: String, platform: String, targetId: String): Resource<List<com.example.mistreal_mini.data.api.SocialHistoryMessage>> {
        return try {
            val response = api.getSocialHistory(deviceId, platform, targetId)
            if (response.success) Resource.Success(response.messages)
            else Resource.Error("Failed to fetch history")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun sendEmergencyAlert(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        distressSignature: String
    ): Resource<Boolean> {
        return try {
            val response = api.sendEmergencyAlert(
                com.example.mistreal_mini.data.api.EmergencyAlertRequest(
                    deviceId, latitude, longitude, distressSignature
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Emergency alert failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Emergency alert error")
        }
    }

    suspend fun getCelestialVectors(bodyId: String): Resource<com.example.mistreal_mini.data.api.CelestialVectorResponse> {
        return try {
            Resource.Success(api.getCelestialVectors(bodyId))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Celestial data failure")
        }
    }

    suspend fun getNearbyPlaces(lat: Double, lon: Double, radius: Double, category: String): Resource<List<com.example.mistreal_mini.data.model.DiscoveryResult>> {
        return try {
            Resource.Success(api.getNearbyPlaces(lat, lon, radius, category).results)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Discovery lookup failed")
        }
    }
}
