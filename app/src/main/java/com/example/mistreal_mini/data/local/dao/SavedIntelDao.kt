package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.SavedIntelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedIntelDao {
    @Query("SELECT * FROM saved_intel WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAll(userId: String): Flow<List<SavedIntelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SavedIntelEntity)

    @Query("DELETE FROM saved_intel WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_intel WHERE groupName = :groupName")
    suspend fun deleteGroup(groupName: String)
}
