package com.example.mistreal_mini.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.mistreal_mini.data.repository.CallRepository
import com.example.mistreal_mini.util.DeviceIdProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Declining a call needs nothing camera/mic-related — routing it through
 * CallService (a camera|microphone-typed foreground service) would risk a
 * SecurityException on Android 14+ if the user never granted those
 * permissions, just for saying no to a call. A plain BroadcastReceiver has
 * no such requirement.
 */
@AndroidEntryPoint
class CallDeclineReceiver : BroadcastReceiver() {

    @Inject lateinit var callRepository: CallRepository
    @Inject lateinit var deviceIdProvider: DeviceIdProvider

    companion object {
        const val ACTION_DECLINE = "com.example.mistreal_mini.ACTION_DECLINE_CALL"
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                callRepository.connect(deviceIdProvider.deviceId)
                // The socket needs a moment to actually connect before decline
                // can be emitted — there's no ack to await here (fire-and-forget
                // is fine for a decline), just enough of a window for the
                // connect handshake to complete.
                delay(1500)
                callRepository.decline(callId)
                delay(500)
            } finally {
                // callRepository is a shared Singleton — if CallService is
                // mid-call on this exact socket (a second incoming-call push
                // arrived for a different call attempt while one was already
                // active), disconnecting here would kill the active call's
                // signaling out from under it. Only tear down if nothing else
                // is actually using it.
                if (CallService.callStatusState.value == "idle") {
                    callRepository.disconnect()
                }
                pendingResult.finish()
            }
        }
    }
}
