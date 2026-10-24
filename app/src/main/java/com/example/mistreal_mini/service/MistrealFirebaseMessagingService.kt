package com.example.mistreal_mini.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.mistreal_mini.MainActivity
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.NotificationChannels
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * The only server-initiated channel this app has — everything else is
 * socket-connected-only or next-time-you-open-it polling. Today this only
 * exists to wake a backgrounded/killed app for an incoming call (see
 * callSignalingService.ts/push.ts); data-only messages so this class
 * always decides how to present it rather than the OS auto-showing a
 * generic notification.
 */
@AndroidEntryPoint
class MistrealFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var infoRepository: InfoRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch {
            val result = infoRepository.registerFcmToken(token)
            if (result is Resource.Error) {
                Timber.w("MistrealFcm: failed to register token: ${result.message}")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        when (data["type"]) {
            "incoming_call" -> showIncomingCallNotification(data["callId"], data["callerDeviceId"])
            else -> Timber.d("MistrealFcm: unhandled data message type=${data["type"]}")
        }
    }

    private fun showIncomingCallNotification(callId: String?, callerDeviceId: String?) {
        if (callId == null || callerDeviceId == null) return
        NotificationChannels.ensureCallChannel(this)

        val acceptIntent = Intent(this, CallService::class.java).apply {
            action = CallService.ACTION_ACCEPT_INCOMING
            putExtra(CallService.EXTRA_CALL_ID, callId)
        }
        val acceptPendingIntent = PendingIntent.getForegroundService(
            this, callId.hashCode(), acceptIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val declineIntent = Intent(this, CallDeclineReceiver::class.java).apply {
            action = CallDeclineReceiver.ACTION_DECLINE
            putExtra(CallDeclineReceiver.EXTRA_CALL_ID, callId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, callId.hashCode() xor 1, declineIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Also opens the in-app incoming-call screen on tap (not just the
        // two notification actions), same as every other deep-link-into-
        // MainActivity's-NavHost pattern this app already uses.
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("incoming_call_id", callId)
            putExtra("incoming_caller_device_id", callerDeviceId)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, callId.hashCode() xor 2, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText(callerDeviceId)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(android.R.drawable.sym_action_call, "Decline", declinePendingIntent)
            .addAction(android.R.drawable.sym_action_call, "Answer", acceptPendingIntent)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(callId.hashCode(), notification)
    }
}
