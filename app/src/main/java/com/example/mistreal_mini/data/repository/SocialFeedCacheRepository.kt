package com.example.mistreal_mini.data.repository

import com.example.mistreal_mini.data.local.dao.SocialPostDao
import com.example.mistreal_mini.data.local.entity.SocialPostEntity
import com.example.mistreal_mini.data.model.SocialPost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cache for the social feed so posts survive offline/process death instead of
 * living only in FeedViewModel's in-memory state. Kept separate from InfoRepository,
 * which stays network-only.
 */
@Singleton
class SocialFeedCacheRepository @Inject constructor(
    private val dao: SocialPostDao
) {
    fun getCachedPosts(): Flow<List<SocialPost>> =
        dao.getCachedPosts().map { entities -> entities.map { it.toSocialPost() } }

    suspend fun cachePosts(posts: List<SocialPost>) {
        dao.upsertAll(posts.map { it.toEntity(fetchedAt = System.currentTimeMillis()) })
    }

    suspend fun setLiked(id: String, liked: Boolean, likes: Int) {
        dao.updateLikeState(id, liked, likes)
    }

    suspend fun setBookmarked(id: String, bookmarked: Boolean) {
        dao.updateBookmarkState(id, bookmarked)
    }
}

private fun SocialPost.toEntity(fetchedAt: Long, isLikedByUser: Boolean = false, isBookmarked: Boolean = false) = SocialPostEntity(
    id = id,
    platform = platform,
    author = author,
    content = content,
    timestamp = timestamp,
    type = type,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    likes = likes,
    commentsCount = commentsCount,
    sourceUrl = sourceUrl,
    platformIcon = platformIcon,
    platformColor = platformColor,
    platformDisplayName = platformDisplayName,
    isLikedByUser = isLikedByUser,
    isBookmarked = isBookmarked,
    fetchedAt = fetchedAt
)

private fun SocialPostEntity.toSocialPost() = SocialPost(
    id = id,
    platform = platform,
    author = author,
    content = content,
    timestamp = timestamp,
    type = type,
    imageUrl = imageUrl,
    videoUrl = videoUrl,
    likes = likes,
    commentsCount = commentsCount,
    sourceUrl = sourceUrl,
    platformIcon = platformIcon,
    platformColor = platformColor,
    platformDisplayName = platformDisplayName,
    isLikedByUser = isLikedByUser,
    isBookmarked = isBookmarked
)
