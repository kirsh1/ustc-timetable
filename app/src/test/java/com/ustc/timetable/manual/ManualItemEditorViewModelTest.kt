package com.ustc.timetable.manual

import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.toEntity
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
class ManualItemEditorViewModelTest {

    private val createdAt = Instant.parse("2026-08-20T01:00:00Z")
    private val editedAt = Instant.parse("2026-09-01T02:03:04Z")
    private val editorClock = Clock.fixed(editedAt, ZoneOffset.UTC)
    private lateinit var db: TimetableDatabase
    private lateinit var profile: ScheduleProfile
    private lateinit var semester: Semester
    private lateinit var manual: ManualItemRepository

    @Before fun setUp() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        profile = OfficialProfileLoader.load(context)
        semester = SemesterDefaults.AUTUMN_2026("semester-viewed", profile.id, createdAt)
        db.scheduleProfileDao().insert(profile.toEntity())
        db.semesterDao().insert(Mappers.toEntity(semester))
        manual = ManualItemRepository(db, editorClock)
    }

    @After fun tearDown() {
        db.close()
    }

    private fun newEditor(
        start: LocalTime = LocalTime.of(14, 20),
        viewedWeek: Int = 5,
    ) = ManualItemEditorViewModel(
        manual = manual,
        semester = semester,
        profile = profile,
        initial = ManualEditorInitial.New(weekday = 3, startTime = start, viewedWeek = viewedWeek),
        clock = editorClock,
    )

    private fun existing(
        id: String = "manual-existing",
        weeks: WeekPattern = WeekPattern.range(3, 8),
        semesterForItem: Semester = semester,
    ) = ManualScheduleItem(
        id = ManualItemId(id),
        semesterId = semesterForItem.id,
        title = "  组会  ",
        weekday = 4,
        startTime = LocalTime.of(14, 23),
        endTime = LocalTime.of(15, 37),
        weekPattern = weeks,
        location = "  教室 A  ",
        note = "  带材料  ",
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun editEditor(
        item: ManualScheduleItem,
        viewedWeek: Int = 5,
    ) = ManualItemEditorViewModel(
        manual = manual,
        semester = semester,
        profile = profile,
        initial = ManualEditorInitial.Edit(item, viewedWeek),
        clock = editorClock,
    )

    private fun valid(editor: ManualItemEditorViewModel): ManualItemEditorViewModel {
        editor.update { it.copy(title = "  新项目  ") }
        return editor
    }

    private fun storedItems(): List<ManualScheduleItem> = runBlocking {
        db.manualItemDao().itemsForSemester(semester.id.value).map(Mappers::toDomain)
    }

    @Test fun new_default_duration_is_45_minutes() {
        val draft = newEditor(start = LocalTime.of(14, 20)).draft.value
        assertEquals(LocalTime.of(15, 5), draft.endTime)
    }

    @Test fun new_default_end_clamps_to_day_window_end() {
        val lateProfile = ScheduleProfile(
            id = "profile.late",
            name = "late",
            isBundledOfficial = false,
            periods = (1..13).map { number ->
                val hour = 10 + number
                PeriodTime(
                    number = number,
                    start = LocalTime.of(hour, 0),
                    end = if (number == 13) LocalTime.of(23, 59) else LocalTime.of(hour, 45),
                )
            },
        )
        val editor = ManualItemEditorViewModel(
            manual = manual,
            semester = semester,
            profile = lateProfile,
            initial = ManualEditorInitial.New(3, LocalTime.of(23, 45), 5),
            clock = editorClock,
        )
        assertEquals(LocalTime.of(23, 59), editor.draft.value.endTime)
    }

    @Test fun new_draft_keeps_prefill_start_exactly() {
        val draft = newEditor(start = LocalTime.of(14, 23)).draft.value
        assertEquals(LocalTime.of(14, 23), draft.startTime)
    }

    @Test fun new_at_window_end_stays_invalid_until_edited() {
        val editor = newEditor(start = profile.dayWindow().endInclusive)
        assertEquals(profile.dayWindow().endInclusive, editor.draft.value.startTime)
        assertEquals(profile.dayWindow().endInclusive, editor.draft.value.endTime)
        editor.update { it.copy(title = "边界") }
        assertEquals("结束需晚于开始", editor.validationError())
    }

    @Test fun existing_single_current_week_infers_current_only() {
        val draft = editEditor(existing(weeks = WeekPattern.of(5)), viewedWeek = 5).draft.value
        assertEquals(WeekMode.CURRENT_ONLY, draft.mode)
    }

    @Test fun existing_single_other_week_infers_custom() {
        val draft = editEditor(existing(weeks = WeekPattern.of(7)), viewedWeek = 5).draft.value
        assertEquals(WeekMode.CUSTOM, draft.mode)
        assertEquals(setOf(7), draft.customWeeks)
    }

    @Test fun existing_contiguous_infers_continuous() {
        val draft = editEditor(existing(weeks = WeekPattern.range(3, 12))).draft.value
        assertEquals(WeekMode.CONTINUOUS, draft.mode)
        assertEquals(3, draft.continuousStart)
        assertEquals(12, draft.continuousEnd)
    }

    @Test fun existing_sparse_infers_custom() {
        val draft = editEditor(existing(weeks = WeekPattern.of(2, 6, 8, 10, 12))).draft.value
        assertEquals(WeekMode.CUSTOM, draft.mode)
        assertEquals(setOf(2, 6, 8, 10, 12), draft.customWeeks)
    }

    @Test fun existing_fields_load_losslessly() {
        val item = existing(weeks = WeekPattern.of(2, 6, 8))
        val draft = editEditor(item).draft.value
        assertEquals(item.title, draft.title)
        assertEquals(item.location, draft.location)
        assertEquals(item.note, draft.note)
        assertEquals(item.weekday, draft.weekday)
        assertEquals(item.startTime, draft.startTime)
        assertEquals(item.endTime, draft.endTime)
    }

    @Test fun existing_open_and_save_without_changes_preserves_week_pattern() = runBlocking {
        val item = existing(weeks = WeekPattern.of(7))
        manual.add(item)
        editEditor(item, viewedWeek = 5).save()
        assertEquals(WeekPattern.of(7), storedItems().single().weekPattern)
    }

    @Test fun existing_pattern_outside_semester_fails_explicitly() {
        assertThrows(IllegalArgumentException::class.java) {
            editEditor(existing(weeks = WeekPattern.of(semester.totalWeeks + 1)))
        }
    }

    @Test fun existing_item_from_another_semester_fails_explicitly() {
        val other = semester.copy(id = com.ustc.timetable.timetable.domain.SemesterId("other"))
        assertThrows(IllegalArgumentException::class.java) {
            editEditor(existing(semesterForItem = other))
        }
    }

    @Test fun validation_title_required() {
        assertEquals("请填写标题", newEditor().validationError())
    }

    @Test fun validation_bad_weekday() {
        val editor = valid(newEditor())
        editor.update { it.copy(weekday = 8) }
        assertEquals("请选择星期", editor.validationError())
    }

    @Test fun validation_end_after_start() {
        val editor = valid(newEditor())
        editor.update { it.copy(endTime = it.startTime) }
        assertEquals("结束需晚于开始", editor.validationError())
    }

    @Test fun validation_rejects_outside_day_window() {
        val editor = valid(newEditor())
        editor.update { it.copy(startTime = LocalTime.of(6, 0)) }
        assertEquals("时间需在作息窗口内", editor.validationError())
        editor.update { it.copy(startTime = LocalTime.of(14, 20), endTime = LocalTime.of(22, 0)) }
        assertEquals("时间需在作息窗口内", editor.validationError())
    }

    @Test fun validation_current_week_bounds() {
        val editor = valid(newEditor(viewedWeek = semester.totalWeeks + 1))
        assertEquals("周次无效", editor.validationError())
    }

    @Test fun validation_continuous_bounds() {
        val editor = valid(newEditor())
        editor.update { it.copy(mode = WeekMode.CONTINUOUS, continuousStart = 0, continuousEnd = 3) }
        assertEquals("周次无效", editor.validationError())
        editor.update { it.copy(continuousStart = 3, continuousEnd = semester.totalWeeks + 1) }
        assertEquals("周次无效", editor.validationError())
    }

    @Test fun validation_continuous_order() {
        val editor = valid(newEditor())
        editor.update { it.copy(mode = WeekMode.CONTINUOUS, continuousStart = 8, continuousEnd = 3) }
        assertEquals("连续周次起始需不晚于结束", editor.validationError())
    }

    @Test fun validation_rejects_empty_custom() {
        val editor = valid(newEditor())
        editor.update { it.copy(mode = WeekMode.CUSTOM, customWeeks = emptySet()) }
        assertEquals("请选择周次", editor.validationError())
    }

    @Test fun validation_rejects_out_of_semester_custom_week() {
        val editor = valid(newEditor())
        editor.update { it.copy(mode = WeekMode.CUSTOM, customWeeks = setOf(5, semester.totalWeeks + 1)) }
        assertEquals("周次无效", editor.validationError())
    }

    @Test fun current_only_builds_single_week() {
        val item = valid(newEditor(viewedWeek = 5)).buildItem()
        assertEquals(WeekPattern.of(5), item.weekPattern)
    }

    @Test fun continuous_builds_exact_range() {
        val editor = valid(newEditor())
        editor.update { it.copy(mode = WeekMode.CONTINUOUS, continuousStart = 3, continuousEnd = 12) }
        assertEquals(WeekPattern.range(3, 12), editor.buildItem().weekPattern)
    }

    @Test fun custom_builds_exact_sparse_pattern() {
        val editor = valid(newEditor())
        editor.update { it.copy(mode = WeekMode.CUSTOM, customWeeks = linkedSetOf(12, 2, 8, 6, 10)) }
        assertEquals(WeekPattern.of(2, 6, 8, 10, 12), editor.buildItem().weekPattern)
    }

    @Test fun custom_order_does_not_affect_pattern() {
        val a = valid(newEditor()).also {
            it.update { d -> d.copy(mode = WeekMode.CUSTOM, customWeeks = linkedSetOf(8, 2, 6)) }
        }.buildItem().weekPattern
        val b = valid(newEditor()).also {
            it.update { d -> d.copy(mode = WeekMode.CUSTOM, customWeeks = linkedSetOf(2, 6, 8)) }
        }.buildItem().weekPattern
        assertEquals(a, b)
    }

    @Test fun new_save_adds_manual_item() = runBlocking {
        valid(newEditor()).save()
        assertEquals(1, storedItems().size)
        assertEquals("新项目", storedItems().single().title)
    }

    @Test fun new_item_uses_viewed_semester() {
        assertEquals(semester.id, valid(newEditor()).buildItem().semesterId)
    }

    @Test fun new_item_created_and_updated_same_instant() {
        val item = valid(newEditor()).buildItem()
        assertEquals(editedAt, item.createdAt)
        assertEquals(editedAt, item.updatedAt)
    }

    @Test fun edit_save_preserves_identity_and_createdAt() = runBlocking {
        val original = existing()
        manual.add(original)
        val editor = editEditor(original)
        editor.update { it.copy(title = "改名") }
        editor.save()
        val saved = storedItems().single()
        assertEquals(original.id, saved.id)
        assertEquals(original.semesterId, saved.semesterId)
        assertEquals(original.createdAt, saved.createdAt)
        assertEquals(editedAt, saved.updatedAt)
        assertEquals(1, storedItems().size)
    }

    @Test fun invalid_save_writes_nothing() {
        val editor = newEditor()
        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking { editor.save() }
        }
        assertEquals("请填写标题", error.message)
        assertTrue(storedItems().isEmpty())
    }

    @Test fun save_never_touches_school_rows() = runBlocking {
        seedSchoolRows()
        val coursesBefore = db.courseDao().coursesForSemester(semester.id.value)
        val meetingsBefore = db.courseDao().meetingsForSemester(semester.id.value)
        valid(newEditor()).save()
        assertEquals(coursesBefore, db.courseDao().coursesForSemester(semester.id.value))
        assertEquals(meetingsBefore, db.courseDao().meetingsForSemester(semester.id.value))
    }

    @Test fun blank_optional_fields_become_null() {
        val editor = valid(newEditor())
        editor.update { it.copy(location = "   ", note = "\t") }
        val item = editor.buildItem()
        assertNull(item.location)
        assertNull(item.note)
    }

    @Test fun source_is_manual() {
        assertEquals(ItemSource.MANUAL, valid(newEditor()).buildItem().source)
    }

    @Test fun update_is_ephemeral_and_preserves_arbitrary_minute() {
        val editor = valid(newEditor())
        editor.update { it.copy(startTime = LocalTime.of(14, 23), endTime = LocalTime.of(15, 7)) }
        assertEquals(LocalTime.of(14, 23), editor.draft.value.startTime)
        assertEquals(LocalTime.of(15, 7), editor.draft.value.endTime)
        assertTrue(storedItems().isEmpty())
    }

    @Test fun new_editor_cannot_delete() {
        val editor = newEditor()
        assertFalse(editor.canDelete)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { editor.delete() }
        }
    }

    @Test fun existing_delete_removes_exact_item() = runBlocking {
        val keep = existing(id = "keep")
        val remove = existing(id = "remove")
        manual.add(keep)
        manual.add(remove)
        val editor = editEditor(remove)
        assertTrue(editor.canDelete)
        editor.delete()
        assertEquals(listOf("keep"), storedItems().map { it.id.value })
    }

    @Test fun deleting_manual_item_does_not_touch_school_data() = runBlocking {
        seedSchoolRows()
        val item = existing()
        manual.add(item)
        val coursesBefore = db.courseDao().coursesForSemester(semester.id.value)
        val meetingsBefore = db.courseDao().meetingsForSemester(semester.id.value)
        editEditor(item).delete()
        assertEquals(coursesBefore, db.courseDao().coursesForSemester(semester.id.value))
        assertEquals(meetingsBefore, db.courseDao().meetingsForSemester(semester.id.value))
    }

    private suspend fun seedSchoolRows() {
        db.courseDao().insertCourses(
            listOf(
                CourseEntity(
                    id = "school-course",
                    semesterId = semester.id.value,
                    sourceCourseKey = "portal:school-course",
                    courseCode = "TEST1001",
                    name = "学校课程",
                    credits = 2.0,
                    courseType = null,
                    source = ItemSource.SCHOOL.name,
                ),
            ),
        )
        db.courseDao().insertMeetings(
            listOf(
                CourseMeetingEntity(
                    id = "school-meeting",
                    courseId = "school-course",
                    weekday = 2,
                    startPeriod = 1,
                    endPeriod = 2,
                    weekPatternMask = WeekPattern.range(1, 10).mask,
                    location = "教室",
                    teacherNamesJoined = "教师",
                    source = ItemSource.SCHOOL.name,
                ),
            ),
        )
    }
}
