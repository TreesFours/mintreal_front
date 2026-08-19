package com.example.mistreal_mini.di

import android.content.Context
import androidx.room.Room
import com.example.mistreal_mini.data.local.MistrealDatabase
import com.example.mistreal_mini.data.local.dao.ChatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): com.google.firebase.auth.FirebaseAuth {
        return com.google.firebase.auth.FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MistrealDatabase {
        return Room.databaseBuilder(
            context,
            MistrealDatabase::class.java,
            "mistreal_db"
        )
        .fallbackToDestructiveMigration() // Professional strategy for development phase
        .build()
    }

    @Provides
    fun provideChatDao(db: MistrealDatabase): ChatDao {
        return db.chatDao()
    }

    @Provides
    fun provideLocationHistoryDao(db: MistrealDatabase): com.example.mistreal_mini.data.local.dao.LocationHistoryDao {
        return db.locationHistoryDao()
    }

    @Provides
    fun provideSavedIntelDao(db: MistrealDatabase): com.example.mistreal_mini.data.local.dao.SavedIntelDao {
        return db.savedIntelDao()
    }
}
