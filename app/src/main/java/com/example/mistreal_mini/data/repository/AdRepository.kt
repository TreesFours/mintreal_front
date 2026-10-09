package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AdEventRequest
import com.example.mistreal_mini.data.api.AdPayload
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.util.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdRepository @Inject constructor(
    private val api: InfoApiService,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) {
    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    private fun String.toTextBody() = this.toRequestBody("text/plain".toMediaTypeOrNull())

    suspend fun createAd(
        businessId: String,
        mediaType: String,
        durationSeconds: Int,
        caption: String?,
        targetUrl: String?,
        ctaLabel: String?,
        videoUri: Uri?,
        imageUris: List<Uri>?
    ): Resource<Int> {
        return try {
            val videoPart = videoUri?.let { FileUtil.uriToMultipart(context, it, "video") }
            val imageParts = imageUris?.mapNotNull { FileUtil.uriToMultipart(context, it, "images") }

            val response = api.createAd(
                deviceId = deviceId.toTextBody(),
                firebaseUid = authRepository.currentUser?.uid?.toTextBody(),
                businessId = businessId.toTextBody(),
                mediaType = mediaType.toTextBody(),
                durationSeconds = durationSeconds.toString().toTextBody(),
                caption = caption?.toTextBody(),
                targetUrl = targetUrl?.toTextBody(),
                ctaLabel = ctaLabel?.toTextBody(),
                video = videoPart,
                images = imageParts
            )
            if (response.success && response.adId != null) {
                Resource.Success(response.adId)
            } else {
                Timber.w("AdRepository.createAd: backend rejected — %s", response.error)
                Resource.Error(response.error ?: "Failed to create ad")
            }
        } catch (e: Exception) {
            Timber.e(e, "AdRepository.createAd failed")
            Resource.Error(e.message ?: "Failed to create ad")
        }
    }

    suspend fun getDueAd(): AdPayload? {
        return try {
            api.getDueAd(deviceId).takeIf { it.success && it.due }?.ad
        } catch (e: Exception) {
            Timber.e(e, "AdRepository.getDueAd failed")
            null
        }
    }

    suspend fun trackAdEvent(adId: Int, eventType: String) {
        try {
            api.trackAdEvent(adId, AdEventRequest(deviceId, eventType))
        } catch (e: Exception) {
            // Best-effort — a failed tracking call must never surface to the user, but still log it.
            Timber.w(e, "AdRepository.trackAdEvent(%d, %s) failed", adId, eventType)
        }
    }
}
