package com.eaglepoint.task136.shared.services

import com.eaglepoint.task136.shared.db.MeetingDao
import com.eaglepoint.task136.shared.viewmodel.MeetingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class MeetingNoShowReconciliationService(
    private val meetingDao: MeetingDao,
    private val clock: Clock,
) {
    suspend fun reconcileOverdueApprovedMeetings(): Int = withContext(Dispatchers.IO) {
        val overdue = meetingDao.getOverdueApprovedNoShowCandidates(clock.now().toEpochMilliseconds())
        overdue.forEach { meeting ->
            meetingDao.update(
                meeting.copy(
                    status = MeetingStatus.NoShow.name,
                    checkInDueAt = null,
                    note = appendNoShowReconciledNote(meeting.note),
                ),
            )
        }
        overdue.size
    }

    private fun appendNoShowReconciledNote(existing: String?): String {
        val marker = "reconciledNoShow=true"
        return if (existing.isNullOrBlank()) marker else "$existing;$marker"
    }
}
