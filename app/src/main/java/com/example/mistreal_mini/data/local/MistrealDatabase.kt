package com.example.mistreal_mini.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.mistreal_mini.data.local.dao.ChatDao
import com.example.mistreal_mini.data.local.dao.LocationHistoryDao
import com.example.mistreal_mini.data.local.dao.SavedIntelDao
import com.example.mistreal_mini.data.local.dao.SocialContactDao
import com.example.mistreal_mini.data.local.dao.SocialPostDao
import com.example.mistreal_mini.data.local.dao.ScribeDao
import com.example.mistreal_mini.data.local.dao.BankDao
import com.example.mistreal_mini.data.local.dao.business.BusinessDao
import com.example.mistreal_mini.data.local.entity.*

@Database(
    entities = [
        ChatEntity::class, 
        LocationHistoryEntity::class, 
        SavedIntelEntity::class, 
        SocialContactEntity::class,
        SocialPostEntity::class,
        ScribeNoteEntity::class,
        BusinessEntity::class,
        BusinessItemEntity::class,
        BankLinkEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class MistrealDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun locationHistoryDao(): LocationHistoryDao
    abstract fun savedIntelDao(): SavedIntelDao
    abstract fun socialContactDao(): SocialContactDao
    abstract fun socialPostDao(): SocialPostDao
    abstract fun scribeDao(): ScribeDao
    abstract fun businessDao(): BusinessDao
    abstract fun bankDao(): BankDao
}
