package com.example.mistreal_mini.ui.call

import androidx.lifecycle.ViewModel
import com.example.mistreal_mini.data.local.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Just enough DI to read the Call Ringtone setting from Compose — the
 * notification-driven ringtone (MistrealFirebaseMessagingService's channel
 * sound) and the ring-back tone (CallService) handle their own cases;
 * this one is for CallScreen's in-app IncomingCallPrompt, shown before
 * CallService itself is even started.
 */
@HiltViewModel
class CallScreenViewModel @Inject constructor(
    private val preferenceManager: PreferenceManager
) : ViewModel() {
    val isRingtoneEnabled = preferenceManager.isCallRingtoneEnabled
}
