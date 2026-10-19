package com.example.mistreal_mini.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CloudStorageConfig
import com.example.mistreal_mini.data.api.CloudStorageFile
import com.example.mistreal_mini.data.repository.CloudStorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudStorageViewModel @Inject constructor(
    private val repository: CloudStorageRepository
) : ViewModel() {

    private val _configs = MutableStateFlow<List<CloudStorageConfig>>(emptyList())
    val configs: StateFlow<List<CloudStorageConfig>> = _configs.asStateFlow()

    private val _files = MutableStateFlow<List<CloudStorageFile>>(emptyList())
    val files: StateFlow<List<CloudStorageFile>> = _files.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isLoadingFiles = MutableStateFlow(false)
    val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    fun loadConfigs() {
        viewModelScope.launch {
            when (val result = repository.getConfigs()) {
                is Resource.Success -> _configs.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load cloud storage accounts")
                else -> {}
            }
        }
    }

    fun addConfig(label: String, baseUrl: String, username: String, password: String) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.createConfig(label, baseUrl, username, password)
            _isSaving.value = false
            if (result is Resource.Success) loadConfigs()
            else _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to save cloud storage account")
        }
    }

    fun activateConfig(configId: Int) {
        viewModelScope.launch {
            val result = repository.activateConfig(configId)
            if (result is Resource.Success) loadConfigs()
            else _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to switch cloud storage account")
        }
    }

    fun deleteConfig(configId: Int) {
        viewModelScope.launch {
            val result = repository.deleteConfig(configId)
            if (result is Resource.Success) loadConfigs()
            else _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to remove account")
        }
    }

    fun loadFiles(path: String = "/") {
        viewModelScope.launch {
            _isLoadingFiles.value = true
            when (val result = repository.getFiles(path)) {
                is Resource.Success -> _files.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to list files")
                else -> {}
            }
            _isLoadingFiles.value = false
        }
    }
}
