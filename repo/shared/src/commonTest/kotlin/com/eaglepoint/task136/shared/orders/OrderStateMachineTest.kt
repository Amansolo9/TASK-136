package com.eaglepoint.task136.shared.orders

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.OrderLineItemDao
import com.eaglepoint.task136.shared.db.OrderLineItemEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.rbac.Role
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for OrderStateMachine.
 *
 * State transition tests use FakeOrderDao to verify guard logic and
 * state changes via the real OrderStateMachine class. Since Room's
 * `withTransaction` requires a real SQLite connection, we override
 * `withTransaction` behavior by subclassing AppDatabase with a fake
 * that executes the block directly (no-op transaction wrapper).
 */
class OrderStateMachineTest {

    // ── Enum structure ────────────────────────────────────────────

    @Test
    fun `OrderState has 13 states`() {
        assertEquals(13, OrderState.entries.size)
    }

    @Test
    fun `DeliveryState has 5 states`() {
        assertEquals(5, DeliveryState.entries.size)
    }

    @Test
    fun `PaymentMethod has 3 values`() {
        assertEquals(3, PaymentMethod.entries.size)
    }

    // ── Price validation (pure function, no DB) ──────────────────

    @Test
    fun `price below minimum rejected`() {
        val sm = createSM()
        assertNotNull(sm.validatePrice(0.001))
    }

    @Test
    fun `price above maximum rejected`() {
        val sm = createSM()
        assertNotNull(sm.validatePrice(10_000.00))
    }

    @Test
    fun `price at minimum accepted`() {
        assertNull(createSM().validatePrice(0.01))
    }

    @Test
    fun `price at maximum accepted`() {
        assertNull(createSM().validatePrice(9_999.99))
    }

    @Test
    fun `normal price accepted`() {
        assertNull(createSM().validatePrice(25.50))
    }

    @Test
    fun `zero price rejected`() {
        assertNotNull(createSM().validatePrice(0.0))
    }

    @Test
    fun `negative price rejected`() {
        assertNotNull(createSM().validatePrice(-5.0))
    }

    // ── cancel() ──────────────────────────────────────────────────

    @Test
    fun `cancel from PendingTender restocks`() = runBlocking {
        val store = InMemoryOrderStore()
        val resources = InMemoryResourceStore()
        store.put(makeOrder("o1", "PendingTender", quantity = 2, resourceId = "r1"))
        resources.put(ResourceEntity("r1", "Hall", "Ops", availableUnits = 3, unitPrice = 10.0))
        val sm = createSM(store, resources)

        assertTrue(sm.cancel("o1"))
        assertEquals("Cancelled", store.get("o1")?.state)
        assertEquals(5, resources.get("r1")?.availableUnits)
    }

    @Test
    fun `cancel from Confirmed restocks`() = runBlocking {
        val store = InMemoryOrderStore()
        val resources = InMemoryResourceStore()
        store.put(makeOrder("o1", "Confirmed", quantity = 1, resourceId = "r1"))
        resources.put(ResourceEntity("r1", "Hall", "Ops", availableUnits = 4, unitPrice = 10.0))
        val sm = createSM(store, resources)

        assertTrue(sm.cancel("o1"))
        assertEquals("Cancelled", store.get("o1")?.state)
        assertEquals(5, resources.get("r1")?.availableUnits)
    }

    @Test
    fun `cancel fails from already Cancelled`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Cancelled"))
        val sm = createSM(store)

