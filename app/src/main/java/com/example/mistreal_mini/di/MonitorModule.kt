package com.example.mistreal_mini.di

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.PowerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MonitorModule {

    @Provides
    @Singleton
    fun provideBatteryManager(@ApplicationContext context: Context): BatteryManager {
        return context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    @Provides
    @Singleton
    fun providePowerManager(@ApplicationContext context: Context): PowerManager {
        return context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    @Provides
    @Singleton
    fun provideConnectivityManager(@ApplicationContext context: Context): ConnectivityManager {
        return context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    @Provides
    @Singleton
    fun provideActivityManager(@ApplicationContext context: Context): ActivityManager {
        return context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
}
