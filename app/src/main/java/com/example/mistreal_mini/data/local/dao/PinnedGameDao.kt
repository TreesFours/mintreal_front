package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.PinnedGameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedGameDao {
    @Query("SELECT gameId FROM pinned_games")
    fun getPinnedIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun pin(entity: PinnedGameEntity)

    @Query("DELETE FROM pinned_games WHERE gameId = :gameId")
    suspend fun unpin(gameId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM pinned_games WHERE gameId = :gameId)")
    suspend fun isPinned(gameId: String): Boolean
}
