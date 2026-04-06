package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import com.eaglepoint.task136.shared.repository.CoreRepository
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OrderAuthorizationTest {

    private val testClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }

    private val testOrder = OrderEntity(
        id = "ord-1",
        userId = "operator",
        resourceId = "res-1",
        state = "Confirmed",
        startTime = 0L,
        endTime = 0L,
        expiresAt = null,
        quantity = 1,
        totalPrice = 10.0,
        createdAt = 0L,
    )

    private val fakeOrderDao = object : OrderDao {
        override suspend fun upsert(order: OrderEntity) = Unit
        override suspend fun update(order: OrderEntity) = Unit
        override suspend fun getById(orderId: String): OrderEntity? =
            if (orderId == "ord-1") testOrder else null
        override suspend fun getByIdForActor(orderId: String, actorId: String): OrderEntity? =
            if (orderId == "ord-1" && actorId == "operator") testOrder else null
        override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity? =
            if (orderId == "ord-1" && (ownerId == "operator" || delegateOwnerId == "operator")) testOrder else null
        override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
        override suspend fun getActiveByResource(resourceId: String) = emptyList<OrderEntity>()
        override suspend fun deleteById(orderId: String) = Unit
        override suspend fun page(limit: Int) = emptyList<OrderEntity>()
        override suspend fun getExpiredPendingOrders(nowMillis: Long) = emptyList<OrderEntity>()
        override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long) = 0.0
        override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long) = 0.0
    }

    private val fakeResourceDao = object : ResourceDao {
        override suspend fun upsert(resource: ResourceEntity) = Unit
        override suspend fun upsertAll(resources: List<ResourceEntity>) = Unit
        override suspend fun update(resource: ResourceEntity) = Unit
        override suspend fun getById(id: String): ResourceEntity? = null
        override suspend fun page(limit: Int, offset: Int) = emptyList<ResourceEntity>()
        override suspend fun countAll() = 0
        override suspend fun deleteById(id: String) = Unit
    }

    private val fakeUserDao = object : com.eaglepoint.task136.shared.db.UserDao {
        override suspend fun upsert(user: com.eaglepoint.task136.shared.db.UserEntity) = Unit
        override suspend fun getById(id: String): com.eaglepoint.task136.shared.db.UserEntity? = null
        override suspend fun countAll() = 0
        override suspend fun update(user: com.eaglepoint.task136.shared.db.UserEntity) = Unit
        override suspend fun getAllActive() = emptyList<com.eaglepoint.task136.shared.db.UserEntity>()
    }

    private val permissionEvaluator = PermissionEvaluator(defaultRules())

    private fun createRepository(): CoreRepository {
        return CoreRepository(
            userDao = fakeUserDao,
            resourceDao = fakeResourceDao,
            orderDao = fakeOrderDao,
            permissionEvaluator = permissionEvaluator,
        )
    }

    @Test
    fun `owner can load own order via repository`() = runTest {
        val repo = createRepository()
        val order = repo.getOrder(Role.Operator, "ord-1", "operator")
        assertNotNull(order)
        assertEquals("ord-1", order.id)
    }

    @Test
    fun `unauthorized user cannot load another users order via repository`() = runTest {
        val repo = createRepository()
        val order = repo.getOrder(Role.Operator, "ord-1", "other-user")
        assertNull(order)
    }

    @Test
    fun `admin can load any order via repository`() = runTest {
        val repo = createRepository()
        val order = repo.getOrder(Role.Admin, "ord-1", "admin-user")
        assertNotNull(order)
        assertEquals("ord-1", order.id)
    }

    @Test
    fun `supervisor can load any order via repository`() = runTest {
        val repo = createRepository()
        val order = repo.getOrder(Role.Supervisor, "ord-1", "supervisor-user")
        assertNotNull(order)
        assertEquals("ord-1", order.id)
    }

    @Test
    fun `delegate can load delegated users order via repository`() = runTest {
        val repo = createRepository()
        val order = repo.getOrder(Role.Companion, "ord-1", "companion-user", "operator")
        assertNotNull(order)
        assertEquals("ord-1", order.id)
    }

    @Test
    fun `viewer can read orders via RBAC`() {
        val canRead = permissionEvaluator.canAccess(Role.Viewer, com.eaglepoint.task136.shared.rbac.ResourceType.Order, "*", com.eaglepoint.task136.shared.rbac.Action.Read)
        assertEquals(true, canRead)
    }

    @Test
    fun `viewer cannot write orders via RBAC`() {
        val canWrite = permissionEvaluator.canAccess(Role.Viewer, com.eaglepoint.task136.shared.rbac.ResourceType.Order, "*", com.eaglepoint.task136.shared.rbac.Action.Write)
        assertEquals(false, canWrite)
    }

    @Test
    fun `non-owner non-admin gets null for unauthorized order`() = runTest {
        val repo = createRepository()
        // Companion without delegation to the order owner
        val order = repo.getOrder(Role.Companion, "ord-1", "companion-user", "non-existent-delegate")
        assertNull(order)
    }
}
