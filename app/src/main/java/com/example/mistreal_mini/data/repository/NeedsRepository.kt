package com.example.mistreal_mini.data.repository

import android.content.Context
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.AddSkillRequest
import com.example.mistreal_mini.data.api.DeviceIdBody
import com.example.mistreal_mini.data.api.NeedPost
import com.example.mistreal_mini.data.api.NeedsApiService
import com.example.mistreal_mini.data.api.PostNeedRequest
import com.example.mistreal_mini.data.api.UserSkill
import com.example.mistreal_mini.util.DeviceIdProvider
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Needs/Offers matching: UserSkill is the persistent "I offer this" tag on
 * your own profile; NeedPost is the ephemeral "I need this" want-ad. See
 * needRoutes.ts — matching/ranking all happens server-side, this is a
 * plain Resource-wrapped pass-through.
 */
@Singleton
class NeedsRepository @Inject constructor(
    private val api: NeedsApiService,
    private val locationHelper: LocationHelper,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    private val deviceId: String
        get() = deviceIdProvider.deviceId

    suspend fun addSkill(skillLabel: String): Resource<UserSkill> {
        return try {
            val response = api.addSkill(AddSkillRequest(deviceId, skillLabel))
            if (response.success && response.skill != null) Resource.Success(response.skill)
            else Resource.Error(response.error ?: "Failed to add skill")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to add skill")
        }
    }

    suspend fun getMySkills(): Resource<List<UserSkill>> {
        return try {
            val response = api.getSkills(deviceId)
            if (response.success) Resource.Success(response.skills ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load skills")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load skills")
        }
    }

    suspend fun deleteSkill(skillId: Int): Resource<Unit> {
        return try {
            val response = api.deleteSkill(skillId, deviceId)
            if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to remove skill")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to remove skill")
        }
    }

    suspend fun postNeed(skillLabel: String, description: String?): Resource<NeedPost> {
        return try {
            val location = locationHelper.getCurrentLocation()
                ?: return Resource.Error("Couldn't get your location — check location permission and try again.")
            val response = api.postNeed(PostNeedRequest(deviceId, skillLabel, description, location.latitude, location.longitude))
            if (response.success && response.need != null) Resource.Success(response.need)
            else Resource.Error(response.error ?: "Failed to post")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to post")
        }
    }

    suspend fun getNearbyNeeds(): Resource<List<NeedPost>> {
        return try {
            val location = locationHelper.getCurrentLocation()
            val response = api.getNearbyNeeds(location?.latitude, location?.longitude)
            if (response.success) Resource.Success(response.needs ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load nearby needs")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load nearby needs")
        }
    }

    suspend fun getMatchingNeeds(): Resource<List<NeedPost>> {
        return try {
            val response = api.getMatchingNeeds(deviceId)
            if (response.success) Resource.Success(response.needs ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load matches")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load matches")
        }
    }

    suspend fun closeNeed(needId: Int): Resource<Unit> {
        return try {
            val response = api.closeNeed(needId, DeviceIdBody(deviceId))
            if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to close")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to close")
        }
    }
}
