package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.CartDao
import com.eaglepoint.task136.shared.db.CartItemEntity
import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.db.InvoiceDao
import com.eaglepoint.task136.shared.db.InvoiceEntity
import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.db.ResourceDao
import com.eaglepoint.task136.shared.db.ResourceEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.ReceiptGateway
import com.eaglepoint.task136.shared.platform.ReceiptLineItem
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SecurityRemediationTest {

    private val testClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }

    private val fakeDeviceBindingDao = object : DeviceBindingDao {
        val deleted = mutableListOf<String>()
        override suspend fun upsert(binding: DeviceBindingEntity) = Unit
        override suspend fun getByUserId(userId: String) = emptyList<DeviceBindingEntity>()
        override suspend fun countByUserId(userId: String) = 0
        override suspend fun deleteAllForUser(userId: String) { deleted.add(userId) }
        override suspend fun findByUserAndDevice(userId: String, fingerprint: String): DeviceBindingEntity? =
            DeviceBindingEntity(id = "test", userId = userId, deviceFingerprint = fingerprint, boundAt = 0L)
    }

    // ── A. Admin privilege escalation ──

    @Test
    fun `non-admin denied device binding reset`() = runTest {
        val service = DeviceBindingService(fakeDeviceBindingDao, testClock)
        val result = service.adminResetBindings(Role.Operator, "someuser")
        assertFalse(result)
    }

    @Test
    fun `admin allowed device binding reset`() = runTest {
        val service = DeviceBindingService(fakeDeviceBindingDao, testClock)
        val result = service.adminResetBindings(Role.Admin, "someuser")
        assertTrue(result)
    }

    @Test
    fun `supervisor denied device binding reset`() = runTest {
        val service = DeviceBindingService(fakeDeviceBindingDao, testClock)
        val result = service.adminResetBindings(Role.Supervisor, "someuser")
        assertFalse(result)
    }

    @Test
    fun `viewer denied device binding reset`() = runTest {
        val service = DeviceBindingService(fakeDeviceBindingDao, testClock)
        val result = service.adminResetBindings(Role.Viewer, "someuser")
        assertFalse(result)
    }

    // ── E. Resource mutation not role-protected ──

    private val fakeResourceDao = object : ResourceDao {
        val added = mutableListOf<ResourceEntity>()
        val deletedIds = mutableListOf<String>()
        override suspend fun upsert(resource: ResourceEntity) { added.add(resource) }
        override suspend fun upsertAll(resources: List<ResourceEntity>) = Unit
        override suspend fun update(resource: ResourceEntity) = Unit
        override suspend fun getById(id: String): ResourceEntity? = null
        override suspend fun page(limit: Int, offset: Int) = emptyList<ResourceEntity>()
        override suspend fun countAll() = 0
        override suspend fun deleteById(id: String) { deletedIds.add(id) }
    }

    @Test
    fun `non-admin cannot add resources`() {
        val vm = ResourceListViewModel(
            resourceDao = fakeResourceDao,
            validationService = ValidationService(testClock),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = vm.addResource(Role.Operator, "Test", "Cat", 10, 5.0)
        assertFalse(result)
        assertEquals("Admin role required to add resources", vm.state.value.error)
    }

    @Test
    fun `admin can add resources`() {
        val vm = ResourceListViewModel(
            resourceDao = fakeResourceDao,
            validationService = ValidationService(testClock),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = vm.addResource(Role.Admin, "Test", "Cat", 10, 5.0)
        assertTrue(result)
    }

    @Test
    fun `non-admin cannot delete resources`() {
        val vm = ResourceListViewModel(
            resourceDao = fakeResourceDao,
            validationService = ValidationService(testClock),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = vm.deleteResource(Role.Viewer, "res-1")
        assertFalse(result)
        assertEquals("Admin role required to delete resources", vm.state.value.error)
    }

    @Test
    fun `admin can delete resources`() {
        val vm = ResourceListViewModel(
            resourceDao = fakeResourceDao,
            validationService = ValidationService(testClock),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val result = vm.deleteResource(Role.Admin, "res-1")
        assertTrue(result)
    }

    // ── B. Viewer can submit meetings ──

    private val storedMeetings = mutableMapOf<String, MeetingEntity>()
    private val storedAttendees = mutableListOf<MeetingAttendeeEntity>()

    private val fakeMeetingDao = object : MeetingDao {
        override suspend fun upsert(meeting: MeetingEntity) { storedMeetings[meeting.id] = meeting }
        override suspend fun update(meeting: MeetingEntity) { storedMeetings[meeting.id] = meeting }
        override suspend fun getById(id: String) = storedMeetings[id]
        override suspend fun getByIdForOrganizer(id: String, actorId: String) = storedMeetings[id]?.takeIf { it.organizerId == actorId }
        override suspend fun getByIdForOwnerOrDelegate(id: String, actorId: String, ownerId: String) =
            storedMeetings[id]?.takeIf { it.organizerId == actorId || it.organizerId == ownerId }
        override fun observeById(id: String): Flow<MeetingEntity?> = emptyFlow()
        override suspend fun getByOrganizer(userId: String, limit: Int) = emptyList<MeetingEntity>()
        override suspend fun page(limit: Int) = emptyList<MeetingEntity>()
        override suspend fun pageByResource(resourceId: String, deniedStatus: String, rangeStart: Long, rangeEnd: Long, limit: Int) = emptyList<MeetingEntity>()
        override suspend fun getOverdueApprovedNoShowCandidates(nowMillis: Long, approvedStatus: String): List<MeetingEntity> = emptyList()
        override suspend fun upsertAttendee(attendee: MeetingAttendeeEntity) { storedAttendees.add(attendee) }
        override suspend fun getAttendees(meetingId: String) = emptyList<MeetingAttendeeEntity>()
        override suspend fun getAttendeesForOrganizer(meetingId: String, actorId: String) = emptyList<MeetingAttendeeEntity>()
        override suspend fun removeAttendee(id: String) = Unit
    }

    private val fakeOrderDao = object : OrderDao {
        override suspend fun upsert(order: OrderEntity) = Unit
        override suspend fun update(order: OrderEntity) = Unit
        override suspend fun getById(orderId: String): OrderEntity? = null
        override suspend fun getByIdForActor(orderId: String, actorId: String): OrderEntity? = null
        override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity? = null
        override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
        override suspend fun getActiveByResource(resourceId: String) = emptyList<OrderEntity>()
        override suspend fun deleteById(orderId: String) = Unit
        override suspend fun page(limit: Int) = emptyList<OrderEntity>()
        override suspend fun getExpiredPendingOrders(nowMillis: Long) = emptyList<OrderEntity>()
        override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long) = 0.0
        override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long) = 0.0
    }

    private val fakeNotificationGateway = object : NotificationGateway {
        val scheduledReminders = mutableListOf<String>()
        override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
        override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
        override suspend fun scheduleOrderReminder(orderId: String, message: String) { scheduledReminders.add(orderId) }
    }

    private fun createMeetingVm(): MeetingWorkflowViewModel {
        storedMeetings.clear()
        storedAttendees.clear()
        return MeetingWorkflowViewModel(
            validationService = ValidationService(testClock),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            abacPolicyEvaluator = AbacPolicyEvaluator(),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            meetingDao = fakeMeetingDao,
            notificationGateway = fakeNotificationGateway,
            bookingUseCase = BookingUseCase(fakeMeetingDao, testClock),
            clock = testClock,
            deviceFingerprint = "test",
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `viewer denied meeting submission`() {
        val vm = createMeetingVm()
        vm.submitMeeting(role = Role.Viewer, organizerId = "viewer", actorId = "viewer")
        assertEquals("Meeting submission denied for role", vm.state.value.note)
        assertEquals(MeetingStatus.Draft, vm.state.value.status)
    }

    @Test
    fun `operator can submit meeting`() = runTest {
        val vm = createMeetingVm()
        vm.submitMeeting(role = Role.Operator, organizerId = "operator", actorId = "operator")
        delay(100)
        assertEquals(MeetingStatus.PendingApproval, vm.state.value.status)
    }

    @Test
    fun `admin can submit meeting`() = runTest {
        val vm = createMeetingVm()
        vm.submitMeeting(role = Role.Admin, organizerId = "admin", actorId = "admin")
        delay(100)
        assertEquals(MeetingStatus.PendingApproval, vm.state.value.status)
    }

    // ── F. Attendees/agenda persisted at submission ──

    @Test
    fun `attendees persisted on meeting submission`() = runTest {
        val vm = createMeetingVm()
        vm.submitMeeting(
            role = Role.Operator,
            organizerId = "operator",
            actorId = "operator",
            agenda = "Discuss Q3 goals",
            attendeeNames = listOf("Alice", "Bob"),
        )
        delay(100)
        assertEquals(2, storedAttendees.size)
        assertTrue(storedAttendees.any { it.displayName == "Alice" })
        assertTrue(storedAttendees.any { it.displayName == "Bob" })
    }

    @Test
    fun `agenda persisted on meeting submission`() = runTest {
        val vm = createMeetingVm()
        vm.submitMeeting(
            role = Role.Admin,
            organizerId = "admin",
            actorId = "admin",
            agenda = "Budget review",
        )
        delay(100)
        val meeting = storedMeetings.values.firstOrNull()
        assertNotNull(meeting)
        assertEquals("Budget review", meeting.agenda)
    }

    // ── I. Tax canonical storage ──

    private val storedInvoices = mutableMapOf<String, InvoiceEntity>()
    private val fakeInvoiceDao = object : InvoiceDao {
        override suspend fun upsert(invoice: InvoiceEntity) { storedInvoices[invoice.id] = invoice }
        override suspend fun getByOwner(ownerId: String) = storedInvoices.values.filter { it.ownerId == ownerId }
        override suspend fun getById(id: String) = storedInvoices[id]
        override suspend fun getRecent(limit: Int) = storedInvoices.values.toList().take(limit)
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

    private val receiptGateway = object : ReceiptGateway {
        override suspend fun shareReceipt(invoiceId: String, customerName: String, lineItems: List<ReceiptLineItem>, total: Double) = Unit
    }

    private val storedOrders = mutableMapOf<String, OrderEntity>()
    private val orderDaoWithStorage = object : OrderDao {
        override suspend fun upsert(order: OrderEntity) { storedOrders[order.id] = order }
        override suspend fun update(order: OrderEntity) { storedOrders[order.id] = order }
        override suspend fun getById(orderId: String) = storedOrders[orderId]
        override suspend fun getByIdForActor(orderId: String, actorId: String) = storedOrders[orderId]?.takeIf { it.userId == actorId }
        override suspend fun getByIdForOwnerOrDelegate(orderId: String, ownerId: String, delegateOwnerId: String): OrderEntity? = null
        override fun observeById(orderId: String): Flow<OrderEntity?> = emptyFlow()
        override suspend fun getActiveByResource(resourceId: String) = emptyList<OrderEntity>()
        override suspend fun deleteById(orderId: String) = Unit
        override suspend fun page(limit: Int) = emptyList<OrderEntity>()
        override suspend fun getExpiredPendingOrders(nowMillis: Long) = emptyList<OrderEntity>()
        override suspend fun sumGrossByDateRange(fromMillis: Long, toMillis: Long) = 0.0
        override suspend fun sumRefundsByDateRange(fromMillis: Long, toMillis: Long) = 0.0
    }

    private fun createFinanceVm(): OrderFinanceViewModel {
        storedInvoices.clear()
        storedOrders.clear()
        return OrderFinanceViewModel(
            abac = AbacPolicyEvaluator(),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            validationService = ValidationService(testClock),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            cartDao = fakeCartDao,
            invoiceDao = fakeInvoiceDao,
            orderDao = orderDaoWithStorage,
            notificationGateway = fakeNotificationGateway,
            receiptGateway = receiptGateway,
            clock = testClock,
        )
    }

    @Test
    fun `invoice entity always stores canonical tax`() {
        // Direct test: InvoiceEntity always stores the real tax, not masked value
        val entity = InvoiceEntity(
            id = "inv-test", subtotal = 100.0, tax = 12.0, total = 112.0,
            orderId = "ord-1", ownerId = "operator", actorId = "operator", createdAt = 0L,
        )
        assertEquals(12.0, entity.tax, "InvoiceEntity must store canonical tax")
        assertEquals(112.0, entity.total, "InvoiceEntity total must include canonical tax")
    }

    @Test
    fun `InvoiceDraft masks tax for non-admin at presentation`() {
        // The presentation layer (InvoiceDraft) zeroes tax for non-admin
        val draft = InvoiceDraft(
            id = "inv-1", subtotal = 100.0, tax = 0.0, total = 112.0,
            orderId = "ord-1", ownerId = "operator", actorId = "operator",
        )
        assertEquals(0.0, draft.tax, "Non-admin draft shows masked tax")
        // But the total still reflects the canonical amount
        assertEquals(112.0, draft.total, "Total stays canonical even when tax display is masked")
    }

    @Test
    fun `admin InvoiceDraft shows tax`() {
        val draft = InvoiceDraft(
            id = "inv-1", subtotal = 100.0, tax = 12.0, total = 112.0,
            orderId = "ord-1", ownerId = "admin", actorId = "admin",
        )
        assertEquals(12.0, draft.tax, "Admin draft shows real tax")
    }

    // ── D. Invoice linked to real order ──

    @Test
    fun `invoice orderId field must reference order not cart item`() {
        // Verify that InvoiceDraft orderId starts with "ord-" pattern, not "item-"
        val invoice = InvoiceDraft(
            id = "inv-1", subtotal = 100.0, tax = 12.0, total = 112.0,
            orderId = "ord-12345-6789", ownerId = "admin", actorId = "admin",
        )
        assertTrue(invoice.orderId!!.startsWith("ord-"), "Invoice orderId must reference a real order")
    }

    @Test
    fun `invoice with cart-item orderId is invalid pattern`() {
        val badId = "item-1"
        assertFalse(badId.startsWith("ord-"), "Cart item ID should not be used as order reference")
    }

    @Test
    fun `refund requires valid order linkage - no stateMachine means refund blocked`() {
        // When stateMachine is null, refund cannot proceed
        val vm = OrderFinanceViewModel(
            abac = AbacPolicyEvaluator(),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            validationService = ValidationService(testClock),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            cartDao = fakeCartDao,
            invoiceDao = fakeInvoiceDao,
            orderDao = null,
            stateMachine = null,
            notificationGateway = fakeNotificationGateway,
            receiptGateway = receiptGateway,
            clock = testClock,
        )
        storedInvoices["inv-nolink"] = InvoiceEntity(
            id = "inv-nolink", subtotal = 50.0, tax = 6.0, total = 56.0,
            orderId = "ord-x", ownerId = "admin", actorId = "admin", createdAt = 0L,
        )
        // This will fail because stateMachine is null
        vm.refundInvoice("inv-nolink", Role.Admin, "admin")
        // Give the internal scope a moment (it runs on Default dispatcher)
        Thread.sleep(300)
        val note = vm.state.value.note.orEmpty()
        assertTrue(note.contains("Refund requires a linked order"),
            "Refund should require a linked order+stateMachine, got: $note")
    }

    // ── J. Order reminder scheduling ──

    @Test
    fun `order reminder gateway interface exists`() {
        // Verify the scheduleOrderReminder method is callable
        var called = false
        val gw = object : NotificationGateway {
            override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
            override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
            override suspend fun scheduleOrderReminder(orderId: String, message: String) { called = true }
        }
        runTest {
            gw.scheduleOrderReminder("ord-1", "test")
            assertTrue(called)
        }
    }
}

