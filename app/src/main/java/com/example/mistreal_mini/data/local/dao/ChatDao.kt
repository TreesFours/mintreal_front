package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.ChatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats WHERE userId = :userId ORDER BY timestamp ASC")
    fun getAllMessages(userId: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE userId = :userId AND trendTitle = :trendTitle ORDER BY timestamp ASC")
    fun getTrendMessages(userId: String, trendTitle: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE userId = :userId AND isTrend = 1 ORDER BY timestamp DESC")
    fun getTrends(userId: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE userId = :userId AND isTrend = 0 ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedMessages(userId: String, limit: Int, offset: Int): List<ChatEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatEntity)

    @Query("DELETE FROM chats WHERE userId = :userId AND isTrend = 0")
    suspend fun deleteNonTrendMessages(userId: String)

    @Query("DELETE FROM chats WHERE isTrend = 1 AND timestamp < :threshold")
    suspend fun deleteExpiredTrends(threshold: Long)

    @Query("DELETE FROM chats WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chats WHERE userId = :userId")
    suspend fun nukeChat(userId: String)

    @Query("DELETE FROM chats WHERE trendTitle = :title")
    suspend fun deleteTrend(title: String)

    @Query("SELECT * FROM chats WHERE userId = :userId AND isTrend = 1 GROUP BY trendTitle ORDER BY timestamp DESC")
    fun getUniqueTrends(userId: String): Flow<List<ChatEntity>>
}
