package com.ustc.timetable.manual

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.ustc.timetable.scheduleprofile.LocalTimeRange
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManualItemEditorSheetTest {

    @get:Rule val rule = createComposeRule()

    private val dayWindow = LocalTimeRange(LocalTime.of(7, 50), LocalTime.of(21, 55))

    private fun validDraft(
        mode: WeekMode = WeekMode.CURRENT_ONLY,
        customWeeks: Set<Int> = emptySet(),
    ) = EditorDraft(
        title = "组会",
        location = "教室 A",
        note = "带材料",
        weekday = 3,
        startTime = LocalTime.of(14, 23),
        endTime = LocalTime.of(15, 8),
        mode = mode,
        continuousStart = 3,
        continuousEnd = 8,
        customWeeks = customWeeks,
    )

    private fun content(
        draft: EditorDraft = validDraft(),
        viewedWeek: Int = 5,
        totalWeeks: Int = 20,
        validationError: String? = null,
        canDelete: Boolean = false,
        onDraftChange: (EditorDraft) -> Unit = {},
    ) {
        rule.setContent {
            ManualItemEditorContent(
                draft = draft,
                viewedWeek = viewedWeek,
                totalWeeks = totalWeeks,
                dayWindow = dayWindow,
                validationError = validationError,
                canDelete = canDelete,
                onDraftChange = onDraftChange,
                onSave = {},
                onDeleteRequest = {},
                onDismiss = {},
            )
        }
        rule.waitForIdle()
    }

    @Test fun sheet_has_title_location_note_fields() {
        content()
        rule.onNodeWithTag("editor_title").assertExists()
        rule.onNodeWithTag("editor_location").assertExists()
        rule.onNodeWithTag("editor_note").assertExists()
    }

    @Test fun sheet_has_seven_weekday_choices() {
        content()
        for (weekday in 1..7) rule.onNodeWithTag("weekday_$weekday").assertExists()
        for (label in listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")) {
            rule.onAllNodesWithText(label, useUnmergedTree = true).assertCountEquals(1)
        }
    }

    @Test fun sheet_has_three_week_modes() {
        content()
        rule.onNodeWithTag("week_mode_current_only").assertExists()
        rule.onNodeWithTag("week_mode_continuous").assertExists()
        rule.onNodeWithTag("week_mode_custom").assertExists()
    }

    @Test fun current_only_shows_viewed_week() {
        content(viewedWeek = 7)
        rule.onAllNodesWithText("第 7 周", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun continuous_mode_shows_range_controls() {
        content(draft = validDraft(mode = WeekMode.CONTINUOUS))
        rule.onNodeWithTag("continuous_start").assertExists()
        rule.onNodeWithTag("continuous_end").assertExists()
        rule.onAllNodesWithText("第 3 周", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("第 8 周", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun custom_mode_shows_exact_totalWeeks_choices() {
        content(draft = validDraft(mode = WeekMode.CUSTOM), totalWeeks = 6)
        for (week in 1..6) rule.onNodeWithTag("week_$week", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithTag("week_7", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun custom_week_click_updates_callback() {
        var changed: EditorDraft? = null
        content(
            draft = validDraft(mode = WeekMode.CUSTOM, customWeeks = setOf(2)),
            totalWeeks = 6,
            onDraftChange = { changed = it },
        )
        rule.onNodeWithTag("editor_content")
            .performScrollToNode(hasTestTag("custom_weeks_grid"))
        rule.onNodeWithTag("week_4")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        assertEquals(setOf(2, 4), changed!!.customWeeks)
    }

    @Test fun save_disabled_when_title_blank() {
        content(validationError = "请填写标题")
        rule.onNodeWithTag("editor_save").assertIsNotEnabled()
    }

    @Test fun save_disabled_when_time_invalid() {
        content(validationError = "结束需晚于开始")
        rule.onNodeWithTag("editor_save").assertIsNotEnabled()
    }

    @Test fun save_disabled_when_custom_empty() {
        content(validationError = "请选择周次")
        rule.onNodeWithTag("editor_save").assertIsNotEnabled()
    }

    @Test fun save_enabled_when_valid() {
        content(validationError = null)
        rule.onNodeWithTag("editor_save").assertIsEnabled()
    }

    @Test fun delete_hidden_for_new() {
        content(canDelete = false)
        rule.onAllNodesWithTag("editor_delete").assertCountEquals(0)
    }

    @Test fun delete_visible_for_existing() {
        content(canDelete = true)
        rule.onNodeWithTag("editor_delete").assertExists()
    }

    @Test fun validation_message_rendered() {
        content(validationError = "结束需晚于开始")
        rule.onAllNodesWithText("结束需晚于开始", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun edited_arbitrary_minute_is_preserved() {
        val changed = applyPickedTime(validDraft(), editingStart = true, hour = 14, minute = 23)
        assertEquals(LocalTime.of(14, 23), changed.startTime)
        assertTrue(changed.startTime.minute % 5 != 0)
    }
}
