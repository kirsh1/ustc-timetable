package com.ustc.timetable.semester

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.Term
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.ui.TimetableViewModel
import com.ustc.timetable.timetable.ui.sortSemesterChoices
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
import org.junit.Assert.assertNotNull
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
class SemesterSwitcherTest {

    // today = 2026-09-08（周二，A 的第 2 教学周）
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

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val ctx = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(ctx, TimetableDatabase::class.java).allowMainThreadQueries().build()
        val dir = Files.createTempDirectory("c2-vm")
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

    private suspend fun awaitUntil(timeoutMs: Long = 5_000, cond: suspend () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(20) }
    }

    /** A：academic-current、portalLinked、2026 秋 20 周（含今天）。 */
    private suspend fun seedA(current: Boolean = true, portalLinked: Boolean = true): Semester {
        val sem = SemesterDefaults.AUTUMN_2026(id = "A", profileId = bundledId, now = t0)
            .copy(isCurrentAcademicSemester = current, portalLinked = portalLinked)
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    /** B：历史学期 2025 秋 18 周（week1Start=2025-09-01 周一），today 已越界 → default 18。 */
    private suspend fun seedB(current: Boolean = false, portalLinked: Boolean = true): Semester {
        val sem = Semester(
            id = SemesterId("B"), displayName = "2025-2026 秋季", academicYear = "2025-2026", term = Term.AUTUMN,
            week1Start = LocalDate.of(2025, 9, 1), totalWeeks = 18,
            startDate = LocalDate.of(2025, 8, 30), endDate = LocalDate.of(2026, 1, 15),
            importedAt = t0, lastSyncedAt = null,
            isCurrentAcademicSemester = current, portalLinked = portalLinked,
            profileId = ProfileId(bundledId), sourceFingerprint = null,
        )
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    /** F：未来学期 2027 春 18 周（week1Start=2027-03-01 周一）→ default 1。 */
    private suspend fun seedF(): Semester {
        val sem = Semester(
            id = SemesterId("F"), displayName = "2026-2027 春季", academicYear = "2026-2027", term = Term.SPRING,
            week1Start = LocalDate.of(2027, 3, 1), totalWeeks = 18,
            startDate = LocalDate.of(2027, 2, 27), endDate = LocalDate.of(2027, 7, 11),
            importedAt = t0, lastSyncedAt = null,
            isCurrentAcademicSemester = false, portalLinked = true,
            profileId = ProfileId(bundledId), sourceFingerprint = null,
        )
        db.semesterDao().insert(Mappers.toEntity(sem))
        return sem
    }

    private fun schoolSnapshot(semesterId: SemesterId, courseKey: String, meetingId: String, weekday: Int = 5) = listOf(
        Course(CourseId("c-$meetingId"), semesterId, courseKey, "CODE", courseKey.removePrefix("name:"), 3.0, null),
    ) to listOf(
        CourseMeeting(MeetingId(meetingId), CourseId("c-$meetingId"), weekday, 6, 6, WeekPattern.range(1, 18), "TH-B", listOf("师")),
    )

    // ---- correction 4：排序纯函数 ----

    @Test fun semester_choices_sorted_by_startDate_desc() {
        val a = seedAForUi()                                       // startDate 2026-08-30
        val b = seedBForUi()                                       // startDate 2025-08-30
        val f = seedFForUi()                                       // startDate 2027-02-27
        val sorted = sortSemesterChoices(listOf(a, b, f))
        assertEquals(listOf("F", "A", "B"), sorted.map { it.id.value })
    }

    @Test fun sorting_does_not_change_academic_current_flags() {
        val sorted = sortSemesterChoices(listOf(seedAForUi(), seedBForUi()))
        assertEquals(true, sorted.first { it.id.value == "A" }.isCurrentAcademicSemester)
        assertEquals(false, sorted.first { it.id.value == "B" }.isCurrentAcademicSemester)
    }

    // ---- Sheet content（correction 5/6/7） ----

    @get:Rule val rule = createComposeRule()

    private fun sheetSemesters(): List<Semester> = listOf(
        seedAForUi(), seedBForUi(), seedFForUi(),
    )

    private fun seedAForUi(): Semester = SemesterDefaults.AUTUMN_2026(id = "A", profileId = "p")
        .copy(isCurrentAcademicSemester = true, portalLinked = true)
    private fun seedBForUi(): Semester = Semester(
        id = SemesterId("B"), displayName = "2025-2026 秋季", academicYear = "2025-2026", term = Term.AUTUMN,
        week1Start = LocalDate.of(2025, 9, 1), totalWeeks = 18,
        startDate = LocalDate.of(2025, 8, 30), endDate = LocalDate.of(2026, 1, 15),
        importedAt = t0, lastSyncedAt = null,
        isCurrentAcademicSemester = false, portalLinked = true,
        profileId = ProfileId("p"), sourceFingerprint = null,
    )
    private fun seedFForUi(): Semester = Semester(
        id = SemesterId("F"), displayName = "2026-2027 春季", academicYear = "2026-2027", term = Term.SPRING,
        week1Start = LocalDate.of(2027, 3, 1), totalWeeks = 18,
        startDate = LocalDate.of(2027, 2, 27), endDate = LocalDate.of(2027, 7, 11),
        importedAt = t0, lastSyncedAt = null,
        isCurrentAcademicSemester = false, portalLinked = false,
        profileId = ProfileId("p"), sourceFingerprint = null,
    )

    private fun content(viewed: String, onSelect: (SemesterId) -> Unit = {}) {
        rule.setContent {
            SemesterSwitcherContent(semesters = sheetSemesters(), viewedSemesterId = SemesterId(viewed), onSelect = onSelect)
        }
        rule.waitForIdle()
    }

    @Test fun sheet_marks_viewed_semester() {
        content(viewed = "B")
        rule.onAllNodesWithTag("viewed_semester:B", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("viewed_semester:A", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun sheet_marks_academic_current_independently() {
        content(viewed = "B")   // viewed=B 但 academic-current=A：两个概念分开
        rule.onAllNodesWithTag("academic_current:A", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("academic_current:B", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun sheet_marks_portal_linked() {
        content(viewed = "A")
        rule.onAllNodesWithTag("portal_linked:A", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("portal_linked:F", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun sheet_marks_local_semester() {
        content(viewed = "A")
        rule.onAllNodesWithTag("local_semester:F", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("local_semester:A", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun local_academic_current_can_have_both_current_and_local_markers() {
        val local = listOf(seedAForUi().copy(id = SemesterId("L"), portalLinked = false))
        rule.setContent { SemesterSwitcherContent(semesters = local, viewedSemesterId = SemesterId("L"), onSelect = {}) }
        rule.waitForIdle()
        rule.onAllNodesWithTag("academic_current:L", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("local_semester:L", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("semester_item:L").assertCountEquals(1)
    }

    @Test fun sheet_uses_readable_status_labels_instead_of_bare_symbols() {
        content(viewed = "A")
        rule.onAllNodesWithText("当前学期", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("学校课表", useUnmergedTree = true).assertCountEquals(2)
        rule.onAllNodesWithText("本地课表", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("●", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("校", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("本", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun sheet_list_only_contains_known_semesters() {
        content(viewed = "A")
        // 恰好 3 个本地学期 item，无“添加学期”之类的额外入口
        for (id in listOf("A", "B", "F")) rule.onAllNodesWithTag("semester_item:$id").assertCountEquals(1)
    }

    // ---- VM 行为（correction 1/2/3/13） ----

    @Test fun selecting_semester_writes_viewedSemesterId() = runBlocking {
        seedA(); seedB()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { settings.viewedSemesterId.first() == "B" }
        awaitUntil { m.state.value.semester?.id?.value == "B" }
    }

    @Test fun selecting_already_viewed_semester_does_not_reset_week() = runBlocking {
        seedA(); seedB()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        m.onSemesterSelected(SemesterId("A"))   // 再点自身
        delay(200)
        assertEquals(5, m.state.value.viewedWeek)   // 不重置
        assertEquals("A", settings.viewedSemesterId.first() ?: "A")  // 无需写（null 或仍指向 A 均可，但不能是 B）
    }

    @Test fun unknown_semester_id_is_rejected_without_state_change() = runBlocking {
        seedA(); seedB()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        m.onSemesterSelected(SemesterId("ghost"))
        delay(200)
        assertEquals("A", m.state.value.semester?.id?.value)   // 无状态变化
        assertEquals(5, m.state.value.viewedWeek)
        assertTrue(settings.viewedSemesterId.first() == null)  // 无 dangling 写入
    }

    @Test fun switch_resets_explicit_week() = runBlocking {
        seedA(); seedB()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { m.state.value.semester?.id?.value == "B" }
        awaitUntil { m.state.value.viewedWeek == 18 }   // B 是历史学期 → default totalWeeks
    }

    @Test fun switch_back_does_not_restore_old_explicit_week() = runBlocking {
        seedA(); seedB()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onWeekSelected(5)
        awaitUntil { m.state.value.viewedWeek == 5 }
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { m.state.value.semester?.id?.value == "B" }
        m.onSemesterSelected(SemesterId("A"))
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        awaitUntil { m.state.value.viewedWeek == 2 }   // 回 A → 自然周，而非旧 week5
    }

    @Test fun switch_to_future_semester_starts_week1() = runBlocking {
        seedA(); seedF()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onSemesterSelected(SemesterId("F"))
        awaitUntil { m.state.value.semester?.id?.value == "F" }
        awaitUntil { m.state.value.viewedWeek == 1 }
    }

    @Test fun switch_to_past_semester_starts_totalWeeks() = runBlocking {
        seedA(); seedB()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { m.state.value.semester?.id?.value == "B" }
        awaitUntil { m.state.value.viewedWeek == 18 }
    }

    @Test fun switch_to_semester_containing_today_starts_natural_week() = runBlocking {
        // A2：另一个包含今天(2026-09-08)的学期，教学周从 09-07 开始 → 自然周 1
        val a2 = Semester(
            id = SemesterId("A2"), displayName = "2026-2027 秋季(短)", academicYear = "2026-2027", term = Term.AUTUMN,
            week1Start = LocalDate.of(2026, 9, 7), totalWeeks = 16,
            startDate = LocalDate.of(2026, 9, 5), endDate = LocalDate.of(2026, 12, 27),
            importedAt = t0, lastSyncedAt = null,
            isCurrentAcademicSemester = false, portalLinked = true,
            profileId = ProfileId(bundledId), sourceFingerprint = null,
        )
        db.semesterDao().insert(Mappers.toEntity(a2))
        seedA()
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onSemesterSelected(SemesterId("A2"))
        awaitUntil { m.state.value.semester?.id?.value == "A2" }
        awaitUntil { m.state.value.viewedWeek == 1 }   // 09-08 是 A2 的第 1 教学周
    }

    // ---- correction 9：完整 projection 切换 ----

    @Test fun switch_updates_header_week_dates_profile_and_blocks() = runBlocking {
        val a = seedA()
        val (ac, am) = schoolSnapshot(a.id, "name:课A", "mA")
        db.applySchoolSnapshot(a.id, ac, am, "fpA", t0)
        manual.add(
            ManualScheduleItem(ManualItemId("iA"), a.id, "讲座A", 6, LocalTime.of(14, 20), LocalTime.of(16, 0), WeekPattern.of(2), null, null, t0, t0),
        )
        val b = seedB()
        val (bc, bm) = schoolSnapshot(b.id, "name:课B", "mB", weekday = 3)
        db.applySchoolSnapshot(b.id, bc, bm, "fpB", t0)
        manual.add(
            ManualScheduleItem(ManualItemId("iB"), b.id, "讲座B", 5, LocalTime.of(15, 0), LocalTime.of(16, 30), WeekPattern.range(1, 18), null, null, t0, t0),
        )
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" && m.state.value.placedManual.isNotEmpty() }
        val before = m.state.value
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { m.state.value.semester?.id?.value == "B" }
        awaitUntil { m.state.value.placedManual.isNotEmpty() }
        val after = m.state.value
        // header / 周 / 日期 / blocks 全部切到 B，无 A 泄漏
        assertNotEquals(before.semester!!.id, after.semester!!.id)
        assertEquals("2025-2026 秋季", after.semester!!.displayName)
        assertEquals(18, after.viewedWeek)
        assertEquals(LocalDate.of(2025, 12, 29), after.weekDates!!.start)   // B 第 18 周周一（2025-09-01 + 17 周）
        assertEquals("B:name:课B", after.placedSchool.first().block.colorKey)
        assertTrue(after.placedSchool.first().block.colorKey.endsWith("name:课B"))
        assertEquals("manual:iB", after.placedManual.first().block.colorKey)
        assertEquals("manual:iA", before.placedManual.first().block.colorKey)
        assertEquals("A:name:课A", before.placedSchool.first().block.colorKey)
    }

    // ---- correction 10/11/12：隔离与持久化 ----

    @Test fun switch_does_not_mutate_any_semester_database_row() = runBlocking {
        val a = seedA()
        val b = seedB()
        val aBefore = db.semesterDao().byId("A")!!
        val bBefore = db.semesterDao().byId("B")!!
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { m.state.value.semester?.id?.value == "B" }
        val aAfter = db.semesterDao().byId("A")!!
        val bAfter = db.semesterDao().byId("B")!!
        assertEquals(aBefore, aAfter)   // 整行逐字段不变
        assertEquals(bBefore, bAfter)
        assertTrue(aAfter.isCurrentAcademicSemester)
        assertFalseSafe(bAfter.isCurrentAcademicSemester)
    }

    private fun assertFalseSafe(v: Boolean) = org.junit.Assert.assertFalse(v)

    @Test fun switch_writes_only_viewedSemesterId() = runBlocking {
        seedA(); seedB()
        settings.setShowNonCurrentWeek(true)
        settings.setWeeklySyncEnabled(false)
        settings.setNeedReauth(true)
        settings.setActiveWorkingProfileId("sentinel-profile")
        settings.setLastSyncFinishedAt(777L)
        val m = vm()
        awaitUntil { m.state.value.semester?.id?.value == "A" }
        m.onSemesterSelected(SemesterId("B"))
        awaitUntil { settings.viewedSemesterId.first() == "B" }
        awaitUntil { m.state.value.semester?.id?.value == "B" }
        val showNon = settings.showNonCurrentWeek.first()
        val weekly = settings.weeklySyncEnabled.first()
        val reauth = settings.needReauth.first()
        val profile = settings.activeWorkingProfileId.first()
        val lastSync = settings.lastSyncFinishedAt.first()
        assertEquals(true, showNon)
        assertEquals(false, weekly)
        assertEquals(true, reauth)
        assertEquals("sentinel-profile", profile)
        assertEquals(777L, lastSync)
    }

    @Test fun viewed_semester_survives_vm_recreation() = runBlocking {
        seedA(); seedB()
        val m1 = vm()
        awaitUntil { m1.state.value.semester?.id?.value == "A" }
        m1.onSemesterSelected(SemesterId("B"))
        awaitUntil { settings.viewedSemesterId.first() == "B" }
        val m2 = vm()   // 同一 SettingsStore/仓库
        awaitUntil { m2.state.value.semester?.id?.value == "B" }
    }

    // ---- Sheet 点击（correction 5 content 级） ----

    @Test fun sheet_select_fires_callback() {
        var selected: SemesterId? = null
        content(viewed = "A") { selected = it }
        rule.onNodeWithTag("semester_item:B").performClick()
        rule.waitForIdle()
        assertEquals(SemesterId("B"), selected)
    }
}
