package com.eaglepoint.task136.shared.services

import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.db.MeetingEntity
import com.eaglepoint.task136.shared.viewmodel.MeetingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MeetingNoShowReconciliationServiceTest {

    @Test
    fun `overdue approved meeting is reconciled to no-show`() = runTest {
        val now = Instant.parse("2026-04-01T10:00:00Z")
        val store = mutableMapOf<String, MeetingEntity>()
        val dao = FakeMeetingDao(store)
        store["m-1"] = MeetingEntity(
            id = "m-1",
            organizerId = "operator",
            resourceId = "res-1",
            title = "Checkin",
            startTime = now.toEpochMilliseconds() - 60_000,
            endTime = now.toEpochMilliseconds() + 30_000,
            status = MeetingStatus.Approved.name,
            requireCheckIn = true,
            checkInDueAt = now.toEpochMilliseconds() - 1,
        )

        val service = MeetingNoShowReconciliationService(dao, FixedClock(now))
        val changed = service.reconcileOverdueApprovedMeetings()

        assertEquals(1, changed)
        assertEquals(MeetingStatus.NoShow.name, store["m-1"]?.status)
        assertEquals(null, store["m-1"]?.checkInDueAt)
    }

    @Test
    fun `checked in meeting remains unchanged`() = runTest {
        val now = Instant.parse("2026-04-01T10:00:00Z")
        val store = mutableMapOf<String, MeetingEntity>()
        val dao = FakeMeetingDao(store)
        store["m-2"] = MeetingEntity(
            id = "m-2",
            organizerId = "operator",
            resourceId = "res-1",
            title = "Done",
            startTime = now.toEpochMilliseconds() - 60_000,
            endTime = now.toEpochMilliseconds() + 30_000,
            status = MeetingStatus.CheckedIn.name,
            requireCheckIn = true,
            checkInDueAt = now.toEpochMilliseconds() - 1,
        )

        val service = MeetingNoShowReconciliationService(dao, FixedClock(now))
        val changed = service.reconcileOverdueApprovedMeetings()

        assertEquals(0, changed)
        assertEquals(MeetingStatus.CheckedIn.name, store["m-2"]?.status)
    }

    @Test
    fun `approved meeting still inside window remains unchanged`() = runTest {
        val now = Instant.parse("2026-04-01T10:00:00Z")
        val store = mutableMapOf<String, MeetingEntity>()
        val dao = FakeMeetingDao(store)
        store["m-3"] = MeetingEntity(
            id = "m-3",
            organizerId = "operator",
            resourceId = "res-1",
            title = "Future",
            startTime = now.toEpochMilliseconds() + 60_000,
            endTime = now.toEpochMilliseconds() + 120_000,
            status = MeetingStatus.Approved.name,
            requireCheckIn = true,
            checkInDueAt = now.toEpochMilliseconds() + 60_000,
        )

        val service = MeetingNoShowReconciliationService(dao, FixedClock(now))
        val changed = service.reconcileOverdueApprovedMeetings()

        assertEquals(0, changed)
        assertEquals(MeetingStatus.Approved.name, store["m-3"]?.status)
    }
}

private class FakeMeetingDao(
    private val store: MutableMap<String, MeetingEntity>,
) : MeetingDao {
    override suspend fun upsert(meeting: MeetingEntity) { store[meeting.id] = meeting }
    override suspend fun update(meeting: MeetingEntity) { store[meeting.id] = meeting }
    override suspend fun getById(id: String): MeetingEntity? = store[id]
    override suspend fun getByIdForOrganizer(id: String, actorId: String): MeetingEntity? =
        store[id]?.takeIf { it.organizerId == actorId }
    override suspend fun getByIdForOwnerOrDelegate(id: String, actorId: String, ownerId: String): MeetingEntity? =
        store[id]?.takeIf { it.organizerId == actorId || it.organizerId == ownerId }
    override fun observeById(id: String): Flow<MeetingEntity?> = emptyFlow()
    override suspend fun getByOrganizer(userId: String, limit: Int): List<MeetingEntity> =
        store.values.filter { it.organizerId == userId }.take(limit)
    override suspend fun page(limit: Int): List<MeetingEntity> = store.values.take(limit)
    override suspend fun pageByResource(resourceId: String, deniedStatus: String, rangeStart: Long, rangeEnd: Long, limit: Int): List<MeetingEntity> =
        store.values.filter {
            it.resourceId == resourceId &&
                it.status != deniedStatus &&
                it.endTime >= rangeStart &&
                it.startTime <= rangeEnd
        }.sortedByDescending { it.startTime }.take(limit)
    override suspend fun getOverdueApprovedNoShowCandidates(nowMillis: Long, approvedStatus: String): List<MeetingEntity> =
        store.values.filter { it.status == approvedStatus && it.requireCheckIn && it.checkInDueAt != null && it.checkInDueAt <= nowMillis }
    override suspend fun upsertAttendee(attendee: MeetingAttendeeEntity) = Unit
    override suspend fun getAttendees(meetingId: String): List<MeetingAttendeeEntity> = emptyList()
    override suspend fun getAttendeesForOrganizer(meetingId: String, actorId: String): List<MeetingAttendeeEntity> = emptyList()
    override suspend fun removeAttendee(id: String) = Unit
}

private class FixedClock(private val now: Instant) : Clock {
    override fun now(): Instant = now
}
