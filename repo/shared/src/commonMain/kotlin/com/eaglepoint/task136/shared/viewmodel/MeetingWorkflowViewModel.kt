package com.eaglepoint.task136.shared.viewmodel

import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.orders.BookingUseCase
import com.eaglepoint.task136.shared.orders.TimeWindow
import com.eaglepoint.task136.shared.platform.NotificationGateway
import com.eaglepoint.task136.shared.platform.getDeviceFingerprint
import com.eaglepoint.task136.shared.rbac.AbacPolicyEvaluator
import com.eaglepoint.task136.shared.rbac.AccessContext
import com.eaglepoint.task136.shared.rbac.Action
import com.eaglepoint.task136.shared.rbac.PermissionEvaluator
import com.eaglepoint.task136.shared.rbac.ResourceType
import com.eaglepoint.task136.shared.rbac.Role
import com.eaglepoint.task136.shared.security.DeviceBindingService
import com.eaglepoint.task136.shared.services.ValidationService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

enum class MeetingStatus {
    Draft,
    PendingApproval,
    Approved,
    Denied,
    CheckedIn,
    NoShow,
}

data class AttendeeInfo(
    val id: String,
    val name: String,
    val rsvp: String = "Pending",
)

data class MeetingWorkflowState(
    val status: MeetingStatus = MeetingStatus.Draft,
    val meetingId: String? = null,
    val meetingStart: Instant? = null,
    val agenda: String = "",
    val attendees: List<AttendeeInfo> = emptyList(),
    val note: String? = null,
)

