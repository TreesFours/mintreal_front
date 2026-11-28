package com.example.mistreal_mini.ui.novels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.NovelSearchResult
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NovelSearchViewModel @Inject constructor(
    private val infoRepository: InfoRepository
) : ViewModel() {

    private val _results = MutableStateFlow<Resource<List<NovelSearchResult>>>(Resource.Success(emptyList()))
    val results: StateFlow<Resource<List<NovelSearchResult>>> = _results.asStateFlow()

    // Separate from `results` loading state — opening one book shouldn't
    // block/blank out the list the user is still looking at.
    private val _openingWorkKey = MutableStateFlow<String?>(null)
    val openingWorkKey: StateFlow<String?> = _openingWorkKey.asStateFlow()

    private val _openedUrl = MutableStateFlow<String?>(null)
    val openedUrl: StateFlow<String?> = _openedUrl.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) {
            _results.value = Resource.Success(emptyList())
            return
        }
        viewModelScope.launch {
            _results.value = Resource.Loading()
            _results.value = infoRepository.searchNovels(query)
        }
    }

    fun openBook(workKey: String) {
        viewModelScope.launch {
            _openingWorkKey.value = workKey
            when (val result = infoRepository.getNovelReadUrl(workKey)) {
                is Resource.Success -> _openedUrl.value = result.data
                else -> {}
            }
            _openingWorkKey.value = null
        }
    }

    fun consumeOpenedUrl() {
        _openedUrl.value = null
    }
}