        assertFalse(sm.cancel("o1"))
    }

    @Test
    fun `cancel fails from Refunded`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Refunded"))
        val sm = createSM(store)

        assertFalse(sm.cancel("o1"))
    }

    @Test
    fun `cancel blocked for Viewer`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertFalse(sm.cancel("o1", Role.Viewer))
    }

    // ── confirm() state guard ────────────────────────────────────

    @Test
    fun `confirm succeeds from PendingTender`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "PendingTender"))
        val sm = createSM(store)

        val result = sm.confirm("o1")
        assertTrue(result)
        assertEquals("Confirmed", store.get("o1")?.state)
    }

    @Test
    fun `confirm fails from Draft`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Draft"))
        val sm = createSM(store)

        val result = sm.confirm("o1")
        assertFalse(result)
        assertEquals("Draft", store.get("o1")?.state)
    }

    @Test
    fun `confirm fails from Cancelled`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Cancelled"))
        val sm = createSM(store)

        assertFalse(sm.confirm("o1"))
    }

    @Test
    fun `confirm fails from Refunded`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Refunded"))
        val sm = createSM(store)

        assertFalse(sm.confirm("o1"))
    }

    @Test
    fun `confirm blocked for Viewer role`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "PendingTender"))
        val sm = createSM(store)

        assertFalse(sm.confirm("o1", Role.Viewer))
        assertEquals("PendingTender", store.get("o1")?.state)
    }

    // ── requestReturn() ──────────────────────────────────────────

    @Test
    fun `return succeeds from Confirmed`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertTrue(sm.requestReturn("o1"))
        assertEquals("ReturnRequested", store.get("o1")?.state)
    }

    @Test
    fun `return succeeds from Delivered`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Delivered"))
        val sm = createSM(store)

        assertTrue(sm.requestReturn("o1"))
    }

    @Test
    fun `return fails from Draft`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Draft"))
        val sm = createSM(store)

        assertFalse(sm.requestReturn("o1"))
    }

    @Test
    fun `return blocked for Viewer`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertFalse(sm.requestReturn("o1", Role.Viewer))
    }

    @Test
    fun `return blocked for Companion`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertFalse(sm.requestReturn("o1", Role.Companion))
    }

    // ── completeReturn() with inventory restock ──────────────────

    @Test
    fun `complete return restocks inventory`() = runBlocking {
        val store = InMemoryOrderStore()
        val resources = InMemoryResourceStore()
        store.put(makeOrder("o1", "ReturnRequested", quantity = 3, resourceId = "r1"))
        resources.put(ResourceEntity("r1", "Hall", "Ops", availableUnits = 2, unitPrice = 10.0))
        val sm = createSM(store, resources)

        assertTrue(sm.completeReturn("o1", Role.Admin))
        assertEquals("Returned", store.get("o1")?.state)
        assertEquals(5, resources.get("r1")?.availableUnits)
    }

    @Test
    fun `complete return fails from wrong state`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertFalse(sm.completeReturn("o1", Role.Admin))
    }

    // ── requestRefund() ──────────────────────────────────────────

    @Test
    fun `refund request succeeds from Confirmed`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertTrue(sm.requestRefund("o1"))
        assertEquals("RefundRequested", store.get("o1")?.state)
    }

    @Test
    fun `refund request succeeds from Returned`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Returned"))
        val sm = createSM(store)

        assertTrue(sm.requestRefund("o1"))
    }

    @Test
    fun `refund request fails from Draft`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Draft"))
        val sm = createSM(store)

        assertFalse(sm.requestRefund("o1"))
    }

    @Test
    fun `refund blocked for Companion`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertFalse(sm.requestRefund("o1", Role.Companion))
    }

    // ── completeRefund() ─────────────────────────────────────────

    @Test
    fun `complete refund restocks inventory`() = runBlocking {
        val store = InMemoryOrderStore()
        val resources = InMemoryResourceStore()
        store.put(makeOrder("o1", "RefundRequested", quantity = 2, resourceId = "r1"))
        resources.put(ResourceEntity("r1", "Hall", "Ops", availableUnits = 5, unitPrice = 10.0))
        val sm = createSM(store, resources)

        assertTrue(sm.completeRefund("o1", Role.Supervisor))
        assertEquals("Refunded", store.get("o1")?.state)
        assertEquals(7, resources.get("r1")?.availableUnits)
    }

    // ── exchange ─────────────────────────────────────────────────

    @Test
    fun `exchange succeeds from Confirmed`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertTrue(sm.requestExchange("o1"))
        assertEquals("ExchangeRequested", store.get("o1")?.state)
    }

    @Test
    fun `exchange fails from Draft`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Draft"))
        val sm = createSM(store)

        assertFalse(sm.requestExchange("o1"))
    }

    // ── delivery flow ────────────────────────────────────────────

    @Test
    fun `delivery flow from Confirmed`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertTrue(sm.markAwaitingDelivery("o1"))
        assertEquals("AwaitingDelivery", store.get("o1")?.state)

        assertTrue(sm.confirmDelivery("o1", "sig-abc"))
        assertEquals("Delivered", store.get("o1")?.state)
        assertEquals("sig-abc", store.get("o1")?.deliverySignature)
    }

    @Test
    fun `delivery fails from Draft`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Draft"))
        val sm = createSM(store)

        assertFalse(sm.markAwaitingDelivery("o1"))
    }

    // ── expireStaleOrders ────────────────────────────────────────

    @Test
    fun `expire stale orders on startup`() = runBlocking {
        val store = InMemoryOrderStore()
        val resources = InMemoryResourceStore()
        store.put(makeOrder("o1", "PendingTender", expiresAt = 100L, quantity = 2, resourceId = "r1"))
        resources.put(ResourceEntity("r1", "Hall", "Ops", availableUnits = 3, unitPrice = 10.0))
        val sm = createSM(store, resources, clockMillis = 200L)

        sm.expireStaleOrders()
        assertEquals("Cancelled", store.get("o1")?.state)
        assertEquals(5, resources.get("r1")?.availableUnits)
    }

    // ── nonexistent order ────────────────────────────────────────

    @Test
    fun `operations on nonexistent order return false`() = runBlocking {
        val sm = createSM()

        assertFalse(sm.confirm("nope", Role.Admin))
        assertFalse(sm.requestReturn("nope"))
        assertFalse(sm.requestRefund("nope"))
        assertFalse(sm.markAwaitingDelivery("nope"))
        assertFalse(sm.completeReturn("nope", Role.Admin))
        assertFalse(sm.completeRefund("nope", Role.Admin))
    }

    // ── null-role rejection (defense-in-depth) ─────────────────

    @Test
    fun `completeReturn blocked with null role`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "ReturnRequested"))
        val sm = createSM(store)

        assertFalse(sm.completeReturn("o1", role = null))
        assertEquals("ReturnRequested", store.get("o1")?.state)
    }

    @Test
    fun `completeRefund blocked with null role`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "RefundRequested"))
        val sm = createSM(store)

        assertFalse(sm.completeRefund("o1", role = null))
        assertEquals("RefundRequested", store.get("o1")?.state)
    }

    @Test
    fun `completeExchange blocked with null role`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "ExchangeRequested"))
        val sm = createSM(store)

        assertFalse(sm.completeExchange("o1", role = null))
        assertEquals("ExchangeRequested", store.get("o1")?.state)
    }

    @Test
    fun `completeReturn succeeds with Admin role`() = runBlocking {
        val store = InMemoryOrderStore()
        val resources = InMemoryResourceStore()
        store.put(makeOrder("o1", "ReturnRequested", quantity = 1, resourceId = "r1"))
        resources.put(ResourceEntity("r1", "Hall", "Ops", availableUnits = 3, unitPrice = 10.0))
        val sm = createSM(store, resources)

        assertTrue(sm.completeReturn("o1", Role.Admin))
        assertEquals("Returned", store.get("o1")?.state)
    }

    @Test
    fun `completeReturn blocked for Operator role`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "ReturnRequested"))
        val sm = createSM(store)

        assertFalse(sm.completeReturn("o1", Role.Operator))
    }

    // ── markInTransit ────────────────────────────────────────────

    @Test
    fun `markInTransit from AwaitingDelivery`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "AwaitingDelivery"))
        val sm = createSM(store)

        assertTrue(sm.markInTransit("o1"))
        assertEquals("InTransit", store.get("o1")?.deliveryState)
    }

    @Test
    fun `markInTransit fails from Confirmed`() = runBlocking {
        val store = InMemoryOrderStore()
        store.put(makeOrder("o1", "Confirmed"))
        val sm = createSM(store)

        assertFalse(sm.markInTransit("o1"))
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun makeOrder(
        id: String,
        state: String,
        quantity: Int = 1,
        resourceId: String = "r1",
        expiresAt: Long? = null,
    ) = OrderEntity(
        id = id, userId = "u1", resourceId = resourceId, state = state,
        startTime = 0L, endTime = 100L, expiresAt = expiresAt,
        quantity = quantity, totalPrice = quantity * 10.0,
    )

    private fun createSM(
        orders: InMemoryOrderStore = InMemoryOrderStore(),
        resources: InMemoryResourceStore = InMemoryResourceStore(),
        clockMillis: Long = 0L,
    ): OrderStateMachine {
        val clock = object : kotlinx.datetime.Clock {
            override fun now() = kotlinx.datetime.Instant.fromEpochMilliseconds(clockMillis)
        }
        val db = FakeOrderDatabase(orders, resources)
        return object : OrderStateMachine(database = db, clock = clock) {
            override suspend fun <R> runInTransaction(block: suspend () -> R): R = block()
        }
    }
}

