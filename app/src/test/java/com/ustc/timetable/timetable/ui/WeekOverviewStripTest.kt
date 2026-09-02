package com.ustc.timetable.timetable.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeekOverviewStripTest {
    @get:Rule val rule = createComposeRule()

    private val profile = ScheduleProfile(
        "profile.test.overview",
        "overview",
        false,
        (1..13).map { PeriodTime(it, LocalTime.of(7, 0).plusMinutes((it - 1) * 50L), LocalTime.of(7, 45).plusMinutes((it - 1) * 50L)) },
    )
    private val axis = WeeklyTimetableLayout.axisOf(profile)

    private fun block(id: String, weeks: WeekPattern, weekday: Int = 2) = UiTimedBlock(
        colorKey = "s:$id",
        meetingId = MeetingId(id),
        manualItemId = null,
        weekday = weekday,
        start = LocalTime.of(9, 0),
        endInclusive = LocalTime.of(10, 0),
        weeks = weeks,
        title = id,
        location = "",
        teacherNames = emptyList(),
    )

    private fun semester(id: String = "s", weeks: Int = 4): Semester =
        SemesterDefaults.AUTUMN_2026(id = id, profileId = profile.id).copy(totalWeeks = weeks)

    private fun state(id: String = "s", viewedWeek: Int = 2): TimetableUiState {
        val sem = semester(id)
        val overview = buildWeekOverviewPages(
            sem,
            listOf(block("w1", WeekPattern.of(1)), block("w3", WeekPattern.of(3))),
            axis,
        )
        val pages = (1..sem.totalWeeks).map { week ->
            val start = sem.week1Start.plusWeeks((week - 1).toLong())
            TimetableWeekPageUiState(week, LocalDateRange(start, start.plusDays(6)), emptyList(), emptyList(), null)
        }
        return TimetableUiState(
            semester = sem,
            viewedWeek = viewedWeek,
            naturalWeek = 3,
            profile = profile,
            weekPages = pages,
            weekOverviewPages = overview,
            isLoading = false,
        )
    }

    private fun screen(state: TimetableUiState = state(), onWeekSelected: (Int) -> Unit = {}) {
        rule.setContent {
            TimetableScreen(
                state = state,
                onPrevWeek = {},
                onNextWeek = {},
                onWeekSelected = onWeekSelected,
            )
        }
        rule.waitForIdle()
    }

    @Test fun overview_is_collapsed_by_default() {
        screen()
        rule.onAllNodesWithTag("week_overview_strip").assertCountEquals(0)
    }

    @Test fun overview_toggle_expands_and_collapses() {
        screen()
        rule.onNodeWithTag("week_overview_toggle").performClick()
        rule.onAllNodesWithTag("week_overview_strip").assertCountEquals(1)
        rule.onNodeWithTag("week_overview_toggle").performClick()
        rule.onAllNodesWithTag("week_overview_strip").assertCountEquals(0)
    }

    @Test fun overview_card_count_equals_totalWeeks() {
        screen()
        rule.onNodeWithTag("week_overview_toggle").performClick()
        for (week in 1..4) rule.onAllNodesWithTag("week_overview_card:$week").assertCountEquals(1)
    }

    @Test fun selected_week_is_highlighted() {
        screen()
        rule.onNodeWithTag("week_overview_toggle").performClick()
        rule.onAllNodesWithTag("week_overview_selected:2", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag("week_overview_selected:1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun clicking_week_changes_viewedWeek() {
        var selected = 0
        screen(onWeekSelected = { selected = it })
        rule.onNodeWithTag("week_overview_toggle").performClick()
        rule.onNodeWithTag("week_overview_card:4").performClick()
        rule.waitForIdle()
        assertEquals(4, selected)
    }

    @Test fun click_keeps_overview_expanded() {
        screen()
        rule.onNodeWithTag("week_overview_toggle").performClick()
        rule.onNodeWithTag("week_overview_card:4").performClick()
        rule.onAllNodesWithTag("week_overview_strip").assertCountEquals(1)
    }

    @Test fun each_week_uses_its_own_active_blocks() {
        val pages = buildWeekOverviewPages(
            semester(),
            listOf(block("only-one", WeekPattern.of(1)), block("only-three", WeekPattern.of(3))),
            axis,
        )
        assertEquals(listOf("only-one"), pages[0].placedBlocks.map { it.block.meetingId?.value })
        assertEquals(emptyList<String>(), pages[1].placedBlocks.mapNotNull { it.block.meetingId?.value })
        assertEquals(listOf("only-three"), pages[2].placedBlocks.map { it.block.meetingId?.value })
    }

    @Test fun showNonCurrentWeek_does_not_add_ghosts_to_minimap() {
        val pages = buildWeekOverviewPages(semester(), listOf(block("week-one", WeekPattern.of(1))), axis)
        assertEquals(1, pages[0].placedBlocks.size)
        assertEquals(0, pages[1].placedBlocks.size)
        assertEquals(0, pages[2].placedBlocks.size)
    }

    @Test fun history_and_future_semesters_build_overviews() {
        val history = semester("history", 18).copy(
            week1Start = LocalDate.of(2024, 9, 2),
            startDate = LocalDate.of(2024, 9, 1),
            endDate = LocalDate.of(2025, 1, 31),
        )
        val future = semester("future", 20).copy(
            week1Start = LocalDate.of(2028, 9, 4),
            startDate = LocalDate.of(2028, 9, 3),
            endDate = LocalDate.of(2029, 2, 2),
        )
        assertEquals(18, buildWeekOverviewPages(history, emptyList(), axis).size)
        assertEquals(20, buildWeekOverviewPages(future, emptyList(), axis).size)
    }

    @Test fun overview_state_is_not_persisted() {
        assertFalse(TimetableUiState::class.java.declaredFields.any { it.name == "overviewExpanded" })
    }

    @Test fun semester_change_resets_overview_to_collapsed() {
        val current = mutableStateOf(state("A"))
        rule.setContent {
            TimetableScreen(current.value, {}, {}, {})
        }
        rule.onNodeWithTag("week_overview_toggle").performClick()
        rule.onAllNodesWithTag("week_overview_strip").assertCountEquals(1)
        current.value = state("B")
        rule.waitForIdle()
        rule.onAllNodesWithTag("week_overview_strip").assertCountEquals(0)
    }
}
