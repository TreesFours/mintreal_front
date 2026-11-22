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

    // The user's own multi-platform broadcasts (DispatchCenterView) and
    // their merged-across-platforms comments — a separate surface from the
    // Zernio-synced feed above (fetchFeed still explodes a multi-platform
    // post into one item per platform on the read side; unifying that is a
    // separate, bigger fix. This at least lets a user see their own
    // broadcast as the one post it actually is, with its real comments).
    private val _ownPosts = mutableStateListOf<com.example.mistreal_mini.data.api.PostDto>()
    val ownPosts: List<com.example.mistreal_mini.data.api.PostDto> = _ownPosts

    private val _aggregatedComments = mutableStateListOf<com.example.mistreal_mini.data.api.AggregatedCommentDto>()
    val aggregatedComments: List<com.example.mistreal_mini.data.api.AggregatedCommentDto> = _aggregatedComments

    private val _isLoadingComments = mutableStateOf(false)
    val isLoadingComments: State<Boolean> = _isLoadingComments

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

    fun loadFeed(deviceId: String, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            supervisorScope {
                val intelDeferred = async { fetchIntelligence(fastLoad = !forceRefresh, refresh = forceRefresh) }
                val socialDeferred = async { syncSocials(deviceId) }
                val youtubeDeferred = async { loadYoutubeVideos() }
                val sosDeferred = async { fetchNearbySos(deviceId) }
                intelDeferred.await()
                socialDeferred.await()
                youtubeDeferred.await()
                sosDeferred.await()
            }
            _isLoading.value = false
        }
    }

    /**
     * Guardian dead-man's-switch community broadcast — a stateless live
     * query (no push infra), surfaced at the top of the same Intelligence
     * feed as a distinct, non-interactive card. See
     * emergencyRoutes.ts's GET /nearby-sos.
     */
    private suspend fun fetchNearbySos(deviceId: String) {
        try {
            val result = infoRepository.getNearbySosAlerts(deviceId)
            if (result is com.example.mistreal_mini.data.Resource.Success) {
                val sosArticles = result.data.orEmpty().map { alert ->
                    com.example.mistreal_mini.data.api.Article(
                        title = "🆘 Unconfirmed-safe person nearby (~${alert.distanceMiles} mi)",
                        description = (alert.distressSignature ?: "A Mistreal user hasn't checked in.") +
                            if (alert.fullDetail && alert.latitude != null && alert.longitude != null)
                                " Last known location: https://www.google.com/maps?q=${alert.latitude},${alert.longitude}"
                            else "",
                        url = "sos://alert/${alert.id}",
                        type = "sos_alert",
                        timestamp = alert.createdAt
                    )
                }
                // Surfaced at the top, not interleaved by date — this is
                // informational safety content, not routine news.
                _newsArticles.addAll(0, sosArticles)
                _interleavedFeed.addAll(0, sosArticles)
            }
        } catch (e: Exception) {
            // Best-effort enrichment — never block the rest of the feed.
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

    private suspend fun fetchIntelligence(fastLoad: Boolean, refresh: Boolean = false) {
        when (val result = getIntelligenceFeedUseCase.getNews(fastLoad = fastLoad, refresh = refresh)) {
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

    suspend fun postToSocial(
        deviceId: String, platform: String, type: String, content: String, targetId: String = "self",
        mistrealOnly: Boolean = false, mediaBase64: String? = null, mediaMimeType: String? = null,
        shareToCommunity: Boolean = false
    ): Boolean {
        val result = infoRepository.performSocialAction(
            deviceId = deviceId,
            type = type,
            platform = platform,
            content = content,
            targetId = targetId,
            mediaBase64 = mediaBase64,
            mediaMimeType = mediaMimeType,
            mistrealOnly = mistrealOnly,
            shareToCommunity = shareToCommunity
        )
        return result is Resource.Success
    }

    /** Returns (success, unlinkedPlatforms) — see DispatchCenterView's onPostMultiPlatform doc. */
    suspend fun postMultiPlatform(
        deviceId: String, content: String, platforms: List<String>,
        mediaBase64: String? = null, mediaMimeType: String? = null
    ): Pair<Boolean, List<String>> {
        val result = infoRepository.createMultiPlatformPost(deviceId, content, platforms, mediaBase64, mediaMimeType)
        return when (result) {
            is Resource.Success -> true to (result.data?.unlinkedPlatforms ?: emptyList())
            else -> false to emptyList()
        }
    }

    fun loadOwnPosts(deviceId: String) {
        viewModelScope.launch {
            val result = infoRepository.getOwnPosts(deviceId)
            if (result is Resource.Success) {
                _ownPosts.clear()
                _ownPosts.addAll(result.data ?: emptyList())
            }
        }
    }

    private var loadCommentsJob: kotlinx.coroutines.Job? = null

    fun loadAggregatedComments(postId: String, deviceId: String) {
        // Cleared synchronously, before launching — otherwise switching from
        // post A's comment sheet straight to post B's briefly shows A's
        // stale comments for a frame (the clear used to happen only after
        // the network call landed, inside the coroutine).
        _aggregatedComments.clear()
        _isLoadingComments.value = true
        loadCommentsJob?.cancel() // a fast A->B->A tap sequence shouldn't let A's slower first load overwrite B's
        loadCommentsJob = viewModelScope.launch {
            val result = infoRepository.getAggregatedComments(postId, deviceId)
            if (result is Resource.Success) _aggregatedComments.addAll(result.data ?: emptyList())
            _isLoadingComments.value = false
        }
    }

    suspend fun addPostComment(postId: String, deviceId: String, content: String): Boolean {
        val result = infoRepository.addPostComment(postId, deviceId, content)
        return result is Resource.Success
    }
}
