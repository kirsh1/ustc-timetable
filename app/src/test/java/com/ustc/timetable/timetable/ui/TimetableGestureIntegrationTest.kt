package com.ustc.timetable.timetable.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.LongPressDraft
import com.ustc.timetable.timetable.layout.TimedBlock
import com.ustc.timetable.timetable.layout.TimelineAxis
import com.ustc.timetable.timetable.layout.WeeklyTimetableGrid
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class TimetableGestureIntegrationTest {
    @get:Rule val rule = createComposeRule()

    private val axis = TimelineAxis(LocalTime.of(7, 50), LocalTime.of(21, 55))
    private val periods = listOf(
        PeriodTime(1, LocalTime.of(7, 50), LocalTime.of(8, 35)),
        PeriodTime(2, LocalTime.of(8, 40), LocalTime.of(9, 25)),
        PeriodTime(3, LocalTime.of(9, 45), LocalTime.of(10, 30)),
    )

    private fun block(school: Boolean): TimedBlock = object : TimedBlock {
        override val colorKey = if (school) "school" else "manual"
        override val meetingId = if (school) MeetingId("m1") else null
        override val manualItemId = if (school) null else ManualItemId("i1")
        override val weekday = 3
        override val start = LocalTime.of(9, 45)
        override val endInclusive = LocalTime.of(10, 30)
        override val weeks = WeekPattern.range(1, 20)
        override val title = if (school) "学校课程" else "手动课程"
        override val location = "TH-A301"
        override val teacherNames = if (school) listOf("教师甲") else emptyList()
    }

    private fun render(
        school: List<TimedBlock> = emptyList(),
        manual: List<TimedBlock> = emptyList(),
        onSchool: (MeetingId) -> Unit = {},
        onManual: (ManualItemId) -> Unit = {},
        onEmpty: (LongPressDraft) -> Unit = {},
        onVertical: (VerticalOverviewAction) -> Unit = {},
    ) {
        rule.setContent {
            Box(Modifier.requiredSize(400.dp, 800.dp).testTag("gesture_host")) {
                WeeklyTimetableGrid(
                    weekDates = LocalDateRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13)),
                    axis = axis,
                    periodStarts = periods.map { it.start },
                    placedSchool = WeeklyTimetableLayout.place(school, axis),
                    placedManual = WeeklyTimetableLayout.place(manual, axis),
                    showNonCurrentWeek = false,
                    viewedWeek = 2,
                    nowLine = null,
                    today = null,
                    onSchoolBlockClick = onSchool,
                    onManualBlockClick = onManual,
                    onEmptyLongPress = onEmpty,
                    onVerticalOverviewAction = onVertical,
                    periods = periods,
                )
            }
        }
    }

    @Test fun downward_grid_drag_expands_overview_once() {
        val actions = mutableListOf<VerticalOverviewAction>()
        render(onVertical = actions::add)
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            moveBy(Offset(0f, 80f))
            moveBy(Offset(0f, 40f))
            up()
        }
        assertEquals(listOf(VerticalOverviewAction.EXPAND), actions)
    }

    @Test fun upward_grid_drag_collapses_overview_once() {
        val actions = mutableListOf<VerticalOverviewAction>()
        render(onVertical = actions::add)
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            moveBy(Offset(0f, -80f))
            up()
        }
        assertEquals(listOf(VerticalOverviewAction.COLLAPSE), actions)
    }

    @Test fun horizontal_drag_does_not_toggle_overview_or_create_manual_item() {
        var longPresses = 0
        val actions = mutableListOf<VerticalOverviewAction>()
        render(onEmpty = { longPresses++ }, onVertical = actions::add)
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            moveBy(Offset(100f, 1f))
            up()
        }
        assertEquals(emptyList<VerticalOverviewAction>(), actions)
        assertEquals(0, longPresses)
    }

    @Test fun card_tap_dispatches_once_and_card_long_press_is_consumed() {
        var schoolClicks = 0
        var emptyLongPresses = 0
        render(school = listOf(block(true)), onSchool = { schoolClicks++ }, onEmpty = { emptyLongPresses++ })
        val card = rule.onNodeWithTag("school_block:m1")
        card.performTouchInput { click(center) }
        card.performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }
        assertEquals(1, schoolClicks)
        assertEquals(0, emptyLongPresses)
    }

    @Test fun manual_card_long_press_is_consumed_without_editor_click() {
        var manualClicks = 0
        var emptyLongPresses = 0
        render(manual = listOf(block(false)), onManual = { manualClicks++ }, onEmpty = { emptyLongPresses++ })
        rule.onNodeWithTag("manual_block:i1").performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }
        assertEquals(0, manualClicks)
        assertEquals(0, emptyLongPresses)
    }

    @Test fun empty_long_press_dispatches_once() {
        var longPresses = 0
        render(onEmpty = { longPresses++ })
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }
        assertEquals(1, longPresses)
    }

    @Test fun equal_diagonal_beyond_slop_releases_without_any_action() {
        var longPresses = 0
        val actions = mutableListOf<VerticalOverviewAction>()
        render(onEmpty = { longPresses++ }, onVertical = actions::add)
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            moveBy(Offset(80f, 80f))
            advanceEventTime(1_000L)
            up()
        }
        assertEquals(0, longPresses)
        assertEquals(emptyList<VerticalOverviewAction>(), actions)
    }
}
