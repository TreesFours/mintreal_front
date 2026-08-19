package com.example.mistreal_mini.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.dao.LocationHistoryDao
import com.example.mistreal_mini.data.local.dao.SavedIntelDao
import com.example.mistreal_mini.data.local.entity.ChatEntity
import com.example.mistreal_mini.data.local.entity.LocationHistoryEntity
import com.example.mistreal_mini.data.local.entity.SavedIntelEntity

@Database(entities = [ChatEntity::class, LocationHistoryEntity::class, SavedIntelEntity::class], version = 4, exportSchema = false)
abstract class MistrealDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun savedIntelDao(): SavedIntelDao
}
