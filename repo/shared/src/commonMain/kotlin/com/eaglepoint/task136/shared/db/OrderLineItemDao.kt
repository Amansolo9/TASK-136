package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface OrderLineItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: OrderLineItemEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<OrderLineItemEntity>)

    @Query("SELECT * FROM order_line_items WHERE orderId = :orderId ORDER BY id ASC")
    suspend fun getByOrderId(orderId: String): List<OrderLineItemEntity>

    @Query("DELETE FROM order_line_items WHERE orderId = :orderId")
    suspend fun deleteByOrderId(orderId: String)
}
