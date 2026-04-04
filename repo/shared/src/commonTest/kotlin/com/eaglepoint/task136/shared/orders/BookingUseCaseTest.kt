package com.eaglepoint.task136.shared.orders

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class BookingUseCaseTest {
    @Test
    fun `overlap honors ten minute buffer`() {
        val useCase = BookingUseCase(
            orderDao = FakeOrderDao(),
            clock = object : Clock {
                override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
            },
        )

        val existing = listOf(
            TimeWindow(
                start = Instant.parse("2026-03-30T10:00:00Z"),
                end = Instant.parse("2026-03-30T11:00:00Z"),
            ),
        )

        val candidate = TimeWindow(
            start = Instant.parse("2026-03-30T11:05:00Z"),
            end = Instant.parse("2026-03-30T11:35:00Z"),
        )

        assertTrue(useCase.overlaps(existing, candidate))
    }

    @Test
    fun `find three slots returns three windows`() = runTest {
        val dao = FakeOrderDao(
            listOf(
                OrderEntity(
                    id = "1",
                    userId = "u1",
                    resourceId = "r1",
                    state = "Confirmed",
                    startTime = Instant.parse("2026-03-30T10:00:00Z").toEpochMilliseconds(),
                    endTime = Instant.parse("2026-03-30T11:00:00Z").toEpochMilliseconds(),
                    expiresAt = null,
                    quantity = 1,
                    totalPrice = 10.0,
                ),
            ),
        )

        val useCase = BookingUseCase(
            orderDao = dao,
            clock = object : Clock {
                override fun now(): Instant = Instant.parse("2026-03-30T09:00:00Z")
            },
        )

        val slots = useCase.findThreeAvailableSlots(
            resourceId = "r1",
            duration = 30.minutes,
            anchor = Instant.parse("2026-03-30T09:00:00Z"),
        )

        assertEquals(3, slots.size)
    }
}

private class FakeOrderDao(
    private val orders: List<OrderEntity> = emptyList(),
) : OrderDao {
    override suspend fun upsert(order: OrderEntity) = Unit
    override suspend fun update(order: OrderEntity) = Unit
    override suspend fun getById(orderId: String): OrderEntity? = orders.firstOrNull { it.id == orderId }
    override suspend fun getByIdForActor(orderId: String, actorId: String): OrderEntity? =
        orders.firstOrNull { it.id == orderId && it.userId == actorId }
    override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity? =
        orders.firstOrNull { it.id == orderId && it.userId in listOf(ownerId, delegateOwnerId) }
    override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
    override suspend fun getActiveByResource(resourceId: String): List<OrderEntity> = orders.filter { it.resourceId == resourceId }
    override suspend fun deleteById(orderId: String) = Unit
    override suspend fun page(limit: Int): List<OrderEntity> = orders.take(limit)
    override suspend fun getExpiredPendingOrders(nowMillis: Long): List<OrderEntity> = orders.filter {
        it.state == "PendingTender" && it.expiresAt != null && it.expiresAt <= nowMillis
    }
    override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long): Double = 0.0
    override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long): Double = 0.0
}
