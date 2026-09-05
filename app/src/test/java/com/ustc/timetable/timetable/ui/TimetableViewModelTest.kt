package com.ustc.timetable.timetable.ui

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
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
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableViewModelTest {

    // 2026-09-08 10:00 Asia/Shanghai（周二，秋季第 2 教学周）
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val t0: Instant = Instant.parse("2026-09-08T02:00:00Z")
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

    /** 合成 13 节 profile（第 i 节 = (6+i):00 起 45 分钟），非官方时间；验证绑定 profile 为换算唯一权威。 */
    private fun syntheticProfile(): ScheduleProfile =
        ScheduleProfile(
            "profile.test.synthetic", "synthetic", false,
            (1..13).map { PeriodTime(it, LocalTime.of(6 + it, 0), LocalTime.of(6 + it, 45)) },
        )

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java).allowMainThreadQueries().build()
        val dir = Files.createTempDirectory("b3-vm")
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = storeScope, produceFile = { dir.resolve("settings.preferences_pb").toFile() }))
        profiles = ScheduleProfileRepository(db, settings, OfficialProfileLoader.load(ctx))
        profiles.ensureBundledSeeded()   // 真实 13 节 bundled 行（"[]" 会让 toProfile 的 13 节校验炸掉观察流）
        semesters = SemesterRepository(db, profiles)
        timetable = TimetableRepository(db)
        manual = ManualItemRepository(db, Clock.fixed(t0, zone))
    }

    @After fun tearDown() {
        collectors.forEach { it.cancel() }
        kotlinx.coroutines.Dispatchers.resetMain()
        db.close()
        storeScope.cancel()
    }

    // ---- helpers ----

    /** WhileSubscribed 需要 collector 驱动；collector scope 在 tearDown 统一取消。 */
    private fun vm(): TimetableViewModel {
        val model = TimetableViewModel(semesters, timetable, manual, profiles, settings, clock, nowFlow)
        collectors += CoroutineScope(Dispatchers.Unconfined).also { scope -> scope.launch { model.state.collect {} } }
        return model
    }

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, cond: () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(20) }
    }

    private suspend fun seedSemester(
        id: String,
        current: Boolean = false,
        portalLinked: Boolean = false,
        start: LocalDate = LocalDate.of(2026, 8, 30),
    ): Semester {
        val sem = SemesterDefaults.AUTUMN_2026(id = id, profileId = bundledId, now = t0)
            .copy(isCurrentAcademicSemester = current, portalLinked = portalLinked, startDate = start)
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    private fun schoolSnapshot(
        semesterId: SemesterId,
        meetingId: String = "m1", weekday: Int = 5, startPeriod: Int = 3, endPeriod: Int = 5,
        weeks: WeekPattern = WeekPattern.range(1, 20), teacher: String = "刘斯",
        key: String = "name:高等无机化学", courseId: String = "c1",
    ): Pair<List<Course>, List<CourseMeeting>> = listOf(
        Course(CourseId(courseId), semesterId, key, "CHEM5013P", "高等无机化学", 3.0, null),
    ) to listOf(
        CourseMeeting(MeetingId(meetingId), CourseId(courseId), weekday, startPeriod, endPeriod, weeks, "TH-B301", listOf(teacher)),
    )

    private fun manualItem(
        semesterId: SemesterId, id: String = "i1", weekday: Int = 6,
        start: LocalTime = LocalTime.of(14, 20), end: LocalTime = LocalTime.of(16, 0),
        weeks: WeekPattern = WeekPattern.of(2),
    ) = ManualScheduleItem(ManualItemId(id), semesterId, "讲座", weekday, start, end, weeks, null, null, t0, t0)

    // ---- weekFilter 纯函数 ----

    @Test fun applied_palette_survives_recreation_and_week_navigation_without_changing_geometry() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        assertEquals(0L, m.state.value.coursePaletteSeed)
        val pages = m.state.value.weekPages
        settings.setCoursePaletteSeed(13L)
        awaitUntil { m.state.value.coursePaletteSeed == 13L }
        assertEquals(pages, m.state.value.weekPages)
        m.onWeekSelected(3)
        awaitUntil { m.state.value.viewedWeek == 3 }
        assertEquals(13L, m.state.value.coursePaletteSeed)
        val recreated = vm()
        awaitUntil { recreated.state.value.semester != null }
        assertEquals(13L, recreated.state.value.coursePaletteSeed)
        assertEquals(2, recreated.state.value.viewedWeek)
    }



    @Test fun weekFilter_uses_contains() {
        val b = fixtureBlock(weeks = WeekPattern.range(3, 5))
        assertTrue(weekFilter(listOf(b), viewedWeek = 2, showNonCurrentWeek = false).isEmpty())
        assertEquals(listOf(b), weekFilter(listOf(b), viewedWeek = 4, showNonCurrentWeek = false))
    }

    @Test fun weekFilter_keeps_all_when_showNonCurrent() {
        val b = fixtureBlock(weeks = WeekPattern.of(5))
        assertEquals(listOf(b), weekFilter(listOf(b), viewedWeek = 2, showNonCurrentWeek = true))
    }

    // ---- viewedWeek ----

    @Test fun viewedWeek_clamped() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null && !m.state.value.isLoading }
        m.onWeekSelected(99)
        awaitUntil { m.state.value.viewedWeek == 20 }
        m.onWeekSelected(0)
        awaitUntil { m.state.value.viewedWeek == 1 }
    }

    @Test fun initial_week_uses_natural_week_when_available() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        assertEquals(2, m.state.value.viewedWeek)  // today 2026-09-08 → 第 2 教学周
    }

    @Test fun state_exposes_weekDates_monday_to_sunday() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        val wd = m.state.value.weekDates!!
        assertEquals(DayOfWeek.MONDAY, wd.start.dayOfWeek)
        assertEquals(6L, ChronoUnit.DAYS.between(wd.start, wd.endInclusive))
        assertEquals(WeekCalculator.weekRange(m.state.value.semester!!, 2), wd)
    }

    // ---- reactive viewed semester selection ----

    @Test fun empty_database_then_semester_insert_updates_state_without_settings_change() = runBlocking {
        val m = vm()
        awaitUntil { !m.state.value.isLoading && m.state.value.semester == null }
        seedSemester("s-late", current = true)   // 不写 viewedSemesterId、不重建 VM
        awaitUntil { m.state.value.semester?.id?.value == "s-late" }
    }

    @Test fun valid_viewed_id_wins() = runBlocking {
        seedSemester("s-hist", start = LocalDate.of(2025, 8, 25))
        seedSemester("s-cur", current = true)
        settings.setViewedSemesterId("s-hist")
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "s-hist" }
        // selection 只读：学术 current 不因浏览而改变
        assertEquals(true, db.semesterDao().byId("s-cur")!!.isCurrentAcademicSemester)
        assertEquals(false, db.semesterDao().byId("s-hist")!!.isCurrentAcademicSemester)
    }

    @Test fun invalid_viewed_id_falls_back_to_academic_current() = runBlocking {
        seedSemester("s-hist", start = LocalDate.of(2025, 8, 25))
        seedSemester("s-cur", current = true)
        settings.setViewedSemesterId("nope")
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "s-cur" }
    }

    @Test fun no_current_falls_back_to_latest() = runBlocking {
        seedSemester("s-old", start = LocalDate.of(2025, 8, 25))
        seedSemester("s-new", start = LocalDate.of(2026, 8, 30))
        settings.setViewedSemesterId("nope")
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "s-new" }
    }

    // ---- academic-current / syncable 分离 ----

    @Test fun manual_academic_current_is_current_but_not_syncable() = runBlocking {
        seedSemester("s-manual", current = true, portalLinked = false)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        assertEquals(true, m.state.value.isAcademicCurrentViewed)
        assertEquals(false, m.state.value.canSyncViewed)
    }

    @Test fun portal_linked_academic_current_is_syncable() = runBlocking {
        seedSemester("s-portal", current = true, portalLinked = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        assertEquals(true, m.state.value.isAcademicCurrentViewed)
        assertEquals(true, m.state.value.canSyncViewed)
    }

    @Test fun historical_viewed_is_not_syncable() = runBlocking {
        seedSemester("s-hist", start = LocalDate.of(2025, 8, 25), portalLinked = true)
        seedSemester("s-cur", current = true, portalLinked = true)
        settings.setViewedSemesterId("s-hist")
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "s-hist" }
        assertEquals(false, m.state.value.isAcademicCurrentViewed)
        assertEquals(false, m.state.value.canSyncViewed)
    }

    // ---- NowLinePolicy 纯函数 ----

    private val policySem = SemesterDefaults.AUTUMN_2026(id = "s", profileId = "p")
    private val policyToday = LocalDate.of(2026, 9, 8)  // week 2

    @Test fun nowline_current_semester_current_week_returns_now() {
        assertEquals(
            LocalTime.of(10, 0),
            NowLinePolicy.line(policySem, 2, WeekCalculator.weekRange(policySem, 2), policyToday, LocalTime.of(10, 0)),
        )
    }

    @Test fun nowline_history_returns_null() {
        val hist = policySem.copy(isCurrentAcademicSemester = false)
        assertNull(NowLinePolicy.line(hist, 2, WeekCalculator.weekRange(hist, 2), policyToday, LocalTime.of(10, 0)))
    }

    @Test fun nowline_other_week_returns_null() {
        assertNull(NowLinePolicy.line(policySem, 3, WeekCalculator.weekRange(policySem, 3), policyToday, LocalTime.of(10, 0)))
    }

    @Test fun nowline_today_outside_weekDates_returns_null() {
        assertNull(NowLinePolicy.line(policySem, 2, WeekCalculator.weekRange(policySem, 2), LocalDate.of(2026, 9, 5), LocalTime.of(10, 0)))
    }

    @Test fun nowline_null_semester_returns_null() {
        assertNull(NowLinePolicy.line(null, 2, null, policyToday, LocalTime.of(10, 0)))
    }

    // ---- now tick 驱动（不重建 VM） ----

    @Test fun now_tick_moves_nowline_without_recreating_vm() = runBlocking {
        seedSemester("s-cur", current = true)
        val m = vm()
        awaitUntil { m.state.value.semester != null }
        awaitUntil { m.state.value.nowLine == LocalTime.of(10, 0) }
        nowFlow.value = t0.plusSeconds(3600)   // 11:00
        awaitUntil { m.state.value.nowLine == LocalTime.of(11, 0) }
    }

    // ---- 学校块映射（绑定 profile 为时间换算唯一权威） ----

    @Test fun school_block_uses_bound_profile_for_period_conversion() = runBlocking {
        val created = semesters.createLocalSemester(
            SemesterDefaults.AUTUMN_2026(id = "s-pro", profileId = "ignored", now = t0), syntheticProfile(),
        )
        val (courses, meetings) = schoolSnapshot(created.id, startPeriod = 3, endPeriod = 4)
        db.applySchoolSnapshot(created.id, courses, meetings, "fp", t0)
        val m = vm()
        awaitUntil { m.state.value.placedSchool.isNotEmpty() }
        val block = m.state.value.placedSchool.first().block
        assertEquals(LocalTime.of(9, 0), block.start)           // 合成 profile 第 3 节开始
        assertEquals(LocalTime.of(10, 45), block.endInclusive)  // 第 4 节结束
    }

    @Test fun school_color_key_uses_semester_and_sourceCourseKey() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id, key = "name:高等无机化学")
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        val m = vm()
        awaitUntil { m.state.value.placedSchool.isNotEmpty() }
        assertEquals("s-cur:name:高等无机化学", m.state.value.placedSchool.first().block.colorKey)
    }

    @Test fun school_block_preserves_teacher_names() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id, teacher = "郭宇桥")
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        val m = vm()
        awaitUntil { m.state.value.placedSchool.isNotEmpty() }
        assertEquals(listOf("郭宇桥"), m.state.value.placedSchool.first().block.teacherNames)
    }

    // ---- 手动块映射 ----

    @Test fun manual_block_preserves_arbitrary_minutes() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        manual.add(manualItem(sem.id))
        val m = vm()
        awaitUntil { m.state.value.placedManual.isNotEmpty() }
        val block = m.state.value.placedManual.first().block
        assertEquals(LocalTime.of(14, 20), block.start)
        assertEquals(LocalTime.of(16, 0), block.endInclusive)
        assertNull(block.meetingId)
        assertNotNull(block.manualItemId)
    }

    @Test fun manual_color_key_uses_manual_id() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        manual.add(manualItem(sem.id, id = "i-42"))
        val m = vm()
        awaitUntil { m.state.value.placedManual.isNotEmpty() }
        assertEquals("manual:i-42", m.state.value.placedManual.first().block.colorKey)
    }

    @Test fun manual_items_belong_to_viewed_semester() = runBlocking {
        seedSemester("s-hist", start = LocalDate.of(2025, 8, 25))
        seedSemester("s-cur", current = true)
        val hist = db.semesterDao().byId("s-hist")!!.let(Mappers::toDomain)
        val cur = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        manual.add(manualItem(hist.id, id = "i-hist"))
        manual.add(manualItem(cur.id, id = "i-cur"))
        settings.setViewedSemesterId("s-hist")
        val m = vm()
        awaitUntil { m.state.value.placedManual.map { it.block.colorKey } == listOf("manual:i-hist") }
        settings.setViewedSemesterId("s-cur")
        awaitUntil { m.state.value.placedManual.map { it.block.colorKey } == listOf("manual:i-cur") }
    }

    // ---- 联合布局：school + manual 一次 place ----

    @Test fun school_and_manual_overlap_share_same_two_column_group() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id, weekday = 2, startPeriod = 6, endPeriod = 6)  // 官方 14:00–14:45
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        manual.add(manualItem(sem.id, weekday = 2, start = LocalTime.of(14, 20), end = LocalTime.of(16, 0), weeks = WeekPattern.range(1, 20)))
        val m = vm()
        awaitUntil { m.state.value.placedSchool.isNotEmpty() && m.state.value.placedManual.isNotEmpty() }
        val s = m.state.value.placedSchool.first()
        val man = m.state.value.placedManual.first()
        assertEquals(2, s.columnsInGroup)
        assertEquals(2, man.columnsInGroup)
        assertTrue(s.column != man.column)
    }

    @Test fun hidden_noncurrent_block_does_not_consume_overlap_column() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id, weekday = 2, startPeriod = 6, endPeriod = 6)
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        // ghost 手动项：属第 3 周（非 viewedWeek=2）且与学校课重叠
        manual.add(manualItem(sem.id, weekday = 2, start = LocalTime.of(14, 20), end = LocalTime.of(16, 0), weeks = WeekPattern.of(3)))
        settings.setShowNonCurrentWeek(false)
        val m = vm()
        awaitUntil { m.state.value.placedSchool.isNotEmpty() }
        assertEquals(1, m.state.value.placedSchool.first().columnsInGroup)  // ghost 在 place() 前被过滤 → 学校课整列
        assertTrue(m.state.value.placedManual.isEmpty())
        settings.setShowNonCurrentWeek(true)   // 开关打开后 ghost 参与联合布局（B2 以 0.35 alpha 绘制）
        awaitUntil { m.state.value.placedManual.isNotEmpty() }
        assertEquals(2, m.state.value.placedSchool.first().columnsInGroup)
        assertEquals(2, m.state.value.placedManual.first().columnsInGroup)
    }

    // ---- selectViewedSemester 边界 ----

    @Test fun selectViewedSemester_empty_returns_null() {
        assertNull(selectViewedSemester(emptyList(), null))
        assertNull(selectViewedSemester(emptyList(), "x"))
    }

    // ---- C3 projection：detail map 由未过滤 school snapshot 构造 ----

    @Test fun detail_map_resolves_every_school_meeting() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id)
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        val m = vm()
        awaitUntil { !m.state.value.isLoading }
        awaitUntil { m.state.value.courseDetailsByMeetingId.isNotEmpty() }
        assertEquals(meetings.map { it.id }, m.state.value.courseDetailsByMeetingId.keys.toList())
        meetings.forEach { mt ->
            assertEquals(mt, m.state.value.courseDetailsByMeetingId[mt.id]!!.selectedMeeting)
        }
    }

    @Test fun detail_map_includes_hidden_week_meetings() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        // 同一课程两条安排：当前周(2)可见 + 第 5 周隐藏；详情必须仍含两条。
        val (courses, visible) = schoolSnapshot(sem.id, meetingId = "vis", weeks = WeekPattern.of(2))
        val hidden = visible.single().copy(id = MeetingId("hid"), weekPattern = WeekPattern.of(5))
        db.applySchoolSnapshot(sem.id, courses, visible + hidden, "fp", t0)
        val m = vm()
        awaitUntil { m.state.value.courseDetailsByMeetingId.size == 2 }   // 隐藏周 meeting 也在 detail map
        assertEquals(1, m.state.value.placedSchool.size)                   // 但布局里只显示当前周
        assertEquals(
            listOf("vis", "hid"),
            m.state.value.courseDetailsByMeetingId[MeetingId("vis")]!!.allMeetings.map { it.id.value },
        )
    }

    @Test fun input_permutation_produces_same_detail_order() {
        val semesterId = SemesterId("s")
        val (courses, first) = schoolSnapshot(semesterId, meetingId = "m2", weeks = WeekPattern.range(7, 12))
        val courseId = courses.single().id
        val meetings = listOf(
            first.single(),
            first.single().copy(id = MeetingId("m1"), weekPattern = WeekPattern.range(2, 6)),
            first.single().copy(id = MeetingId("m3"), weekPattern = WeekPattern.range(13, 18)),
        )
        val forward = buildCourseDetailsByMeetingId(courses, meetings)
        val reversed = buildCourseDetailsByMeetingId(courses, meetings.reversed())
        val expected = listOf("m1", "m2", "m3")
        assertEquals(expected, forward[MeetingId("m2")]!!.allMeetings.map { it.id.value })
        assertEquals(expected, reversed[MeetingId("m2")]!!.allMeetings.map { it.id.value })
        assertEquals(courseId, reversed[MeetingId("m2")]!!.selectedMeeting.courseId)
    }

    @Test fun detail_map_never_contains_manual_items() = runBlocking {
        seedSemester("s-cur", current = true)
        val sem = db.semesterDao().byId("s-cur")!!.let(Mappers::toDomain)
        val (courses, meetings) = schoolSnapshot(sem.id)
        db.applySchoolSnapshot(sem.id, courses, meetings, "fp", t0)
        manual.add(manualItem(sem.id))
        val m = vm()
        awaitUntil { m.state.value.placedManual.isNotEmpty() }
        val detailIds = m.state.value.courseDetailsByMeetingId.keys.map { it.value }
        assertTrue(!detailIds.contains("i1"))   // manual id 不出现在学校 detail map
    }

    // ---- fixture ----

    /** 仅供 weekFilter 纯函数测试的最小 TimedBlock。 */
    private fun fixtureBlock(weeks: WeekPattern) = UiTimedBlock(
        colorKey = "k", meetingId = MeetingId("m"), manualItemId = null,
        weekday = 1, start = LocalTime.of(9, 0), endInclusive = LocalTime.of(10, 0),
        weeks = weeks, title = "t", location = "l", teacherNames = emptyList(),
    )
}
