package com.ustc.timetable.semester

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.encodePeriods
import com.ustc.timetable.school.ustc.parser.CourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.NormalizationIssue
import com.ustc.timetable.school.ustc.parser.NormalizedSchoolSnapshot
import com.ustc.timetable.school.ustc.parser.SemesterMetaParser
import com.ustc.timetable.school.ustc.parser.TimetablePageParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.portal.SchoolPortalSource
import com.ustc.timetable.sync.FreshLocalIds
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncFailure
import com.ustc.timetable.sync.asFailure
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.data.db.importNewSemesterWithSnapshot
import com.ustc.timetable.timetable.domain.FingerprintedSchoolContent
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.SchoolSnapshotFingerprint
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.Term
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ImportStep {
    data object AwaitingLogin : ImportStep
    data object Fetching : ImportStep
    data class ConfirmMeta(val draft: SemesterImportDraft) : ImportStep
    data class Done(val semesterId: SemesterId) : ImportStep
    data class Error(val error: SyncError) : ImportStep
}

data class SemesterImportDraft(
    val displayName: String?,
    val academicYear: String?,
    val term: Term?,
    val week1Start: LocalDate?,
    val totalWeeks: Int?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)

data class ConfirmedSemesterMeta(
    val displayName: String,
    val academicYear: String,
    val term: Term,
    val week1Start: LocalDate,
    val totalWeeks: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

class ImportFlowViewModel(
    private val portal: SchoolPortalSource,
    private val selectionParser: CourseSelectionPageParser,
    private val timetableParser: TimetablePageParser,
    private val metaParser: SemesterMetaParser,
    private val normalizer: UstcSnapshotNormalizer,
    private val db: TimetableDatabase,
    private val settings: SettingsStore,
    private val workingProfile: suspend () -> ScheduleProfile,
    private val clock: Clock,
    private val setViewedSemesterId: suspend (String) -> Unit = settings::setViewedSemesterId,
    private val importDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val provisionalSemesterId: () -> SemesterId = {
        SemesterId("semester.portal.${UUID.randomUUID()}")
    },
    private val privateProfileId: () -> String = {
        "profile.semester.${UUID.randomUUID()}"
    },
) : ViewModel() {

    private val mutableStep = MutableStateFlow<ImportStep>(ImportStep.AwaitingLogin)
    val step: StateFlow<ImportStep> = mutableStep.asStateFlow()

    private var pending: PendingImport? = null

    fun startLoginImport() {
        prepareForLogin()
        viewModelScope.launch(importDispatcher) { onLoginResultOk() }
    }

    fun confirmMeta(meta: ConfirmedSemesterMeta) {
        val current = claimMetaConfirmation() ?: return
        viewModelScope.launch(importDispatcher) { completeMetaConfirmation(current, meta) }
    }

    fun prepareForLogin() {
        val error = mutableStep.value as? ImportStep.Error ?: return
        mutableStep.compareAndSet(error, ImportStep.AwaitingLogin)
    }

    suspend fun onLoginResultOk() {
        if (!mutableStep.compareAndSet(ImportStep.AwaitingLogin, ImportStep.Fetching)) return
        val semesterId = provisionalSemesterId()
        try {
            val selectionPage = portal.fetchCourseSelectionPage()
            val timetablePage = portal.fetchTimetablePage()
            val selection = selectionParser.parse(selectionPage)
            val timetable = timetableParser.parse(timetablePage)
            val parsedMeta = metaParser.parse(selectionPage, timetablePage)
            val snapshot = normalizer.normalize(selection, timetable, parsedMeta.meta, semesterId)
            validateSnapshot(
                snapshot = snapshot,
                expectedSemesterId = semesterId,
                totalWeeks = parsedMeta.meta.totalWeeks,
            )
            pending = PendingImport(semesterId, snapshot)

            val draft = parsedMeta.meta.toDraft()
            val confirmed = draft.toConfirmedOrNull()
            if (parsedMeta.isConfident && confirmed != null) {
                commit(confirmed)
            } else {
                mutableStep.value = ImportStep.ConfirmMeta(draft)
            }
        } catch (failure: SyncFailure) {
            mutableStep.value = ImportStep.Error(failure.error)
        } catch (cancelled: CancellationException) {
            mutableStep.compareAndSet(ImportStep.Fetching, ImportStep.AwaitingLogin)
            throw cancelled
        }
    }

    suspend fun onMetaConfirmed(meta: ConfirmedSemesterMeta) {
        val current = claimMetaConfirmation() ?: return
        completeMetaConfirmation(current, meta)
    }

    private fun claimMetaConfirmation(): ImportStep.ConfirmMeta? {
        val current = mutableStep.value as? ImportStep.ConfirmMeta ?: return null
        return current.takeIf { mutableStep.compareAndSet(current, ImportStep.Fetching) }
    }

    private suspend fun completeMetaConfirmation(
        current: ImportStep.ConfirmMeta,
        meta: ConfirmedSemesterMeta,
    ) {
        try {
            if (!meta.isValidSemesterConfirmation()) throw SyncError.ValidationFailed.asFailure()
            commit(meta)
        } catch (failure: SyncFailure) {
            mutableStep.value = ImportStep.Error(failure.error)
        } catch (cancelled: CancellationException) {
            mutableStep.compareAndSet(ImportStep.Fetching, current)
            throw cancelled
        }
    }

    fun onMetaCancelled() {
        val current = mutableStep.value as? ImportStep.ConfirmMeta ?: return
        if (!mutableStep.compareAndSet(current, ImportStep.AwaitingLogin)) return
        pending = null
    }

    private suspend fun commit(meta: ConfirmedSemesterMeta) {
        if (!meta.isValidSemesterConfirmation()) throw SyncError.ValidationFailed.asFailure()
        val candidate = pending ?: throw SyncError.ValidationFailed.asFailure()
        validateSnapshot(
            snapshot = candidate.snapshot,
            expectedSemesterId = candidate.semesterId,
            totalWeeks = meta.totalWeeks,
        )

        val sourceProfile = workingProfile()
        val profile = ScheduleProfileEntity(
            id = privateProfileId(),
            name = sourceProfile.name,
            isBundledOfficial = false,
            periodsJson = encodePeriods(sourceProfile.periods),
        )
        val now = clock.instant()
        val semester = Semester(
            id = candidate.semesterId,
            displayName = meta.displayName.trim(),
            academicYear = meta.academicYear.trim(),
            term = meta.term,
            week1Start = meta.week1Start,
            totalWeeks = meta.totalWeeks,
            startDate = meta.startDate,
            endDate = meta.endDate,
            importedAt = now,
            lastSyncedAt = null,
            isCurrentAcademicSemester = true,
            portalLinked = true,
            profileId = ProfileId(profile.id),
            sourceFingerprint = null,
        )
        val fingerprint = SchoolSnapshotFingerprint.compute(
            FingerprintedSchoolContent.of(
                semester,
                candidate.snapshot.courses,
                candidate.snapshot.meetings,
            ),
        )
        val persistent = FreshLocalIds.assign(candidate.snapshot)
        db.importNewSemesterWithSnapshot(
            boundProfile = profile,
            semester = Mappers.toEntity(semester),
            courses = persistent.courses,
            meetings = persistent.meetings,
            fingerprint = fingerprint,
            syncedAt = now,
        )
        setViewedSemesterId(semester.id.value)
        settings.setLastSyncFinishedAt(now.toEpochMilli())
        pending = null
        mutableStep.value = ImportStep.Done(semester.id)
    }

    private fun validateSnapshot(
        snapshot: NormalizedSchoolSnapshot,
        expectedSemesterId: SemesterId,
        totalWeeks: Int?,
    ) {
        if (snapshot.courses.isEmpty()) validationFailure()
        if (snapshot.issues.any { it.severity == NormalizationIssue.Severity.HARD }) validationFailure()
        if (snapshot.courses.map { it.id }.toSet().size != snapshot.courses.size) validationFailure()
        if (snapshot.courses.map { it.sourceCourseKey }.toSet().size != snapshot.courses.size) validationFailure()
        if (snapshot.meetings.map { it.id }.toSet().size != snapshot.meetings.size) validationFailure()

        if (snapshot.courses.any { it.semesterId != expectedSemesterId || it.source != ItemSource.SCHOOL }) {
            validationFailure()
        }
        val courseById = snapshot.courses.associateBy { it.id }
        snapshot.meetings.forEach { meeting ->
            if (meeting.source != ItemSource.SCHOOL || courseById[meeting.courseId] == null) validationFailure()
            if (meeting.weekday !in 1..7) validationFailure()
            if (meeting.startPeriod !in 1..13 || meeting.endPeriod !in meeting.startPeriod..13) validationFailure()
            if (totalWeeks != null && (1..63).any { it in meeting.weekPattern && it !in 1..totalWeeks }) {
                validationFailure()
            }
        }
        val duplicateCanonicalRows = snapshot.meetings.groupingBy { meeting ->
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
        if (duplicateCanonicalRows) validationFailure()
    }

    private fun validationFailure(): Nothing = throw SyncError.ValidationFailed.asFailure()

    private fun SemesterImportDraft.toConfirmedOrNull(): ConfirmedSemesterMeta? {
        val confirmed = ConfirmedSemesterMeta(
            displayName = displayName ?: return null,
            academicYear = academicYear ?: return null,
            term = term ?: return null,
            week1Start = week1Start ?: return null,
            totalWeeks = totalWeeks ?: return null,
            startDate = startDate ?: return null,
            endDate = endDate ?: return null,
        )
        return confirmed.takeIf { it.isValidSemesterConfirmation() }
    }

    private data class PendingImport(
        val semesterId: SemesterId,
        val snapshot: NormalizedSchoolSnapshot,
    )
}

private fun com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial.toDraft() =
    SemesterImportDraft(
        displayName = displayName,
        academicYear = academicYear,
        term = term,
        week1Start = week1Start,
        totalWeeks = totalWeeks,
        startDate = startDate,
        endDate = endDate,
    )
