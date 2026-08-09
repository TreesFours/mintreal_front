// app/src/main/java/com/example/mistreal/data/repository/SocialRepository.kt
package com.example.mistreal.data.repository

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.example.mistreal.data.api.SocialApiService
import com.example.mistreal.data.models.SocialSyncResponse
import com.example.mistreal_mini.data.local.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("PropertyName")
class SocialRepository @Inject constructor(
    private val socialApi: SocialApiService,
    private val preferenceManager: PreferenceManager,
    @ApplicationContext private val context: Context
) {
    
    /**
     * Professional Dynamic Post Fetching
     */
    suspend fun getSocialPosts(deviceId: String): Result<SocialSyncResponse> = withContext(Dispatchers.IO) {
        try {
            val response = socialApi.syncAllPlatforms(mapOf("deviceId" to deviceId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generic Connector - Supports any platform dynamic ID
     */
    suspend fun initiateConnect(deviceId: String, platform: String) = withContext(Dispatchers.Main) {
        try {
            val auth = socialApi.getAuthUrl(platform, deviceId)
            openOAuthFlow(auth.authUrl)
        } catch (e: Exception) {
            throw e
        }
    }
    
    private fun openOAuthFlow(authUrl: String) {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setToolbarColor(android.graphics.Color.parseColor("#6B4CFF"))
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(authUrl))
    }
    
    suspend fun disconnectPlatform(deviceId: String, platform: String): Result<Map<String, Any>> = withContext(Dispatchers.IO) {
        try {
            val response = socialApi.disconnectPlatform(platform, mapOf("deviceId" to deviceId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