class MeetingWorkflowViewModel(
    private val validationService: ValidationService,
    private val permissionEvaluator: PermissionEvaluator,
    private val abacPolicyEvaluator: AbacPolicyEvaluator,
    private val deviceBindingService: DeviceBindingService,
    private val meetingDao: MeetingDao,
    private val notificationGateway: NotificationGateway,
    private val bookingUseCase: BookingUseCase,
    private val clock: Clock,
    private val deviceFingerprint: String = getDeviceFingerprint(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MeetingWorkflowState())
    val state: StateFlow<MeetingWorkflowState> = _state.asStateFlow()

    fun submitMeeting(
        start: Instant = clock.now().plus(20.minutes),
        agenda: String = "",
        organizerId: String = "",
        actorId: String = organizerId,
        resourceId: String = "res-1",
    ) {
        val meetingId = "mtg-${clock.now().toEpochMilliseconds()}-${(1000..9999).random()}"
        scope.launch(ioDispatcher) {
            // Check for time conflicts
            val endTime = start.plus(30.minutes)
            val candidate = TimeWindow(start, endTime)
            val existingMeetings = meetingDao.pageByResource(
                resourceId = resourceId,
                rangeStart = start.toEpochMilliseconds() - 86_400_000L,
                rangeEnd = endTime.toEpochMilliseconds() + 86_400_000L,
            )
            val existingWindows = existingMeetings
                .map { TimeWindow(
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it.startTime),
                    kotlinx.datetime.Instant.fromEpochMilliseconds(it.endTime),
                ) }

            if (bookingUseCase.overlaps(existingWindows, candidate)) {
                _state.value = _state.value.copy(note = "Conflict: time slot overlaps with existing meeting")
                return@launch
            }

            _state.value = MeetingWorkflowState(
                status = MeetingStatus.PendingApproval,
                meetingId = meetingId,
                meetingStart = start,
                agenda = agenda,
                note = "Awaiting supervisor approval",
            )
            meetingDao.upsert(
                MeetingEntity(
                    id = meetingId,
                    organizerId = organizerId,
                    resourceId = resourceId,
                    title = "Meeting",
                    startTime = start.toEpochMilliseconds(),
                    endTime = endTime.toEpochMilliseconds(),
                    status = MeetingStatus.PendingApproval.name,
                    agenda = agenda,
                    note = if (actorId != organizerId) "createdBy:$actorId" else null,
                ),
            )
            notificationGateway.scheduleMeetingNotification(meetingId, "Meeting submitted, awaiting approval")
        }
    }

    fun addAttendee(name: String, role: Role, actorId: String, ownerId: String = actorId, isDelegate: Boolean = false) {
        val meetingId = _state.value.meetingId ?: return
        scope.launch(ioDispatcher) {
            val trusted = deviceBindingService.isDeviceTrusted(actorId, deviceFingerprint)
            val context = AccessContext(
                requesterId = actorId,
                ownerId = ownerId,
                isDelegate = isDelegate,
                deviceTrusted = trusted,
            )
            if (!abacPolicyEvaluator.canManageAttendee(role, context)) {
                _state.value = _state.value.copy(note = "Attendee access denied")
                return@launch
            }

            val existing = meetingDao.getById(meetingId) ?: run {
                _state.value = _state.value.copy(note = "Meeting not found")
                return@launch
            }

            val attendeeId = "att-${clock.now().toEpochMilliseconds()}"
            val info = AttendeeInfo(id = attendeeId, name = name)
            _state.value = _state.value.copy(attendees = _state.value.attendees + info, note = null)
            meetingDao.upsertAttendee(
                MeetingAttendeeEntity(
                    id = attendeeId,
                    meetingId = existing.id,
                    userId = name,
                    displayName = name,
                ),
            )
        }
    }

    fun loadMeetingDetail(meetingId: String, role: Role, actorId: String, ownerId: String = actorId, isDelegate: Boolean = false) {
        scope.launch(ioDispatcher) {
            val meeting = when (role) {
                Role.Admin, Role.Supervisor -> meetingDao.getById(meetingId)
                else -> meetingDao.getByIdForOrganizer(meetingId, actorId)
            }
            if (meeting == null) {
                _state.value = _state.value.copy(note = "Meeting not found or access denied")
                return@launch
            }

            val trusted = deviceBindingService.isDeviceTrusted(actorId, deviceFingerprint)
            val context = AccessContext(
                requesterId = actorId,
                ownerId = ownerId,
                isDelegate = isDelegate,
                deviceTrusted = trusted,
            )
            val canSeeAttendees = abacPolicyEvaluator.canReadAttendee(role, context)
            val attendees = if (canSeeAttendees) {
                meetingDao.getAttendees(meetingId).map {
                    AttendeeInfo(id = it.id, name = it.displayName, rsvp = it.rsvpStatus)
                }
            } else {
                emptyList()
            }

            _state.value = MeetingWorkflowState(
                status = MeetingStatus.valueOf(meeting.status),
                meetingId = meeting.id,
                meetingStart = Instant.fromEpochMilliseconds(meeting.startTime),
                agenda = meeting.agenda,
                attendees = attendees,
                note = if (canSeeAttendees) null else "Attendee list restricted to Supervisor/Admin",
            )
        }
    }

    fun updateAgenda(agenda: String) {
        _state.value = _state.value.copy(agenda = agenda)
        val meetingId = _state.value.meetingId ?: return
        scope.launch(ioDispatcher) {
            val existing = meetingDao.getById(meetingId) ?: return@launch
            meetingDao.update(existing.copy(agenda = agenda))
        }
    }

    fun approve(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Meeting, "*", Action.Approve)) {
            _state.value = _state.value.copy(note = "Approval denied for role")
            return
        }
        val current = _state.value
        if (current.status != MeetingStatus.PendingApproval) return
        _state.value = current.copy(status = MeetingStatus.Approved, note = "Approved")
        persistStatus(MeetingStatus.Approved)
        scheduleAutoNoShow()
        scope.launch(ioDispatcher) {
            val meetingId = current.meetingId ?: return@launch
            notificationGateway.scheduleMeetingNotification(meetingId, "Meeting approved")
        }
    }

    fun deny(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Meeting, "*", Action.Approve)) {
            _state.value = _state.value.copy(note = "Denial denied for role")
            return
        }
        val current = _state.value
        if (current.status != MeetingStatus.PendingApproval) return
        _state.value = current.copy(status = MeetingStatus.Denied, note = "Denied by supervisor")
        persistStatus(MeetingStatus.Denied)
    }

    fun checkIn(role: Role) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Meeting, "*", Action.Write)) {
            _state.value = _state.value.copy(note = "Check-in denied for role")
            return
        }
        val current = _state.value
        val start = current.meetingStart ?: return
        if (current.status != MeetingStatus.Approved) return

        val valid = validationService.isWithinSupervisorWindow(start, clock.now())
        _state.value = if (valid) {
            current.copy(status = MeetingStatus.CheckedIn, note = "Checked in within +/-10 minutes")
        } else {
            current.copy(note = "Outside check-in window")
        }
        if (valid) persistStatus(MeetingStatus.CheckedIn)
    }

    fun markNoShowIfDue(role: Role, now: Instant = clock.now()) {
        if (!permissionEvaluator.canAccess(role, ResourceType.Meeting, "*", Action.Approve)) {
            _state.value = _state.value.copy(note = "No-show action denied for role")
            return
        }
        val current = _state.value
        val start = current.meetingStart ?: return
        if (current.status == MeetingStatus.Approved && now > start.plus(10.minutes)) {
            _state.value = current.copy(status = MeetingStatus.NoShow, note = "Marked no-show")
            persistStatus(MeetingStatus.NoShow)
        }
    }

    private fun scheduleAutoNoShow() {
        val start = _state.value.meetingStart ?: return
        scope.launch(ioDispatcher) {
            val waitUntil = start.plus(10.minutes)
            val waitDuration = waitUntil - clock.now()
            if (waitDuration.isPositive()) {
                delay(waitDuration)
            }
            val current = _state.value
            if (current.status == MeetingStatus.Approved) {
                _state.value = current.copy(status = MeetingStatus.NoShow, note = "Auto-marked no-show (10 min past start)")
                persistStatus(MeetingStatus.NoShow)
            }
        }
    }

    private fun persistStatus(status: MeetingStatus) {
        val meetingId = _state.value.meetingId ?: return
        scope.launch(ioDispatcher) {
            val existing = meetingDao.getById(meetingId) ?: return@launch
            meetingDao.update(existing.copy(status = status.name))
        }
    }

    fun clearSessionState() {
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        _state.value = MeetingWorkflowState()
    }
}
