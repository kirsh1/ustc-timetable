package com.ustc.timetable.scheduleprofile

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProfileEditorTest {
    @get:Rule val rule = createComposeRule()

    @Test fun profile_editor_has_periods_1_to_13() {
        rule.setContent { ProfileEditorScreen(profile(), onSave = {}, onBack = {}) }
        (1..13).forEach { period ->
            rule.onNodeWithTag("profile_period_list").performScrollToNode(hasText("第${period}节"))
            rule.onAllNodesWithText("第${period}节")[0].assertExists()
        }
    }

    @Test fun profile_editor_uses_custom_working_profile_values() {
        val custom = profile().copy(periods = periods().mapIndexed { i, p -> if (i == 0) PeriodTime(1, LocalTime.of(8, 12), LocalTime.of(8, 57)) else p })
        rule.setContent { ProfileEditorScreen(custom, onSave = {}, onBack = {}) }
        rule.onAllNodesWithText("08:12").assertCountEquals(1)
        rule.onAllNodesWithText("08:57").assertCountEquals(1)
    }

    @Test fun editor_rejects_end_not_after_start() {
        assertFalse(ProfileEditorValidator.isValid(periods().mapIndexed { i, p -> if (i == 0) EditablePeriod(1, "08:00", "08:00") else p.toEditable() }))
    }

    @Test fun editor_rejects_overlapping_or_touching_periods() {
        val edited = periods().map(PeriodTime::toEditable).toMutableList(); edited[1] = EditablePeriod(2, edited[1].start, edited[0].end)
        assertFalse(ProfileEditorValidator.isValid(edited))
    }

    @Test fun editor_accepts_strictly_separated_thirteen_periods() { assertTrue(ProfileEditorValidator.isValid(periods().map(PeriodTime::toEditable))) }

    @Test fun editor_back_returns_settings() {
        var calls = 0; rule.setContent { ProfileEditorScreen(profile(), onSave = {}, onBack = { calls++ }) }
        rule.onNodeWithTag("profile_editor_back").performClick(); rule.waitForIdle(); assertEquals(1, calls)
    }

    @Test fun editor_back_uses_vector_icon_instead_of_unicode_glyph() {
        rule.setContent { ProfileEditorScreen(profile(), onSave = {}, onBack = {}) }
        rule.onNodeWithContentDescription("返回").assertExists()
        rule.onAllNodesWithText("‹").assertCountEquals(0)
    }

    private fun profile() = ScheduleProfile("custom", "自定义", false, periods())
    private fun periods(): List<PeriodTime> = (1..13).map { i ->
        val start = LocalTime.of(7 + i, 0); PeriodTime(i, start, start.plusMinutes(45))
    }
}
