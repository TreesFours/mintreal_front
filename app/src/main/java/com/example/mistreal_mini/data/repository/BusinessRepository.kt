package com.example.mistreal_mini.data.repository

import android.content.Context
import android.net.Uri
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.safeApiCall
import com.example.mistreal_mini.data.api.BusinessAdResponse
import com.example.mistreal_mini.data.api.BusinessPlatformHandle
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.RegisterBusinessRequest
import com.example.mistreal_mini.data.api.RemoteBusiness
import com.example.mistreal_mini.data.api.RemoteDiscoveryPoi
import com.example.mistreal_mini.data.api.UploadOwnerPhotoRequest
import com.example.mistreal_mini.data.local.dao.business.BusinessDao
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
import com.example.mistreal_mini.util.DeviceIdProvider
import com.example.mistreal_mini.util.FileUtil
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class BusinessSearchResult(val native: List<RemoteBusiness>, val external: List<RemoteDiscoveryPoi>)

@Singleton
class BusinessRepository @Inject constructor(
    private val businessDao: BusinessDao,
    private val authRepository: AuthRepository,
    private val api: InfoApiService,
    @ApplicationContext private val context: Context,
    private val deviceIdProvider: DeviceIdProvider
) {
    val currentUserId: String
        get() = authRepository.currentUser?.uid ?: "guest"

    private val deviceId: String
        get() = deviceIdProvider.deviceId

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
    suspend fun uploadOwnerPhoto(business: BusinessEntity, photoUri: Uri, context: Context): Resource<String> =
        safeApiCall("Failed to upload owner photo") {
            val base64 = FileUtil.uriToBase64(context, photoUri) ?: return@safeApiCall Resource.Error("Couldn't read the selected photo.")
            val mimeType = FileUtil.getMimeType(context, photoUri) ?: "image/jpeg"
            val response = api.uploadOwnerPhoto(business.businessId, UploadOwnerPhotoRequest(deviceId, base64, mimeType))
            if (response.success && response.ownerPhotoUrl != null) {
                businessDao.upsertBusiness(business.copy(ownerImageUrl = response.ownerPhotoUrl))
                Resource.Success(response.ownerPhotoUrl)
            } else {
                Resource.Error(response.error ?: "Failed to upload owner photo")
            }
        }

    /** Same one-time-upload shape as uploadOwnerPhoto, but for the business's own logo/cover photo. */
    suspend fun uploadBusinessLogo(business: BusinessEntity, photoUri: Uri, context: Context): Resource<String> =
        safeApiCall("Failed to upload business logo") {
            val base64 = FileUtil.uriToBase64(context, photoUri) ?: return@safeApiCall Resource.Error("Couldn't read the selected photo.")
            val mimeType = FileUtil.getMimeType(context, photoUri) ?: "image/jpeg"
            val response = api.uploadBusinessLogo(business.businessId, UploadOwnerPhotoRequest(deviceId, base64, mimeType))
            if (response.success && response.logoUrl != null) {
                businessDao.upsertBusiness(business.copy(logoUrl = response.logoUrl))
                Resource.Success(response.logoUrl)
            } else {
                Resource.Error(response.error ?: "Failed to upload business logo")
            }
        }

    suspend fun canShareBusiness(businessId: String): Resource<Boolean> = safeApiCall("Failed to check share eligibility") {
        val response = api.canShareBusiness(businessId, deviceId)
        if (response.success) Resource.Success(response.canShare) else Resource.Error(response.error ?: "Failed to check share eligibility")
    }

    suspend fun getAdForBusiness(businessId: String): Resource<BusinessAdResponse> = safeApiCall("Failed to load ad") {
        val response = api.getAdForBusiness(businessId)
        if (response.success) Resource.Success(response) else Resource.Error(response.error ?: "No ad found")
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
    suspend fun searchBusinessesRemote(category: String, city: String, lat: Double? = null, lon: Double? = null): Resource<List<RemoteBusiness>> =
        safeApiCall("Failed to search businesses") {
            val response = api.searchBusinessesRemote(category.takeIf { it != "All" }, city.takeIf { it.isNotBlank() }, lat, lon)
            if (response.success) Resource.Success(response.businesses ?: emptyList())
            else Resource.Error(response.error ?: "Failed to search businesses")
        }

    /**
     * Same search, but also surfaces the external (non-Mistreal) fallback
     * results the backend only populates when `businesses` comes back
     * empty — kept separate from searchBusinessesRemote() above so
     * DiscoverySearchScreen's existing native-only consumption is
     * untouched; only the new Market screen needs the external half.
     */
    suspend fun searchBusinessesRemoteFull(
        category: String, city: String, lat: Double? = null, lon: Double? = null
    ): Resource<BusinessSearchResult> = safeApiCall("Failed to search businesses") {
        val response = api.searchBusinessesRemote(category.takeIf { it != "All" }, city.takeIf { it.isNotBlank() }, lat, lon)
        if (response.success) {
            Resource.Success(BusinessSearchResult(response.businesses ?: emptyList(), response.externalBusinesses ?: emptyList()))
        } else {
            Resource.Error(response.error ?: "Failed to search businesses")
        }
    }

    suspend fun getBusinessDetail(businessId: String): Resource<com.example.mistreal_mini.data.api.BusinessDetailResponse> =
        safeApiCall("Failed to load business") {
            val response = api.getBusinessDetail(businessId)
            if (response.success) Resource.Success(response) else Resource.Error(response.error ?: "Business not found")
        }

    /** Null if this business has no live Watch Hub session right now — not an error, just "nothing to watch." */
    suspend fun getLiveWatchSession(businessId: String): String? {
        return try {
            val response = api.getWatchSessions("market", businessId)
            response.sessions?.firstOrNull { it.status == "live" }?.id
        } catch (e: Exception) {
            Timber.w(e, "BusinessRepository: getLiveWatchSession failed for $businessId")
            null
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

    /** Business-only posting — the advert's tap target is always the posting business's owner profile. */
    suspend fun createAdvert(
        businessId: String, title: String, description: String?, price: String?, category: String,
        imageUri: Uri?, context: Context
    ): Resource<Unit> = safeApiCall("Failed to post advert") {
        val imageBase64 = imageUri?.let { FileUtil.uriToBase64(context, it) }
        val imageMimeType = imageUri?.let { FileUtil.getMimeType(context, it) }
        val response = api.createAdvert(
            com.example.mistreal_mini.data.api.CreateAdvertRequest(
                deviceId, businessId, title, description, price, category,
                imageUrl = null, imageBase64 = imageBase64, imageMimeType = imageMimeType
            )
        )
        if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to post advert")
    }

    suspend fun getAdverts(category: String, maxPrice: Double? = null, limit: Int? = null, offset: Int? = null): Resource<List<com.example.mistreal_mini.data.api.RemoteAdvert>> =
        safeApiCall("Failed to load adverts") {
            val response = api.getAdverts(category.takeIf { it != "All" }, maxPrice, limit, offset)
            if (response.success) Resource.Success(response.adverts ?: emptyList())
            else Resource.Error(response.error ?: "Failed to load adverts")
        }

    suspend fun updateAdvertStatus(advertId: String, status: String): Resource<Unit> = safeApiCall("Failed to update advert") {
        val response = api.updateAdvertStatus(advertId, com.example.mistreal_mini.data.api.UpdateAdvertStatusRequest(deviceId, status))
        if (response.success) Resource.Success(Unit) else Resource.Error(response.error ?: "Failed to update advert")
    }
}
