package com.ustc.timetable.sync

import com.ustc.timetable.school.ustc.parser.CourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.NormalizationIssue
import com.ustc.timetable.school.ustc.parser.NormalizedSchoolSnapshot
import com.ustc.timetable.school.ustc.parser.SemesterMetaParser
import com.ustc.timetable.school.ustc.parser.TimetablePageParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.portal.SchoolPortalSource
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.FingerprintedSchoolContent
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.SchoolSnapshotFingerprint
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.SnapshotDiffer
import java.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncEngine(
    private val portal: SchoolPortalSource,
    private val selectionParser: CourseSelectionPageParser,
    private val timetableParser: TimetablePageParser,
    private val metaParser: SemesterMetaParser,
    private val normalizer: UstcSnapshotNormalizer,
    private val differ: SnapshotDiffer,
    private val db: TimetableDatabase,
    private val clock: Clock,
) {
    private val syncMutex = Mutex()

    suspend fun syncCurrentAcademicSemester(): SyncResult =
        executeCurrentAcademicSemester().result

    suspend fun executeCurrentAcademicSemester(): SyncExecutionReport = syncMutex.withLock {
        executeCurrentAcademicSemesterLocked()
    }

    private suspend fun executeCurrentAcademicSemesterLocked(): SyncExecutionReport {
        val target = db.semesterDao().academicCurrentPortalLinked()?.let(Mappers::toDomain)
            ?: return SyncExecutionReport(SyncResult.NoChange, portalAttempted = false, finishedAt = null)
        val result = try {
            val selectionPage = portal.fetchCourseSelectionPage()
            val timetablePage = portal.fetchTimetablePage()
            val selection = selectionParser.parse(selectionPage)
            val timetable = timetableParser.parse(timetablePage)
            val meta = metaParser.parse(selectionPage, timetablePage)
            val normalized = normalizer.normalize(selection, timetable, meta.meta, target.id)
            validateSnapshot(normalized, target)

            val newContent = FingerprintedSchoolContent.of(target, normalized.courses, normalized.meetings)
            val newFingerprint = SchoolSnapshotFingerprint.compute(newContent)
            if (target.sourceFingerprint == newFingerprint) {
                return SyncExecutionReport(SyncResult.NoChange, portalAttempted = true, finishedAt = clock.instant())
            }

            val (oldCourses, oldMeetings) = loadTargetSchoolSnapshot(target.id)
            val changes = if (target.sourceFingerprint == null) {
                emptyList()
            } else {
                differ.diff(
                    FingerprintedSchoolContent.of(target, oldCourses, oldMeetings),
                    newContent,
                )
            }
            val persistent = FreshLocalIds.assign(normalized)
            db.applySchoolSnapshot(
                semesterId = target.id,
                courses = persistent.courses,
                meetings = persistent.meetings,
                fingerprint = newFingerprint,
                syncedAt = clock.instant(),
            )
            SyncResult.Success(changes)
        } catch (failure: SyncFailure) {
            SyncResult.Failed(failure.error)
        }
        return SyncExecutionReport(result, portalAttempted = true, finishedAt = clock.instant())
    }

    private suspend fun loadTargetSchoolSnapshot(
        semesterId: SemesterId,
    ): Pair<List<Course>, List<CourseMeeting>> {
        val courses = db.courseDao().coursesForSemester(semesterId.value)
            .filter { it.source == ItemSource.SCHOOL.name }
            .map { Mappers.toDomain(it, semesterId) }
        val courseIds = courses.map { it.id.value }.toSet()
        val meetings = db.courseDao().meetingsForSemester(semesterId.value)
            .filter { it.source == ItemSource.SCHOOL.name && it.courseId in courseIds }
            .map(Mappers::toDomain)
        return courses to meetings
    }

    private fun validateSnapshot(snapshot: NormalizedSchoolSnapshot, target: Semester) {
        if (snapshot.courses.isEmpty()) validationFailure()
        if (snapshot.issues.any { it.severity == NormalizationIssue.Severity.HARD }) validationFailure()
        if (snapshot.courses.any { it.semesterId != target.id || it.source != ItemSource.SCHOOL }) validationFailure()
        if (snapshot.courses.map { it.id }.toSet().size != snapshot.courses.size) validationFailure()
        if (snapshot.courses.map { it.sourceCourseKey }.toSet().size != snapshot.courses.size) validationFailure()
        if (snapshot.meetings.map { it.id }.toSet().size != snapshot.meetings.size) validationFailure()

        val courseById = snapshot.courses.associateBy { it.id }
        snapshot.meetings.forEach { meeting ->
            if (meeting.source != ItemSource.SCHOOL || courseById[meeting.courseId] == null) validationFailure()
            if (meeting.weekday !in 1..7) validationFailure()
            if (meeting.startPeriod !in 1..13 || meeting.endPeriod !in meeting.startPeriod..13) validationFailure()
            if ((1..63).any { week -> week in meeting.weekPattern && week !in 1..target.totalWeeks }) {
                validationFailure()
            }
        }

        val duplicateMeetings = snapshot.meetings.groupingBy { meeting ->
            listOf(
                courseById.getValue(meeting.courseId).sourceCourseKey,
                meeting.weekday,
                meeting.startPeriod,
                meeting.endPeriod,
                meeting.weekPattern.mask,
                meeting.location,
                meeting.teacherNames,
            )
        }.eachCount().any { it.value > 1 }
        if (duplicateMeetings) validationFailure()
    }

    private fun validationFailure(): Nothing = throw SyncError.ValidationFailed.asFailure()
}
