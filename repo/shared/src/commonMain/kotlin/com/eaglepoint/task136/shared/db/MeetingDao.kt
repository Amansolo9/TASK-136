package com.eaglepoint.task136.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeetingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meeting: MeetingEntity)

    @Update
    suspend fun update(meeting: MeetingEntity)

    @Query("SELECT * FROM meetings WHERE id = :id")
    suspend fun getById(id: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE id = :id AND organizerId = :actorId")
    suspend fun getByIdForOrganizer(id: String, actorId: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE id = :id AND organizerId IN (:actorId, :ownerId)")
    suspend fun getByIdForOwnerOrDelegate(id: String, actorId: String, ownerId: String): MeetingEntity?

    @Query("SELECT * FROM meetings WHERE id = :id")
    fun observeById(id: String): Flow<MeetingEntity?>

    @Query("SELECT * FROM meetings WHERE organizerId = :userId ORDER BY startTime DESC LIMIT :limit")
    suspend fun getByOrganizer(userId: String, limit: Int = 50): List<MeetingEntity>

    @Query("SELECT * FROM meetings ORDER BY startTime DESC LIMIT :limit")
    suspend fun page(limit: Int = 50): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE resourceId = :resourceId AND status != :deniedStatus AND endTime >= :rangeStart AND startTime <= :rangeEnd ORDER BY startTime DESC LIMIT :limit")
    suspend fun pageByResource(resourceId: String, deniedStatus: String = "Denied", rangeStart: Long = 0L, rangeEnd: Long = Long.MAX_VALUE, limit: Int = 100): List<MeetingEntity>

    @Query("SELECT * FROM meetings WHERE status = :approvedStatus AND requireCheckIn = 1 AND checkInDueAt IS NOT NULL AND checkInDueAt <= :nowMillis")
    suspend fun getOverdueApprovedNoShowCandidates(nowMillis: Long, approvedStatus: String = "Approved"): List<MeetingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttendee(attendee: MeetingAttendeeEntity)

    @Query("SELECT * FROM meeting_attendees WHERE meetingId = :meetingId")
    suspend fun getAttendees(meetingId: String): List<MeetingAttendeeEntity>

    @Query("SELECT a.* FROM meeting_attendees a INNER JOIN meetings m ON m.id = a.meetingId WHERE a.meetingId = :meetingId AND m.organizerId = :actorId")
    suspend fun getAttendeesForOrganizer(meetingId: String, actorId: String): List<MeetingAttendeeEntity>

    @Query("DELETE FROM meeting_attendees WHERE id = :id")
    suspend fun removeAttendee(id: String)
}
