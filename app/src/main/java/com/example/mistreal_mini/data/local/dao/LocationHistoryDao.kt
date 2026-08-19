package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.LocationHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationHistoryDao {
    @Query("SELECT * FROM location_history WHERE userId = :userId ORDER BY timestamp DESC")
    fun getLocationHistory(userId: String): Flow<List<LocationHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: LocationHistoryEntity)

    @Query("DELETE FROM location_history WHERE id = :id")
    suspend fun deleteLocation(id: Long)

    @Query("DELETE FROM location_history WHERE userId = :userId")
    suspend fun nukeHistory(userId: String)
}