// ── In-memory stores ─────────────────────────────────────────────

internal class InMemoryOrderStore {
    private val map = mutableMapOf<String, OrderEntity>()
    fun put(order: OrderEntity) { map[order.id] = order }
    fun get(id: String) = map[id]

    fun toDao() = object : OrderDao {
        override suspend fun upsert(order: OrderEntity) { map[order.id] = order }
        override suspend fun update(order: OrderEntity) { map[order.id] = order }
        override suspend fun getById(orderId: String) = map[orderId]
        override suspend fun getByIdForActor(orderId: String, actorId: String) = map[orderId]?.takeIf { it.userId == actorId }
        override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String) =
            map[orderId]?.takeIf { it.userId == ownerId || it.userId == delegateOwnerId }
        override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
        override suspend fun getActiveByResource(resourceId: String) = map.values.filter { it.resourceId == resourceId && it.state != "Cancelled" }
        override suspend fun deleteById(orderId: String) { map.remove(orderId) }
        override suspend fun page(limit: Int) = map.values.take(limit)
        override suspend fun getExpiredPendingOrders(nowMillis: Long) = map.values.filter {
            it.state == "PendingTender" && it.expiresAt != null && it.expiresAt <= nowMillis
        }
        override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long) = map.values
            .filter { it.state in listOf("Confirmed", "Delivered", "AwaitingDelivery") && it.createdAt in fromMillis until toMillis }
            .sumOf { it.totalPrice }
        override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long) = map.values
            .filter { it.state in listOf("Refunded", "Returned") && it.createdAt in fromMillis until toMillis }
            .sumOf { it.totalPrice }
    }
}

