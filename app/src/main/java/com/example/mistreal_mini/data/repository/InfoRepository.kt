package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
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
import com.example.mistreal_mini.util.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
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

    // Mapped into the existing flat SocialPost shape at this boundary so
    // FeedPostCard/SocialPagerView need no branching changes for most of the
    // pipeline — sourceUrl carries a "youtube://{videoId}" sentinel the
    // client recognizes to route taps to the WebView player instead of the
    // ExoPlayer-based FeedVideoPlayer (YouTube never gives a direct
    // streamable URL).
    suspend fun getYoutubeVideos(): List<com.example.mistreal_mini.data.model.SocialPost> {
        return try {
            api.getYoutubeVideos().videos.map { v ->
                com.example.mistreal_mini.data.model.SocialPost(
                    id = "youtube_${v.videoId}",
                    platform = "youtube",
                    author = v.channelTitle ?: "YouTube",
                    content = v.title,
                    timestamp = v.publishedAt ?: java.time.Instant.now().toString(),
                    type = "post",
                    imageUrl = v.thumbnailUrl,
                    likes = v.likeCount?.toInt(),
                    sourceUrl = "youtube://${v.videoId}",
                    platformIcon = "▶️",
                    platformColor = "#FF0000",
                    platformDisplayName = "YouTube"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun setCommunityFeedPreferences(platforms: List<String>) {
        try {
            val firebaseUid = authRepository.currentUser?.uid
            api.setCommunityFeedPreferences(
                com.example.mistreal_mini.data.api.CommunityPreferencesRequest(deviceId, firebaseUid, platforms)
            )
        } catch (e: Exception) {
            // Best-effort — the local DataStore write already happened; a
            // failed server sync just means the next app open re-syncs it.
        }
    }

    // Best-effort only — no photo API key configured, or no match found, both
    // degrade to null rather than surfacing an error; saving intel must never
    // fail just because an illustrative photo couldn't be found.
    suspend fun getLocationPhoto(label: String, lat: Double, lon: Double): String? {
        return try {
            api.getLocationPhoto(label, lat, lon).photoUrl
        } catch (e: Exception) {
            null
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

    suspend fun verifyPayment(deviceId: String, purchaseToken: String, productId: String): Resource<com.example.mistreal_mini.data.api.PaymentVerifyResponse> {
        return try {
            // deviceId previously wasn't sent at all here — the backend had
            // nothing to grant the purchase against, so a real Play
            // purchase never actually upgraded the user. See addonService.ts.
            val response = api.verifyPayment(com.example.mistreal_mini.data.api.PaymentVerifyRequest(deviceId, purchaseToken, productId))
            if (response.success) Resource.Success(response)
            else Resource.Error(response.message ?: "Verification failed")
        } catch (e: Exception) {
            Resource.Error("Payment verification error")
        }
    }

    suspend fun getMyAddons(deviceId: String): Resource<List<String>> {
        return try {
            val response = api.getMyAddons(deviceId)
            if (response.success) Resource.Success(response.addons ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load add-ons")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load add-ons")
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
        aiAutoSendEnabled: Boolean? = null
    ): Resource<Boolean> {
        return try {
            val response = api.updateUserSettings(
                com.example.mistreal_mini.data.api.UserSettingsRequest(
                    deviceId, authRepository.currentUser?.uid, userName, aiPersona, aiAudience, autoReplyDelay, guardianEnabled, aiAutoSendEnabled
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

    // Separate BYOK slot for video editing — same reasoning as above, different
    // backend fields (byokVideo*) so a text provider and a video provider can be
    // configured independently.

    suspend fun saveByokVideoKey(providerType: String, apiKey: String, baseUrl: String?, modelName: String?): Resource<ByokStatusResponse> {
        return try {
            val response = aiProviderApi.saveByokVideoKey(
                SaveByokKeyRequest(deviceId, authRepository.currentUser?.uid, providerType, apiKey, baseUrl, modelName)
            )
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to save video key")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Save video key error")
        }
    }

    suspend fun clearByokVideoKey(): Resource<Boolean> {
        return try {
            val response = aiProviderApi.clearByokVideoKey(ClearByokKeyRequest(deviceId, authRepository.currentUser?.uid))
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Failed to clear video key")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Clear video key error")
        }
    }

    suspend fun getByokVideoStatus(): Resource<ByokStatusResponse> {
        return try {
            val response = aiProviderApi.getByokVideoStatus(deviceId)
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to fetch video status")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Fetch video status error")
        }
    }

    // --- Saved image/video GENERATION provider configs (multi, switchable) ---

    suspend fun addMediaProviderConfig(
        capability: String, label: String, providerType: String, apiKey: String, baseUrl: String, modelName: String?
    ): Resource<com.example.mistreal_mini.data.api.MediaProviderConfigSummary> {
        return try {
            val response = aiProviderApi.addMediaProviderConfig(
                com.example.mistreal_mini.data.api.MediaProviderConfigRequest(
                    deviceId, authRepository.currentUser?.uid, capability, label, providerType, apiKey, baseUrl, modelName
                )
            )
            if (response.success && response.config != null) Resource.Success(response.config)
            else Resource.Error(response.error ?: "Failed to save provider")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Save provider error")
        }
    }

    suspend fun getMediaProviderConfigs(capability: String): Resource<com.example.mistreal_mini.data.api.MediaProviderConfigListResponse> {
        return try {
            val response = aiProviderApi.getMediaProviderConfigs(deviceId, capability)
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to fetch providers")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Fetch providers error")
        }
    }

    suspend fun activateMediaProviderConfig(capability: String, configId: Int?): Resource<Int?> {
        return try {
            val response = aiProviderApi.activateMediaProviderConfig(
                com.example.mistreal_mini.data.api.MediaProviderActivateRequest(deviceId, authRepository.currentUser?.uid, capability, configId)
            )
            if (response.success) Resource.Success(response.activeConfigId)
            else Resource.Error(response.error ?: "Failed to switch provider")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Switch provider error")
        }
    }

    suspend fun deleteMediaProviderConfig(id: Int): Resource<Boolean> {
        return try {
            val response = aiProviderApi.deleteMediaProviderConfig(id, deviceId)
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Failed to remove provider")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Remove provider error")
        }
    }

    suspend fun performSocialAction(
        deviceId: String,
        type: String,
        platform: String,
        content: String,
        targetId: String,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        shareToCommunity: Boolean = false
    ): Resource<Boolean> {
        return try {
            val response = api.performSocialAction(
                com.example.mistreal_mini.data.api.SocialActionRequest(
                    deviceId = deviceId,
                    type = type,
                    platform = platform,
                    content = content,
                    targetId = targetId,
                    mediaBase64 = mediaBase64,
                    mediaMimeType = mediaMimeType,
                    shareToCommunity = shareToCommunity
                )
            )
            if (response.success) Resource.Success(true)
            else Resource.Error(response.error ?: "Action failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Action error")
        }
    }

    private fun String.toTextBody() = this.toRequestBody("text/plain".toMediaTypeOrNull())

    // Native upload — bypasses performSocialAction/Zernio entirely, since
    // Zernio has no YouTube posting capability at all. Requires the device
    // to have already connected YouTube via the native Google OAuth flow
    // (same "Connect" button in Social Connections as every other platform —
    // the backend routes it differently once it sees platform == "youtube").
    suspend fun uploadYoutubeVideo(
        title: String,
        description: String?,
        privacyStatus: String,
        videoUri: Uri
    ): Resource<String> {
        return try {
            val videoPart = FileUtil.uriToMultipart(context, videoUri, "video")
                ?: return Resource.Error("Couldn't read the selected video.")

            val response = api.uploadYoutubeVideo(
                deviceId = deviceId.toTextBody(),
                title = title.toTextBody(),
                description = description?.toTextBody(),
                privacyStatus = privacyStatus.toTextBody(),
                video = videoPart
            )
            if (response.success && response.url != null) Resource.Success(response.url)
            else Resource.Error(response.error ?: "YouTube upload failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "YouTube upload failed")
        }
    }

    suspend fun getYoutubeConnectStatus(): Resource<Boolean> {
        return try {
            val response = api.getYoutubeConnectStatus(deviceId)
            Resource.Success(response.connected)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Couldn't check YouTube connection status")
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

    suspend fun fireEmergencyAlert(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        distressSignature: String,
        triggerType: String = "manual",
        sosAudioBase64: String? = null,
        sosAudioMimeType: String? = null
    ): Resource<com.example.mistreal_mini.data.api.EmergencyAlertFireResponse> {
        return try {
            val response = api.fireEmergencyAlert(
                com.example.mistreal_mini.data.api.FireEmergencyAlertRequest(
                    deviceId, authRepository.currentUser?.uid, latitude, longitude, distressSignature,
                    triggerType, sosAudioBase64, sosAudioMimeType
                )
            )
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Emergency alert failed")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Emergency alert error")
        }
    }

    suspend fun getEmergencyAlerts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.EmergencyAlert>> {
        return try {
            val response = api.getEmergencyAlerts(deviceId)
            if (response.success) Resource.Success(response.alerts ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load alerts")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load alerts")
        }
    }

    suspend fun attachSosAudio(alertId: Int, deviceId: String, sosAudioBase64: String, sosAudioMimeType: String): Resource<Boolean> {
        return try {
            val response = api.attachSosAudio(
                alertId,
                com.example.mistreal_mini.data.api.AttachSosAudioRequest(deviceId, sosAudioBase64, sosAudioMimeType)
            )
            if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to attach SOS audio")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to attach SOS audio")
        }
    }

    suspend fun resolveEmergencyAlert(alertId: Int, deviceId: String): Resource<Boolean> {
        return try {
            val response = api.resolveEmergencyAlert(alertId, mapOf("deviceId" to deviceId))
            if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to resolve alert")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to resolve alert")
        }
    }

    suspend fun getEmergencyContacts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.EmergencyContact>> {
        return try {
            val response = api.getEmergencyContacts(deviceId)
            if (response.success) Resource.Success(response.contacts ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load emergency contacts")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load emergency contacts")
        }
    }

    suspend fun addEmergencyContact(
        deviceId: String,
        name: String,
        channel: String,
        platform: String? = null,
        platformContactId: String? = null,
        email: String? = null,
        phoneNumber: String? = null
    ): Resource<com.example.mistreal_mini.data.api.EmergencyContact> {
        return try {
            val response = api.addEmergencyContact(
                com.example.mistreal_mini.data.api.AddEmergencyContactRequest(
                    deviceId, authRepository.currentUser?.uid, name, channel, platform, platformContactId, email, phoneNumber
                )
            )
            if (response.success && response.contact != null) Resource.Success(response.contact)
            else Resource.Error(response.error ?: "Failed to add emergency contact")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add emergency contact")
        }
    }

    suspend fun deleteEmergencyContact(id: Int, deviceId: String): Resource<Boolean> {
        return try {
            val response = api.deleteEmergencyContact(id, deviceId)
            if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to remove contact")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove contact")
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

    suspend fun getBankChannels(): Resource<List<com.example.mistreal_mini.data.api.BankChannel>> {
        return try {
            val response = api.getBankChannels(deviceId)
            if (response.success) Resource.Success(response.banks)
            else {
                timber.log.Timber.w("InfoRepository.getBankChannels: success=false — %s", response.error)
                Resource.Error(response.error ?: "Could not load bank list")
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "InfoRepository.getBankChannels failed")
            Resource.Error(e.message ?: "Could not load bank list")
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
