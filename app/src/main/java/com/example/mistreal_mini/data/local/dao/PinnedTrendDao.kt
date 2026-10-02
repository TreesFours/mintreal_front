package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.PinnedTrendEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedTrendDao {
    @Query("SELECT trendTitle FROM pinned_trends")
    fun getPinnedTitles(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pin(entity: PinnedTrendEntity)

    @Query("DELETE FROM pinned_trends WHERE trendTitle = :title")
    suspend fun unpin(title: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_trends WHERE trendTitle = :title)")
    suspend fun isPinned(title: String): Boolean
}
