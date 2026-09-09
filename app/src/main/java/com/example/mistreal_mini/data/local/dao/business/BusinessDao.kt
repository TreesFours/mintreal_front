package com.example.mistreal_mini.data.local.dao.business

import androidx.room.*
import com.example.mistreal_mini.data.local.entity.BusinessEntity
import com.example.mistreal_mini.data.local.entity.BusinessItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BusinessDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusiness(business: BusinessEntity)

    @Query("SELECT * FROM businesses WHERE ownerId = :ownerId")
    fun getBusinessByOwner(ownerId: String): Flow<BusinessEntity?>

    @Query("SELECT * FROM businesses WHERE category = :category AND address LIKE '%' || :city || '%'")
    fun searchBusinesses(category: String, city: String): Flow<List<BusinessEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: BusinessItemEntity)

    @Delete
    suspend fun deleteItem(item: BusinessItemEntity)

    @Query("SELECT * FROM business_items WHERE businessId = :businessId")
    fun getItemsForBusiness(businessId: String): Flow<List<BusinessItemEntity>>
}
