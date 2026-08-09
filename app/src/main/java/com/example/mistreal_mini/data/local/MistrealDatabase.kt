package com.example.mistreal_mini.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.entity.ChatEntity

@Database(entities = [ChatEntity::class], version = 2, exportSchema = false)
abstract class MistrealDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
