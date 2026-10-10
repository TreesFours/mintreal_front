package com.example.mistreal_mini.ui.business

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.api.Meetup
import com.example.mistreal_mini.data.repository.MeetupRepository
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeetupViewModel @Inject constructor(
    private val repository: MeetupRepository,
    private val locationHelper: LocationHelper
) : ViewModel() {

    private val _meetups = MutableStateFlow<List<Meetup>>(emptyList())
    val meetups: StateFlow<List<Meetup>> = _meetups.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _lastProposalResult = MutableStateFlow<Resource<Meetup>?>(null)
    val lastProposalResult: StateFlow<Resource<Meetup>?> = _lastProposalResult.asStateFlow()

    fun fetchMeetups() {
        viewModelScope.launch {
            _isLoading.value = true
            val result = repository.getMyMeetups()
            _isLoading.value = false
            if (result is Resource.Success) _meetups.value = result.data ?: emptyList()
        }
    }

    suspend fun getCurrentLocation() = locationHelper.getCurrentLocation()

    fun proposeMeetup(
        businessId: String?,
        counterpartyPlatform: String?,
        counterpartyContactId: String?,
        latitude: Double,
        longitude: Double,
        addressLabel: String?,
        scheduledAtIso: String
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.proposeMeetup(businessId, counterpartyPlatform, counterpartyContactId, latitude, longitude, addressLabel, scheduledAtIso)
            _isSubmitting.value = false
            _lastProposalResult.value = result
            if (result is Resource.Success) fetchMeetups()
        }
    }

    fun clearProposalResult() {
        _lastProposalResult.value = null
    }

    fun confirmMeetup(
        meetupId: Int,
        latitude: Double,
        longitude: Double,
        outcome: String,
        reasonIfFailed: String?,
        reviewText: String?,
        buyerVote: String? = null,
        photoUri: Uri?,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            val result = repository.confirmMeetup(meetupId, latitude, longitude, outcome, reasonIfFailed, reviewText, buyerVote, photoUri)
            _isSubmitting.value = false
            if (result is Resource.Success) fetchMeetups()
            onDone(result is Resource.Success)
        }
    }
}
