package com.example.mistreal_mini.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.CameraProfileDto
import com.example.mistreal_mini.data.repository.CameraRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Dashboard's "Cameras" tab — the live-cam discovery feed. */
@HiltViewModel
class CamerasViewModel @Inject constructor(
    private val cameraRepository: CameraRepository
) : ViewModel() {

    private val _cameras = MutableStateFlow<List<CameraProfileDto>>(emptyList())
    val cameras = _cameras.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting = _isSubmitting.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    fun loadCameras() {
        viewModelScope.launch {
            _isLoading.value = true
            when (val result = cameraRepository.getCameras()) {
                is Resource.Success -> _cameras.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load cameras")
                else -> {}
            }
            _isLoading.value = false
        }
    }

    fun submitCamera(title: String, location: String?, streamUrl: String, streamType: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = cameraRepository.submitCamera(title, location, streamUrl, streamType)
            _isSubmitting.value = false
            if (result is Resource.Success) {
                onResult(true)
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to submit camera")
                onResult(false)
            }
        }
    }
}
