package com.example.mistreal_mini

import android.app.Application
import com.google.firebase.FirebaseApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MistrealApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Using a safe check since BuildConfig might not be generated yet in this environment
        try {
            val debugClass = Class.forName("${packageName}.BuildConfig")
            val isDebug = debugClass.getField("DEBUG").getBoolean(null)
            if (isDebug) {
                Timber.plant(Timber.DebugTree())
            }
        } catch (e: Exception) {
            // Fallback: only plant if actually needed or skip
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
