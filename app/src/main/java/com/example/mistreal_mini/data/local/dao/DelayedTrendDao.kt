package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.DelayedTrendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DelayedTrendDao {
    @Query("SELECT trendTitle FROM delayed_trends")
    fun getEnabledTitles(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enable(entity: DelayedTrendEntity)

    @Query("DELETE FROM delayed_trends WHERE trendTitle = :trendTitle")
    suspend fun disable(trendTitle: String)

    @Query("SELECT EXISTS(SELECT 1 FROM delayed_trends WHERE trendTitle = :trendTitle)")
    suspend fun isEnabled(trendTitle: String): Boolean
}
