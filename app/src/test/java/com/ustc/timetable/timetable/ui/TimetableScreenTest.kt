package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.PlacedBlock
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import java.time.LocalDate
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
@Config(sdk = [36], qualifiers = "w400dp-h800dp")
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

    private fun placedManual(itemId: String = "i1"): List<PlacedBlock> {
        val block = UiTimedBlock(
            colorKey = "manual:$itemId", meetingId = null, manualItemId = ManualItemId(itemId),
            weekday = 5, start = LocalTime.of(9, 0), endInclusive = LocalTime.of(10, 45),
            weeks = WeekPattern.range(1, 20), title = "手动项", location = "", teacherNames = emptyList(),
        )
        return WeeklyTimetableLayout.place(listOf(block), WeeklyTimetableLayout.axisOf(profile))
    }

    // C1 page-projection fixture：20 页，viewed 第 2 页含目标块，其余空页
    private fun fullState(
        placedSchool: List<PlacedBlock> = emptyList(),
        placedManual: List<PlacedBlock> = emptyList(),
        isAcademicCurrentViewed: Boolean = false,
        canSyncViewed: Boolean = false,
        naturalWeek: Int? = null,
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
            naturalWeek = naturalWeek,
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

    @Test fun compact_top_bar_leads_with_week_and_inline_date_range() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithText("2026-2027 秋季 ▼").assertCountEquals(0)
        rule.onAllNodesWithText("第 2 周").assertCountEquals(1)
        rule.onAllNodesWithText("9.7 - 9.13").assertCountEquals(1)
        rule.onAllNodesWithTag("timetable_top_bar").assertCountEquals(1)
    }

    @Test fun top_bar_is_short_and_week_date_are_vertically_centered() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        val bar = rule.onNodeWithTag("timetable_top_bar").getUnclippedBoundsInRoot()
        val week = rule.onNodeWithTag("viewed_week").getUnclippedBoundsInRoot()
        val dates = rule.onNodeWithTag("week_dates").getUnclippedBoundsInRoot()
        val weekCenter = (week.top + week.bottom) / 2f
        val dateCenter = (dates.top + dates.bottom) / 2f
        assertTrue(bar.bottom - bar.top <= 44.dp)
        assertTrue(kotlin.math.abs((weekCenter - dateCenter).value) <= 1f)
    }

    @Test fun week_number_and_range_text_visual_centers_match() {
        assertWeekTextCenters(widthDp = 400, fontScale = 1f)
    }

    @Test fun week_number_and_range_text_visual_centers_match_at_font_scale_1_3() {
        assertWeekTextCenters(widthDp = 400, fontScale = 1.3f)
    }

    @Test fun week_number_and_range_text_visual_centers_match_at_compact_width() {
        assertWeekTextCenters(widthDp = 320, fontScale = 1f)
    }

    @Test fun week_number_and_range_text_visual_centers_match_at_compact_width_and_font_scale_1_3() {
        assertWeekTextCenters(widthDp = 320, fontScale = 1.3f)
    }

    @Test fun week_bar_typography_tokens_are_explicit() {
        assertEquals(44, WEEK_BAR_CONTENT_HEIGHT_DP)
        assertEquals(16, WEEK_NUMBER_FONT_SP)
        assertEquals(20, WEEK_NUMBER_LINE_HEIGHT_SP)
        assertEquals(12, WEEK_RANGE_FONT_SP)
        assertEquals(16, WEEK_RANGE_LINE_HEIGHT_SP)
    }

    private fun assertWeekTextCenters(widthDp: Int, fontScale: Float) {
        rule.setContent {
            val baseDensity = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(baseDensity, fontScale)) {
                Box(Modifier.width(widthDp.dp)) {
                    TimetableScreen(
                        state = fullState(),
                        onPrevWeek = {},
                        onNextWeek = {},
                        onWeekSelected = {},
                    )
                }
            }
        }
        val week = rule.onNodeWithTag("week_number_text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val range = rule.onNodeWithTag("week_range_text", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val weekCenter = (week.top + week.bottom) / 2f
        val rangeCenter = (range.top + range.bottom) / 2f
        assertTrue(
            "width=${widthDp}dp fontScale=$fontScale week=$week range=$range",
            kotlin.math.abs((weekCenter - rangeCenter).value) <= 1f,
        )
    }

    @Test fun previous_and_next_arrows_are_symmetric_around_week_and_range() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        val previous = rule.onNodeWithTag("prev_week").getUnclippedBoundsInRoot()
        val week = rule.onNodeWithTag("viewed_week").getUnclippedBoundsInRoot()
        val range = rule.onNodeWithTag("week_dates").getUnclippedBoundsInRoot()
        val next = rule.onNodeWithTag("next_week").getUnclippedBoundsInRoot()
        val leftGap = week.left - previous.right
        val rightGap = next.left - range.right
        assertTrue("leftGap=$leftGap rightGap=$rightGap", kotlin.math.abs((leftGap - rightGap).value) <= 1f)
    }

    @Test fun screen_renders_weekly_grid() {
        rule.setContent { TimetableScreen(state = fullState(placedSchool = placedSchool()), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithTag("timetable_grid").assertCountEquals(1)
        rule.onAllNodesWithTag("school_block:m1").assertCountEquals(1)
        rule.onAllNodesWithText("周一").assertCountEquals(1)
        rule.onAllNodesWithText("9-07").assertCountEquals(1)
    }

    @Test fun time_rail_is_outside_horizontal_pager() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        val rail = rule.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot()
        val pager = rule.onNodeWithTag("week_pager").getUnclippedBoundsInRoot()
        assertTrue("rail=$rail pager=$pager", rail.right <= pager.left)
        rule.onAllNodesWithTag("time_gutter", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun arrows_fire_prev_next_callbacks() {
        var prev = 0; var next = 0
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = { prev++ }, onNextWeek = { next++ }, onWeekSelected = {}) }
        rule.onNodeWithTag("prev_week").performClick()
        rule.onNodeWithTag("next_week").performClick()
        rule.waitForIdle()
        assertTrue(prev == 1 && next == 1)
    }

    @Test fun refresh_is_absent_from_timetable_for_manual_semester() {
        rule.setContent { TimetableScreen(state = fullState(isAcademicCurrentViewed = true, canSyncViewed = false), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithTag("refresh").assertCountEquals(0)
    }

    @Test fun refresh_is_absent_from_timetable_for_portal_semester() {
        rule.setContent { TimetableScreen(state = fullState(isAcademicCurrentViewed = true, canSyncViewed = true), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}, manualSyncAvailable = true) }
        rule.onAllNodesWithTag("refresh").assertCountEquals(0)
    }

    @Test fun timetable_gear_opens_settings() {
        var calls = 0
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}, onSettingsClick = { calls++ }) }
        rule.onAllNodesWithTag("settings").assertCountEquals(1)
        rule.onNodeWithTag("settings").performClick()
        rule.waitForIdle()
        assertEquals(1, calls)
    }

    @Test fun top_bar_actions_expose_vector_icon_semantics() {
        rule.setContent { TimetableScreen(state = fullState(), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onNodeWithTag("prev_week").assertContentDescriptionEquals("上一周")
        rule.onNodeWithTag("next_week").assertContentDescriptionEquals("下一周")
        rule.onNodeWithTag("week_overview_toggle").assertContentDescriptionEquals("展开周缩略图")
        rule.onNodeWithTag("settings").assertContentDescriptionEquals("设置")
        rule.onAllNodesWithText("‹").assertCountEquals(0)
        rule.onAllNodesWithText("›").assertCountEquals(0)
        rule.onAllNodesWithText("▦").assertCountEquals(0)
        rule.onAllNodesWithText("⚙").assertCountEquals(0)
        rule.onAllNodesWithText("↻").assertCountEquals(0)
    }

    @Test fun return_to_current_week_fab_only_appears_when_viewed_week_differs() {
        var selected = 0
        rule.setContent {
            TimetableScreen(
                state = fullState(naturalWeek = 5),
                onPrevWeek = {},
                onNextWeek = {},
                onWeekSelected = { selected = it },
            )
        }
        rule.onNodeWithContentDescription("返回本周").performClick()
        rule.waitForIdle()
        assertEquals(5, selected)
    }

    @Test fun return_to_current_week_fab_is_hidden_on_current_or_outside_semester() {
        val current = androidx.compose.runtime.mutableStateOf(fullState(naturalWeek = 2))
        rule.setContent { TimetableScreen(current.value, {}, {}, {}) }
        rule.onAllNodesWithTag("return_to_current_week").assertCountEquals(0)
        current.value = fullState(naturalWeek = null)
        rule.waitForIdle()
        rule.onAllNodesWithTag("return_to_current_week").assertCountEquals(0)
    }

    @Test fun screen_school_block_click_forwards_exact_meeting_id() {
        var got: MeetingId? = null
        rule.setContent {
            TimetableScreen(
                state = fullState(placedSchool = placedSchool("m9")),
                onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {},
                onSchoolBlockClick = { got = it },
            )
        }
        rule.onNodeWithTag("school_block:m9").performClick()
        rule.waitForIdle()
        assertEquals(MeetingId("m9"), got)
    }

    @Test fun manual_block_does_not_open_school_course_detail() {
        var schoolClick: MeetingId? = null
        rule.setContent {
            TimetableScreen(
                state = fullState(placedManual = placedManual("i9")),
                onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {},
                onSchoolBlockClick = { schoolClick = it },
            )
        }
        rule.onNodeWithTag("manual_block:i9").performClick()
        rule.waitForIdle()
        assertNull(schoolClick)
    }

    @Test fun empty_state_has_no_dashboard() {
        rule.setContent { TimetableScreen(state = TimetableUiState(isLoading = false), onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {}) }
        rule.onAllNodesWithText("暂无课表").assertCountEquals(1)
        rule.onAllNodesWithTag("timetable_grid").assertCountEquals(0)
        rule.onAllNodesWithTag("prev_week").assertCountEquals(0)
    }
}
