package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(invoice: InvoiceEntity)

    @Query("SELECT * FROM invoices WHERE ownerId = :ownerId ORDER BY createdAt DESC")
    suspend fun getByOwner(ownerId: String): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: String): InvoiceEntity?

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<InvoiceEntity>
}
