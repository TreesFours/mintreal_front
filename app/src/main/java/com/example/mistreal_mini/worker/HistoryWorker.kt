package com.example.mistreal_mini.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mistreal_mini.data.local.MistrealDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class HistoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: MistrealDatabase
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            // 4 days in milliseconds
            val fourDaysInMillis = 4 * 24 * 60 * 60 * 1000L
            val threshold = System.currentTimeMillis() - fourDaysInMillis
            
            database.chatDao().deleteExpiredTrends(threshold)
            
            Timber.d("🧹 HistoryWorker: Expired trend messages purged (older than 4 days)")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "❌ HistoryWorker: Failed to purge old messages")
            Result.retry()
        }
    }
}
