package com.example.mistreal_mini.data.local.dao

import androidx.room.*
import com.example.mistreal_mini.data.local.entity.ScribeNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScribeDao {
    @Query("SELECT * FROM scribe_notes WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllNotes(userId: String): Flow<List<ScribeNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: ScribeNoteEntity)

    @Delete
    suspend fun deleteNote(note: ScribeNoteEntity)

    @Query("DELETE FROM scribe_notes WHERE userId = :userId")
    suspend fun nukeNotes(userId: String)
}
