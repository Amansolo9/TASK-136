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

class MeetingWorkflowViewModelTest {
    private val fakeMeetingDao = object : MeetingDao {
        override suspend fun upsert(meeting: MeetingEntity) = Unit
        override suspend fun update(meeting: MeetingEntity) = Unit
        override suspend fun getById(id: String): MeetingEntity? = null
        override suspend fun getByIdForOrganizer(id: String, actorId: String): MeetingEntity? = null
        override fun observeById(id: String): Flow<MeetingEntity?> = emptyFlow()
        override suspend fun getByOrganizer(userId: String, limit: Int) = emptyList<MeetingEntity>()
        override suspend fun page(limit: Int) = emptyList<MeetingEntity>()
        override suspend fun pageByResource(resourceId: String, deniedStatus: String, rangeStart: Long, rangeEnd: Long, limit: Int) = emptyList<MeetingEntity>()
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
        return MeetingWorkflowViewModel(
            validationService = ValidationService(clock),
            permissionEvaluator = PermissionEvaluator(defaultRules()),
            abacPolicyEvaluator = AbacPolicyEvaluator(),
            deviceBindingService = DeviceBindingService(fakeDeviceBindingDao, clock),
            meetingDao = fakeMeetingDao,
            notificationGateway = fakeNotificationGateway,
            bookingUseCase = BookingUseCase(fakeOrderDao, clock),
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
}

private class FixedMeetingClock(var current: Instant) : Clock {
    override fun now(): Instant = current
}
