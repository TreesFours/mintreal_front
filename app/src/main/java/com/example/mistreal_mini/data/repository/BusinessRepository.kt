package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import android.provider.Settings
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.BusinessAdResponse
import com.example.mistreal_mini.data.api.BusinessPlatformHandle
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.RegisterBusinessRequest
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.data.api.UploadOwnerPhotoRequest
import com.example.mistreal_mini.data.local.dao.business.BusinessDao
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
import com.example.mistreal_mini.util.FileUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessRepository @Inject constructor(
    private val businessDao: BusinessDao,
    private val authRepository: AuthRepository,
    private val api: InfoApiService,
    @ApplicationContext private val context: Context
) {
    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: "guest"

    private val deviceId: String
        get() = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)

    fun getMyBusiness(): Flow<BusinessEntity?> {
        return businessDao.getBusinessByOwner(currentUserId)
    }

    fun parseConnectedPlatforms(json: String?): List<BusinessPlatformHandle> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Gson().fromJson(json, object : TypeToken<List<BusinessPlatformHandle>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "BusinessRepository: couldn't parse connectedPlatforms JSON")
            emptyList()
        }
    }

    fun serializeConnectedPlatforms(platforms: List<BusinessPlatformHandle>): String = Gson().toJson(platforms)

    suspend fun saveBusiness(business: BusinessEntity) {
        businessDao.upsertBusiness(business)
        // Best-effort server mirror — this is what makes the business
        // discoverable/contactable by OTHER users at all; previously this
        // mirror only ever carried name/logo/category, with no address,
        // location or declared contact handles, so even when it worked the
        // server copy wasn't useful for cross-device discovery yet.
        try {
            api.registerBusiness(
                RegisterBusinessRequest(
                    deviceId = deviceId,
                    firebaseUid = authRepository.currentUser?.uid,
                    businessId = business.businessId,
                    name = business.name,
                    description = business.description,
                    logoUrl = business.logoUrl,
                    category = business.category,
                    address = business.address,
                    latitude = business.latitude,
                    longitude = business.longitude,
                    connectedPlatforms = parseConnectedPlatforms(business.connectedPlatforms),
                    ownerName = business.ownerName,
                    ownerPhotoUrl = business.ownerImageUrl
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "BusinessRepository: server mirror sync failed, local save still succeeded")
        }
    }

    /**
     * One-time upload — either the owner's live-captured Verified Face file
     * or a separate gallery pick (caller resolves which Uri to pass; both
     * are just local files from this repository's point of view). Updates
     * the local cache's ownerImageUrl too, so saveBusiness carries the new
     * URL forward on its next routine re-save instead of needing a second
     * round-trip.
     */
    suspend fun uploadOwnerPhoto(business: BusinessEntity, photoUri: Uri, context: Context): Resource<String> {
        return try {
            val base64 = FileUtil.uriToBase64(context, photoUri) ?: return Resource.Error("Couldn't read the selected photo.")
            val mimeType = FileUtil.getMimeType(context, photoUri) ?: "image/jpeg"
            val response = api.uploadOwnerPhoto(business.businessId, UploadOwnerPhotoRequest(deviceId, base64, mimeType))
            if (response.success && response.ownerPhotoUrl != null) {
                businessDao.upsertBusiness(business.copy(ownerImageUrl = response.ownerPhotoUrl))
                Resource.Success(response.ownerPhotoUrl)
            } else {
                Resource.Error(response.error ?: "Failed to upload owner photo")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to upload owner photo")
        }
    }

    suspend fun canShareBusiness(businessId: String): Resource<Boolean> {
        return try {
            val response = api.canShareBusiness(businessId, deviceId)
            if (response.success) Resource.Success(response.canShare) else Resource.Error(response.error ?: "Failed to check share eligibility")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to check share eligibility")
        }
    }

    suspend fun getAdForBusiness(businessId: String): Resource<BusinessAdResponse> {
        return try {
            val response = api.getAdForBusiness(businessId)
            if (response.success) Resource.Success(response) else Resource.Error(response.error ?: "No ad found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load ad")
        }
    }

    /** Local-only cache of the owner's own business (used by SellerCommandScreen). */
    fun searchBusinesses(category: String, city: String): Flow<List<BusinessEntity>> {
        return businessDao.searchBusinesses(category, city)
    }

    /**
     * The actual cross-device business directory — previously this didn't
     * exist at all: /business only had a /register endpoint, so a business
     * was only ever visible on the device that registered it.
     */
    suspend fun searchBusinessesRemote(category: String, city: String): Resource<List<RemoteBusiness>> {
        return try {
            val response = api.searchBusinessesRemote(category.takeIf { it != "All" }, city.takeIf { it.isNotBlank() })
            if (response.success) Resource.Success(response.businesses ?: emptyList())
            else Resource.Error(response.error ?: "Failed to search businesses")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to search businesses")
        }
    }

    suspend fun getBusinessDetail(businessId: String): Resource<com.example.mistreal_mini.data.api.BusinessDetailResponse> {
        return try {
            val response = api.getBusinessDetail(businessId)
            if (response.success) Resource.Success(response) else Resource.Error(response.error ?: "Business not found")
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to load business")
        }
    }

    suspend fun addInventoryItem(name: String, price: String?, imageUrl: String?, businessId: String) {
        val item = BusinessItemEntity(
            businessId = businessId,
            name = name,
            price = price,
            imageUrl = imageUrl
        )
        businessDao.insertItem(item)
    }

    fun getInventory(businessId: String): Flow<List<BusinessItemEntity>> {
        return businessDao.getItemsForBusiness(businessId)
    }

    suspend fun deleteItem(item: BusinessItemEntity) {
        businessDao.deleteItem(item)
    }
}
