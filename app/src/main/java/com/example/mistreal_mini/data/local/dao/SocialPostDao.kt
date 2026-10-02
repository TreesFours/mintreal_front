package com.example.mistreal_mini.data.local.dao

import androidx.room.*
import com.example.mistreal_mini.data.local.entity.SocialPostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SocialPostDao {
    @Query("SELECT * FROM social_posts ORDER BY fetchedAt DESC LIMIT 200")
    fun getCachedPosts(): Flow<List<SocialPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(posts: List<SocialPostEntity>)

    @Query("UPDATE social_posts SET isLikedByUser = :liked, likes = :likes WHERE id = :id")
    suspend fun updateLikeState(id: String, liked: Boolean, likes: Int)

    @Query("UPDATE social_posts SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmarkState(id: String, bookmarked: Boolean)

    @Query("DELETE FROM social_posts WHERE fetchedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)
}
