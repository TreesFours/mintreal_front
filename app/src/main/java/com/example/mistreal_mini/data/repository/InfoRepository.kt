package com.example.mistreal_mini.data.repository

import android.content.Context
import android.provider.Settings
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AiProviderApiService
import com.example.mistreal_mini.data.api.ByokStatusResponse
import com.example.mistreal_mini.data.api.ClearByokKeyRequest
import com.example.mistreal_mini.data.api.SaveByokKeyRequest
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.WeatherResponse
import com.example.mistreal_mini.data.api.NewsResponse
import com.example.mistreal_mini.data.model.SocialSyncResponse
import com.example.mistreal_mini.data.api.SocialPlatformResponse
import com.example.mistreal_mini.data.api.CelestialVectorResponse
import com.example.mistreal_mini.data.repository.AuthRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InfoRepository @Inject constructor(
    private val api: InfoApiService,
    private val aiProviderApi: AiProviderApiService,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    suspend fun getWeather(lat: Double, lon: Double): Resource<WeatherResponse> {
        return try {
            val firebaseUid = authRepository.currentUser?.uid
            Resource.Success(api.getWeather(lat, lon, deviceId, firebaseUid))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Weather error")
        }
    }

    suspend fun getNews(category: String?, location: String?, fastLoad: Boolean = false): Resource<NewsResponse> {
        return try {
            val firebaseUid = authRepository.currentUser?.uid
            Resource.Success(api.getNews(category, location, firebaseUid, fastLoad))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "News error")
        }
    }

    suspend fun togglePin(article: com.example.mistreal_mini.data.api.Article): Resource<Boolean> {
        return try {
            val firebaseUid = authRepository.currentUser?.uid ?: return Resource.Error("Auth required")
            val isCurrentlyPinned = article.isPinned ?: false
            val request = com.example.mistreal_mini.data.api.PinIntelRequest(
                firebaseUid = firebaseUid,
                itemTitle = article.title,
                itemUrl = article.url,
                itemType = article.type ?: "news"
            )
            val response = if (isCurrentlyPinned) api.unpinIntel(request) else api.pinIntel(request)
            if (response["success"] == true) Resource.Success(true)
            else Resource.Error("Pin action failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
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
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Platform disconnect failed")
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
            val response = api.updateLocation(com.example.mistreal_mini.data.api.LocationRequest(deviceId, authRepository.currentUser?.uid, lat, lon))
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
        aiAudience: String?,
        autoReplyDelay: Int?,
        guardianEnabled: Boolean? = null,
        emergencyContacts: List<com.example.mistreal_mini.data.api.EmergencyContact>? = null,
        aiAutoSendEnabled: Boolean? = null
    ): Resource<Boolean> {
        return try {
            val response = api.updateUserSettings(
                com.example.mistreal_mini.data.api.UserSettingsRequest(
                    deviceId, authRepository.currentUser?.uid, userName, aiPersona, aiAudience, autoReplyDelay, guardianEnabled, emergencyContacts, aiAutoSendEnabled
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Failed to update settings")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Update settings error")
        }
    }

    suspend fun getUserSettings(deviceId: String): Resource<com.example.mistreal_mini.data.api.UserSettingsResponse> {
        return try {
            val response = api.getUserSettings(deviceId)
            if (response.success) Resource.Success(response)
            else Resource.Error("Failed to fetch settings")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Fetch settings error")
        }
    }

    // --- BYOK (bring-your-own AI provider key) ---
    // Deliberately separate from updateUserSettings: the key must never round-trip
    // through a generic settings object other code paths might log or cache.

    suspend fun saveByokKey(providerType: String, apiKey: String, baseUrl: String?, modelName: String?): Resource<ByokStatusResponse> {
        return try {
            val response = aiProviderApi.saveByokKey(
                SaveByokKeyRequest(deviceId, authRepository.currentUser?.uid, providerType, apiKey, baseUrl, modelName)
            )
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to save key")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Save key error")
        }
    }

    suspend fun clearByokKey(): Resource<Boolean> {
        return try {
            val response = aiProviderApi.clearByokKey(ClearByokKeyRequest(deviceId, authRepository.currentUser?.uid))
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Failed to clear key")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Clear key error")
        }
    }

    suspend fun getByokStatus(): Resource<ByokStatusResponse> {
        return try {
            val response = aiProviderApi.getByokStatus(deviceId)
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to fetch status")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Fetch status error")
        }
    }

    suspend fun performSocialAction(
        deviceId: String,
        type: String,
        platform: String,
        content: String,
        targetId: String,
        delayMinutes: Int? = 0,
        mediaBase64: String? = null,
        mediaMimeType: String? = null
    ): Resource<Boolean> {
        return try {
            val response = api.performSocialAction(
                com.example.mistreal_mini.data.api.SocialActionRequest(
                    deviceId,
                    com.example.mistreal_mini.data.api.SocialAction(type, platform, content, targetId, mediaBase64, mediaMimeType),
                    delayMinutes
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Action failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Action error")
        }
    }

    suspend fun initiateConnection(deviceId: String, platform: String): Resource<String> {
        return try {
            val response = api.initiateConnection(mapOf("deviceId" to deviceId, "platform" to platform))
            val success = response["success"]
            val isSuccess = when (success) {
                is Boolean -> success
                is String -> success.lowercase() == "true"
                else -> false
            }
            
            if (isSuccess) Resource.Success(response["connectUrl"]?.toString() ?: "")
            else Resource.Error(response["error"]?.toString() ?: "Connection initiation failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
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

    suspend fun setContactAutoReply(deviceId: String, platform: String, contactId: String, enabled: Boolean): Resource<Boolean> {
        return try {
            val response = api.setContactAutoReply(
                com.example.mistreal_mini.data.api.ContactAutoReplyRequest(deviceId, platform, contactId, enabled)
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Failed to update auto-reply")
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
        distressSignature: String,
        broadcastToSocials: Boolean = false
    ): Resource<com.example.mistreal_mini.data.api.EmergencyAlertResponse> {
        return try {
            val response = api.sendEmergencyAlert(
                com.example.mistreal_mini.data.api.EmergencyAlertRequest(
                    deviceId, authRepository.currentUser?.uid, latitude, longitude, distressSignature, broadcastToSocials
                )
            )
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Emergency alert failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Emergency alert error")
        }
    }

    suspend fun sendEmail(deviceId: String, toEmail: String, toName: String?, subject: String, body: String): Resource<Boolean> {
        return try {
            val response = api.sendEmail(
                com.example.mistreal_mini.data.api.SendEmailRequest(
                    deviceId, authRepository.currentUser?.uid, toEmail, toName, subject, body
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Email send failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Email send error")
        }
    }

    suspend fun getEmailHistory(deviceId: String, toEmail: String): Resource<List<com.example.mistreal_mini.data.api.EmailMessage>> {
        return try {
            val response = api.getEmailHistory(deviceId, toEmail)
            if (response.success) Resource.Success(response.messages ?: emptyList())
            else Resource.Error("Failed to fetch email history")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun getEmailContacts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.EmailContactSummary>> {
        return try {
            val response = api.getEmailContacts(deviceId)
            if (response.success) Resource.Success(response.contacts ?: emptyList())
            else Resource.Error("Failed to fetch email contacts")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Network error")
        }
    }

    suspend fun getCelestialVectors(bodyId: String, lat: Double?, lon: Double?): Resource<CelestialVectorResponse> {
        return try {
            Resource.Success(api.getCelestialVectors(bodyId, lat, lon))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Celestial data failure")
        }
    }

    suspend fun getNearbyPlaces(lat: Double, lon: Double, radius: Double, category: String): Resource<List<com.example.mistreal_mini.data.model.DiscoveryResult>> {
        return try {
            val response = api.getNearbyPlaces(lat, lon, radius, category)
            // 🛰️ succeeded=false means every Overpass mirror failed/timed out — that's a
            // service outage, not "genuinely nothing nearby." Surface it as an error so the
            // UI doesn't claim "not found" for what's actually a lookup failure.
            if (response.succeeded) Resource.Success(response.results)
            else Resource.Error("Discovery service unavailable — try again")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Discovery lookup failed")
        }
    }
}
