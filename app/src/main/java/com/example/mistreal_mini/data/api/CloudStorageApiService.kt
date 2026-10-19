package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface CloudStorageApiService {

    @POST("api/cloud-storage/configs")
    suspend fun createConfig(@Body request: CreateCloudStorageConfigRequest): CloudStorageConfigResponse

    @GET("api/cloud-storage/configs")
    suspend fun getConfigs(@Query("deviceId") deviceId: String): CloudStorageConfigListResponse

    @POST("api/cloud-storage/configs/activate")
    suspend fun activateConfig(@Body request: ActivateCloudStorageConfigRequest): CloudStorageConfigResponse

    @DELETE("api/cloud-storage/configs/{id}")
    suspend fun deleteConfig(@Path("id") id: Int, @Query("deviceId") deviceId: String): GenericSuccessResponse

    @GET("api/cloud-storage/files")
    suspend fun getFiles(@Query("deviceId") deviceId: String, @Query("path") path: String = "/"): CloudStorageFileListResponse
}

data class CreateCloudStorageConfigRequest(
    val deviceId: String,
    val label: String,
    val providerType: String = "webdav",
    val baseUrl: String,
    val username: String,
    val password: String
)

data class ActivateCloudStorageConfigRequest(val deviceId: String, val configId: Int)

// Password never comes back from the server — write-only, same convention
// as the AI BYOK status endpoints.
data class CloudStorageConfig(
    val id: Int,
    val deviceId: String,
    val label: String,
    val providerType: String,
    val baseUrl: String,
    val username: String,
    val isActive: Boolean
)

data class CloudStorageConfigResponse(val success: Boolean, val config: CloudStorageConfig? = null, val error: String? = null)
data class CloudStorageConfigListResponse(val success: Boolean, val configs: List<CloudStorageConfig>? = null, val error: String? = null)

data class CloudStorageFile(
    val name: String,
    val isDirectory: Boolean,
    val size: Long? = null,
    val modifiedAt: String? = null
)

data class CloudStorageFileListResponse(val success: Boolean, val files: List<CloudStorageFile>? = null, val error: String? = null)
