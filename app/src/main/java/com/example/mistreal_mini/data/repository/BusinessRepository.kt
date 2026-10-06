package com.example.mistreal_mini.data.repository

import android.content.Context
import android.provider.Settings
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.api.RegisterBusinessRequest
import com.example.mistreal_mini.data.local.dao.business.BusinessDao
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
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

    suspend fun saveBusiness(business: BusinessEntity) {
        businessDao.upsertBusiness(business)
        // Best-effort server mirror so Ad.businessId has something real to FK
        // against — the local save above is the source of truth either way,
        // so a network failure here must never block business registration.
        try {
            api.registerBusiness(
                RegisterBusinessRequest(
                    deviceId = deviceId,
                    firebaseUid = authRepository.currentUser?.uid,
                    businessId = business.businessId,
                    name = business.name,
                    logoUrl = business.logoUrl,
                    category = business.category
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "BusinessRepository: server mirror sync failed, local save still succeeded")
        }
    }

    fun searchBusinesses(category: String, city: String): Flow<List<BusinessEntity>> {
        return businessDao.searchBusinesses(category, city)
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
