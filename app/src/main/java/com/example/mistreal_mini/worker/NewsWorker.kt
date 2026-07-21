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
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NewsWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val infoRepository: InfoRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Fetch breaking news for a default category or user preference
        val result = infoRepository.getNews("general", null)
        if (result is Resource.Success) {
            val articles = result.data?.articles
            if (!articles.isNullOrEmpty()) {
                val topArticle = articles[0]
                showNotification(
                    "Breaking News 🗞️",
                    topArticle.title
                )
            }
        }
        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "NEWS_ALERTS"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "News Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        manager.notify(200, notification)
    }
}
