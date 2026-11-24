package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CameraApiService
import com.example.mistreal_mini.data.api.CameraProfileDto
import com.example.mistreal_mini.data.api.SubmitCameraRequest
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.util.DeviceIdProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CameraRepository @Inject constructor(
    private val api: CameraApiService,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId get() = deviceIdProvider.deviceId

    suspend fun submitCamera(title: String, location: String?, streamUrl: String, streamType: String): Resource<Unit> =
        safeApiCall("Failed to submit camera") {
            val response = api.submitCamera(SubmitCameraRequest(deviceId, title, location, streamUrl, streamType))
            if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to submit camera")
        }

    suspend fun getCameras(): Resource<List<CameraProfileDto>> = safeApiCall("Failed to load cameras") {
        val response = api.getCameras()
        if (response.success) Resource.Success(response.cameras ?: emptyList())
        else Resource.Error(response.error ?: "Failed to load cameras")
    }
}
