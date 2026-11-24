package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AdCampaign
import com.example.mistreal_mini.data.api.AdsApiService
import com.example.mistreal_mini.data.api.ConnectAdAccountRequest
import com.example.mistreal_mini.data.api.CreateCampaignRequest
import com.example.mistreal_mini.data.api.UpdateCampaignRequest
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.util.DeviceIdProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsRepository @Inject constructor(
    private val api: AdsApiService,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId get() = deviceIdProvider.deviceId

    suspend fun connectAdAccount(businessId: String, platform: String): Resource<String> =
        safeApiCall("Failed to start ad account connection") {
            val response = api.connectAdAccount(ConnectAdAccountRequest(deviceId, businessId, platform))
            if (response.success && response.connectUrl != null) Resource.Success(response.connectUrl)
            else Resource.Error(response.error ?: "Failed to start ad account connection")
        }

    suspend fun createCampaign(businessId: String, platform: String, name: String, budget: Double, objective: String): Resource<AdCampaign> =
        safeApiCall("Failed to create campaign") {
            val response = api.createCampaign(CreateCampaignRequest(deviceId, businessId, platform, name, budget, objective))
            if (response.success && response.campaign != null) Resource.Success(response.campaign)
            else Resource.Error(response.error ?: "Failed to create campaign")
        }

    suspend fun updateCampaignStatus(campaignId: String, status: String): Resource<Unit> =
        safeApiCall("Failed to update campaign") {
            val response = api.updateCampaign(campaignId, UpdateCampaignRequest(status = status))
            if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to update campaign")
        }

    suspend fun deleteCampaign(campaignId: String): Resource<Unit> = safeApiCall("Failed to delete campaign") {
        val response = api.deleteCampaign(campaignId)
        if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to delete campaign")
    }

    suspend fun getCampaigns(businessId: String, platform: String): Resource<List<AdCampaign>> =
        safeApiCall("Failed to load campaigns") {
            val response = api.getCampaigns(deviceId, businessId, platform)
            if (response.success) Resource.Success(response.campaigns ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load campaigns")
        }

    suspend fun getCampaignAnalytics(campaignId: String): Resource<Map<String, Any?>> =
        safeApiCall("Failed to load analytics") {
            val response = api.getCampaignAnalytics(campaignId)
            if (response.success) Resource.Success(response.analytics ?: emptyMap())
            else Resource.Error(response.error ?: "Failed to load analytics")
        }
}
