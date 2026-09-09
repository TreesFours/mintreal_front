package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.local.dao.business.BusinessDao
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessRepository @Inject constructor(
    private val businessDao: BusinessDao,
    private val authRepository: AuthRepository
) {
    private val currentUserId: String
        get() = authRepository.currentUser?.uid ?: "guest"

    fun getMyBusiness(): Flow<BusinessEntity?> {
        return businessDao.getBusinessByOwner(currentUserId)
    }

    suspend fun saveBusiness(business: BusinessEntity) {
        businessDao.upsertBusiness(business)
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
