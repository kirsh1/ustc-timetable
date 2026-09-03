package com.ustc.timetable.school.ustc

import com.ustc.timetable.school.ustc.auth.SessionStore
import com.ustc.timetable.school.ustc.auth.UstcSessionManager
import com.ustc.timetable.school.ustc.parser.UstcCourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.UstcSemesterMetaParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.parser.UstcTimetablePageParser
import com.ustc.timetable.school.ustc.portal.UstcHttpPortalSource
import com.ustc.timetable.school.ustc.portal.UstcPortalDescriptor
import com.ustc.timetable.sync.ManualSyncController
import com.ustc.timetable.sync.SyncEngine
import com.ustc.timetable.sync.SyncExecutionRecorder
import com.ustc.timetable.sync.WeeklySyncWorkerFactory

data class UstcPortalRuntime(
    val descriptor: UstcPortalDescriptor,
    val sessionStore: SessionStore,
    val sessionManager: UstcSessionManager,
    val portalSource: UstcHttpPortalSource,
    val courseParser: UstcCourseSelectionPageParser,
    val timetableParser: UstcTimetablePageParser,
    val semesterMetaParser: UstcSemesterMetaParser,
    val normalizer: UstcSnapshotNormalizer,
    val syncEngine: SyncEngine,
    val syncRecorder: SyncExecutionRecorder,
    val manualSyncController: ManualSyncController,
    val workerFactory: WeeklySyncWorkerFactory,
)
