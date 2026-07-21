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
import com.example.mistreal_mini.util.LocationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class WeatherWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val location = locationHelper.getCurrentLocation()
        if (location != null) {
            val result = infoRepository.getWeather(location.latitude, location.longitude)
            if (result is Resource.Success) {
                val weather = result.data
                if (weather != null && weather.rainExpected) {
                    showNotification(
                        "Rain Alert ☔",
                        "Hey dude, grab your umbrella! Rain expected in ${weather.timeToRain ?: 60} minutes."
                    )
                }
            }
        }
        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "WEATHER_ALERTS"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Weather Alerts", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        manager.notify(100, notification)
    }
}
