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
    // Reads already cap at 200 rows (getCachedPosts' LIMIT), but nothing was
    // ever pruning the underlying table, so it grew unbounded. 7 days is
    // generous for a "feed" cache that's meant to survive offline/process
    // death, not be a permanent archive.
    private val maxCacheAgeMillis = 7L * 24 * 60 * 60 * 1000

    fun getCachedPosts(): Flow<List<SocialPost>> =
        dao.getCachedPosts().map { entities -> entities.map { it.toSocialPost() } }

    suspend fun cachePosts(posts: List<SocialPost>) {
        dao.upsertAll(posts.map { it.toEntity(fetchedAt = System.currentTimeMillis()) })
        dao.deleteOlderThan(System.currentTimeMillis() - maxCacheAgeMillis)
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
    isCommunityPost = isCommunityPost,
    authorDeviceId = authorDeviceId,
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
    isBookmarked = isBookmarked,
    isCommunityPost = isCommunityPost,
    authorDeviceId = authorDeviceId
)
