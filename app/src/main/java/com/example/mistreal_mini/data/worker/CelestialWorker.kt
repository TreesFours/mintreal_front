package com.example.mistreal_mini.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.mistreal_mini.data.repository.InfoRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class CelestialWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val infoRepository: InfoRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("🚀 CelestialWorker: Synchronizing orbital assets...")
            
            // Sync Planets from NASA JPL — kept in sync with the full list DashboardViewModel
            // actually uses (fetchCelestialData), which previously drifted from this one.
            val bodies = listOf("10", "199", "299", "399", "499", "599", "699", "799", "899", "301", "999")
            bodies.forEach { id ->
                infoRepository.getCelestialVectors(id, null, null)
            }
            
            // In a real implementation, we would store these in a Local DB 
            // so the UI can react to them immediately even offline.
            
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "❌ CelestialWorker failed")
            Result.retry()
        }
    }
}
