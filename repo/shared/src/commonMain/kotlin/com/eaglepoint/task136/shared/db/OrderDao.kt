package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(order: OrderEntity)

    @Update
    suspend fun update(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :orderId")
    suspend fun getById(orderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :orderId AND userId = :actorId")
    suspend fun getByIdForActor(orderId: String, actorId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :orderId AND userId IN (:ownerId, :delegateOwnerId)")
    suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE id = :orderId")
    fun observeById(orderId: String): Flow<OrderEntity?>

    @Query("SELECT * FROM orders WHERE resourceId = :resourceId AND state != 'Cancelled' ORDER BY startTime ASC")
    suspend fun getActiveByResource(resourceId: String): List<OrderEntity>

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteById(orderId: String)

    @Query("SELECT * FROM orders ORDER BY startTime DESC LIMIT :limit")
    suspend fun page(limit: Int): List<OrderEntity>

    @Query("SELECT * FROM orders WHERE state = 'PendingTender' AND expiresAt IS NOT NULL AND expiresAt <= :nowMillis")
    suspend fun getExpiredPendingOrders(nowMillis: Long): List<OrderEntity>

    @Query("SELECT COALESCE(SUM(totalPrice), 0.0) FROM orders WHERE state IN ('Confirmed', 'Delivered', 'AwaitingDelivery') AND createdAt >= :fromMillis AND createdAt < :toMillis")
    suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long): Double

    @Query("SELECT COALESCE(SUM(totalPrice), 0.0) FROM orders WHERE state IN ('Refunded', 'Returned') AND createdAt >= :fromMillis AND createdAt < :toMillis")
    suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long): Double
}
