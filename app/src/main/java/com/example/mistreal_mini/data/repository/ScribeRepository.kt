package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.local.dao.ScribeDao
import com.example.mistreal_mini.data.local.entity.ScribeNoteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScribeRepository @Inject constructor(
    private val scribeDao: ScribeDao,
    private val authRepository: AuthRepository
) {
    private val currentUserId: String
        get() = authRepository.currentUser?.uid ?: "guest"

    fun getAllNotes(): Flow<List<ScribeNoteEntity>> {
        return scribeDao.getAllNotes(currentUserId)
    }

    suspend fun saveNote(
        sourcePostId: String? = null,
        platform: String? = null,
        author: String? = null,
        content: String? = null,
        analysis: String
    ) {
        val note = ScribeNoteEntity(
            userId = currentUserId,
            sourcePostId = sourcePostId,
            sourcePlatform = platform,
            sourceAuthor = author,
            sourceContent = content,
            aiAnalysis = analysis
        )
        scribeDao.insertNote(note)
    }

    suspend fun deleteNote(note: ScribeNoteEntity) {
        scribeDao.deleteNote(note)
    }
}
