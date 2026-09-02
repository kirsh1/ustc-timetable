package com.ustc.timetable.timetable.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.TimetableRepository
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
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShowNonCurrentWeekTest {

    // 2026-09-08 10:00 Asia/Shanghai：AUTUMN_2026 的自然教学周为第 2 周。
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-08T02:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val bundledId = OfficialProfileLoader.BUNDLED_PROFILE_ID
    private val nowFlow = MutableStateFlow(now)
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val collectorScopes = mutableListOf<CoroutineScope>()

    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var timetable: TimetableRepository
    private lateinit var manual: ManualItemRepository

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val directory = Files.createTempDirectory("c4-show-non-current")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = storeScope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() },
            ),
        )
        profiles = ScheduleProfileRepository(db, settings, OfficialProfileLoader.load(context))
        profiles.ensureBundledSeeded()
        semesters = SemesterRepository(db, profiles)
        timetable = TimetableRepository(db)
        manual = ManualItemRepository(db, clock)
    }

    @After fun tearDown() {
        collectorScopes.forEach { it.cancel() }
        Dispatchers.resetMain()
        db.close()
        storeScope.cancel()
    }

    private fun newViewModel(): TimetableViewModel = TimetableViewModel(
        semestersRepo = semesters,
        timetableRepo = timetable,
        manualRepo = manual,
        profilesRepo = profiles,
        settings = settings,
        clock = clock,
        nowTicks = nowFlow,
    )

    private data class RunningViewModel(
        val model: TimetableViewModel,
        val collectorScope: CoroutineScope,
    )

    private fun runningViewModel(): RunningViewModel {
        val model = newViewModel()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        scope.launch { model.state.collect {} }
        collectorScopes += scope
        return RunningViewModel(model, scope)
    }

    private suspend fun awaitUntil(
        timeoutMs: Long = 5_000,
        condition: suspend () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!condition()) delay(20)
        }
    }

    private suspend fun seedSemester(id: String, current: Boolean = false): Semester {
        val semester = SemesterDefaults.AUTUMN_2026(id = id, profileId = bundledId, now = now)
            .copy(isCurrentAcademicSemester = current)
        db.semesterDao().insert(Mappers.toEntity(semester))
        return semester
    }

    private suspend fun seedSchool(
        semesterId: SemesterId,
        meetingId: String = "school",
        weeks: WeekPattern,
        weekday: Int = 2,
        startPeriod: Int = 6,
        endPeriod: Int = 6,
    ) {
        val courseId = CourseId("course-$meetingId")
        val courses = listOf(
            Course(courseId, semesterId, "name:$meetingId", "CODE-$meetingId", meetingId, 3.0, null),
        )
        val meetings = listOf(
            CourseMeeting(
                MeetingId(meetingId), courseId, weekday, startPeriod, endPeriod,
                weeks, "TH-B", listOf("教师"),
            ),
        )
        db.applySchoolSnapshot(semesterId, courses, meetings, "fp-$meetingId", now)
    }

    private suspend fun seedManual(
        semesterId: SemesterId,
        itemId: String = "manual",
        weeks: WeekPattern,
        weekday: Int = 2,
        start: LocalTime = LocalTime.of(14, 20),
        end: LocalTime = LocalTime.of(16, 0),
    ) {
        manual.add(
            ManualScheduleItem(
                ManualItemId(itemId), semesterId, itemId, weekday, start, end,
                weeks, null, null, now, now,
            ),
        )
    }

    private suspend fun awaitUsable(model: TimetableViewModel): TimetableUiState {
        awaitUntil { !model.state.value.isLoading && model.state.value.semester != null }
        return model.state.value
    }

    private data class DatabaseSnapshot(
        val semesters: List<SemesterEntity>,
        val courses: List<CourseEntity>,
        val meetings: List<CourseMeetingEntity>,
        val manualItems: List<ManualItemEntity>,
        val profiles: List<ScheduleProfileEntity>,
    )

    private suspend fun databaseSnapshot(semesterId: String): DatabaseSnapshot = DatabaseSnapshot(
        semesters = db.semesterDao().allByStartDateDesc(),
        courses = db.courseDao().coursesForSemester(semesterId),
        meetings = db.courseDao().meetingsForSemester(semesterId),
        manualItems = db.manualItemDao().itemsForSemester(semesterId),
        profiles = db.scheduleProfileDao().all(),
    )

    @Test fun default_setting_is_false() = runBlocking {
        seedSemester("A", current = true)
        val model = runningViewModel().model
        val state = awaitUsable(model)
        assertFalse(settings.showNonCurrentWeek.first())
        assertFalse(state.showNonCurrentWeek)
    }

    @Test fun toggle_true_updates_state() = runBlocking {
        seedSemester("A", current = true)
        val model = runningViewModel().model
        awaitUsable(model)
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        assertTrue(settings.showNonCurrentWeek.first())
    }

    @Test fun toggle_false_updates_state() = runBlocking {
        settings.setShowNonCurrentWeek(true)
        seedSemester("A", current = true)
        val model = runningViewModel().model
        awaitUntil { model.state.value.showNonCurrentWeek }
        model.onToggleShowNonCurrentWeek(false)
        awaitUntil { !model.state.value.showNonCurrentWeek }
        assertFalse(settings.showNonCurrentWeek.first())
    }

    @Test fun toggle_true_persists_across_vm_recreation() = runBlocking {
        seedSemester("A", current = true)
        val first = runningViewModel()
        awaitUsable(first.model)
        first.model.onToggleShowNonCurrentWeek(true)
        awaitUntil { settings.showNonCurrentWeek.first() }
        first.collectorScope.cancel()

        val recreated = runningViewModel().model
        awaitUntil { !recreated.state.value.isLoading && recreated.state.value.showNonCurrentWeek }
        assertTrue(recreated.state.value.showNonCurrentWeek)
    }

    @Test fun toggle_false_persists_across_vm_recreation() = runBlocking {
        settings.setShowNonCurrentWeek(true)
        seedSemester("A", current = true)
        val first = runningViewModel()
        awaitUntil { first.model.state.value.showNonCurrentWeek }
        first.model.onToggleShowNonCurrentWeek(false)
        awaitUntil { !settings.showNonCurrentWeek.first() }
        first.collectorScope.cancel()

        val recreated = runningViewModel().model
        val recreatedState = awaitUsable(recreated)
        assertFalse(recreatedState.showNonCurrentWeek)
    }

    @Test fun persisted_true_applies_to_first_nonloading_timetable_state() = runBlocking {
        settings.setShowNonCurrentWeek(true)
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "ghost", weeks = WeekPattern.of(3))
        val model = newViewModel()

        val firstUsable = withTimeout(5_000) {
            model.state.filter { !it.isLoading && it.semester != null }.first()
        }
        assertTrue(firstUsable.showNonCurrentWeek)
        assertEquals(listOf("ghost"), firstUsable.placedSchool.map { it.block.meetingId!!.value })
    }

    @Test fun filtering_uses_page_week_not_natural_week() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "natural-active", weeks = WeekPattern.range(2, 6))
        val model = runningViewModel().model
        awaitUsable(model)
        assertEquals(2, model.state.value.naturalWeek)
        model.onWeekSelected(10)
        awaitUntil { model.state.value.viewedWeek == 10 }
        assertTrue(model.state.value.placedSchool.isEmpty())

        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.placedSchool.isNotEmpty() }
        assertEquals("natural-active", model.state.value.placedSchool.single().block.meetingId!!.value)
    }

    @Test fun toggle_true_reveals_noncurrent_school_block() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "school-ghost", weeks = WeekPattern.of(3))
        val model = runningViewModel().model
        awaitUsable(model)
        assertTrue(model.state.value.placedSchool.isEmpty())
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.placedSchool.isNotEmpty() }
        assertEquals("school-ghost", model.state.value.placedSchool.single().block.meetingId!!.value)
    }

    @Test fun toggle_true_does_not_add_ghosts_to_week_overview() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "school-ghost", weeks = WeekPattern.of(3))
        val model = runningViewModel().model
        awaitUsable(model)
        assertTrue(model.state.value.weekOverviewPages[1].placedBlocks.isEmpty())

        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek && model.state.value.placedSchool.isNotEmpty() }

        assertTrue(model.state.value.weekOverviewPages[1].placedBlocks.isEmpty())
        assertEquals("school-ghost", model.state.value.weekOverviewPages[2].placedBlocks.single().block.meetingId!!.value)
    }

    @Test fun toggle_true_reveals_noncurrent_manual_block() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedManual(semester.id, itemId = "manual-ghost", weeks = WeekPattern.of(3))
        val model = runningViewModel().model
        awaitUsable(model)
        assertTrue(model.state.value.placedManual.isEmpty())
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.placedManual.isNotEmpty() }
        assertEquals("manual-ghost", model.state.value.placedManual.single().block.manualItemId!!.value)
    }

    @Test fun toggle_false_hides_both_again() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "school-ghost", weeks = WeekPattern.of(3))
        seedManual(semester.id, itemId = "manual-ghost", weeks = WeekPattern.of(3))
        val model = runningViewModel().model
        awaitUsable(model)
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.placedSchool.isNotEmpty() && model.state.value.placedManual.isNotEmpty() }
        model.onToggleShowNonCurrentWeek(false)
        awaitUntil { model.state.value.placedSchool.isEmpty() && model.state.value.placedManual.isEmpty() }
        assertFalse(model.state.value.showNonCurrentWeek)
    }

    @Test fun visible_noncurrent_block_does_consume_overlap_column() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "active", weeks = WeekPattern.of(2))
        seedManual(semester.id, itemId = "ghost", weeks = WeekPattern.of(3))
        val model = runningViewModel().model
        awaitUntil { model.state.value.placedSchool.isNotEmpty() }
        assertEquals(1, model.state.value.placedSchool.single().columnsInGroup)

        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.placedManual.isNotEmpty() }
        assertEquals(2, model.state.value.placedSchool.single().columnsInGroup)
        assertEquals(2, model.state.value.placedManual.single().columnsInGroup)
    }

    @Test fun toggle_does_not_change_viewed_week() = runBlocking {
        seedSemester("A", current = true)
        val model = runningViewModel().model
        awaitUsable(model)
        model.onWeekSelected(5)
        awaitUntil { model.state.value.viewedWeek == 5 }
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        assertEquals(5, model.state.value.viewedWeek)
    }

    @Test fun toggle_does_not_change_viewed_semester() = runBlocking {
        seedSemester("A", current = true)
        seedSemester("B")
        val model = runningViewModel().model
        awaitUntil { model.state.value.semester?.id == SemesterId("A") }
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        assertEquals(SemesterId("A"), model.state.value.semester!!.id)
    }

    @Test fun toggle_does_not_reset_requested_week() = runBlocking {
        seedSemester("A", current = true)
        val model = runningViewModel().model
        awaitUsable(model)
        model.onWeekSelected(5)
        awaitUntil { model.state.value.viewedWeek == 5 }
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        model.onToggleShowNonCurrentWeek(false)
        awaitUntil { !model.state.value.showNonCurrentWeek }
        assertEquals(5, model.state.value.viewedWeek)
    }

    @Test fun toggle_value_survives_semester_switch() = runBlocking {
        seedSemester("A", current = true)
        seedSemester("B")
        val model = runningViewModel().model
        awaitUntil { model.state.value.semester?.id == SemesterId("A") }
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        model.onSemesterSelected(SemesterId("B"))
        awaitUntil { model.state.value.semester?.id == SemesterId("B") }
        assertTrue(model.state.value.showNonCurrentWeek)
        assertTrue(settings.showNonCurrentWeek.first())
    }

    @Test fun toggle_does_not_mutate_timetable_database() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "school", weeks = WeekPattern.of(2))
        seedManual(semester.id, itemId = "manual", weeks = WeekPattern.of(2))
        val before = databaseSnapshot("A")
        val model = runningViewModel().model
        awaitUsable(model)
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        assertEquals(before, databaseSnapshot("A"))
    }

    @Test fun toggle_does_not_change_course_detail_projection() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "hidden-detail", weeks = WeekPattern.of(3))
        val model = runningViewModel().model
        awaitUntil { model.state.value.courseDetailsByMeetingId.isNotEmpty() }
        assertTrue(model.state.value.placedSchool.isEmpty())
        val before = model.state.value.courseDetailsByMeetingId
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek && model.state.value.placedSchool.isNotEmpty() }
        assertEquals(before, model.state.value.courseDetailsByMeetingId)
        assertEquals(SemesterId("A"), model.state.value.semester!!.id)
    }

    @Test fun toggle_reprojects_without_returning_to_loading() = runBlocking {
        val semester = seedSemester("A", current = true)
        seedSchool(semester.id, meetingId = "ghost", weeks = WeekPattern.of(3))
        val running = runningViewModel()
        awaitUsable(running.model)
        val observedLoadingValues = mutableListOf<Boolean>()
        val observer = CoroutineScope(Dispatchers.Unconfined)
        collectorScopes += observer
        observer.launch { running.model.state.collect { observedLoadingValues += it.isLoading } }
        observedLoadingValues.clear()

        running.model.onToggleShowNonCurrentWeek(true)
        awaitUntil { running.model.state.value.showNonCurrentWeek && running.model.state.value.placedSchool.isNotEmpty() }
        delay(50)
        assertTrue(observedLoadingValues.isNotEmpty())
        assertFalse(observedLoadingValues.any { it })
    }
}
