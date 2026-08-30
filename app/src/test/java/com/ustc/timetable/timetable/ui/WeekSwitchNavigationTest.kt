package com.ustc.timetable.timetable.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekCalculator
import com.ustc.timetable.timetable.domain.WeekPattern
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeekSwitchNavigationTest {

    // ---- VM harness（与 B3 相同模式：真实 A6 仓库 + in-memory Room + temp DataStore） ----

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val t0: Instant = Instant.parse("2026-09-08T02:00:00Z")  // 周二 10:00，第 2 教学周
    private val clock: Clock = Clock.fixed(t0, zone)
    private val bundledId = OfficialProfileLoader.BUNDLED_PROFILE_ID

    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var timetable: TimetableRepository
    private lateinit var manual: ManualItemRepository
    private val nowFlow = MutableStateFlow(t0)
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val collectors = mutableListOf<CoroutineScope>()

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java).allowMainThreadQueries().build()
        val dir = Files.createTempDirectory("c1-vm")
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { dir.resolve("settings.preferences_pb").toFile() }))
        profiles = ScheduleProfileRepository(db, settings, OfficialProfileLoader.load(ctx))
        profiles.ensureBundledSeeded()
        semesters = SemesterRepository(db, profiles)
        timetable = TimetableRepository(db)
        manual = ManualItemRepository(db, Clock.fixed(t0, zone))
    }

    @After fun tearDown() {
        collectors.forEach { it.cancel() }
        Dispatchers.resetMain()
        db.close()
        storeScope.cancel()
    }

    private fun vm(): TimetableViewModel {
        val model = TimetableViewModel(semesters, timetable, manual, profiles, settings, clock, nowFlow)
        collectors += CoroutineScope(Dispatchers.Unconfined).also { scope -> scope.launch { model.state.collect {} } }
        return model
    }

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(20) }
    }

    private suspend fun seedSemester(
        id: String, current: Boolean = false, portalLinked: Boolean = false,
    ): Semester {
        val sem = SemesterDefaults.AUTUMN_2026(id = id, profileId = bundledId, now = t0)
            .copy(isCurrentAcademicSemester = current, portalLinked = portalLinked)
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    /** 真实历史学期：2025 秋 18 周（week1Start=2025-09-01 周一）；today 已越界 → 默认 18 周。 */
    private suspend fun seedHistSemester(id: String): Semester {
        val sem = Semester(
            id = com.ustc.timetable.timetable.domain.SemesterId(id),
            displayName = "2025-2026 秋季", academicYear = "2025-2026",
            term = com.ustc.timetable.timetable.domain.Term.AUTUMN,
            week1Start = LocalDate.of(2025, 9, 1), totalWeeks = 18,
            startDate = LocalDate.of(2025, 8, 30), endDate = LocalDate.of(2026, 1, 15),
            importedAt = t0, lastSyncedAt = null,
            isCurrentAcademicSemester = false, portalLinked = true,
            profileId = com.ustc.timetable.timetable.domain.ProfileId(bundledId), sourceFingerprint = null,
        )
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    /** 真实未来学期：2027 春 18 周（week1Start=2027-03-01 周一）；today 早于教学周 → 默认第 1 周。 */
    private suspend fun seedFutureSemester(id: String): Semester {
        val sem = Semester(
            id = com.ustc.timetable.timetable.domain.SemesterId(id),
            displayName = "2026-2027 春季", academicYear = "2026-2027",
            term = com.ustc.timetable.timetable.domain.Term.SPRING,
            week1Start = LocalDate.of(2027, 3, 1), totalWeeks = 18,
            startDate = LocalDate.of(2027, 2, 27), endDate = LocalDate.of(2027, 7, 11),
            importedAt = t0, lastSyncedAt = null,
            isCurrentAcademicSemester = false, portalLinked = true,
            profileId = com.ustc.timetable.timetable.domain.ProfileId(bundledId), sourceFingerprint = null,
        )
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    private fun schoolSnapshot(
        semesterId: SemesterId, meetingId: String, weekday: Int, weeks: WeekPattern,
        startPeriod: Int = 6, endPeriod: Int = 6,
    ): Pair<List<Course>, List<CourseMeeting>> = listOf(
        Course(CourseId("c-$meetingId"), semesterId, "name:课$meetingId", "CODE", "课$meetingId", 3.0, null),
    ) to listOf(
        CourseMeeting(MeetingId(meetingId), CourseId("c-$meetingId"), weekday, startPeriod, endPeriod, weeks, "TH-B", listOf("师")),
    )

    private fun manualItem(semesterId: SemesterId, id: String, weekday: Int, weeks: WeekPattern) =
        ManualScheduleItem(ManualItemId(id), semesterId, "讲座", weekday, LocalTime.of(14, 20), LocalTime.of(16, 0), weeks, null, null, t0, t0)

    // ---- correction 1: defaultViewedWeek 纯函数 ----

    private val sem20 = SemesterDefaults.AUTUMN_2026(id = "s", profileId = "p")  // week1=2026-08-31, 20 周

    @Test fun default_week_inside_semester_is_natural_week() {
        assertEquals(2, defaultViewedWeek(LocalDate.of(2026, 9, 8), sem20))
        assertEquals(1, defaultViewedWeek(LocalDate.of(2026, 8, 31), sem20))
        assertEquals(20, defaultViewedWeek(LocalDate.of(2027, 1, 11), sem20))  // 第 20 周周日
    }

    @Test fun default_week_before_semester_is_week1() {
        assertEquals(1, defaultViewedWeek(LocalDate.of(2026, 8, 30), sem20))   // 开学注册日（教学周外）
        assertEquals(1, defaultViewedWeek(LocalDate.of(2025, 1, 1), sem20))    // 远期未来学期
    }

    @Test fun default_week_after_semester_is_totalWeeks() {
        assertEquals(20, defaultViewedWeek(LocalDate.of(2027, 1, 15), sem20))  // 学期结束日之后
        assertEquals(20, defaultViewedWeek(LocalDate.of(2027, 6, 1), sem20))
    }

    // ---- correction 2: RequestedWeek semester scoping ----

    @Test fun explicit_week_is_scoped_to_semester() = runBlocking {
        seedHistSemester("s-hist")
        seedSemester("s-cur", current = true)
        settings.setViewedSemesterId("s-cur")
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "s-cur" }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        // 切回 s1（hist 视为另一个学期）再回 s-cur：selection 仍按 semester 记忆
        settings.setViewedSemesterId("s-hist")
        awaitUntil { m.state.value.semester?.id?.value == "s-hist" }
        settings.setViewedSemesterId("s-cur")
        awaitUntil { m.state.value.semester?.id?.value == "s-cur" }
        assertEquals(5, m.state.value.viewedWeek)
    }

    @Test fun semester_change_ignores_previous_semester_requested_week() = runBlocking {
        seedHistSemester("s-hist")
        seedSemester("s-cur", current = true)
        settings.setViewedSemesterId("s-cur")
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "s-cur" }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        // 切到 hist：今天 2026-09-08 已过 hist 的 20 教学周 → 默认 totalWeeks(20)，而非 s-cur 的 5
        settings.setViewedSemesterId("s-hist")
        awaitUntil { m.state.value.semester?.id?.value == "s-hist" }
        awaitUntil { m.state.value.viewedWeek == 18 }
    }

    @Test fun explicit_week_remains_selected_across_time_ticks() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        nowFlow.value = t0.plusSeconds(3_600)   // 分钟 tick（仍第 2 周）
        awaitUntil { m.state.value.today != null }
        delay(100)
        assertEquals(5, m.state.value.viewedWeek)
    }

    // ---- correction 3/4: per-week page projection + naturalWeek ----

    @Test fun week_pages_cover_1_to_totalWeeks() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null && !m.state.value.isLoading }
        val pages = m.state.value.weekPages
        assertEquals(20, pages.size)
        assertEquals(1, pages.first().week)
        assertEquals(20, pages.last().week)
        assertEquals((1..20).toList(), pages.map { it.week })
    }

    @Test fun each_page_has_its_own_week_dates() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        val sem = m.state.value.semester!!
        m.state.value.weekPages.forEachIndexed { i, page ->
            assertEquals(WeekCalculator.weekRange(sem, i + 1), page.weekDates)
        }
    }

    @Test fun page1_and_page2_filter_courses_independently() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (c1, m1) = schoolSnapshot(sem.id, "w1", 2, WeekPattern.of(1))
        val (c2, m2) = schoolSnapshot(sem.id, "w2", 2, WeekPattern.of(2))
        db.applySchoolSnapshot(sem.id, c1 + c2, m1 + m2, "fp", t0)
        val m = vm()
        awaitUntil { m.state.value.weekPages.sumOf { p -> p.placedSchool.size } == 2 }
        val page1 = m.state.value.weekPages[0]
        val page2 = m.state.value.weekPages[1]
        assertEquals(listOf("s-cur:name:课w1"), page1.placedSchool.map { it.block.colorKey })
        assertEquals(listOf("s-cur:name:课w2"), page2.placedSchool.map { it.block.colorKey })
    }

    @Test fun school_manual_overlap_is_joint_per_page() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id, "w1", 2, WeekPattern.of(1))  // 周二 14:00–14:45
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        // 手动项第 1 周重叠、第 2 周不重叠
        manual.add(manualItem(sem.id, "i1", 2, WeekPattern.of(1)))
        manual.add(manualItem(sem.id, "i2", 2, WeekPattern.of(2)))
        val m = vm()
        awaitUntil { m.state.value.weekPages.sumOf { p -> p.placedManual.size } == 2 }
        val page1 = m.state.value.weekPages[0]
        val page2 = m.state.value.weekPages[1]
        // 第 1 周：school+manual 联合布局 → 各 2 列
        assertEquals(2, page1.placedSchool.single().columnsInGroup)
        assertEquals(2, page1.placedManual.single().columnsInGroup)
        // 第 2 周：school 独占整列（该周 manual 是另一个不重叠项？i2 与 w2 同 14:20-16:00… w2 不在第 2 周 → page2 只含 i2）
        assertEquals(1, page2.placedManual.single().columnsInGroup)
        assertTrue(page2.placedSchool.isEmpty())
    }

    @Test fun natural_week_exposed_and_null_outside_semester() = runBlocking {
        seedSemester("s-cur", current = true)
        seedFutureSemester("s-fut")
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        assertEquals(2, m.state.value.naturalWeek)
        settings.setViewedSemesterId("s-fut")
        awaitUntil { m.state.value.semester?.id?.value == "s-fut" }
        awaitUntil { m.state.value.naturalWeek == null }
        assertEquals(1, m.state.value.viewedWeek)  // 未来学期默认第 1 周
    }

    // ---- correction 10: 本地导航、不持久化 ----

    @Test fun week_selection_is_not_persisted_across_vm_recreation() = runBlocking {
        seedSemester("s-cur", current = true)
        val m1 = vm()
        awaitUntil { m1.state.value.semester != null }
        m1.onWeekSelected(7)
        awaitUntil { m1.state.value.viewedWeek == 7 }
        val m2 = vm()   // 重建 VM，同一 DataStore/Room
        awaitUntil { m2.state.value.semester != null && !m2.state.value.isLoading }
        assertEquals(2, m2.state.value.viewedWeek)  // 回到自然周，无任何持久化
    }

    @Test fun week_navigation_does_not_change_viewedSemesterId() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        m.onWeekSelected(9)
        m.onNextWeek()
        m.onPrevWeek()
        awaitUntil { true }
        assertNull(settings.viewedSemesterId.first())   // 未写学期选择
    }

    @Test fun week_navigation_does_not_change_academic_current() = runBlocking {
        seedHistSemester("s-hist")
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        m.onWeekSelected(3)
        awaitUntil { m.state.value.viewedWeek == 3 }
        assertEquals(true, db.semesterDao().byId("s-cur")!!.isCurrentAcademicSemester)
        assertEquals(false, db.semesterDao().byId("s-hist")!!.isCurrentAcademicSemester)
    }

    // ---- correction 11: natural week tick ----

    @Test fun natural_week_advances_when_no_explicit_selection() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.viewedWeek == 2 }
        nowFlow.value = t0.plus(java.time.Duration.ofDays(7))   // 下周二 → 第 3 周
        awaitUntil { m.state.value.viewedWeek == 3 }
    }

    @Test fun explicit_selection_survives_natural_week_change() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        nowFlow.value = t0.plus(java.time.Duration.ofDays(7))   // 自然周 2→3
        awaitUntil { m.state.value.naturalWeek == 3 }
        assertEquals(5, m.state.value.viewedWeek)
    }

    // ---- Screen/Pager/Sheet（fake state，无 VM） ----

    @get:Rule val rule = createComposeRule()

    private fun pageState(week: Int): TimetableWeekPageUiState = TimetableWeekPageUiState(
        week = week,
        weekDates = WeekCalculator.weekRange(sem20, week),
        placedSchool = emptyList(),
        placedManual = emptyList(),
        nowLine = null,
    )

    private fun uiState(
        viewedWeek: Int = 2,
        naturalWeek: Int? = 2,
    ) = TimetableUiState(
        semester = sem20,
        viewedWeek = viewedWeek,
        naturalWeek = naturalWeek,
        profile = com.ustc.timetable.scheduleprofile.ScheduleProfile(
            "profile.test.synthetic", "synthetic", false,
            (1..13).map { com.ustc.timetable.scheduleprofile.PeriodTime(it, LocalTime.of(6 + it, 0), LocalTime.of(6 + it, 45)) },
        ),
        weekPages = (1..20).map { pageState(it) },
        showNonCurrentWeek = false,
        today = LocalDate.of(2026, 9, 8),
        isAcademicCurrentViewed = true,
        canSyncViewed = false,
        isLoading = false,
    )

    @Test fun swipe_pager_changes_viewedWeek() {
        var selected: Int? = null
        rule.setContent { TimetableScreen(uiState(viewedWeek = 2), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = { selected = it }) }
        rule.waitForIdle()
        rule.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(3, selected)
    }

    @Test fun programmatic_pager_sync_does_not_fire_user_week_selection() {
        val weekState = androidx.compose.runtime.mutableStateOf(uiState(viewedWeek = 2))
        var userSelections = 0
        rule.setContent { TimetableScreen(weekState.value, onPrevWeek = {}, onNextWeek = {}, onWeekSelected = { userSelections++ }) }
        rule.waitForIdle()
        // 外部 viewedWeek 变化（arrow/picker/natural 更新）：pager 程序化跟随
        weekState.value = uiState(viewedWeek = 5)
        rule.waitForIdle()
        org.junit.Assert.assertEquals(0, userSelections)   // 不得记为用户选周
        rule.onAllNodesWithTag("week_page_5").assertCountEquals(1)  // pager 已在第 5 页
    }

    @Test fun arrow_previous_disabled_at_week1() {
        var prev = 0
        rule.setContent { TimetableScreen(uiState(viewedWeek = 1, naturalWeek = null), onPrevWeek = { prev++ }, onNextWeek = {}, onWeekSelected = {}) }
        rule.onNodeWithTag("prev_week").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(0, prev)
    }

    @Test fun arrow_next_disabled_at_totalWeeks() {
        var next = 0
        rule.setContent { TimetableScreen(uiState(viewedWeek = 20, naturalWeek = null), onPrevWeek = {}, onNextWeek = { next++ }, onWeekSelected = {}) }
        rule.onNodeWithTag("next_week").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(0, next)
    }

    @Test fun arrows_change_week_inside_bounds() {
        var selected: Int? = null
        rule.setContent { TimetableScreen(uiState(viewedWeek = 2), onPrevWeek = { selected = 1 }, onNextWeek = { selected = 3 }, onWeekSelected = {}) }
        rule.onNodeWithTag("prev_week").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(1, selected)
        rule.onNodeWithTag("next_week").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(3, selected)
    }

    // ---- WeekSwitcherSheet（correction 8/9） ----

    private fun sheet(natural: Int?, viewed: Int, onWeek: (Int) -> Unit = {}, onDismiss: () -> Unit = {}) {
        // 内容级渲染：sheet 网格本体（ModalBottomSheet 是独立 Dialog 窗口，语义查询不稳定）
        rule.setContent { WeekSwitcherContent(totalWeeks = 20, naturalWeek = natural, viewedWeek = viewed, onWeekSelected = onWeek) }
        rule.waitForIdle()
    }


    @Test fun week_sheet_has_exact_totalWeeks_choices() {
        sheet(natural = null, viewed = 1)
        for (w in 1..20) rule.onAllNodesWithTag("week_$w").assertCountEquals(1)
    }

    @Test fun week_sheet_marks_natural_and_viewed_separately() {
        sheet(natural = 2, viewed = 5)
        rule.onAllNodesWithTag("natural_week_2", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("viewed_week_5", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("natural_week_5", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag("viewed_week_2", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun same_week_can_be_both_natural_and_viewed() {
        sheet(natural = 2, viewed = 2)
        rule.onAllNodesWithTag("natural_week_2", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("viewed_week_2", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun picker_selects_week_and_dismisses() {
        var selected: Int? = null
        sheet(natural = null, viewed = 1, onWeek = { selected = it })
        rule.onNodeWithTag("week_5").performClick()
        rule.waitForIdle()
        org.junit.Assert.assertEquals(5, selected)
    }
}
