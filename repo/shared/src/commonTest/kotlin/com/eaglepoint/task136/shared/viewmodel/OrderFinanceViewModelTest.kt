package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.CartDao
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.db.InvoiceDao
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.orders.PaymentMethod
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.platform.ReceiptLineItem
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class OrderFinanceViewModelTest {
    private val notificationGateway = object : NotificationGateway {
        override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
        override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
        override suspend fun scheduleOrderReminder(orderId: String, message: String) = Unit
    }

    private val receiptGateway = object : ReceiptGateway {
        override suspend fun shareReceipt(invoiceId: String, customerName: String, lineItems: List<ReceiptLineItem>, total: Double) = Unit
    }

    private val testClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }

    private val fakeCartDao = object : CartDao {
        override suspend fun upsert(item: CartItemEntity) = Unit
        override suspend fun update(item: CartItemEntity) = Unit
        override suspend fun getByUser(userId: String) = emptyList<CartItemEntity>()
        override fun observeByUser(userId: String): Flow<List<CartItemEntity>> = emptyFlow()
        override suspend fun getById(id: String): CartItemEntity? = null
        override suspend fun getByIdForUser(id: String, userId: String): CartItemEntity? = null
        override suspend fun deleteById(id: String) = Unit
        override suspend fun deleteByIdForUser(id: String, userId: String) = Unit
        override suspend fun clearForUser(userId: String) = Unit
        override suspend fun upsertAll(items: List<CartItemEntity>) = Unit
    }

    private val fakeDeviceBindingDao = object : DeviceBindingDao {
        override suspend fun upsert(binding: DeviceBindingEntity) = Unit
        override suspend fun getByUserId(userId: String) = emptyList<DeviceBindingEntity>()
        override suspend fun countByUserId(userId: String) = 0
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun findByUserAndDevice(userId: String, fingerprint: String): DeviceBindingEntity? =
            DeviceBindingEntity(id = "test", userId = userId, deviceFingerprint = fingerprint, boundAt = 0L)
    }

    private fun createVm(): OrderFinanceViewModel {
        var orders = mutableMapOf<String, OrderEntity>()
        val fakeOrderDao = object : OrderDao {
            override suspend fun upsert(order: OrderEntity) { orders[order.id] = order }
            override suspend fun update(order: OrderEntity) { orders[order.id] = order }
            override suspend fun getById(orderId: String): OrderEntity? = orders[orderId]
            override suspend fun getByIdForActor(orderId: String, actorId: String): OrderEntity? = orders[orderId]
            override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity? = orders[orderId]
            override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
            override suspend fun getActiveByResource(resourceId: String): List<OrderEntity> = emptyList()
            override suspend fun deleteById(orderId: String) = Unit
            override suspend fun page(limit: Int): List<OrderEntity> = emptyList()
            override suspend fun getExpiredPendingOrders(nowMillis: Long): List<OrderEntity> = emptyList()
            override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long): Double = 0.0
            override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long): Double = 0.0
        }
        return OrderFinanceViewModel(
            abac = AbacPolicyEvaluator(),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            validationService = ValidationService(testClock),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            cartDao = fakeCartDao,
            orderDao = fakeOrderDao,
            notificationGateway = notificationGateway,
            receiptGateway = receiptGateway,
            transitionToPendingTender = { orderId, _ ->
                val order = orders[orderId]
                if (order?.paymentMethod == PaymentMethod.InternalWallet.name) {
                    "Insufficient wallet balance"
                } else {
                    null
                }
            },
            confirmOrderTransition = { _, _ -> true },
        )
    }

    @Test
    fun `admin can add item to cart`() {
        val vm = createVm()
        vm.addDemoItem(Role.Admin, "admin")

        assertEquals(1, vm.state.value.cart.size)
        assertEquals("Service Package 1", vm.state.value.cart[0].label)
    }

    @Test
    fun `viewer add item denied`() {
        val vm = createVm()
        vm.addDemoItem(Role.Viewer, "viewer")

        assertEquals("Add item denied for role", vm.state.value.note)
        assertTrue(vm.state.value.cart.isEmpty())
    }

    @Test
    fun `split requires quantity at least 2`() {
        val vm = createVm()
        vm.addDemoItem(Role.Admin, "admin")
        vm.splitFirstItem(Role.Admin, "admin")

        assertEquals("Split requires quantity >= 2", vm.state.value.note)
    }

    @Test
    fun `merge requires at least 2 items`() {
        val vm = createVm()
        vm.addDemoItem(Role.Admin, "admin")
        vm.mergeFirstTwoItems(Role.Admin, "admin")

        // No error, just no-op (cart stays at 1)
        assertEquals(1, vm.state.value.cart.size)
    }

    @Test
    fun `invoice denied for empty cart`() {
        val vm = createVm()
        vm.generateInvoice(Role.Admin, "admin")

        // Empty cart = no-op
        assertTrue(vm.state.value.invoices.isEmpty())
    }

    @Test
    fun `cash checkout persists tender mode`() {
        val vm = createVm()
        vm.addDemoItem(Role.Admin, "admin")
        vm.selectPaymentMethod(PaymentMethod.Cash)
        vm.generateInvoice(Role.Admin, "admin")
        Thread.sleep(200)

        val invoice = vm.state.value.invoices.lastOrNull()
        assertNotNull(invoice)
        assertEquals(PaymentMethod.Cash.name, invoice.paymentMethod)
    }

    @Test
    fun `wallet checkout insufficient balance returns error`() {
        val vm = createVm()
        vm.addDemoItem(Role.Admin, "admin")
        vm.selectPaymentMethod(PaymentMethod.InternalWallet)
        vm.generateInvoice(Role.Admin, "admin")
        Thread.sleep(200)

        assertEquals("Insufficient wallet balance", vm.state.value.note)
        assertTrue(vm.state.value.invoices.isEmpty())
    }

    @Test
    fun `external tender is recorded and persisted in invoice draft`() {
        val vm = createVm()
        vm.addDemoItem(Role.Admin, "admin")
        vm.selectPaymentMethod(PaymentMethod.ExternalTender)
        vm.setExternalTenderDetails("M-PESA receipt TX123")
        vm.generateInvoice(Role.Admin, "admin")
        Thread.sleep(200)

        val invoice = vm.state.value.invoices.lastOrNull()
        assertNotNull(invoice)
        assertEquals(PaymentMethod.ExternalTender.name, invoice.paymentMethod)
        assertEquals("M-PESA receipt TX123", invoice.externalTenderDetails)
    }

    @Test
    fun `companion delegate cannot refund even with delegation`() {
        val vm = createRefundVm()
        vm.refundInvoice("inv-1", Role.Companion, "companion", "owner")
        Thread.sleep(200)

        assertEquals("Refund denied for role", vm.state.value.note)
    }

    @Test
    fun `unrelated actor denied refund by role policy`() {
        val vm = createRefundVm()
        vm.refundInvoice("inv-1", Role.Operator, "intruder")
        Thread.sleep(200)

        assertEquals("Refund denied for role", vm.state.value.note)
    }

    @Test
    fun `privileged actor can refund with valid invoice order context`() {
        val vm = createRefundVm()
        vm.refundInvoice("inv-1", Role.Supervisor, "supervisor")
        Thread.sleep(200)

        assertEquals("Refund completed for inv-1", vm.state.value.note)
        assertTrue(vm.state.value.refunds.any { it.contains("inv-1") })
    }

    @Test
    fun `refund denied when invoice order ownership context is invalid`() {
        val vm = createRefundVm(invalidOwnership = true)
        vm.refundInvoice("inv-1", Role.Supervisor, "supervisor")
        Thread.sleep(200)

        assertEquals("Refund denied: invalid invoice/order ownership context", vm.state.value.note)
    }

    @Test
    fun `cart and invoice ids remain unique across fresh ViewModel instances`() {
        val vm1 = createVm()
        val vm2 = createVm()

        vm1.addDemoItem(Role.Admin, "admin")
        vm2.addDemoItem(Role.Admin, "admin")
        val itemId1 = vm1.state.value.cart.first().id
        val itemId2 = vm2.state.value.cart.first().id
        assertTrue(itemId1 != itemId2)

        vm1.generateInvoice(Role.Admin, "admin")
        vm2.generateInvoice(Role.Admin, "admin")
        Thread.sleep(250)

        val invoiceId1 = vm1.state.value.invoices.first().id
        val invoiceId2 = vm2.state.value.invoices.first().id
        assertTrue(invoiceId1 != invoiceId2)
    }

    private fun createRefundVm(invalidOwnership: Boolean = false): OrderFinanceViewModel {
        val invoices = mutableMapOf(
            "inv-1" to InvoiceEntity(
                id = "inv-1",
                subtotal = 100.0,
                tax = 12.0,
                total = 112.0,
                orderId = "ord-1",
                ownerId = "owner",
                actorId = "owner",
                createdAt = 0L,
            ),
        )
        val orders = mutableMapOf(
            "ord-1" to OrderEntity(
                id = "ord-1",
                userId = if (invalidOwnership) "other-user" else "owner",
                resourceId = "res-1",
                state = "Confirmed",
                startTime = 0L,
                endTime = 0L,
                expiresAt = null,
                quantity = 1,
                totalPrice = 100.0,
            ),
        )
        val fakeInvoiceDao = object : InvoiceDao {
            override suspend fun upsert(invoice: InvoiceEntity) { invoices[invoice.id] = invoice }
            override suspend fun getByOwner(ownerId: String): List<InvoiceEntity> = invoices.values.filter { it.ownerId == ownerId }
            override suspend fun getById(id: String): InvoiceEntity? = invoices[id]
            override suspend fun getRecent(limit: Int): List<InvoiceEntity> = invoices.values.take(limit)
        }
        val fakeOrderDao = object : OrderDao {
            override suspend fun upsert(order: OrderEntity) { orders[order.id] = order }
            override suspend fun update(order: OrderEntity) { orders[order.id] = order }
            override suspend fun getById(orderId: String): OrderEntity? = orders[orderId]
            override suspend fun getByIdForActor(orderId: String, actorId: String): OrderEntity? = orders[orderId]
            override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity? = orders[orderId]
            override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
            override suspend fun getActiveByResource(resourceId: String): List<OrderEntity> = emptyList()
            override suspend fun deleteById(orderId: String) = Unit
            override suspend fun page(limit: Int): List<OrderEntity> = emptyList()
            override suspend fun getExpiredPendingOrders(nowMillis: Long): List<OrderEntity> = emptyList()
            override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long): Double = 0.0
            override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long): Double = 0.0
        }

        return OrderFinanceViewModel(
            abac = AbacPolicyEvaluator(),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            validationService = ValidationService(testClock),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            cartDao = fakeCartDao,
            invoiceDao = fakeInvoiceDao,
            orderDao = fakeOrderDao,
            notificationGateway = notificationGateway,
            receiptGateway = receiptGateway,
            requestRefundTransition = { _, role -> role == Role.Supervisor || role == Role.Admin },
            completeRefundTransition = { _, role -> role == Role.Supervisor || role == Role.Admin },
        )
    }
}
