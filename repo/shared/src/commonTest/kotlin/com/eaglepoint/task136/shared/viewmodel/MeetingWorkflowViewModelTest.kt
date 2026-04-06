package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.db.DeviceBindingDao
import com.eaglepoint.task136.shared.db.DeviceBindingEntity
import com.eaglepoint.task136.shared.db.OrderDao
import com.eaglepoint.task136.shared.db.OrderEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.rbac.defaultRules
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class MeetingWorkflowViewModelTest {
    private val meetings = mutableMapOf<String, MeetingEntity>()

    private val fakeMeetingDao = object : MeetingDao {
        override suspend fun upsert(meeting: MeetingEntity) { meetings[meeting.id] = meeting }
        override suspend fun update(meeting: MeetingEntity) { meetings[meeting.id] = meeting }
        override suspend fun getById(id: String): MeetingEntity? = meetings[id]
        override suspend fun getByIdForOrganizer(id: String, actorId: String): MeetingEntity? =
            meetings[id]?.takeIf { it.organizerId == actorId }
        override suspend fun getByIdForOwnerOrDelegate(id: String, actorId: String, ownerId: String): MeetingEntity? =
            meetings[id]?.takeIf { it.organizerId == actorId || it.organizerId == ownerId }
        override fun observeById(id: String): Flow<MeetingEntity?> = emptyFlow()
        override suspend fun getByOrganizer(userId: String, limit: Int) = meetings.values.filter { it.organizerId == userId }.take(limit)
        override suspend fun page(limit: Int) = meetings.values.take(limit)
        override suspend fun pageByResource(resourceId: String, deniedStatus: String, rangeStart: Long, rangeEnd: Long, limit: Int) =
            meetings.values.filter {
                it.resourceId == resourceId &&
                    it.status != deniedStatus &&
                    it.endTime >= rangeStart &&
                    it.startTime <= rangeEnd
            }.sortedByDescending { it.startTime }.take(limit)
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
            DeviceBindingEntity(id = "bind", userId = userId, deviceFingerprint = fingerprint, boundAt = 0L)
    }

    private val fakeNotificationGateway = object : NotificationGateway {
        override suspend fun scheduleInvoiceReady(invoiceId: String, total: Double) = Unit
        override suspend fun scheduleMeetingNotification(meetingId: String, message: String) = Unit
        override suspend fun scheduleOrderReminder(orderId: String, message: String) = Unit
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

    private fun createVm(clock: Clock): MeetingWorkflowViewModel {
        meetings.clear()
        return MeetingWorkflowViewModel(
            validationService = ValidationService(clock),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            abacPolicyEvaluator = AbacPolicyEvaluator(),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, clock),
            meetingDao = fakeMeetingDao,
            notificationGateway = fakeNotificationGateway,
            bookingUseCase = BookingUseCase(fakeMeetingDao, clock),
            clock = clock,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @Test
    fun `meeting moves through submit approve checkin`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        vm.approve(Role.Supervisor)
        vm.checkIn(Role.Operator)

        assertEquals(MeetingStatus.CheckedIn, vm.state.value.status)
    }

    @Test
    fun `supervisor can deny meeting`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        vm.deny(Role.Supervisor)

        assertEquals(MeetingStatus.Denied, vm.state.value.status)
    }

    @Test
    fun `companion cannot approve meeting`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        vm.approve(Role.Companion)

        assertEquals(MeetingStatus.PendingApproval, vm.state.value.status)
        assertEquals("Approval denied for role", vm.state.value.note)
    }

    @Test
    fun `check-in skipped when requireCheckIn is false`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now(), requireCheckIn = false)
        vm.approve(Role.Supervisor)
        vm.checkIn(Role.Operator)

        assertEquals(MeetingStatus.Approved, vm.state.value.status)
        assertEquals("Check-in not required for this meeting", vm.state.value.note)
    }

    @Test
    fun `supervisor can toggle requireCheckIn`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        assertEquals(true, vm.state.value.requireCheckIn)

        vm.setRequireCheckIn(Role.Supervisor, false)
        assertEquals(false, vm.state.value.requireCheckIn)

        vm.setRequireCheckIn(Role.Supervisor, true)
        assertEquals(true, vm.state.value.requireCheckIn)
    }

    @Test
    fun `operator cannot toggle requireCheckIn`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        vm.setRequireCheckIn(Role.Operator, false)

        assertEquals(true, vm.state.value.requireCheckIn)
        assertEquals("Only supervisors can configure check-in", vm.state.value.note)
    }

    @Test
    fun `no-show skipped when requireCheckIn is false`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now(), requireCheckIn = false)
        vm.approve(Role.Supervisor)
        vm.markNoShowIfDue(Role.Supervisor, clock.now().plus(15.minutes))

        assertEquals(MeetingStatus.Approved, vm.state.value.status)
        assertEquals("No-show tracking not enabled for this meeting", vm.state.value.note)
    }

    @Test
    fun `attachment can be added and removed`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        vm.addAttachment("photo.jpg", Role.Operator)

        assertEquals("photo.jpg", vm.state.value.attachmentPath)
        assertEquals("Attachment added", vm.state.value.note)

        vm.removeAttachment(Role.Operator)
        assertEquals(null, vm.state.value.attachmentPath)
        assertEquals("Attachment removed", vm.state.value.note)
    }

    @Test
    fun `viewer cannot add attachment`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)

        vm.submitMeeting(clock.now())
        vm.addAttachment("photo.jpg", Role.Viewer)

        assertEquals(null, vm.state.value.attachmentPath)
        assertEquals("Attachment denied for role", vm.state.value.note)
    }

    @Test
    fun `delegate companion can load delegated owner meeting`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)
        meetings["m-1"] = MeetingEntity(
            id = "m-1",
            organizerId = "operator",
            resourceId = "res-1",
            title = "Delegated",
            startTime = clock.now().toEpochMilliseconds(),
            endTime = clock.now().plus(30.minutes).toEpochMilliseconds(),
            status = MeetingStatus.Approved.name,
        )

        vm.loadMeetingDetail(
            meetingId = "m-1",
            role = Role.Companion,
            actorId = "companion",
            ownerId = "operator",
            isDelegate = true,
        )

        assertEquals("m-1", vm.state.value.meetingId)
        assertEquals(MeetingStatus.Approved, vm.state.value.status)
    }

    @Test
    fun `non delegate cannot load other users meeting`() {
        val clock = FixedMeetingClock(Instant.parse("2026-03-30T10:00:00Z"))
        val vm = createVm(clock)
        meetings["m-2"] = MeetingEntity(
            id = "m-2",
            organizerId = "operator",
            resourceId = "res-1",
            title = "Private",
            startTime = clock.now().toEpochMilliseconds(),
            endTime = clock.now().plus(30.minutes).toEpochMilliseconds(),
            status = MeetingStatus.Approved.name,
        )

        vm.loadMeetingDetail(
            meetingId = "m-2",
            role = Role.Companion,
            actorId = "other-companion",
            ownerId = "other-companion",
            isDelegate = false,
        )

        assertEquals("Meeting not found or access denied", vm.state.value.note)
    }
}

private class FixedMeetingClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}

