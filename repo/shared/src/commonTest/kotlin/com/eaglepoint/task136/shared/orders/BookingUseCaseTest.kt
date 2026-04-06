package com.eaglepoint.task136.shared.orders

import com.eaglepoint.task136.shared.db.MeetingAttendeeEntity
import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.db.MeetingEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class BookingUseCaseTest {
    @Test
    fun `overlap honors ten minute buffer`() {
        val useCase = BookingUseCase(
            meetingDao = FakeMeetingDao(),
            clock = object : Clock {
                override fun now(): Instant = Instant.parse("2026-03-30T10:00:00Z")
            },
        )

        val existing = listOf(
            TimeWindow(
                start = Instant.parse("2026-03-30T10:00:00Z"),
                end = Instant.parse("2026-03-30T11:00:00Z"),
            ),
        )

        val candidate = TimeWindow(
            start = Instant.parse("2026-03-30T11:05:00Z"),
            end = Instant.parse("2026-03-30T11:35:00Z"),
        )

        assertTrue(useCase.overlaps(existing, candidate))
    }

    @Test
    fun `find three slots returns max three deterministic windows from meetings`() = runTest {
        val dao = FakeMeetingDao(
            listOf(
                MeetingEntity(
                    id = "1",
                    organizerId = "u1",
                    resourceId = "r1",
                    title = "M1",
                    startTime = Instant.parse("2026-03-30T10:00:00Z").toEpochMilliseconds(),
                    endTime = Instant.parse("2026-03-30T11:00:00Z").toEpochMilliseconds(),
                    status = "Approved",
                ),
            ),
        )

        val useCase = BookingUseCase(
            meetingDao = dao,
            clock = object : Clock {
                override fun now(): Instant = Instant.parse("2026-03-30T09:00:00Z")
            },
        )

        val slots = useCase.findThreeAvailableSlots(
            resourceId = "r1",
            duration = 30.minutes,
            anchor = Instant.parse("2026-03-30T09:00:00Z"),
        )

        assertEquals(3, slots.size)
        assertEquals(Instant.parse("2026-03-30T09:00:00Z"), slots[0].start)
        assertEquals(Instant.parse("2026-03-30T11:20:00Z"), slots[1].start)
        assertEquals(Instant.parse("2026-03-30T11:50:00Z"), slots[2].start)
    }

    @Test
    fun `slots stay within 14 day horizon`() = runTest {
        val useCase = BookingUseCase(
            meetingDao = FakeMeetingDao(),
            clock = object : Clock {
                override fun now(): Instant = Instant.parse("2026-03-30T09:00:00Z")
            },
        )
        val anchor = Instant.parse("2026-03-30T09:00:00Z")
        val slots = useCase.findThreeAvailableSlots("r1", 30.minutes, anchor)
        assertTrue(slots.all { it.end <= anchor.plus(14.days) })
    }
}

private class FakeMeetingDao(
    private val meetings: List<MeetingEntity> = emptyList(),
) : MeetingDao {
    override suspend fun upsert(meeting: MeetingEntity) = Unit
    override suspend fun update(meeting: MeetingEntity) = Unit
    override suspend fun getById(id: String): MeetingEntity? = meetings.firstOrNull { it.id == id }
    override suspend fun getByIdForOrganizer(id: String, actorId: String): MeetingEntity? =
        meetings.firstOrNull { it.id == id && it.organizerId == actorId }
    override suspend fun getByIdForOwnerOrDelegate(id: String, actorId: String, ownerId: String): MeetingEntity? =
        meetings.firstOrNull { it.id == id && (it.organizerId == actorId || it.organizerId == ownerId) }
    override fun observeById(id: String): Flow<MeetingEntity?> = emptyFlow()
    override suspend fun getByOrganizer(userId: String, limit: Int): List<MeetingEntity> =
        meetings.filter { it.organizerId == userId }.take(limit)
    override suspend fun page(limit: Int): List<MeetingEntity> = meetings.take(limit)
    override suspend fun pageByResource(resourceId: String, deniedStatus: String, rangeStart: Long, rangeEnd: Long, limit: Int): List<MeetingEntity> =
        meetings.filter {
            it.resourceId == resourceId &&
                it.status != deniedStatus &&
                it.endTime >= rangeStart &&
                it.startTime <= rangeEnd
        }.sortedByDescending { it.startTime }.take(limit)
    override suspend fun getOverdueApprovedNoShowCandidates(nowMillis: Long, approvedStatus: String): List<MeetingEntity> = emptyList()
    override suspend fun upsertAttendee(attendee: MeetingAttendeeEntity) = Unit
    override suspend fun getAttendees(meetingId: String): List<MeetingAttendeeEntity> = emptyList()
    override suspend fun getAttendeesForOrganizer(meetingId: String, actorId: String): List<MeetingAttendeeEntity> = emptyList()
    override suspend fun removeAttendee(id: String) = Unit
}

