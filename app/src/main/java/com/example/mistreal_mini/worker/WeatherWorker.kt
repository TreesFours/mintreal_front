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
import com.example.mistreal_mini.data.local.PreferenceManager
import com.example.mistreal_mini.data.repository.InfoRepository
import com.example.mistreal_mini.util.LocationHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime

@HiltWorker
class WeatherWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val infoRepository: InfoRepository,
    private val locationHelper: LocationHelper,
    private val preferenceManager: PreferenceManager
) : CoroutineWorker(context, params) {

    companion object {
        // One daily briefing, delivered on whichever hourly run lands first
        // at/after this local hour — not a second, finer-grained schedule.
        private const val BRIEFING_HOUR = 7
    }

    override suspend fun doWork(): Result {
        val location = locationHelper.getCurrentLocation()
        if (location != null) {
            val result = infoRepository.getWeather(location.latitude, location.longitude)
            if (result is Resource.Success) {
                val weather = result.data
                if (weather != null) {
                    if (weather.rainExpected) {
                        showNotification(
                            "Rain Alert ☔",
                            "Intel reports precipitation in ${weather.timeToRain ?: 60}m. Gear up.",
                            101
                        )
                    } else if (weather.summary.lowercase().contains("rain") && !weather.rainExpected) {
                        // Logic to detect rain stopping (summary contains rain but expected is false)
                        showNotification(
                            "Weather Clear 🌤️",
                            "Rain is clearing up. Expect clear skies in 30-60m.",
                            101
                        )
                    }

                    maybeSendDailyBriefing(weather.summary, weather.location)
                }
            }
        }
        return Result.success()
    }

    // Time-based, distinct from the reactive rain alert above — fires once
    // per day, on the first hourly run at/after BRIEFING_HOUR local time.
    private suspend fun maybeSendDailyBriefing(summary: String, location: String?) {
        val today = LocalDate.now().toString()
        val lastSent = preferenceManager.lastWeatherBriefingDate.first()
        if (lastSent == today) return
        if (LocalTime.now().hour < BRIEFING_HOUR) return

        showNotification(
            "Morning Briefing ☀️",
            "${location?.let { "$it — " } ?: ""}$summary",
            102
        )
        preferenceManager.setLastWeatherBriefingDate(today)
    }

    private fun showNotification(title: String, message: String, notificationId: Int) {
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

        manager.notify(notificationId, notification)
    }
}
