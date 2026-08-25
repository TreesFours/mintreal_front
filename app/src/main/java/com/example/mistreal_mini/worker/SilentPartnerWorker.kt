package com.example.mistreal_mini.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mistreal_mini.R
import com.example.mistreal_mini.data.local.PreferenceManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SilentPartnerWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val preferenceManager: PreferenceManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val isTherapistEnabled = preferenceManager.isSupportiveTruthTellerEnabled.first()
        val isNudgeEnabled = preferenceManager.isProactiveNudgeEnabled.first()
        
        if (!isTherapistEnabled || !isNudgeEnabled) return Result.success()

        val lastInteraction = preferenceManager.lastInteractionTime.first()
        val now = System.currentTimeMillis()
        val silenceThreshold = 24 * 60 * 60 * 1000 // 24 hours

        if (now - lastInteraction > silenceThreshold) {
            sendShadowNudge()
        }

        return Result.success()
    }

    private fun sendShadowNudge() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "shadow_nudge"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Shadow Check-in", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Placeholder
            .setContentTitle("Shadow Check-in")
            .setContentText("Operator, just checking in on the mission status. How are things on your end?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }
}
