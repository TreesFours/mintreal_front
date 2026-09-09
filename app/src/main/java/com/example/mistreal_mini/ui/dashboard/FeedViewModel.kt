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
import com.example.mistreal_mini.domain.usecase.GetIntelligenceFeedUseCase
import com.example.mistreal_mini.domain.usecase.SyncSocialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val getIntelligenceFeedUseCase: GetIntelligenceFeedUseCase,
    private val syncSocialsUseCase: SyncSocialsUseCase,
    private val infoRepository: com.example.mistreal_mini.data.repository.InfoRepository
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

    fun loadFeed(deviceId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            fetchIntelligence(fastLoad = true)
            syncSocials(deviceId)
            _isLoading.value = false
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
                }
            }
            is Resource.Error -> {
                Timber.e("Social sync error: ${result.message}")
            }
            else -> {}
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
