package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
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
import com.example.mistreal_mini.util.DeviceIdProvider
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
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId

    suspend fun getWeather(lat: Double, lon: Double): Resource<WeatherResponse> = safeApiCall("Weather error") {
        val firebaseUid = authRepository.currentUser?.uid
        Resource.Success(api.getWeather(lat, lon, deviceId, firebaseUid))
    }

    suspend fun getNews(category: String?, location: String?, fastLoad: Boolean = false): Resource<NewsResponse> = safeApiCall("News error") {
        val firebaseUid = authRepository.currentUser?.uid
        Resource.Success(api.getNews(category, location, firebaseUid, fastLoad))
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
                val durationTag = v.durationSeconds?.let { secs ->
                    " [${secs / 60}:${(secs % 60).toString().padStart(2, '0')}]"
                } ?: ""
                com.example.mistreal_mini.data.model.SocialPost(
                    id = "youtube_${v.videoId}",
                    platform = "youtube",
                    author = v.channelTitle ?: "YouTube",
                    content = "${v.title}$durationTag",
                    timestamp = v.publishedAt ?: java.time.Instant.now().toString(),
                    type = "post",
                    imageUrl = v.thumbnailUrl,
                    likes = v.likeCount?.toInt(),
                    commentsCount = v.commentCount?.toInt(),
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

    suspend fun togglePin(article: com.example.mistreal_mini.data.api.Article): Resource<Boolean> = safeApiCall("Network error") {
        val firebaseUid = authRepository.currentUser?.uid ?: return@safeApiCall Resource.Error("Auth required")
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
    }

    suspend fun getAvailablePlatforms(deviceId: String?): Resource<List<SocialPlatformResponse>> = safeApiCall("Failed to fetch platforms") {
        Resource.Success(api.getAvailablePlatforms(deviceId))
    }

    suspend fun disconnectPlatform(deviceId: String, platform: String): Resource<Boolean> = safeApiCall("Network error") {
        val response = api.disconnectPlatform(platform, mapOf("deviceId" to deviceId))
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Platform disconnect failed")
    }

    /** Called on app start and whenever FCM hands out a new token (MistrealFirebaseMessagingService.onNewToken) — lets a backgrounded app be woken for an incoming call. */
    suspend fun registerFcmToken(fcmToken: String): Resource<Boolean> = safeApiCall("Failed to register push token") {
        val firebaseUid = authRepository.currentUser?.uid
        val response = api.registerFcmToken(
            buildMap {
                put("deviceId", deviceId)
                put("fcmToken", fcmToken)
                if (firebaseUid != null) put("firebaseUid", firebaseUid)
            }
        )
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Failed to register push token")
    }

    suspend fun syncSocials(deviceId: String?): Resource<SocialSyncResponse> = safeApiCall("Social sync error") {
        Resource.Success(api.syncSocials(deviceId))
    }

    suspend fun getAppConfig(): Resource<com.example.mistreal_mini.data.api.AppConfigResponse> = safeApiCall("Failed to load pricing") {
        Resource.Success(api.getAppConfig())
    }

    suspend fun verifyPayment(deviceId: String, purchaseToken: String, productId: String): Resource<com.example.mistreal_mini.data.api.PaymentVerifyResponse> =
        safeApiCall("Payment verification error") {
            // deviceId previously wasn't sent at all here — the backend had
            // nothing to grant the purchase against, so a real Play
            // purchase never actually upgraded the user. See addonService.ts.
            val response = api.verifyPayment(com.example.mistreal_mini.data.api.PaymentVerifyRequest(deviceId, purchaseToken, productId))
            if (response.success) Resource.Success(response)
            else Resource.Error(response.message ?: "Verification failed")
        }

    suspend fun getMyAddons(deviceId: String): Resource<List<String>> = safeApiCall("Failed to load add-ons") {
        val response = api.getMyAddons(deviceId)
        if (response.success) Resource.Success(response.addons ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load add-ons")
    }

    suspend fun createStripeCheckout(deviceId: String, addonId: String): Resource<String> = safeApiCall("Failed to start checkout") {
        val response = api.createStripeCheckout(com.example.mistreal_mini.data.api.StripeCheckoutRequest(deviceId, addonId))
        if (response.success && response.url != null) Resource.Success(response.url)
        else Resource.Error(response.error ?: "Failed to start checkout")
    }

    suspend fun createStripePortalSession(deviceId: String): Resource<String> = safeApiCall("Failed to open subscription management") {
        val response = api.createStripePortalSession(mapOf("deviceId" to deviceId))
        if (response.success && response.url != null) Resource.Success(response.url)
        else Resource.Error(response.error ?: "Failed to open subscription management")
    }

    suspend fun updateLocation(deviceId: String, lat: Double, lon: Double): Resource<Boolean> = safeApiCall("Network error") {
        val response = api.updateLocation(com.example.mistreal_mini.data.api.LocationRequest(deviceId, authRepository.currentUser?.uid, lat, lon))
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Location update failed")
    }

    suspend fun updateUserSettings(
        deviceId: String,
        userName: String?,
        aiPersona: String?,
        aiAudience: String?,
        autoReplyDelay: Int?,
        guardianEnabled: Boolean? = null,
        aiAutoSendEnabled: Boolean? = null
    ): Resource<Boolean> = safeApiCall("Update settings error") {
        val response = api.updateUserSettings(
            com.example.mistreal_mini.data.api.UserSettingsRequest(
                deviceId, authRepository.currentUser?.uid, userName, aiPersona, aiAudience, autoReplyDelay, guardianEnabled, aiAutoSendEnabled
            )
        )
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Failed to update settings")
    }

    suspend fun getUserSettings(deviceId: String): Resource<com.example.mistreal_mini.data.api.UserSettingsResponse> = safeApiCall("Fetch settings error") {
        val response = api.getUserSettings(deviceId)
        if (response.success) Resource.Success(response)
        else Resource.Error("Failed to fetch settings")
    }

    // --- BYOK (bring-your-own AI provider key) ---
    // Deliberately separate from updateUserSettings: the key must never round-trip
    // through a generic settings object other code paths might log or cache.

    suspend fun saveByokKey(providerType: String, apiKey: String, baseUrl: String?, modelName: String?): Resource<ByokStatusResponse> =
        safeApiCall("Save key error") {
            val response = aiProviderApi.saveByokKey(
                SaveByokKeyRequest(deviceId, authRepository.currentUser?.uid, providerType, apiKey, baseUrl, modelName)
            )
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to save key")
        }

    suspend fun clearByokKey(): Resource<Boolean> = safeApiCall("Clear key error") {
        val response = aiProviderApi.clearByokKey(ClearByokKeyRequest(deviceId, authRepository.currentUser?.uid))
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Failed to clear key")
    }

    suspend fun getByokStatus(): Resource<ByokStatusResponse> = safeApiCall("Fetch status error") {
        val response = aiProviderApi.getByokStatus(deviceId)
        if (response.success) Resource.Success(response)
        else Resource.Error(response.error ?: "Failed to fetch status")
    }

    // Separate BYOK slot for video editing — same reasoning as above, different
    // backend fields (byokVideo*) so a text provider and a video provider can be
    // configured independently.

    suspend fun saveByokVideoKey(providerType: String, apiKey: String, baseUrl: String?, modelName: String?): Resource<ByokStatusResponse> =
        safeApiCall("Save video key error") {
            val response = aiProviderApi.saveByokVideoKey(
                SaveByokKeyRequest(deviceId, authRepository.currentUser?.uid, providerType, apiKey, baseUrl, modelName)
            )
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to save video key")
        }

    suspend fun clearByokVideoKey(): Resource<Boolean> = safeApiCall("Clear video key error") {
        val response = aiProviderApi.clearByokVideoKey(ClearByokKeyRequest(deviceId, authRepository.currentUser?.uid))
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Failed to clear video key")
    }

    suspend fun getByokVideoStatus(): Resource<ByokStatusResponse> = safeApiCall("Fetch video status error") {
        val response = aiProviderApi.getByokVideoStatus(deviceId)
        if (response.success) Resource.Success(response)
        else Resource.Error(response.error ?: "Failed to fetch video status")
    }

    // --- Saved image/video GENERATION provider configs (multi, switchable) ---

    suspend fun addMediaProviderConfig(
        capability: String, label: String, providerType: String, apiKey: String, baseUrl: String, modelName: String?
    ): Resource<com.example.mistreal_mini.data.api.MediaProviderConfigSummary> = safeApiCall("Save provider error") {
        val response = aiProviderApi.addMediaProviderConfig(
            com.example.mistreal_mini.data.api.MediaProviderConfigRequest(
                deviceId, authRepository.currentUser?.uid, capability, label, providerType, apiKey, baseUrl, modelName
            )
        )
        if (response.success && response.config != null) Resource.Success(response.config)
        else Resource.Error(response.error ?: "Failed to save provider")
    }

    suspend fun getMediaProviderConfigs(capability: String): Resource<com.example.mistreal_mini.data.api.MediaProviderConfigListResponse> =
        safeApiCall("Fetch providers error") {
            val response = aiProviderApi.getMediaProviderConfigs(deviceId, capability)
            if (response.success) Resource.Success(response)
            else Resource.Error(response.error ?: "Failed to fetch providers")
        }

    suspend fun activateMediaProviderConfig(capability: String, configId: Int?): Resource<Int?> = safeApiCall("Switch provider error") {
        val response = aiProviderApi.activateMediaProviderConfig(
            com.example.mistreal_mini.data.api.MediaProviderActivateRequest(deviceId, authRepository.currentUser?.uid, capability, configId)
        )
        if (response.success) Resource.Success(response.activeConfigId)
        else Resource.Error(response.error ?: "Failed to switch provider")
    }

    suspend fun deleteMediaProviderConfig(id: Int): Resource<Boolean> = safeApiCall("Remove provider error") {
        val response = aiProviderApi.deleteMediaProviderConfig(id, deviceId)
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Failed to remove provider")
    }

    suspend fun performSocialAction(
        deviceId: String,
        type: String,
        platform: String,
        content: String,
        targetId: String,
        mediaBase64: String? = null,
        mediaMimeType: String? = null,
        shareToCommunity: Boolean = false,
        mistrealOnly: Boolean = false
    ): Resource<Boolean> = safeApiCall("Action error") {
        val response = api.performSocialAction(
            com.example.mistreal_mini.data.api.SocialActionRequest(
                deviceId = deviceId,
                type = type,
                platform = platform,
                content = content,
                targetId = targetId,
                mediaBase64 = mediaBase64,
                mediaMimeType = mediaMimeType,
                shareToCommunity = shareToCommunity,
                mistrealOnly = mistrealOnly
            )
        )
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Action failed")
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
    ): Resource<String> = safeApiCall("YouTube upload failed") {
        val videoPart = FileUtil.uriToMultipart(context, videoUri, "video")
            ?: return@safeApiCall Resource.Error("Couldn't read the selected video.")

        val response = api.uploadYoutubeVideo(
            deviceId = deviceId.toTextBody(),
            title = title.toTextBody(),
            description = description?.toTextBody(),
            privacyStatus = privacyStatus.toTextBody(),
            video = videoPart
        )
        if (response.success && response.url != null) Resource.Success(response.url)
        else Resource.Error(response.error ?: "YouTube upload failed")
    }

    suspend fun getYoutubeConnectStatus(): Resource<Boolean> = safeApiCall("Couldn't check YouTube connection status") {
        val response = api.getYoutubeConnectStatus(deviceId)
        Resource.Success(response.connected)
    }

    suspend fun initiateConnection(deviceId: String, platform: String): Resource<String> = safeApiCall("Network error") {
        val response = api.initiateConnection(mapOf("deviceId" to deviceId, "platform" to platform))
        val success = response["success"]
        val isSuccess = when (success) {
            is Boolean -> success
            is String -> success.lowercase() == "true"
            else -> false
        }

        if (isSuccess) Resource.Success(response["connectUrl"]?.toString() ?: "")
        else Resource.Error(response["error"]?.toString() ?: "Connection initiation failed")
    }

    suspend fun getContacts(deviceId: String, platform: String): Resource<List<com.example.mistreal_mini.data.api.SocialContact>> = safeApiCall("Network error") {
        val response = api.getContacts(deviceId, platform)
        if (response.success) Resource.Success(response.contacts)
        else Resource.Error("Failed to fetch contacts")
    }

    suspend fun searchContacts(deviceId: String, platform: String, query: String): Resource<List<com.example.mistreal_mini.data.api.SocialContact>> = safeApiCall("Network error") {
        val response = api.getContacts(deviceId, platform, query)
        if (response.success) Resource.Success(response.contacts)
        else Resource.Error("Search failed")
    }

    suspend fun setContactAutoReply(deviceId: String, platform: String, contactId: String, enabled: Boolean): Resource<Boolean> = safeApiCall("Network error") {
        val response = api.setContactAutoReply(
            com.example.mistreal_mini.data.api.ContactAutoReplyRequest(deviceId, platform, contactId, enabled)
        )
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Failed to update auto-reply")
    }

    suspend fun getUnreadMessages(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.UnreadItem>> = safeApiCall("Network error") {
        val response = api.getUnreadMessages(deviceId)
        if (response.success) Resource.Success(response.unreadItems)
        else Resource.Error("Failed to fetch unread messages")
    }

    suspend fun getSocialHistory(deviceId: String, platform: String, targetId: String): Resource<List<com.example.mistreal_mini.data.api.SocialHistoryMessage>> = safeApiCall("Network error") {
        val response = api.getSocialHistory(deviceId, platform, targetId)
        if (response.success) Resource.Success(response.messages)
        else Resource.Error("Failed to fetch history")
    }

    suspend fun fireEmergencyAlert(
        deviceId: String,
        latitude: Double,
        longitude: Double,
        distressSignature: String,
        triggerType: String = "manual",
        sosAudioBase64: String? = null,
        sosAudioMimeType: String? = null,
        sosImageBase64: String? = null,
        sosImageMimeType: String? = null
    ): Resource<com.example.mistreal_mini.data.api.EmergencyAlertFireResponse> = safeApiCall("Emergency alert error") {
        val response = api.fireEmergencyAlert(
            com.example.mistreal_mini.data.api.FireEmergencyAlertRequest(
                deviceId, authRepository.currentUser?.uid, latitude, longitude, distressSignature,
                triggerType, sosAudioBase64, sosAudioMimeType, sosImageBase64, sosImageMimeType
            )
        )
        if (response.success) Resource.Success(response)
        else Resource.Error(response.error ?: "Emergency alert failed")
    }

    suspend fun getEmergencyAlerts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.EmergencyAlert>> = safeApiCall("Failed to load alerts") {
        val response = api.getEmergencyAlerts(deviceId)
        if (response.success) Resource.Success(response.alerts ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load alerts")
    }

    suspend fun attachSosAudio(alertId: Int, deviceId: String, sosAudioBase64: String, sosAudioMimeType: String): Resource<Boolean> = safeApiCall("Failed to attach SOS audio") {
        val response = api.attachSosAudio(
            alertId,
            com.example.mistreal_mini.data.api.AttachSosAudioRequest(deviceId, sosAudioBase64, sosAudioMimeType)
        )
        if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to attach SOS audio")
    }

    /** Requires a live location — the server checks it against the owner's own pre-configured safe zone (anti-coercion). */
    suspend fun resolveEmergencyAlert(alertId: Int, deviceId: String, latitude: Double, longitude: Double): Resource<Boolean> = safeApiCall("Failed to resolve alert") {
        val response = api.resolveEmergencyAlert(
            alertId,
            com.example.mistreal_mini.data.api.ResolveEmergencyAlertRequest(deviceId, latitude, longitude)
        )
        if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to resolve alert")
    }

    suspend fun attachSosImage(alertId: Int, deviceId: String, sosImageBase64: String, sosImageMimeType: String): Resource<Boolean> = safeApiCall("Failed to attach SOS image") {
        val response = api.attachSosImage(
            alertId,
            com.example.mistreal_mini.data.api.AttachSosImageRequest(deviceId, sosImageBase64, sosImageMimeType)
        )
        if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to attach SOS image")
    }

    suspend fun getSafeZone(deviceId: String): Resource<com.example.mistreal_mini.data.api.SafeZone?> = safeApiCall("Failed to load safe zone") {
        val response = api.getSafeZone(deviceId)
        if (response.success) Resource.Success(response.safeZone) else Resource.Error(response.error ?: "Failed to load safe zone")
    }

    /** [latitude]/[longitude] must always be the device's own live location fix — never a manually-entered value, so a safe zone can't be configured remotely. */
    suspend fun setSafeZone(deviceId: String, latitude: Double, longitude: Double): Resource<com.example.mistreal_mini.data.api.SafeZone?> = safeApiCall("Failed to save safe zone") {
        val response = api.setSafeZone(com.example.mistreal_mini.data.api.SafeZoneRequest(deviceId, latitude, longitude))
        if (response.success) Resource.Success(response.safeZone) else Resource.Error(response.error ?: "Failed to save safe zone")
    }

    suspend fun getSosPreferences(deviceId: String): Resource<com.example.mistreal_mini.data.api.SosPreferences> = safeApiCall("Failed to load SOS preferences") {
        val response = api.getSosPreferences(deviceId)
        if (response.success) Resource.Success(response.preferences ?: com.example.mistreal_mini.data.api.SosPreferences())
        else Resource.Error(response.error ?: "Failed to load SOS preferences")
    }

    suspend fun setSosPreferences(
        deviceId: String,
        camera: Boolean? = null,
        microphone: Boolean? = null,
        image: Boolean? = null,
        fullDetailEscalation: Boolean? = null,
        silentMeetupWindowDays: Int? = null,
        communityBroadcastDelayDays: Int? = null,
        fullDetailWindowDays: Int? = null
    ): Resource<com.example.mistreal_mini.data.api.SosPreferences> = safeApiCall("Failed to save SOS preferences") {
        val response = api.setSosPreferences(
            com.example.mistreal_mini.data.api.SosPreferencesRequest(
                deviceId, camera, microphone, image, fullDetailEscalation,
                silentMeetupWindowDays, communityBroadcastDelayDays, fullDetailWindowDays
            )
        )
        if (response.success) Resource.Success(response.preferences ?: com.example.mistreal_mini.data.api.SosPreferences())
        else Resource.Error(response.error ?: "Failed to save SOS preferences")
    }

    suspend fun getNearbySosAlerts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.NearbySosAlert>> = safeApiCall("Failed to load nearby SOS alerts") {
        val response = api.getNearbySosAlerts(deviceId)
        if (response.success) Resource.Success(response.alerts ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load nearby SOS alerts")
    }

    suspend fun getEmergencyContacts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.EmergencyContact>> = safeApiCall("Failed to load emergency contacts") {
        val response = api.getEmergencyContacts(deviceId)
        if (response.success) Resource.Success(response.contacts ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load emergency contacts")
    }

    suspend fun addEmergencyContact(
        deviceId: String,
        name: String,
        channel: String,
        platform: String? = null,
        platformContactId: String? = null,
        email: String? = null,
        phoneNumber: String? = null
    ): Resource<com.example.mistreal_mini.data.api.EmergencyContact> = safeApiCall("Failed to add emergency contact") {
        val response = api.addEmergencyContact(
            com.example.mistreal_mini.data.api.AddEmergencyContactRequest(
                deviceId, authRepository.currentUser?.uid, name, channel, platform, platformContactId, email, phoneNumber
            )
        )
        if (response.success && response.contact != null) Resource.Success(response.contact)
        else Resource.Error(response.error ?: "Failed to add emergency contact")
    }

    suspend fun deleteEmergencyContact(id: Int, deviceId: String): Resource<Boolean> = safeApiCall("Failed to remove contact") {
        val response = api.deleteEmergencyContact(id, deviceId)
        if (response.success) Resource.Success(true) else Resource.Error(response.error ?: "Failed to remove contact")
    }

    suspend fun sendEmail(deviceId: String, toEmail: String, toName: String?, subject: String, body: String): Resource<Boolean> = safeApiCall("Email send error") {
        val response = api.sendEmail(
            com.example.mistreal_mini.data.api.SendEmailRequest(
                deviceId, authRepository.currentUser?.uid, toEmail, toName, subject, body
            )
        )
        if (response.success) Resource.Success(true)
        else Resource.Error(response.error ?: "Email send failed")
    }

    suspend fun getEmailHistory(deviceId: String, toEmail: String): Resource<List<com.example.mistreal_mini.data.api.EmailMessage>> = safeApiCall("Network error") {
        val response = api.getEmailHistory(deviceId, toEmail)
        if (response.success) Resource.Success(response.messages ?: emptyList())
        else Resource.Error("Failed to fetch email history")
    }

    suspend fun getEmailContacts(deviceId: String): Resource<List<com.example.mistreal_mini.data.api.EmailContactSummary>> = safeApiCall("Network error") {
        val response = api.getEmailContacts(deviceId)
        if (response.success) Resource.Success(response.contacts ?: emptyList())
        else Resource.Error("Failed to fetch email contacts")
    }

    suspend fun getCelestialVectors(bodyId: String, lat: Double?, lon: Double?): Resource<CelestialVectorResponse> = safeApiCall("Celestial data failure") {
        Resource.Success(api.getCelestialVectors(bodyId, lat, lon))
    }

    suspend fun getBankChannels(): Resource<List<com.example.mistreal_mini.data.api.BankChannel>> =
        safeApiCall("Could not load bank list", logTag = "InfoRepository.getBankChannels failed") {
            val response = api.getBankChannels(deviceId)
            if (response.success) Resource.Success(response.banks)
            else {
                timber.log.Timber.w("InfoRepository.getBankChannels: success=false — %s", response.error)
                Resource.Error(response.error ?: "Could not load bank list")
            }
        }

    suspend fun getNearbyPlaces(lat: Double, lon: Double, radius: Double, category: String): Resource<List<com.example.mistreal_mini.data.model.DiscoveryResult>> = safeApiCall("Discovery lookup failed") {
        val response = api.getNearbyPlaces(lat, lon, radius, category)
        // 🛰️ succeeded=false means every Overpass mirror failed/timed out — that's a
        // service outage, not "genuinely nothing nearby." Surface it as an error so the
        // UI doesn't claim "not found" for what's actually a lookup failure.
        if (response.succeeded) Resource.Success(response.results)
        else Resource.Error("Discovery service unavailable — try again")
    }
}
