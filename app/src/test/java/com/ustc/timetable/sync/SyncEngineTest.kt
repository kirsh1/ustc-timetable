package com.ustc.timetable.sync

import androidx.room.Room
import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.school.ustc.parser.CourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.SemesterMetaParser
import com.ustc.timetable.school.ustc.parser.SemesterMetaResult
import com.ustc.timetable.school.ustc.parser.TimetablePageParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.portal.SchoolPortalSource
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.data.db.entity.ManualItemEntity
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.data.db.entity.SemesterEntity
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.FingerprintedSchoolContent
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ScheduleChange
import com.ustc.timetable.timetable.domain.SchoolSnapshotFingerprint
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.SnapshotDiffer
import com.ustc.timetable.timetable.domain.Term
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncEngineTest {
    private lateinit var db: TimetableDatabase
    private val profileId = "profile"
    private val oldTime = Instant.ofEpochMilli(1_000_000)
    private val syncTime = Instant.ofEpochMilli(2_000_000)
    private lateinit var target: Semester
    private lateinit var oldCourse: Course
    private lateinit var oldMeeting: CourseMeeting
    private lateinit var manual: ManualScheduleItem

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.scheduleProfileDao().insert(ScheduleProfileEntity(profileId, "profile", true, "[]"))
        target = SemesterDefaults.AUTUMN_2026("target", profileId, Instant.EPOCH).copy(portalLinked = true)
        db.semesterDao().insert(Mappers.toEntity(target))
        oldCourse = Course(CourseId("old-course"), target.id, "C1", "C1", "课程A", 3.0, "专业")
        oldMeeting = CourseMeeting(
            MeetingId("old-meeting"), oldCourse.id, 1, 1, 2, WeekPattern.range(1, 4), "TH-B301", listOf("教师甲"),
        )
        val oldFingerprint = SchoolSnapshotFingerprint.compute(
            FingerprintedSchoolContent.of(target, listOf(oldCourse), listOf(oldMeeting)),
        )
        db.applySchoolSnapshot(target.id, listOf(oldCourse), listOf(oldMeeting), oldFingerprint, oldTime)
        target = Mappers.toDomain(db.semesterDao().byId(target.id.value)!!)
        manual = ManualScheduleItem(
            ManualItemId("manual"), target.id, "组会", 5, LocalTime.of(14, 0), LocalTime.of(15, 0),
            WeekPattern.range(1, 4), "M101", "note", oldTime, oldTime,
        )
        db.manualItemDao().insert(Mappers.toEntity(manual))
    }

    @After fun tearDown() = db.close()

    @Test fun no_target_returns_nochange_without_portal_calls() = runBlocking {
        val noTarget = target.copy(portalLinked = false)
        replaceSemester(noTarget)
        val before = snapshot()
        val fixture = EngineFixture(db)

        assertEquals(SyncResult.NoChange, fixture.engine().syncCurrentAcademicSemester())
        assertEquals(0, fixture.portal.selectionCalls)
        assertEquals(0, fixture.portal.timetableCalls)
        assertEquals(0, fixture.selectionParser.calls)
        assertEquals(0, fixture.timetableParser.calls)
        assertEquals(before, snapshot())
    }

    @Test fun no_target_does_not_report_a_finished_portal_attempt() = runBlocking {
        replaceSemester(target.copy(portalLinked = false))
        val fixture = EngineFixture(db)

        assertEquals(
            SyncExecutionReport(SyncResult.NoChange, portalAttempted = false, finishedAt = null),
            fixture.engine().executeCurrentAcademicSemester(),
        )
    }

    @Test fun actual_nochange_reports_finished_time() = runBlocking {
        val fixture = EngineFixture(db)

        assertEquals(
            SyncExecutionReport(SyncResult.NoChange, portalAttempted = true, finishedAt = syncTime),
            fixture.engine().executeCurrentAcademicSemester(),
        )
    }

    @Test fun success_reports_finished_time() = runBlocking {
        val fixture = EngineFixture(db, location = "TH-C204")

        val report = fixture.engine().executeCurrentAcademicSemester()

        assertTrue(report.result is SyncResult.Success)
        assertTrue(report.portalAttempted)
        assertEquals(syncTime, report.finishedAt)
    }

    @Test fun typed_failure_reports_finished_time() = runBlocking {
        listOf(
            SyncError.AuthenticationExpired,
            SyncError.NetworkFailed,
            SyncError.ParseFailed,
            SyncError.ValidationFailed,
        ).forEach { error ->
            val fixture = EngineFixture(db, failure = Stage.SELECTION_FETCH to error.asFailure())

            assertEquals(
                SyncExecutionReport(SyncResult.Failed(error), portalAttempted = true, finishedAt = syncTime),
                fixture.engine().executeCurrentAcademicSemester(),
            )
        }
    }

    @Test fun sync_ignores_viewed_and_targets_academic_current_portal_linked() = runBlocking {
        val historical = target.copy(
            id = SemesterId("historical"),
            isCurrentAcademicSemester = false,
            sourceFingerprint = null,
        )
        db.semesterDao().insert(Mappers.toEntity(historical))
        val hc = oldCourse.copy(id = CourseId("historical-course"), semesterId = historical.id)
        val hm = oldMeeting.copy(id = MeetingId("historical-meeting"), courseId = hc.id, location = "HISTORICAL")
        db.applySchoolSnapshot(historical.id, listOf(hc), listOf(hm), "historical-fp", oldTime)
        val historicalBefore = schoolSnapshot(historical.id)
        val fixture = EngineFixture(db, location = "TH-C204")

        val result = fixture.engine().syncCurrentAcademicSemester()

        assertTrue(result is SyncResult.Success)
        assertEquals("TH-C204", db.courseDao().meetingsForSemester(target.id.value).single().location)
        assertEquals(historicalBefore, schoolSnapshot(historical.id))
    }

    @Test fun success_replaces_only_target_school_rows() = runBlocking {
        val fixture = EngineFixture(db, location = "TH-C204")

        val result = fixture.engine().syncCurrentAcademicSemester()

        assertTrue(result is SyncResult.Success)
        val storedCourse = db.courseDao().coursesForSemester(target.id.value).single()
        val storedMeeting = db.courseDao().meetingsForSemester(target.id.value).single()
        assertEquals("课程A", storedCourse.name)
        assertEquals("TH-C204", storedMeeting.location)
        assertNotEquals(oldCourse.id.value, storedCourse.id)
        assertNotEquals(oldMeeting.id.value, storedMeeting.id)
    }

    @Test fun manual_items_survive_successful_sync() = runBlocking {
        EngineFixture(db, location = "TH-C204").engine().syncCurrentAcademicSemester()

        assertEquals(listOf(Mappers.toEntity(manual)), db.manualItemDao().itemsForSemester(target.id.value))
    }

    @Test fun authentication_failure_is_zero_write() = runBlocking {
        assertKnownFailureIsZeroWrite(SyncError.AuthenticationExpired, Stage.SELECTION_FETCH)
    }

    @Test fun network_failure_is_zero_write() = runBlocking {
        assertKnownFailureIsZeroWrite(SyncError.NetworkFailed, Stage.TIMETABLE_FETCH)
    }

    @Test fun parse_failure_is_zero_write() = runBlocking {
        assertKnownFailureIsZeroWrite(SyncError.ParseFailed, Stage.SELECTION_PARSE)
    }

    @Test fun validation_failure_is_zero_write() = runBlocking {
        val constrained = target.copy(totalWeeks = 10)
        replaceSemester(constrained)
        val before = snapshot()
        val fixture = EngineFixture(db, weeks = "15", metaTotalWeeks = 20)

        assertEquals(SyncResult.Failed(SyncError.ValidationFailed), fixture.engine().syncCurrentAcademicSemester())
        assertEquals(before, snapshot())
    }

    @Test fun empty_snapshot_is_validation_failure_and_zero_write() = runBlocking {
        val before = snapshot()
        val fixture = EngineFixture(db, selection = emptyList(), timetable = emptyList())

        assertEquals(SyncResult.Failed(SyncError.ValidationFailed), fixture.engine().syncCurrentAcademicSemester())
        assertEquals(before, snapshot())
    }

    @Test fun fingerprint_equal_is_true_zero_write() = runBlocking {
        val before = snapshot()
        val fixture = EngineFixture(db)

        assertEquals(SyncResult.NoChange, fixture.engine().syncCurrentAcademicSemester())

        assertEquals(before, snapshot())
        assertEquals(oldCourse.id.value, db.courseDao().coursesForSemester(target.id.value).single().id)
        assertEquals(oldMeeting.id.value, db.courseDao().meetingsForSemester(target.id.value).single().id)
        assertEquals(oldTime.toEpochMilli(), db.semesterDao().byId(target.id.value)!!.lastSyncedAtEpochMilli)
    }

    @Test fun fingerprint_drift_without_user_visible_diff_persists_silently() = runBlocking {
        val fixture = EngineFixture(db, credits = 4.0, courseType = "新类型")

        val result = fixture.engine().syncCurrentAcademicSemester()

        assertEquals(SyncResult.Success(emptyList()), result)
        val stored = db.courseDao().coursesForSemester(target.id.value).single()
        assertEquals(4.0, stored.credits)
        assertEquals("新类型", stored.courseType)
        assertNotEquals(target.sourceFingerprint, db.semesterDao().byId(target.id.value)!!.sourceFingerprint)
        assertEquals(syncTime.toEpochMilli(), db.semesterDao().byId(target.id.value)!!.lastSyncedAtEpochMilli)
    }

    @Test fun changed_snapshot_returns_exact_prewrite_diff() = runBlocking {
        val result = EngineFixture(db, location = "TH-C204").engine().syncCurrentAcademicSemester()

        assertEquals(
            SyncResult.Success(
                listOf(
                    ScheduleChange.LocationChanged(
                        courseName = "课程A",
                        weeks = WeekPattern.range(1, 4),
                        old = "TH-B301",
                        new = "TH-C204",
                    ),
                ),
            ),
            result,
        )
    }

    @Test fun old_snapshot_extraction_contains_only_target_school_rows() = runBlocking {
        val historical = target.copy(id = SemesterId("history"), isCurrentAcademicSemester = false, sourceFingerprint = null)
        db.semesterDao().insert(Mappers.toEntity(historical))
        val hc = oldCourse.copy(id = CourseId("hc"), semesterId = historical.id)
        val hm = oldMeeting.copy(id = MeetingId("hm"), courseId = hc.id, location = "SHOULD-NOT-DIFF")
        db.applySchoolSnapshot(historical.id, listOf(hc), listOf(hm), "history-fp", oldTime)

        val result = EngineFixture(db, location = "TH-C204").engine().syncCurrentAcademicSemester()

        assertEquals(1, (result as SyncResult.Success).changes.size)
        assertTrue(result.changes.single() is ScheduleChange.LocationChanged)
    }

    @Test fun concurrent_sync_calls_are_serialized() = runBlocking {
        val fixture = EngineFixture(db, location = "TH-C204", portalDelayMillis = 75)
        val engine = fixture.engine()

        withContext(Dispatchers.Default) {
            val first = async { engine.syncCurrentAcademicSemester() }
            val second = async { engine.syncCurrentAcademicSemester() }
            first.await()
            second.await()
        }

        assertEquals(1, fixture.portal.maxConcurrentPipelines.get())
        assertEquals(2, fixture.portal.selectionCalls)
    }

    @Test fun pipeline_order_is_fetch_parse_meta_normalize_before_write() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = EngineFixture(db, location = "TH-C204", events = events)

        fixture.engine().syncCurrentAcademicSemester()

        assertEquals(
            listOf("fetch-selection", "fetch-timetable", "parse-selection", "parse-timetable", "parse-meta"),
            events,
        )
        assertEquals("TH-C204", db.courseDao().meetingsForSemester(target.id.value).single().location)
    }

    @Test fun unknown_programming_exception_propagates() {
        val fixture = EngineFixture(db, failure = Stage.SELECTION_FETCH to IllegalStateException("bug"))

        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.engine().syncCurrentAcademicSemester() }
        }
    }

    private suspend fun assertKnownFailureIsZeroWrite(error: SyncError, stage: Stage) {
        val before = snapshot()
        val fixture = EngineFixture(db, failure = stage to error.asFailure())

        assertEquals(SyncResult.Failed(error), fixture.engine().syncCurrentAcademicSemester())
        assertEquals(before, snapshot())
        when (stage) {
            Stage.SELECTION_FETCH -> {
                assertEquals(0, fixture.portal.timetableCalls)
                assertEquals(0, fixture.selectionParser.calls)
            }
            Stage.TIMETABLE_FETCH -> assertEquals(0, fixture.selectionParser.calls)
            Stage.SELECTION_PARSE -> {
                assertEquals(0, fixture.timetableParser.calls)
                assertEquals(0, fixture.metaParser.calls)
            }
        }
    }

    private suspend fun replaceSemester(value: Semester) {
        db.clearAllTables()
        db.scheduleProfileDao().insert(ScheduleProfileEntity(profileId, "profile", true, "[]"))
        db.semesterDao().insert(Mappers.toEntity(value))
        db.applySchoolSnapshot(value.id, listOf(oldCourse.copy(semesterId = value.id)), listOf(oldMeeting), target.sourceFingerprint!!, oldTime)
        db.manualItemDao().insert(Mappers.toEntity(manual.copy(semesterId = value.id)))
        target = Mappers.toDomain(db.semesterDao().byId(value.id.value)!!)
    }

    private suspend fun snapshot() = DatabaseSnapshot(
        semester = db.semesterDao().byId(target.id.value),
        courses = db.courseDao().coursesForSemester(target.id.value),
        meetings = db.courseDao().meetingsForSemester(target.id.value),
        manual = db.manualItemDao().itemsForSemester(target.id.value),
    )

    private suspend fun schoolSnapshot(id: SemesterId) = Pair(
        db.courseDao().coursesForSemester(id.value),
        db.courseDao().meetingsForSemester(id.value),
    )

    private data class DatabaseSnapshot(
        val semester: SemesterEntity?,
        val courses: List<CourseEntity>,
        val meetings: List<CourseMeetingEntity>,
        val manual: List<ManualItemEntity>,
    )

    private enum class Stage { SELECTION_FETCH, TIMETABLE_FETCH, SELECTION_PARSE }

    private inner class EngineFixture(
        private val database: TimetableDatabase,
        location: String = "TH-B301",
        credits: Double? = 3.0,
        courseType: String? = "专业",
        weeks: String = "1-4",
        metaTotalWeeks: Int? = 20,
        selection: List<UstcCourseSummary> = listOf(courseSummary(credits, courseType)),
        timetable: List<UstcTimetableEntry> = listOf(timetableEntry(location, weeks)),
        failure: Pair<Stage, Throwable>? = null,
        portalDelayMillis: Long = 0,
        events: MutableList<String> = mutableListOf(),
    ) {
        val portal = FakePortal(failure, portalDelayMillis, events)
        val selectionParser = FakeSelectionParser(selection, failure, events)
        val timetableParser = FakeTimetableParser(timetable, events)
        val metaParser = FakeMetaParser(metaTotalWeeks, events)

        fun engine() = SyncEngine(
            portal = portal,
            selectionParser = selectionParser,
            timetableParser = timetableParser,
            metaParser = metaParser,
            normalizer = UstcSnapshotNormalizer(),
            differ = SnapshotDiffer(),
            db = database,
            clock = Clock.fixed(syncTime, ZoneOffset.UTC),
        )
    }

    private class FakePortal(
        private val failure: Pair<Stage, Throwable>?,
        private val delayMillis: Long,
        private val events: MutableList<String>,
    ) : SchoolPortalSource {
        var selectionCalls = 0
        var timetableCalls = 0
        private val active = AtomicInteger(0)
        val maxConcurrentPipelines = AtomicInteger(0)

        override suspend fun fetchCourseSelectionPage(): UstcPortalPage {
            selectionCalls++
            events += "fetch-selection"
            failure?.takeIf { it.first == Stage.SELECTION_FETCH }?.second?.let { throw it }
            val nowActive = active.incrementAndGet()
            maxConcurrentPipelines.updateAndGet { max -> maxOf(max, nowActive) }
            if (delayMillis > 0) delay(delayMillis)
            return UstcPortalPage("selection", "test://selection")
        }

        override suspend fun fetchTimetablePage(): UstcPortalPage {
            timetableCalls++
            events += "fetch-timetable"
            failure?.takeIf { it.first == Stage.TIMETABLE_FETCH }?.second?.let {
                active.decrementAndGet()
                throw it
            }
            active.decrementAndGet()
            return UstcPortalPage("timetable", "test://timetable")
        }
    }

    private class FakeSelectionParser(
        private val result: List<UstcCourseSummary>,
        private val failure: Pair<Stage, Throwable>?,
        private val events: MutableList<String>,
    ) : CourseSelectionPageParser {
        var calls = 0
        override fun parse(page: UstcPortalPage): List<UstcCourseSummary> {
            calls++
            events += "parse-selection"
            failure?.takeIf { it.first == Stage.SELECTION_PARSE }?.second?.let { throw it }
            return result
        }
    }

    private class FakeTimetableParser(
        private val result: List<UstcTimetableEntry>,
        private val events: MutableList<String>,
    ) : TimetablePageParser {
        var calls = 0
        override fun parse(page: UstcPortalPage): List<UstcTimetableEntry> {
            calls++
            events += "parse-timetable"
            return result
        }
    }

    private class FakeMetaParser(
        private val totalWeeks: Int?,
        private val events: MutableList<String>,
    ) : SemesterMetaParser {
        var calls = 0
        override fun parse(selection: UstcPortalPage, timetable: UstcPortalPage): SemesterMetaResult {
            calls++
            events += "parse-meta"
            return SemesterMetaResult(
                UstcSemesterMetaPartial(
                    displayName = "ignored portal display name",
                    academicYear = "2099-2100",
                    term = Term.SPRING,
                    week1Start = LocalDate.of(2099, 1, 5),
                    totalWeeks = totalWeeks,
                    startDate = LocalDate.of(2099, 1, 1),
                    endDate = LocalDate.of(2099, 6, 1),
                ),
                isConfident = true,
            )
        }
    }

    private companion object {
        fun courseSummary(credits: Double?, courseType: String?) = UstcCourseSummary(
            courseCode = "C1",
            name = "课程A",
            credits = credits,
            department = "院系",
            courseType = courseType,
            teacherSummary = "教师甲",
            weeksText = "1-4",
        )

        fun timetableEntry(location: String, weeks: String) = UstcTimetableEntry(
            courseName = "课程A",
            courseCode = "C1",
            weekdayText = "周一",
            periodText = "1-2",
            weekText = weeks,
            locationText = location,
            teacherText = "教师甲",
        )
    }
}
