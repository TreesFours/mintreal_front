package com.example.mistreal_mini.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * While a Guardian alert is active (fired, not yet resolved or escalated),
 * repeatedly reminds the user to check in — mirrors SilentPartnerWorker's
 * pattern, but "buzzing" is intentional here rather than a one-shot nudge,
 * since staying silent is exactly what leads to the 30-day public
 * escalation (see the server-side sweep in index.ts). Checking in from this
 * notification, or the blocking confirm sheet shown on app open while an
 * alert is active, resolves it immediately via
 * InfoRepository.resolveEmergencyAlert.
 */
@HiltWorker
class SafeHavenCheckinWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val infoRepository: InfoRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val deviceId = Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)
        val result = infoRepository.getEmergencyAlerts(deviceId)
        val hasActiveAlert = (result as? Resource.Success)?.data?.any { it.status == "active" } == true
        if (hasActiveAlert) {
            sendCheckinReminder()
        }
        return Result.success()
    }

    private fun sendCheckinReminder() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "safe_haven_checkin"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Safe Haven Check-in", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("You have an active safety alert")
            .setContentText("Tap to confirm you're safe, or it may escalate publicly if nobody responds.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1002, notification)
    }
}
