package com.example.mistreal_mini.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mistreal_mini.data.Resource
import com.example.mistreal_mini.data.model.ChatMessage
import com.example.mistreal_mini.data.repository.AiRepository
import com.example.mistreal_mini.data.repository.MarketRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Polls for price alerts that fired server-side since the last check (see
 * marketDataService.checkAlerts() on the backend) and surfaces each one the
 * same way the rest of the app delivers proactive moments: a message in chat
 * plus a local notification — not a push notification, since there's no FCM
 * wiring in this app. Mirrors SilentPartnerWorker's notification pattern.
 */
@HiltWorker
class MarketAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val marketRepository: MarketRepository,
    private val aiRepository: AiRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pendingResult = marketRepository.getPendingAlerts()
        val pending = (pendingResult as? Resource.Success)?.data
        if (pending == null) {
            // getPendingAlerts() already logged the specific cause — retry rather
            // than silently giving up until the next scheduled 30-min run, so a
            // transient network blip doesn't delay a real alert indefinitely.
            Timber.w("MarketAlertWorker: fetch failed, scheduling retry")
            return Result.retry()
        }

        Timber.d("MarketAlertWorker: %d pending alert(s) to deliver", pending.size)
        var deliveredCount = 0
        for (alert in pending) {
            val direction = if (alert.direction == "above") "rose above" else "fell below"
            val priceText = alert.triggeredPrice?.let { "%.2f".format(it) } ?: "your target"
            val message = "🔔 ${alert.symbol} just $direction ${"%.2f".format(alert.targetPrice)} (now $priceText ${alert.currency ?: "USD"}) — your price alert is live."

            aiRepository.saveMessage(
                ChatMessage(role = "assistant", content = message, type = "text", provider = "system")
            )
            sendNotification(alert.symbol, message)
            val ackResult = marketRepository.acknowledgeAlert(alert.id)
            if (ackResult is Resource.Error) {
                // Delivered locally but the backend doesn't know it yet — it'll
                // show up again next run (harmless duplicate, logged either way).
                Timber.w("MarketAlertWorker: acknowledge failed for alert id=%d, will redeliver next run", alert.id)
            }
            deliveredCount++
        }
        Timber.d("MarketAlertWorker: delivered %d/%d alert(s)", deliveredCount, pending.size)

        return Result.success()
    }

    private fun sendNotification(symbol: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "market_alerts"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Price Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Price Alert: $symbol")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2000 + symbol.hashCode(), notification)
    }
}
