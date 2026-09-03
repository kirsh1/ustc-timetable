package com.ustc.timetable.sync

import com.ustc.timetable.timetable.domain.ScheduleChange
import java.time.Instant

sealed class SyncResult {
    data object NoChange : SyncResult()

    data class Success(
        val changes: List<ScheduleChange>,
    ) : SyncResult()

    data class Failed(
        val error: SyncError,
    ) : SyncResult()
}

data class SyncExecutionReport(
    val result: SyncResult,
    val portalAttempted: Boolean,
    val finishedAt: Instant?,
)
