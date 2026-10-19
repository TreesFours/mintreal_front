package com.example.mistreal_mini.ui.needs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.NeedPost
import com.example.mistreal_mini.data.api.UserSkill
import com.example.mistreal_mini.data.repository.NeedsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NeedsViewModel @Inject constructor(
    private val repository: NeedsRepository
) : ViewModel() {

    private val _mySkills = MutableStateFlow<List<UserSkill>>(emptyList())
    val mySkills: StateFlow<List<UserSkill>> = _mySkills.asStateFlow()

    private val _matches = MutableStateFlow<List<NeedPost>>(emptyList())
    val matches: StateFlow<List<NeedPost>> = _matches.asStateFlow()

    private val _nearbyNeeds = MutableStateFlow<List<NeedPost>>(emptyList())
    val nearbyNeeds: StateFlow<List<NeedPost>> = _nearbyNeeds.asStateFlow()

    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    fun loadMySkills() {
        viewModelScope.launch {
            when (val result = repository.getMySkills()) {
                is Resource.Success -> _mySkills.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load skills")
                else -> {}
            }
        }
    }

    fun addSkill(skillLabel: String) {
        viewModelScope.launch {
            val result = repository.addSkill(skillLabel)
            if (result is Resource.Success) loadMySkills()
            else _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to add skill")
        }
    }

    fun deleteSkill(skillId: Int) {
        viewModelScope.launch {
            val result = repository.deleteSkill(skillId)
            if (result is Resource.Success) loadMySkills()
            else _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to remove skill")
        }
    }

    fun loadMatches() {
        viewModelScope.launch {
            when (val result = repository.getMatchingNeeds()) {
                is Resource.Success -> _matches.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load matches")
                else -> {}
            }
        }
    }

    fun loadNearbyNeeds() {
        viewModelScope.launch {
            when (val result = repository.getNearbyNeeds()) {
                is Resource.Success -> _nearbyNeeds.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load nearby needs")
                else -> {}
            }
        }
    }

    fun postNeed(skillLabel: String, description: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isPosting.value = true
            val result = repository.postNeed(skillLabel, description)
            _isPosting.value = false
            if (result is Resource.Success) {
                loadNearbyNeeds()
                onDone(true)
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to post")
                onDone(false)
            }
        }
    }

    fun closeNeed(needId: Int) {
        viewModelScope.launch {
            val result = repository.closeNeed(needId)
            if (result is Resource.Success) {
                _nearbyNeeds.value = _nearbyNeeds.value.filter { it.id != needId }
                _matches.value = _matches.value.filter { it.id != needId }
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to close")
            }
        }
    }
}
