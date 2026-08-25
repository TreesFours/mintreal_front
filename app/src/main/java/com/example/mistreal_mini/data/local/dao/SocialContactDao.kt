package com.example.mistreal_mini.data.local.dao

import androidx.room.*
import com.example.mistreal_mini.data.local.entity.SocialContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialContactDao {
    @Query("SELECT * FROM social_contacts WHERE isEmergency = 0 ORDER BY lastInteractionTime DESC")
    fun getRecentContacts(): Flow<List<SocialContactEntity>>

    @Query("SELECT * FROM social_contacts WHERE isEmergency = 1 ORDER BY lastInteractionTime DESC")
    fun getEmergencyContacts(): Flow<List<SocialContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: SocialContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(contacts: List<SocialContactEntity>)

    @Query("DELETE FROM social_contacts WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("UPDATE social_contacts SET lastInteractionTime = :time WHERE contactId = :id")
    suspend fun updateInteractionTime(id: String, time: Long)
}
