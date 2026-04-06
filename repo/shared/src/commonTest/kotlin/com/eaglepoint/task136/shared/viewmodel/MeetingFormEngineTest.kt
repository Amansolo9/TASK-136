package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.config.CanaryConfig
import com.eaglepoint.task136.shared.config.CanaryEvaluator
import com.eaglepoint.task136.shared.config.CanaryManifest
import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.platform.NotificationGateway
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
import kotlin.test.assertTrue

class MeetingFormEngineTest {

    private val testClock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
    }

    private val storedMeetings = mutableMapOf<String, MeetingEntity>()

    private val fakeMeetingDao = object : MeetingDao {
        override suspend fun upsert(meeting: MeetingEntity) { storedMeetings[meeting.id] = meeting }
        override suspend fun update(meeting: MeetingEntity) { storedMeetings[meeting.id] = meeting }
        override suspend fun getById(id: String) = storedMeetings[id]
        override suspend fun getByIdForOrganizer(id: String, actorId: String) =
            storedMeetings[id]?.takeIf { it.organizerId == actorId }
        override suspend fun getByIdForOwnerOrDelegate(id: String, actorId: String, ownerId: String) =
            storedMeetings[id]?.takeIf { it.organizerId == actorId || it.organizerId == ownerId }
        override fun observeById(id: String): Flow<MeetingEntity?> = emptyFlow()
        override suspend fun getByOrganizer(userId: String, limit: Int) = emptyList<MeetingEntity>()
        override suspend fun page(limit: Int) = emptyList<MeetingEntity>()
        override suspend fun pageByResource(resourceId: String, deniedStatus: String, rangeStart: Long, rangeEnd: Long, limit: Int) = emptyList<MeetingEntity>()
        override suspend fun getOverdueApprovedNoShowCandidates(nowMillis: Long, approvedStatus: String): List<MeetingEntity> = emptyList()
        override suspend fun upsertAttendee(attendee: MeetingAttendeeEntity) = Unit
        override suspend fun getAttendees(meetingId: String) = emptyList<MeetingAttendeeEntity>()
        override suspend fun getAttendeesForOrganizer(meetingId: String, actorId: String) = emptyList<MeetingAttendeeEntity>()
        override suspend fun removeAttendee(id: String) = Unit
    }

    private val fakeDeviceBindingDao = object : DeviceBindingDao {
        override suspend fun upsert(binding: DeviceBindingEntity) = Unit
        override suspend fun getByUserId(userId: String) = emptyList<DeviceBindingEntity>()
        override suspend fun countByUserId(userId: String) = 0
        override suspend fun deleteAllForUser(userId: String) = Unit
        override suspend fun findByUserAndDevice(userId: String, fingerprint: String): DeviceBindingEntity? =
            DeviceBindingEntity(id = "test", userId = userId, deviceFingerprint = fingerprint, boundAt = 0L)
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

    private val notificationGateway = object : NotificationGateway {
        override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
        override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
        override suspend fun scheduleOrderReminder(orderId: String, message: String) = Unit
    }

    private val canaryManifest = CanaryManifest(
        features = listOf(
            CanaryConfig(
                featureId = "meeting_form_v2",
                targetVersion = 2,
                enabledRoles = setOf("Admin", "Supervisor"),
                enabledDeviceGroups = setOf("default", "beta"),
                rolloutPercentage = 100,
            ),
        ),
    )

    private fun createVm(canaryEvaluator: CanaryEvaluator? = CanaryEvaluator(canaryManifest)): MeetingWorkflowViewModel {
        storedMeetings.clear()
        return MeetingWorkflowViewModel(
            validationService = ValidationService(testClock),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            abacPolicyEvaluator = AbacPolicyEvaluator(),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, testClock),
            meetingDao = fakeMeetingDao,
            notificationGateway = notificationGateway,
            bookingUseCase = BookingUseCase(fakeMeetingDao, testClock),
            clock = testClock,
            canaryEvaluator = canaryEvaluator,
            deviceFingerprint = "test-device",
            deviceGroup = "default",
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `admin gets form version 2 via canary`() {
        val vm = createVm()
        val version = vm.resolveFormVersion(Role.Admin, "admin")
        assertEquals(2, version)
    }

    @Test
    fun `operator gets form version 1 fallback`() {
        val vm = createVm()
        val version = vm.resolveFormVersion(Role.Operator, "operator")
        assertEquals(1, version)
    }

    @Test
    fun `meeting submission records form version`() = runTest {
        val vm = createVm()
        vm.submitMeeting(
            organizerId = "admin",
            actorId = "admin",
            role = Role.Admin,
        )
        delay(100)

        val state = vm.state.value
        assertEquals(2, state.formVersion)
        assertTrue(state.note?.contains("form v2") == true)
    }

    @Test
    fun `meeting persists form version in note field`() = runTest {
        val vm = createVm()
        vm.submitMeeting(
            organizerId = "admin",
            actorId = "admin",
            role = Role.Admin,
        )
        delay(100)

        val meetingId = vm.state.value.meetingId
        val persisted = storedMeetings[meetingId]
        assertTrue(persisted?.note?.contains("formVersion:2") == true)
    }

    @Test
    fun `no canary evaluator uses default version`() {
        val vm = createVm(canaryEvaluator = null)
        val version = vm.resolveFormVersion(Role.Admin, "admin")
        assertEquals(1, version)
    }
}

