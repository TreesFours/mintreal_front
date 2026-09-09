package com.example.mistreal_mini.data.local.dao

import androidx.room.*
import com.example.mistreal_mini.data.local.entity.BankLinkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {
    @Query("SELECT * FROM bank_links")
    fun getAllBanks(): Flow<List<BankLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBank(bank: BankLinkEntity)

    @Delete
    suspend fun deleteBank(bank: BankLinkEntity)
}
