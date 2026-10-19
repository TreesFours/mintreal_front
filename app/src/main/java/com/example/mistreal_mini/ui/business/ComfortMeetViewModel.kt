package com.example.mistreal_mini.ui.business

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.repository.MeetupRepository
import com.example.mistreal_mini.monitor.PressureLevel
import com.example.mistreal_mini.monitor.ResourceMonitor
import com.example.mistreal_mini.util.LocationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

// Kept deliberately less aggressive than other adaptive loops in the app —
// this is a short, user-initiated, time-bounded confirmation between two
// people trying to prove they're in the same place right now, not an
// always-on background poll, so even under pressure it shouldn't degrade
// far enough to feel broken.
private fun PressureLevel.comfortMeetPingInterval(): Long = when (this) {
    PressureLevel.NONE, PressureLevel.MILD -> 6_000L
    PressureLevel.MODERATE -> 12_000L
    PressureLevel.SEVERE -> 20_000L
}

/**
 * Drives a comfort meet's "I'm here" screen — repeatedly sends this
 * device's live location to meetupRoutes.ts's comfort-ping while the
 * screen is open, until the backend reports both sides close enough to
 * confirm (cross-checked against the OTHER device's own pings, not a
 * self-report like the business-meetup flow).
 */
@HiltViewModel
class ComfortMeetViewModel @Inject constructor(
    private val meetupRepository: MeetupRepository,
    private val locationHelper: LocationHelper,
    private val resourceMonitor: ResourceMonitor
) : ViewModel() {

    private val _status = mutableStateOf("Tap \"I'm here\" when you're both ready to confirm in person.")
    val status: State<String> = _status

    private val _isActive = mutableStateOf(false)
    val isActive: State<Boolean> = _isActive

    private val _isConfirmed = mutableStateOf(false)
    val isConfirmed: State<Boolean> = _isConfirmed

    private var pingJob: kotlinx.coroutines.Job? = null

    fun start(meetupId: Int) {
        if (_isActive.value) return
        _isActive.value = true
        _status.value = "Checking you're both nearby..."
        resourceMonitor.ensureMonitoring()
        pingJob = viewModelScope.launch {
            while (_isActive.value && !_isConfirmed.value) {
                val location = locationHelper.getCurrentLocation()
                if (location == null) {
                    _status.value = "Couldn't get your location — check location permission and try again."
                } else {
                    when (val result = meetupRepository.comfortMeetPing(meetupId, location.latitude, location.longitude)) {
                        is Resource.Success -> {
                            val response = result.data
                            if (response != null && response.confirmed) {
                                _isConfirmed.value = true
                                _isActive.value = false
                                val distance = response.distanceMeters
                                _status.value = if (distance != null) {
                                    "Confirmed — you were %.0fm apart. Comfort meet logged.".format(distance)
                                } else {
                                    "Confirmed — comfort meet logged."
                                }
                            } else {
                                _status.value = "Waiting for the other person to also open this screen nearby..."
                            }
                        }
                        is Resource.Error -> _status.value = result.message ?: "Couldn't check proximity right now."
                        else -> {}
                    }
                }
                delay(resourceMonitor.pressureNow().comfortMeetPingInterval())
            }
        }
    }

    fun stop() {
        _isActive.value = false
        pingJob?.cancel()
        if (!_isConfirmed.value) {
            _status.value = "Tap \"I'm here\" when you're both ready to confirm in person."
        }
    }

    override fun onCleared() {
        super.onCleared()
        pingJob?.cancel()
    }
}