internal class InMemoryResourceStore {
    private val map = mutableMapOf<String, ResourceEntity>()
    fun put(res: ResourceEntity) { map[res.id] = res }
    fun get(id: String) = map[id]

    fun toDao() = object : ResourceDao {
        override suspend fun upsert(resource: ResourceEntity) { map[resource.id] = resource }
        override suspend fun upsertAll(resources: List<ResourceEntity>) { resources.forEach { map[it.id] = it } }
        override suspend fun update(resource: ResourceEntity) { map[resource.id] = resource }
        override suspend fun getById(id: String) = map[id]
        override suspend fun page(limit: Int, offset: Int) = map.values.drop(offset).take(limit)
        override suspend fun countAll() = map.size
        override suspend fun deleteById(id: String) { map.remove(id) }
    }
}

/**
 * Fake AppDatabase that delegates to in-memory stores.
 * Room's `withTransaction` extension calls `beginTransaction()` internally.
 * This fake overrides the abstract DB methods; the `withTransaction` extension
 * will execute the lambda directly since we provide a no-op open helper.
 */
internal class FakeOrderDatabase(
    private val orders: InMemoryOrderStore,
    private val resources: InMemoryResourceStore,
) : com.eaglepoint.task136.shared.db.AppDatabase() {
    override fun orderDao() = orders.toDao()
    override fun resourceDao() = resources.toDao()
    override fun orderLineItemDao() = object : OrderLineItemDao {
        override suspend fun upsert(item: OrderLineItemEntity) = Unit
        override suspend fun upsertAll(items: List<OrderLineItemEntity>) = Unit
        override suspend fun getByOrderId(orderId: String) = emptyList<OrderLineItemEntity>()
        override suspend fun deleteByOrderId(orderId: String) = Unit
    }
    override fun userDao() = throw NotImplementedError()
    override fun cartDao() = throw NotImplementedError()
    override fun walletDao() = object : com.eaglepoint.task136.shared.db.WalletDao {
        override suspend fun upsert(wallet: com.eaglepoint.task136.shared.db.WalletEntity) = Unit
        override suspend fun getByUserId(userId: String): com.eaglepoint.task136.shared.db.WalletEntity? = null
        override suspend fun update(wallet: com.eaglepoint.task136.shared.db.WalletEntity) = Unit
    }
    override fun invoiceDao() = object : com.eaglepoint.task136.shared.db.InvoiceDao {
        override suspend fun upsert(invoice: com.eaglepoint.task136.shared.db.InvoiceEntity) = Unit
        override suspend fun getByOwner(ownerId: String) = emptyList<com.eaglepoint.task136.shared.db.InvoiceEntity>()
        override suspend fun getById(id: String): com.eaglepoint.task136.shared.db.InvoiceEntity? = null
        override suspend fun getRecent(limit: Int) = emptyList<com.eaglepoint.task136.shared.db.InvoiceEntity>()
    }
    override fun deviceBindingDao() = throw NotImplementedError()
    override fun governanceDao() = throw NotImplementedError()
    override fun meetingDao() = throw NotImplementedError()
    override fun learningDao() = throw NotImplementedError()
    override fun createInvalidationTracker() = throw NotImplementedError()
    override fun clearAllTables() = Unit
}
