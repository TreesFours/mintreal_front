package com.example.mistreal_mini.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.mistreal_mini.data.local.entity.VerifiedFaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VerifiedFaceDao {
    @Query("SELECT * FROM verified_faces ORDER BY createdAt DESC")
    fun getAll(): Flow<List<VerifiedFaceEntity>>

    @Insert
    suspend fun insert(entity: VerifiedFaceEntity): Long

    @Query("DELETE FROM verified_faces WHERE id = :id")
    suspend fun delete(id: Long)
}
