package com.example.mistreal_mini.data.api

import com.example.mistreal_mini.data.model.SocialAuth
import com.example.mistreal_mini.data.model.SocialSyncResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Professional Social API Service
 * 100% Dynamic - No platform-specific hardcoded methods.
 */
interface SocialApiService {
    
    // === OAuth Routes ===
    
    @GET("connect/{platform}")
    suspend fun getAuthUrl(
        @Path("platform") platform: String, 
        @Query("deviceId") deviceId: String
    ): SocialAuth
    
    // === Sync Routes ===
    
    @POST("sync")
    suspend fun syncAllPlatforms(@Body request: Map<String, String>): SocialSyncResponse
    
    // === Disconnect Routes ===
    
    @POST("disconnect/{platform}")
    suspend fun disconnectPlatform(
        @Path("platform") platform: String,
        @Body request: Map<String, String>
    ): Map<String, Any>
}
