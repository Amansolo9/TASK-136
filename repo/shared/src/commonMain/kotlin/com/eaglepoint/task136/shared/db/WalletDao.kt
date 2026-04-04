package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(wallet: WalletEntity)

    @Query("SELECT * FROM wallets WHERE userId = :userId")
    suspend fun getByUserId(userId: String): WalletEntity?

    @Update
    suspend fun update(wallet: WalletEntity)
}
