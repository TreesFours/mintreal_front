package com.example.mistreal_mini.di

import android.content.Context
import androidx.room.Room
import com.example.mistreal_mini.data.local.MistrealDatabase
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.util.SecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
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
    fun provideDatabase(
        @ApplicationContext context: Context,
        securityManager: SecurityManager,
    ): MistrealDatabase {
        net.sqlcipher.database.SQLiteDatabase.loadLibs(context)
        val dbName = "mistreal_db"
        val passphrase = securityManager.getDatabasePassphrase().toByteArray()
        val factory = SupportFactory(passphrase)
        
        // Safety check for SQLCipher encryption mismatch
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            try {
                // Try to open it briefly to verify passphrase
                val db = net.sqlcipher.database.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    securityManager.getDatabasePassphrase(),
                    null,
                    net.sqlcipher.database.SQLiteDatabase.OPEN_READONLY
                )
                db.close()
            } catch (ignored: Exception) {
                // If opening fails (likely "file is not a database"), delete it to allow recreation
                context.deleteDatabase(dbName)
            }
        }

        return Room.databaseBuilder(
            context,
            MistrealDatabase::class.java,
            dbName
        )
        .openHelperFactory(factory)
        .fallbackToDestructiveMigration()
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

    @Provides
    fun provideSocialContactDao(db: MistrealDatabase): com.example.mistreal_mini.data.local.dao.SocialContactDao {
        return db.socialContactDao()
    }

    @Provides
    fun provideScribeDao(db: MistrealDatabase): com.example.mistreal_mini.data.local.dao.ScribeDao {
        return db.scribeDao()
    }

    @Provides
    fun provideBusinessDao(db: MistrealDatabase): com.example.mistreal_mini.data.local.dao.business.BusinessDao {
        return db.businessDao()
    }

    @Provides
    fun provideBankDao(db: MistrealDatabase): com.example.mistreal_mini.data.local.dao.BankDao {
        return db.bankDao()
    }
}
