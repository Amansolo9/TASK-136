package com.eaglepoint.task136.shared.orders

import com.eaglepoint.task136.shared.db.MeetingDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

data class TimeWindow(
    val start: Instant,
    val end: Instant,
)

class BookingUseCase(
    private val meetingDao: MeetingDao,
    private val clock: Clock,
    private val buffer: Duration = 10.minutes,
) {
    suspend fun findThreeAvailableSlots(
        resourceId: String,
        duration: Duration,
        anchor: Instant = clock.now(),
    ): List<TimeWindow> = withContext(Dispatchers.IO) {
        val horizonEnd = anchor.plus(14.days)
        val existing = meetingDao.pageByResource(
            resourceId = resourceId,
            rangeStart = anchor.toEpochMilliseconds(),
            rangeEnd = horizonEnd.toEpochMilliseconds(),
            limit = 500,
        ).map { TimeWindow(Instant.fromEpochMilliseconds(it.startTime), Instant.fromEpochMilliseconds(it.endTime)) }
            .sortedBy { it.start }

        val results = mutableListOf<TimeWindow>()
        var cursor = anchor

        while (cursor.plus(duration) <= horizonEnd && results.size < 3) {
            val candidate = TimeWindow(cursor, cursor.plus(duration))
            if (!overlaps(existing, candidate)) {
                results += candidate
                cursor = cursor.plus(30.minutes)
            } else {
                cursor = cursor.plus(10.minutes)
            }
        }

        results
    }

    fun overlaps(existing: List<TimeWindow>, candidate: TimeWindow): Boolean {
        val bufferedCandidateStart = candidate.start.minus(buffer)
        val bufferedCandidateEnd = candidate.end.plus(buffer)

        return existing.any { slot ->
            val bufferedSlotStart = slot.start.minus(buffer)
            val bufferedSlotEnd = slot.end.plus(buffer)
            bufferedCandidateStart < bufferedSlotEnd && bufferedCandidateEnd > bufferedSlotStart
        }
    }
}
