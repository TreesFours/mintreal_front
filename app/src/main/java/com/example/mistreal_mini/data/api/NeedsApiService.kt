package com.example.mistreal_mini.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface NeedsApiService {

    @POST("api/needs/skills")
    suspend fun addSkill(@Body request: AddSkillRequest): SkillResponse

    @GET("api/needs/skills")
    suspend fun getSkills(@Query("deviceId") deviceId: String): SkillListResponse

    @DELETE("api/needs/skills/{id}")
    suspend fun deleteSkill(@Path("id") id: Int, @Query("deviceId") deviceId: String): GenericSuccessResponse

    @POST("api/needs")
    suspend fun postNeed(@Body request: PostNeedRequest): NeedResponse

    @GET("api/needs")
    suspend fun getNearbyNeeds(@Query("lat") lat: Double?, @Query("lon") lon: Double?): NeedListResponse

    @GET("api/needs/matches")
    suspend fun getMatchingNeeds(@Query("deviceId") deviceId: String): NeedListResponse

    @POST("api/needs/{id}/close")
    suspend fun closeNeed(@Path("id") id: Int, @Body request: DeviceIdBody): GenericSuccessResponse
}

data class AddSkillRequest(val deviceId: String, val skillLabel: String)
data class UserSkill(val id: Int, val deviceId: String, val skillLabel: String)
data class SkillResponse(val success: Boolean, val skill: UserSkill? = null, val error: String? = null)
data class SkillListResponse(val success: Boolean, val skills: List<UserSkill>? = null, val error: String? = null)

data class PostNeedRequest(
    val deviceId: String,
    val skillLabel: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double
)

data class NeedPost(
    val id: Int,
    val deviceId: String,
    val skillLabel: String,
    val description: String? = null,
    val latitude: Double,
    val longitude: Double,
    val status: String, // "open" | "closed"
    val distanceKm: Double? = null,
    val createdAt: String? = null
)

data class NeedResponse(val success: Boolean, val need: NeedPost? = null, val error: String? = null)
data class NeedListResponse(val success: Boolean, val needs: List<NeedPost>? = null, val error: String? = null)
