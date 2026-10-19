package com.example.mistreal_mini.ui.profile

import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.Review
import com.example.mistreal_mini.data.api.ReviewThreadMessage
import com.example.mistreal_mini.data.api.ReviewThreadParticipant
import com.example.mistreal_mini.data.api.SocialStat
import com.example.mistreal_mini.data.repository.ReviewRepository
import com.example.mistreal_mini.util.VoiceRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val repository: ReviewRepository,
    private val voiceRecorder: VoiceRecorder
) : ViewModel() {

    val myDeviceId: String get() = repository.myDeviceId()

    // Voice-review recording — same start/stop/delete/playback shape
    // ChatViewModel already uses for its own voice input, just owned here
    // instead since a review submission isn't part of a chat session.
    private val _isRecording = mutableStateOf(false)
    val isRecording: State<Boolean> = _isRecording

    private val _recordingDuration = mutableIntStateOf(0)
    val recordingDuration: State<Int> = _recordingDuration

    private val _recordedFile = mutableStateOf<File?>(null)
    val recordedFile: State<File?> = _recordedFile

    private val _isPlayingBack = mutableStateOf(false)
    val isPlayingBack: State<Boolean> = _isPlayingBack

    private var recordingTimerJob: Job? = null

    fun startRecording() {
        _isRecording.value = true
        _recordedFile.value = voiceRecorder.startRecording()
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            _recordingDuration.intValue = 0
            while (_isRecording.value) {
                delay(1000)
                _recordingDuration.intValue++
            }
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        voiceRecorder.stopRecording()
        recordingTimerJob?.cancel()
    }

    fun deleteRecording() {
        _recordedFile.value?.let { voiceRecorder.deleteRecording(it) }
        _recordedFile.value = null
        _isRecording.value = false
        recordingTimerJob?.cancel()
    }

    fun playRecording() {
        _recordedFile.value?.let {
            _isPlayingBack.value = true
            voiceRecorder.playRecording(it) { _isPlayingBack.value = false }
        }
    }

    private val _reviews = MutableStateFlow<List<Review>>(emptyList())
    val reviews: StateFlow<List<Review>> = _reviews.asStateFlow()

    private val _pendingReviews = MutableStateFlow<List<Review>>(emptyList())
    val pendingReviews: StateFlow<List<Review>> = _pendingReviews.asStateFlow()

    private val _socialStats = MutableStateFlow<List<SocialStat>>(emptyList())
    val socialStats: StateFlow<List<SocialStat>> = _socialStats.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _errorEvent = MutableSharedFlow<String>()
    val errorEvent = _errorEvent.asSharedFlow()

    private val _threadMessages = MutableStateFlow<List<ReviewThreadMessage>>(emptyList())
    val threadMessages: StateFlow<List<ReviewThreadMessage>> = _threadMessages.asStateFlow()

    private val _threadParticipants = MutableStateFlow<List<ReviewThreadParticipant>>(emptyList())
    val threadParticipants: StateFlow<List<ReviewThreadParticipant>> = _threadParticipants.asStateFlow()

    fun loadProfile(revieweeDeviceId: String) {
        viewModelScope.launch {
            when (val result = repository.getReviews(revieweeDeviceId)) {
                is Resource.Success -> _reviews.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load reviews")
                else -> {}
            }
        }
        viewModelScope.launch {
            when (val result = repository.getSocialStats(revieweeDeviceId)) {
                is Resource.Success -> _socialStats.value = result.data ?: emptyList()
                else -> {} // Stats are opportunistic — no error surfaced if unavailable.
            }
        }
    }

    fun loadPendingReviews() {
        viewModelScope.launch {
            when (val result = repository.getPendingReviews()) {
                is Resource.Success -> _pendingReviews.value = result.data ?: emptyList()
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load pending reviews")
                else -> {}
            }
        }
    }

    fun submitReview(revieweeDeviceId: String, businessId: String?, contentText: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val voiceUri = _recordedFile.value?.let { Uri.fromFile(it) }
            val result = repository.createReview(revieweeDeviceId, businessId, contentText, voiceUri)
            _isSubmitting.value = false
            if (result is Resource.Success) {
                _recordedFile.value = null
                onDone(true)
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to submit review")
                onDone(false)
            }
        }
    }

    fun hideReview(reviewId: Int, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.hideReview(reviewId)
            if (result is Resource.Success) {
                _pendingReviews.value = _pendingReviews.value.filter { it.id != reviewId }
                _reviews.value = _reviews.value.filter { it.id != reviewId }
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to hide review")
            }
            onDone(result is Resource.Success)
        }
    }

    /** Permanent — replaces the review with its tombstone version everywhere it's currently shown, rather than removing it (the row stays as a record, see ReviewRepository.deleteReview). */
    fun deleteReview(reviewId: Int, reason: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteReview(reviewId, reason)
            if (result is Resource.Success) {
                val tombstone = result.data
                _pendingReviews.value = _pendingReviews.value.filter { it.id != reviewId }
                _reviews.value = _reviews.value.map { if (it.id == reviewId) (tombstone ?: it) else it }
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to delete review")
            }
            onDone(result is Resource.Success)
        }
    }

    fun loadThread(reviewId: Int) {
        viewModelScope.launch {
            when (val result = repository.getReviewThread(reviewId)) {
                is Resource.Success -> {
                    _threadMessages.value = result.data?.messages ?: emptyList()
                    _threadParticipants.value = result.data?.participants ?: emptyList()
                }
                is Resource.Error -> _errorEvent.emit(result.message ?: "Failed to load discussion")
                else -> {}
            }
        }
    }

    fun sendReply(reviewId: Int, contentText: String) {
        viewModelScope.launch {
            val result = repository.replyToReview(reviewId, contentText)
            if (result is Resource.Success) {
                loadThread(reviewId)
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to send reply")
            }
        }
    }

    fun inviteToThread(reviewId: Int, invitedDeviceId: String) {
        viewModelScope.launch {
            val result = repository.inviteToReviewThread(reviewId, invitedDeviceId)
            if (result is Resource.Error) {
                _errorEvent.emit(result.message ?: "Failed to send invite")
            }
        }
    }

    fun respondToInvite(reviewId: Int, inviteId: Int, approve: Boolean) {
        viewModelScope.launch {
            val result = repository.respondToReviewInvite(reviewId, inviteId, approve)
            if (result is Resource.Success) {
                loadThread(reviewId)
            } else {
                _errorEvent.emit((result as? Resource.Error)?.message ?: "Failed to respond to invite")
            }
        }
    }
}
