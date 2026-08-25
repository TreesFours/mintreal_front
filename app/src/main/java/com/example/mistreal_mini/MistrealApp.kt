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
        // 🛡️ The old reflection-based BuildConfig.DEBUG check always failed silently
        // (buildFeatures.buildConfig isn't enabled in build.gradle.kts, so the class
        // doesn't exist), meaning Timber was NEVER planted and every Timber.d/w/e call
        // in the app went nowhere. Plant unconditionally so logs are actually visible
        // during development; revisit before a real release build.
        Timber.plant(Timber.DebugTree())
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
