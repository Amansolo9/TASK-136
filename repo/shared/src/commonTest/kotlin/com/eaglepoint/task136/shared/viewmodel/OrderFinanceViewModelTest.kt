package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.CartDao
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
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

class OrderFinanceViewModelTest {
    private val notificationGateway = object : NotificationGateway {
        override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
        override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
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
        return OrderFinanceViewModel(
            abac = AbacPolicyEvaluator(),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            validationService = ValidationService(testClock),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            cartDao = fakeCartDao,
            notificationGateway = notificationGateway,
            receiptGateway = receiptGateway,
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
}
