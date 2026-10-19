package com.example.mistreal_mini.ui.games

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.GameSummary
import com.example.mistreal_mini.data.local.dao.PinnedGameDao
import com.example.mistreal_mini.data.local.entity.PinnedGameEntity
import com.example.mistreal_mini.data.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GamesViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val pinnedGameDao: PinnedGameDao
) : ViewModel() {

    private val _games = MutableStateFlow<Resource<List<GameSummary>>>(Resource.Loading())
    val games: StateFlow<Resource<List<GameSummary>>> = _games.asStateFlow()

    val pinnedIds: StateFlow<Set<String>> = pinnedGameDao.getPinnedIds()
        .map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        loadGames(null)
    }

    fun loadGames(category: String?) {
        viewModelScope.launch {
            _games.value = Resource.Loading()
            _games.value = gameRepository.listGames(category)
        }
    }

    fun togglePin(gameId: String) {
        viewModelScope.launch {
            if (pinnedGameDao.isPinned(gameId)) {
                pinnedGameDao.unpin(gameId)
            } else {
                pinnedGameDao.pin(PinnedGameEntity(gameId))
            }
        }
    }
}
