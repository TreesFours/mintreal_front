package com.example.mistreal_mini.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.local.entity.ScribeNoteEntity
import com.example.mistreal_mini.data.repository.ScribeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScribeViewModel @Inject constructor(
    private val repository: ScribeRepository
) : ViewModel() {

    val scribeNotes: StateFlow<List<ScribeNoteEntity>> = repository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteNote(note: ScribeNoteEntity) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }
}
