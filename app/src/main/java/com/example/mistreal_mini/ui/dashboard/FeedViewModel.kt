package com.example.mistreal_mini.ui.dashboard

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.Article
import com.example.mistreal_mini.data.model.PlatformUpdate
import com.example.mistreal_mini.data.model.SocialPost
import com.example.mistreal_mini.data.repository.SocialFeedCacheRepository
import com.example.mistreal_mini.domain.usecase.GetIntelligenceFeedUseCase
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val getIntelligenceFeedUseCase: GetIntelligenceFeedUseCase,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val infoRepository: com.example.mistreal_mini.data.repository.InfoRepository,
    private val feedCacheRepository: SocialFeedCacheRepository
) : ViewModel() {

    private val _interleavedFeed = mutableStateListOf<Article>()
    val interleavedFeed: List<Article> = _interleavedFeed

    private val _newsArticles = mutableStateListOf<Article>()
    val newsArticles: List<Article> = _newsArticles

    private val _socialUpdates = mutableStateListOf<PlatformUpdate>()
    val socialUpdates: List<PlatformUpdate> = _socialUpdates

    private val _socialPosts = mutableStateListOf<SocialPost>()
    val socialPosts: List<SocialPost> = _socialPosts

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents = _errorEvents.asSharedFlow()

    init {
        // Paint immediately from cache (works offline / survives process death),
        // then loadFeed() below overwrites with a fresh sync when it resolves.
        viewModelScope.launch {
            feedCacheRepository.getCachedPosts().collect { cached ->
                if (_socialPosts.isEmpty() && cached.isNotEmpty()) {
                    _socialPosts.addAll(cached)
                }
            }
        }
    }

    fun loadFeed(deviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            supervisorScope {
                val intelDeferred = async { fetchIntelligence(fastLoad = true) }
                val socialDeferred = async { syncSocials(deviceId) }
                val youtubeDeferred = async { loadYoutubeVideos() }
                intelDeferred.await()
                socialDeferred.await()
                youtubeDeferred.await()
            }
            _isLoading.value = false
        }
    }

    private suspend fun loadYoutubeVideos() {
        try {
            val videos = infoRepository.getYoutubeVideos()
            if (videos.isNotEmpty()) {
                // Dedupe against anything already in the list (re-fetches on
                // a pull-to-refresh shouldn't duplicate the same cached videos).
                val existingIds = _socialPosts.map { it.id }.toSet()
                _socialPosts.addAll(videos.filter { it.id !in existingIds })
            }
        } catch (e: Exception) {
            // Best-effort enrichment — never block the rest of the feed.
        }
    }

    private suspend fun fetchIntelligence(fastLoad: Boolean) {
        when (val result = getIntelligenceFeedUseCase.getNews(fastLoad = fastLoad)) {
            is Resource.Success -> {
                result.data?.articles?.let { articles ->
                    _newsArticles.clear()
                    _newsArticles.addAll(articles)
                    
                    // Also update interleaved for unified view
                    _interleavedFeed.clear()
                    _interleavedFeed.addAll(articles)
                    
                    Timber.d("🧠 Intel Loaded: ${articles.size} items (Types: ${articles.map { it.type }.distinct()})")
                }
            }
            is Resource.Error -> {
                Timber.e("Intel fetch error: ${result.message}")
            }
            else -> {}
        }
    }

    private suspend fun syncSocials(deviceId: String) {
        when (val result = syncSocialsUseCase(deviceId)) {
            is Resource.Success -> {
                result.data?.let { response ->
                    _socialUpdates.clear()
                    _socialUpdates.addAll(response.platformUpdates)
                    _socialPosts.clear()
                    _socialPosts.addAll(response.posts)
                    feedCacheRepository.cachePosts(response.posts)

                    // Surface *why* the feed is empty/partial (bad Zernio link, platform
                    // returned nothing, sync failed) instead of a silent blank state —
                    // these are real backend-reported diagnostics, not guesses.
                    response.syncWarnings?.forEach { warning ->
                        Timber.w("Social sync warning: $warning")
                        _errorEvents.emit(warning)
                    }
                }
            }
            is Resource.Error -> {
                Timber.e("Social sync error: ${result.message}")
                _errorEvents.emit("Social sync failed: ${result.message}")
                // Keep whatever cache-sourced posts are already showing rather than
                // clearing the feed on a transient network failure.
            }
            else -> {}
        }
    }

    fun toggleLike(deviceId: String, post: SocialPost) {
        val index = _socialPosts.indexOfFirst { it.id == post.id }
        if (index == -1) return
        val wasLiked = post.isLikedByUser
        val newLikes = (post.likes ?: 0) + if (wasLiked) -1 else 1
        // Optimistic update first.
        _socialPosts[index] = post.copy(isLikedByUser = !wasLiked, likes = newLikes.coerceAtLeast(0))

        viewModelScope.launch {
            feedCacheRepository.setLiked(post.id, !wasLiked, newLikes.coerceAtLeast(0))
            val result = infoRepository.performSocialAction(
                deviceId = deviceId,
                type = "like",
                platform = post.platform,
                content = "",
                targetId = post.id
            )
            if (result is Resource.Error) {
                // Roll back on failure.
                val rollbackIndex = _socialPosts.indexOfFirst { it.id == post.id }
                if (rollbackIndex != -1) {
                    _socialPosts[rollbackIndex] = post
                }
                feedCacheRepository.setLiked(post.id, wasLiked, post.likes ?: 0)
                _errorEvents.emit("Couldn't like this post: ${result.message}")
            }
        }
    }

    fun toggleBookmark(post: SocialPost) {
        val index = _socialPosts.indexOfFirst { it.id == post.id }
        if (index == -1) return
        val newBookmarked = !post.isBookmarked
        _socialPosts[index] = post.copy(isBookmarked = newBookmarked)
        viewModelScope.launch {
            feedCacheRepository.setBookmarked(post.id, newBookmarked)
        }
    }

    fun toggleFollow(deviceId: String, platform: String, authorName: String, currentlyFollowing: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = infoRepository.performSocialAction(
                deviceId = deviceId,
                type = if (currentlyFollowing) "Unfollow" else "Follow",
                platform = platform,
                content = "",
                targetId = authorName
            )
            if (result is Resource.Success) {
                onResult(!currentlyFollowing)
            } else {
                onResult(currentlyFollowing)
                _errorEvents.emit("Couldn't ${if (currentlyFollowing) "unfollow" else "follow"} $authorName")
            }
        }
    }

    fun togglePin(article: Article) {
        viewModelScope.launch {
            val result = getIntelligenceFeedUseCase.togglePin(article)
            if (result is Resource.Success) {
                // Update local state to reflect pin
                val index = _newsArticles.indexOfFirst { it.url == article.url }
                if (index != -1) {
                    _newsArticles[index] = _newsArticles[index].copy(isPinned = !(article.isPinned ?: false))
                }
            }
        }
    }

    suspend fun postToSocial(deviceId: String, platform: String, type: String, content: String, targetId: String = "self"): Boolean {
        val result = infoRepository.performSocialAction(
            deviceId = deviceId,
            type = type,
            platform = platform,
            content = content,
            targetId = targetId
        )
        return result is Resource.Success
    }
}
