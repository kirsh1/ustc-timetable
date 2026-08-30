package com.ustc.timetable.timetable.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.PlacedBlock
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableScreenTest {

    private val profile = ScheduleProfile(
        "profile.test.synthetic", "synthetic", false,
        (1..13).map { PeriodTime(it, LocalTime.of(6 + it, 0), LocalTime.of(6 + it, 45)) },
    )

    private fun placedSchool(meetingId: String = "m1"): List<PlacedBlock> {
        val block = UiTimedBlock(
            colorKey = "s1:CHEM5013P", meetingId = MeetingId(meetingId), manualItemId = null,
            weekday = 5, start = LocalTime.of(9, 0), endInclusive = LocalTime.of(10, 45),
            weeks = WeekPattern.range(1, 20), title = "高等无机化学", location = "TH-B301", teacherNames = listOf("刘斯"),
        )
        return WeeklyTimetableLayout.place(listOf(block), WeeklyTimetableLayout.axisOf(profile))
    }

    // C1 page-projection fixture：20 页，viewed 第 2 页含目标块，其余空页
    private fun fullState(
        placedSchool: List<PlacedBlock> = emptyList(),
        placedManual: List<PlacedBlock> = emptyList(),
        isAcademicCurrentViewed: Boolean = false,
        canSyncViewed: Boolean = false,
    ): TimetableUiState {
        val semester = SemesterDefaults.AUTUMN_2026(id = "s", profileId = "p")
        val weekDates2 = LocalDateRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13))
        val pages = (1..semester.totalWeeks).map { week ->
            if (week == 2) TimetableWeekPageUiState(week, weekDates2, placedSchool, placedManual, null)
            else TimetableWeekPageUiState(
                week,
                LocalDateRange(semester.week1Start.plusWeeks((week - 1).toLong()), semester.week1Start.plusWeeks((week - 1).toLong()).plusDays(6)),
                emptyList(), emptyList(), null,
            )
        }
        return TimetableUiState(
            semester = semester,
            viewedWeek = 2,
            naturalWeek = null,
            profile = profile,
            weekPages = pages,
            showNonCurrentWeek = false,
            today = null,
            isAcademicCurrentViewed = isAcademicCurrentViewed,
            canSyncViewed = canSyncViewed,
            isLoading = false,
        )
    }

    @get:Rule val rule = createComposeRule()

    @Test fun screen_renders_semester_week_and_date_range() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithText("2026-2027 秋季 ▼").assertCountEquals(1)
        rule.onAllNodesWithText("第 2 周").assertCountEquals(1)
        rule.onAllNodesWithText("9.7 - 9.13").assertCountEquals(1)
    }

    @Test fun screen_renders_weekly_grid() {
        rule.setContent { TimetableScreen(state = fullState(placedSchool = placedSchool()), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithTag("timetable_grid").assertCountEquals(1)
        rule.onAllNodesWithTag("school_block:m1").assertCountEquals(1)
        rule.onAllNodesWithText("一 7").assertCountEquals(1)  // 七列 header 由 Grid 提供
    }

    @Test fun arrows_fire_prev_next_callbacks() {
        var prev = 0; var next = 0
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = { prev++ }, onNextWeek = { next++ }, onWeekSelected = {}) }
        rule.onNodeWithTag("prev_week").performClick()
        rule.onNodeWithTag("next_week").performClick()
        rule.waitForIdle()
        assertTrue(prev == 1 && next == 1)
    }

    @Test fun manual_academic_current_hides_refresh() {
        // 纯手动 academic-current：isCurrent=true 但 canSync=false → 完全隐藏 ↻
        rule.setContent { TimetableScreen(state = fullState(isAcademicCurrentViewed = true, canSyncViewed = false), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithTag("refresh").assertCountEquals(0)
    }

    @Test fun portal_current_shows_disabled_refresh() {
        rule.setContent { TimetableScreen(state = fullState(isAcademicCurrentViewed = true, canSyncViewed = true), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithTag("refresh").assertCountEquals(1)
    }

    @Test fun settings_control_present_but_disabled() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithTag("settings").assertCountEquals(1)
    }

    @Test fun empty_state_has_no_dashboard() {
        rule.setContent { TimetableScreen(state = TimetableUiState(isLoading = false), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithText("暂无课表").assertCountEquals(1)
        rule.onAllNodesWithTag("timetable_grid").assertCountEquals(0)
        rule.onAllNodesWithTag("prev_week").assertCountEquals(0)
    }
}
