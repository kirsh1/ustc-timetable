package com.ustc.timetable.timetable.ui

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.*
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import com.ustc.timetable.ui.theme.TimetableTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w400dp-h800dp")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class UiR4IntegrationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun long_room_code_wraps_without_ellipsis_when_two_lines_fit() {
        val block = UiSchoolTimedBlock("s", MeetingId("m"), 1, LocalTime.of(9, 0), LocalTime.of(12, 0),
            WeekPattern.of(1), "课", "TH-A301", listOf("教师"))
        rule.setContent {
            Box(Modifier.size(28.dp, 200.dp)) {
                BlockTexts.Content(block, CourseCardTextBudget(1, true, 0, false, locationMaxLines = 3), Color.Black)
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("TH-A301").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertTrue(layout.lineCount >= 2)
        assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) })
    }

    @Test fun applied_seed_keeps_school_manual_and_overview_content_across_recomposition() {
        val profile = ScheduleProfile("p", "test", false, (1..13).map {
            PeriodTime(it, LocalTime.of(6 + it, 0), LocalTime.of(6 + it, 45))
        })
        val semester = SemesterDefaults.AUTUMN_2026(id = "s", profileId = "p").copy(totalWeeks = 2)
        val school = UiSchoolTimedBlock("s:course", MeetingId("school"), 2, LocalTime.of(9, 0),
            LocalTime.of(12, 0), WeekPattern.range(1, 2), "课程", "TH-A301", listOf("教师"))
        val manual = UiManualTimedBlock("manual:item", ManualItemId("item"), 4, LocalTime.of(9, 0),
            LocalTime.of(12, 0), WeekPattern.range(1, 2), "事项", "地点", emptyList())
        val axis = WeeklyTimetableLayout.axisOf(profile)
        val pages = (1..2).map { week -> TimetableWeekPageUiState(week,
            WeekCalculator.weekRange(semester, week), WeeklyTimetableLayout.place(listOf(school), axis),
            WeeklyTimetableLayout.place(listOf(manual), axis), null) }
        val state = mutableStateOf(TimetableUiState(semester = semester, profile = profile,
            weekPages = pages, weekOverviewPages = buildWeekOverviewPages(semester, listOf(school, manual), axis), isLoading = false))
        rule.setContent { TimetableTheme { TimetableScreen(state.value, {}, {}, {}) } }
        rule.onNodeWithTag("week_overview_toggle").performClick()
        fun assertRendered() {
            listOf("school_block:school" to school.colorKey, "manual_block:item" to manual.colorKey,
                "week_overview_block:1:school:school" to school.colorKey,
                "week_overview_block:1:manual:item" to manual.colorKey).forEach { (tag, key) ->
                rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
            }
        }
        assertRendered()
        rule.runOnIdle { state.value = state.value.copy(coursePaletteSeed = 13L) }
        assertRendered()
        rule.runOnIdle { state.value = state.value.copy(viewedWeek = 2) }
        rule.waitForIdle()
        assertEquals(13L, state.value.coursePaletteSeed)
    }
}
