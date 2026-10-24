package com.example.mistreal_mini.data.repository

import android.content.Context
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.data.api.ActivateCloudStorageConfigRequest
import com.example.mistreal_mini.data.api.CloudStorageApiService
import com.example.mistreal_mini.data.api.CloudStorageConfig
import com.example.mistreal_mini.data.api.CloudStorageFile
import com.example.mistreal_mini.data.api.CreateCloudStorageConfigRequest
import com.example.mistreal_mini.util.DeviceIdProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BYOK cloud storage — the user's own WebDAV account (Nextcloud, Box, etc.)
 * for app-generated files, same philosophy as the AI provider BYOK keys
 * already in this app. Once a config is active, the backend's
 * persistToStorage dispatcher (adRoutes.ts) routes new uploads there
 * instead of our Firebase bucket automatically — nothing else to wire up
 * client-side for that part.
 */
@Singleton
class CloudStorageRepository @Inject constructor(
    private val api: CloudStorageApiService,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId

    suspend fun createConfig(label: String, baseUrl: String, username: String, password: String): Resource<CloudStorageConfig> =
        safeApiCall("Failed to save cloud storage account") {
            val response = api.createConfig(CreateCloudStorageConfigRequest(deviceId, label, "webdav", baseUrl, username, password))
            if (response.success && response.config != null) Resource.Success(response.config)
            else Resource.Error(response.error ?: "Failed to save cloud storage account")
        }

    suspend fun getConfigs(): Resource<List<CloudStorageConfig>> = safeApiCall("Failed to load cloud storage accounts") {
        val response = api.getConfigs(deviceId)
        if (response.success) Resource.Success(response.configs ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load cloud storage accounts")
    }

    suspend fun activateConfig(configId: Int): Resource<CloudStorageConfig> = safeApiCall("Failed to switch cloud storage account") {
        val response = api.activateConfig(ActivateCloudStorageConfigRequest(deviceId, configId))
        if (response.success && response.config != null) Resource.Success(response.config)
        else Resource.Error(response.error ?: "Failed to switch cloud storage account")
    }

    suspend fun deleteConfig(configId: Int): Resource<Unit> = safeApiCall("Failed to remove account") {
        val response = api.deleteConfig(configId, deviceId)
        if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to remove account")
    }

    suspend fun getFiles(path: String = "/"): Resource<List<CloudStorageFile>> = safeApiCall("Failed to list files") {
        val response = api.getFiles(deviceId, path)
        if (response.success) Resource.Success(response.files ?: emptyList())
        else Resource.Error(response.error ?: "Failed to list files")
    }
}
