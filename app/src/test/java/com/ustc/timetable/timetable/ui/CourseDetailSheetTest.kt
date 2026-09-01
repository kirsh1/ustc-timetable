package com.ustc.timetable.timetable.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CourseDetailSheetTest {

    private val profile = ScheduleProfile(
        "profile.test.synthetic", "synthetic", false,
        (1..13).map { PeriodTime(it, LocalTime.of(6 + it, 0), LocalTime.of(6 + it, 45)) },
    )

    private val semId = SemesterId("s")
    private val courseId = CourseId("c-chem")

    /** 三教师分段 fixture（frozen §7）：m1 吴长征 2–6 / m2 刘斯 7–12 / m3 郭宇桥 13–18，周五 3–5 节。 */
    private fun chemMeetings(): List<CourseMeeting> = listOf(
        CourseMeeting(MeetingId("m2"), courseId, 5, 3, 5, WeekPattern.range(7, 12), "TH-B301", listOf("刘斯")),
        CourseMeeting(MeetingId("m1"), courseId, 5, 3, 5, WeekPattern.range(2, 6), "TH-B301", listOf("吴长征")),
        CourseMeeting(MeetingId("m3"), courseId, 5, 3, 5, WeekPattern.range(13, 18), "TH-B301", listOf("郭宇桥")),
    )

    private fun detail(clicked: MeetingId = MeetingId("m2"), credits: Double? = 3.0): CourseDetailUiModel {
        val course = Course(courseId, semId, "name:高等无机化学", "CHEM5013P", "高等无机化学", credits, null)
        val meetings = chemMeetings()
        return CourseDetailUiModel(
            course = course,
            selectedMeeting = meetings.first { it.id == clicked },
            allMeetings = meetings,
        )
    }

    @get:Rule val rule = createComposeRule()

    private fun content(d: CourseDetailUiModel = detail()) {
        rule.setContent { CourseDetailContent(detail = d, profile = profile) }
        rule.waitForIdle()
    }

    // ---- §4.4 内容 ----

    @Test fun detail_selected_section_uses_clicked_meeting() {
        content(detail(clicked = MeetingId("m2")))   // 点 m2 → 刘斯 第 7–12 周
        rule.onAllNodesWithText("高等无机化学").assertCountEquals(1)
        rule.onAllNodesWithText("周五 · 09:00–11:45", useUnmergedTree = true).assertCountEquals(1)  // 合成 profile 3–5 节
        rule.onAllNodesWithText("第 7–12 周", useUnmergedTree = true).assertCountEquals(1)  // selected 区；完整安排是复合行
    }

    @Test fun detail_lists_all_meetings_of_course() {
        content()
        // 完整安排：三行 教师 · 周次 · 地点
        rule.onAllNodesWithText("刘斯 · 第 7–12 周 · TH-B301", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("吴长征 · 第 2–6 周 · TH-B301", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("郭宇桥 · 第 13–18 周 · TH-B301", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun detail_lists_hidden_week_meetings_too() {
        // selected = m1（第 2–6 周）；完整安排仍含全部三条（含不与 selected 同周的 m2/m3）
        content(detail(clicked = MeetingId("m1")))
        rule.onAllNodesWithText("刘斯 · 第 7–12 周 · TH-B301", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("郭宇桥 · 第 13–18 周 · TH-B301", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun detail_shows_course_code_and_credits() {
        content()
        rule.onAllNodesWithText("CHEM5013P", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("3", useUnmergedTree = true).assertCountEquals(1)   // 3.0 → "3"
    }

    @Test fun detail_shows_dash_when_credits_null() {
        content(detail(credits = null))
        rule.onAllNodesWithText("—", useUnmergedTree = true).assertCountEquals(1)
    }

    // ---- read-only ----

    @Test fun detail_has_no_edit_action() {
        content()
        rule.onAllNodesWithText("编辑").assertCountEquals(0)
    }

    @Test fun detail_has_no_delete_action() {
        content()
        rule.onAllNodesWithText("删除").assertCountEquals(0)
    }

    @Test fun detail_has_no_save_action() {
        content()
        rule.onAllNodesWithText("保存").assertCountEquals(0)
    }

    // ---- 完整安排确定性排序 ----

    @Test fun complete_arrangements_order_is_deterministic() {
        // 输入顺序 m2/m1/m3（chemMeetings 原始顺序）→ 排序后按 weekday/startPeriod/…/weekPattern 稳定序
        val d = detail()
        // m1(2–6) 与 m2(7–12) 与 m3(13–18)：weekday/period/location/teacher 均同 → 按 weekPattern mask 升序
        assertEquals(listOf("m1", "m2", "m3"), d.allMeetings.sortedWith(detailMeetingOrder()).map { it.id.value })
        val reversed = detail().let { model ->
            CourseDetailUiModel(model.course, model.selectedMeeting, model.allMeetings.reversed())
        }
        assertEquals(
            d.allMeetings.sortedWith(detailMeetingOrder()).map { it.id.value },
            reversed.allMeetings.sortedWith(detailMeetingOrder()).map { it.id.value },
        )
    }

    @Test fun complete_arrangements_displays_sorted_rows() {
        content(detail(clicked = MeetingId("m2")))
        // 三行按 周次 升序渲染：吴长征(2–6) → 刘斯(7–12) → 郭宇桥(13–18)
        val positions = listOf(
            "吴长征 · 第 2–6 周 · TH-B301",
            "刘斯 · 第 7–12 周 · TH-B301",
            "郭宇桥 · 第 13–18 周 · TH-B301",
        ).map { text -> rule.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.positionInRoot?.y ?: -1f }
        assertTrue("rows must be y-ascending: $positions", positions[0] <= positions[1] && positions[1] <= positions[2])
    }

    @Test fun detail_selection_is_ephemeral() {
        val selectedId = MeetingId("m2")
        val selectedDetail = detail(clicked = selectedId)
        assertEquals(selectedDetail, resolveSchoolCourseDetail(selectedId, mapOf(selectedId to selectedDetail)))
        assertNull(resolveSchoolCourseDetail(selectedId, emptyMap()))
    }

    @Test fun semester_switch_does_not_show_previous_semester_detail() {
        val previousId = MeetingId("m2")
        val currentId = MeetingId("new-semester-meeting")
        val currentDetail = detail(clicked = previousId).let {
            it.copy(selectedMeeting = it.selectedMeeting.copy(id = currentId))
        }
        assertNull(resolveSchoolCourseDetail(previousId, mapOf(currentId to currentDetail)))
    }

    @Test fun stale_meeting_id_does_not_render_detail() {
        assertNull(resolveSchoolCourseDetail(MeetingId("stale"), emptyMap()))
    }
}
