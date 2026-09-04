package com.ustc.timetable.timetable.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeeklyTimetableViewportTest {
    @get:Rule val rule = createComposeRule()

    private val officialAxis = TimelineAxis(LocalTime.of(7, 50), LocalTime.of(21, 55))
    private val officialPeriodStarts = listOf(
        LocalTime.of(7, 50), LocalTime.of(8, 40), LocalTime.of(9, 45), LocalTime.of(10, 35),
        LocalTime.of(11, 25), LocalTime.of(14, 0), LocalTime.of(14, 50), LocalTime.of(15, 55),
        LocalTime.of(16, 45), LocalTime.of(17, 35), LocalTime.of(19, 30), LocalTime.of(20, 20),
        LocalTime.of(21, 10),
    )
    private val weekDates = LocalDateRange(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13))

    private fun block(id: String, start: LocalTime, end: LocalTime): TimedBlock = object : TimedBlock {
        override val colorKey = id
        override val meetingId = MeetingId(id)
        override val manualItemId: ManualItemId? = null
        override val weekday = 3
        override val start = start
        override val endInclusive = end
        override val weeks = WeekPattern.range(1, 20)
        override val title = "课程"
        override val location = "教室"
        override val teacherNames = emptyList<String>()
    }

    private fun setViewport(
        width: Dp = 400.dp,
        height: Dp = 800.dp,
        axis: TimelineAxis = officialAxis,
        periodStarts: List<LocalTime> = officialPeriodStarts,
        blocks: List<TimedBlock> = emptyList(),
        nowLine: LocalTime? = null,
    ) {
        val placed = WeeklyTimetableLayout.place(blocks, axis)
        rule.setContent {
            Box(Modifier.requiredSize(width, height).testTag("viewport")) {
                WeeklyTimetableGrid(
                    weekDates = weekDates,
                    axis = axis,
                    periodStarts = periodStarts,
                    placedSchool = placed,
                    placedManual = emptyList(),
                    showNonCurrentWeek = false,
                    viewedWeek = 2,
                    nowLine = nowLine,
                    today = null,
                    onSchoolBlockClick = {},
                    onManualBlockClick = {},
                    onEmptyLongPress = {},
                )
            }
        }
    }

    private fun assertClose(label: String, expected: Float, actual: Float) {
        assertTrue("$label expected=$expected actual=$actual", kotlin.math.abs(expected - actual) <= 2f)
    }

    @Test fun gutter_and_grid_have_identical_vertical_bounds() {
        setViewport()
        val gutter = rule.onNodeWithTag("time_gutter").fetchSemanticsNode().boundsInRoot
        val grid = rule.onNodeWithTag("timetable_grid").fetchSemanticsNode().boundsInRoot
        assertClose("top", grid.top, gutter.top)
        assertClose("bottom", grid.bottom, gutter.bottom)
    }

    @Test fun time_labels_keep_a_small_inset_from_the_screen_edge() {
        setViewport()
        val viewport = rule.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithTag("time_boundary:07:50").fetchSemanticsNode().boundsInRoot
        assertTrue("viewport=$viewport label=$label", label.left >= viewport.left + 4.dp.value)
    }

    @Test fun period3_card_top_aligns_with_0945_label() {
        setViewport(blocks = listOf(block("period3", LocalTime.of(9, 45), LocalTime.of(10, 30))))
        val card = rule.onNodeWithTag("school_block:period3").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithTag("time_boundary:09:45").fetchSemanticsNode().boundsInRoot
        assertClose("09:45", card.top - 1f, label.center.y)
    }

    @Test fun custom_profile_card_and_tick_share_axis() {
        val axis = TimelineAxis(LocalTime.of(7, 53), LocalTime.of(21, 57))
        val time = LocalTime.of(14, 23)
        setViewport(
            axis = axis,
            periodStarts = listOf(axis.start, time),
            blocks = listOf(block("custom", time, LocalTime.of(15, 8))),
        )
        val card = rule.onNodeWithTag("school_block:custom").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithTag("time_boundary:14:23").fetchSemanticsNode().boundsInRoot
        assertClose("custom tick", card.top - 1f, label.center.y)
    }

    @Test fun homepage_does_not_render_now_line() {
        setViewport(nowLine = LocalTime.of(9, 45))
        rule.onAllNodesWithTag("now_line").assertCountEquals(0)
    }

    @Test fun axis_end_2155_label_exists_and_is_inside_body() {
        setViewport()
        val body = rule.onNodeWithTag("timetable_body").fetchSemanticsNode().boundsInRoot
        val label = rule.onNodeWithTag("time_boundary:21:55").fetchSemanticsNode().boundsInRoot
        assertTrue("body=$body label=$label", label.top >= body.top && label.bottom <= body.bottom)
    }

    @Test fun evening_labels_are_not_clipped() {
        setViewport()
        val body = rule.onNodeWithTag("timetable_body").fetchSemanticsNode().boundsInRoot
        for (time in listOf("19:30", "20:20", "21:10", "21:55")) {
            val label = rule.onNodeWithTag("time_boundary:$time").fetchSemanticsNode().boundsInRoot
            assertTrue("$time body=$body label=$label", label.top >= body.top && label.bottom <= body.bottom)
        }
    }

    @Test fun full_height_shows_complete_axis() {
        setViewport(blocks = listOf(block("full", officialAxis.start, officialAxis.endInclusive)))
        val body = rule.onNodeWithTag("timetable_body").fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("school_block:full").fetchSemanticsNode().boundsInRoot
        assertTrue("body=$body card=$card", card.top >= body.top && card.bottom <= body.bottom)
    }

    @Test fun compact_height_still_shows_complete_axis() {
        setViewport(
            width = 360.dp,
            height = 560.dp,
            blocks = listOf(block("compact", officialAxis.start, officialAxis.endInclusive)),
        )
        val body = rule.onNodeWithTag("timetable_body").fetchSemanticsNode().boundsInRoot
        val top = rule.onNodeWithTag("time_boundary:07:50").fetchSemanticsNode().boundsInRoot
        val bottom = rule.onNodeWithTag("time_boundary:21:55").fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("school_block:compact").fetchSemanticsNode().boundsInRoot
        assertTrue("body=$body top=$top bottom=$bottom card=$card", top.top >= body.top && bottom.bottom <= body.bottom && card.bottom <= body.bottom)
    }

    @Test fun compact_height_keeps_seven_columns() {
        setViewport(width = 360.dp, height = 560.dp)
        for (day in 0..6) rule.onAllNodesWithTag("header_$day").assertCountEquals(1)
    }

    @Test fun no_extra_spacer_exists_between_time_rail_and_monday() {
        setViewport()
        val rail = rule.onNodeWithTag("time_gutter").fetchSemanticsNode().boundsInRoot
        val monday = rule.onNodeWithTag("header_0").fetchSemanticsNode().boundsInRoot
        assertTrue("rail=$rail monday=$monday", kotlin.math.abs(rail.right - monday.left) <= 1f)
    }

    @Test fun released_width_is_distributed_equally_to_seven_days() {
        setViewport()
        val widths = (0..6).map { day ->
            rule.onNodeWithTag("header_$day").fetchSemanticsNode().boundsInRoot.width
        }
        widths.drop(1).forEach { width -> assertTrue("widths=$widths", kotlin.math.abs(width - widths.first()) <= 1f) }
    }

    @Test fun measured_rail_is_narrower_than_legacy_fixed_width_for_default_labels() {
        setViewport()
        val rail = rule.onNodeWithTag("time_gutter").fetchSemanticsNode().boundsInRoot
        assertTrue("rail=$rail", rail.width < 44.dp.value)
    }
}
