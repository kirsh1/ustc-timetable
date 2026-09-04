package com.ustc.timetable.semester

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performSemanticsAction
import com.ustc.timetable.timetable.domain.Term
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SemesterConfirmSheetTest {

    @get:Rule val rule = createComposeRule()

    @Test fun opening_and_canceling_end_picker_preserves_mode() {
        val before = validState()

        val pickerDate = before.dateForPicker(SemesterDateField.END_DATE)

        assertEquals(LocalDate.of(2027, 1, 15), pickerDate)
        assertEquals(SemesterEndDateMode.MANUAL, before.endDateMode)
        assertEquals(LocalDate.of(2027, 1, 15), before.manualEndDate)
    }

    @Test fun confirming_end_picker_selection_enters_manual() {
        val auto = SemesterConfirmEditor.fromDraft(validDraft().copy(endDate = null))

        val confirmed = auto.withConfirmedDate(
            SemesterDateField.END_DATE,
            LocalDate.of(2027, 1, 15),
        )

        assertEquals(SemesterEndDateMode.MANUAL, confirmed.endDateMode)
        assertEquals(LocalDate.of(2027, 1, 15), confirmed.manualEndDate)
    }

    @Test fun auto_end_date_status_updates_with_week_count() {
        val editorState = mutableStateOf(
            SemesterConfirmEditor.fromDraft(
                validDraft().copy(
                    startDate = LocalDate.of(2026, 8, 31),
                    week1Start = LocalDate.of(2026, 8, 31),
                    endDate = null,
                ),
            ),
        )
        rule.setContent {
            SemesterConfirmContent(
                state = editorState.value,
                validation = SemesterConfirmEditor.validate(editorState.value),
                onStateChange = { editorState.value = it },
                onOpenDatePicker = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        rule.onAllNodesWithText("2027-01-17", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("自动计算", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag("semester_confirm_total_weeks").performTextReplacement("21")

        assertEquals(LocalDate.of(2027, 1, 24), SemesterConfirmEditor.effectiveEndDate(editorState.value))
        rule.onAllNodesWithText("2027-01-24", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun restore_auto_action_recomputes_and_removes_manual_action() {
        val editorState = mutableStateOf(
            validState().copy(week1Start = LocalDate.of(2026, 8, 31)),
        )
        rule.setContent {
            SemesterConfirmContent(
                state = editorState.value,
                validation = SemesterConfirmEditor.validate(editorState.value),
                onStateChange = { editorState.value = it },
                onOpenDatePicker = {},
                onConfirm = {},
                onCancel = {},
            )
        }

        rule.onNodeWithTag("semester_confirm_restore_auto_end").performScrollTo().performClick()

        assertEquals(SemesterEndDateMode.AUTO, editorState.value.endDateMode)
        assertEquals(LocalDate.of(2027, 1, 17), SemesterConfirmEditor.effectiveEndDate(editorState.value))
        rule.onNodeWithTag("semester_confirm_restore_auto_end").assertDoesNotExist()
    }

    @Test fun recognized_manual_mode_survives_saveable_restoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            SemesterConfirmSheet(validDraft(), onConfirm = {}, onCancel = {})
        }

        restoration.emulateSavedInstanceStateRestore()

        rule.onAllNodesWithText("已手动修改", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag("semester_confirm_restore_auto_end").assertExists()
    }

    @Test fun auto_mode_survives_saveable_restoration() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            SemesterConfirmSheet(
                validDraft().copy(
                    startDate = LocalDate.of(2026, 8, 31),
                    week1Start = LocalDate.of(2026, 8, 31),
                    endDate = null,
                ),
                onConfirm = {},
                onCancel = {},
            )
        }

        restoration.emulateSavedInstanceStateRestore()

        rule.onAllNodesWithText("自动计算", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("2027-01-17", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag("semester_confirm_restore_auto_end").assertDoesNotExist()
    }

    @Test fun all_seven_fields_are_present() {
        content(validState())

        for (tag in listOf(
            "semester_confirm_display_name",
            "semester_confirm_academic_year",
            "semester_confirm_term_autumn",
            "semester_confirm_term_spring",
            "semester_confirm_term_summer",
            "semester_confirm_start_date",
            "semester_confirm_week1_start",
            "semester_confirm_total_weeks",
            "semester_confirm_end_date",
        )) rule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
        rule.onAllNodesWithText("确认学期信息", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText(
            "请核对学校课表所属学期，未识别的项目需要手动补充。",
            useUnmergedTree = true,
        ).assertCountEquals(1)
    }

    @Test fun partial_draft_prefills_recognized_values_and_keeps_missing_blank() {
        rule.setContent {
            SemesterConfirmSheet(
                draft = validDraft().copy(
                    displayName = "已识别学期",
                    term = null,
                    week1Start = null,
                    endDate = null,
                ),
                onConfirm = {},
                onCancel = {},
            )
        }

        rule.onNodeWithTag("semester_confirm_sheet", useUnmergedTree = true).assertExists()
        rule.onAllNodesWithText("已识别学期", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("20", useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag("semester_confirm_term_autumn").assertIsNotSelected()
        rule.onNodeWithTag("semester_confirm_term_spring").assertIsNotSelected()
        rule.onNodeWithTag("semester_confirm_term_summer").assertIsNotSelected()
    }

    @Test fun term_has_exactly_three_choices_and_partial_term_is_selected() {
        content(validState().copy(term = Term.SPRING))

        rule.onNodeWithTag("semester_confirm_term_autumn").assertIsNotSelected()
        rule.onNodeWithTag("semester_confirm_term_spring").assertIsSelected()
        rule.onNodeWithTag("semester_confirm_term_summer").assertIsNotSelected()
    }

    @Test fun missing_term_has_no_default_selection() {
        content(validState().copy(term = null))

        rule.onNodeWithTag("semester_confirm_term_autumn").assertIsNotSelected()
        rule.onNodeWithTag("semester_confirm_term_spring").assertIsNotSelected()
        rule.onNodeWithTag("semester_confirm_term_summer").assertIsNotSelected()
    }

    @Test fun selecting_term_updates_editor_state() {
        var changed: SemesterConfirmEditorState? = null
        content(validState().copy(term = null), onStateChange = { changed = it })

        rule.onNodeWithTag("semester_confirm_term_summer").performClick()

        assertEquals(Term.SUMMER, changed?.term)
    }

    @Test fun invalid_form_disables_confirm() {
        content(validState().copy(displayName = ""))
        rule.onNodeWithTag("semester_confirm_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test fun valid_form_enables_confirm() {
        content(validState())
        rule.onNodeWithTag("semester_confirm_submit").performScrollTo().assertIsEnabled()
    }

    @Test fun total_weeks_input_preserves_invalid_raw_text() {
        var changed: SemesterConfirmEditorState? = null
        content(validState(), onStateChange = { changed = it })

        rule.onNodeWithTag("semester_confirm_total_weeks").performTextReplacement("20a")

        assertEquals("20a", changed?.totalWeeksText)
    }

    @Test fun date_fields_forward_exact_target() {
        var opened: SemesterDateField? = null
        content(validState(), onOpenDatePicker = { opened = it })

        rule.onNodeWithTag("semester_confirm_start_date").performScrollTo().performClick()
        assertEquals(SemesterDateField.START_DATE, opened)
        rule.onNodeWithTag("semester_confirm_week1_start").performScrollTo().performClick()
        assertEquals(SemesterDateField.WEEK1_START, opened)
        rule.onNodeWithTag("semester_confirm_end_date").performScrollTo().performClick()
        assertEquals(SemesterDateField.END_DATE, opened)
    }

    @Test fun date_field_opens_material_date_picker() {
        rule.setContent {
            SemesterConfirmSheet(validDraft(), onConfirm = {}, onCancel = {})
        }
        rule.onNodeWithTag("semester_confirm_start_date").performScrollTo().performClick()
        rule.onNodeWithTag("semester_date_picker", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun confirm_forwards_exact_meta_once_and_does_not_cancel() {
        var confirms = 0
        var confirmed: ConfirmedSemesterMeta? = null
        var cancels = 0
        rule.setContent {
            SemesterConfirmSheet(
                draft = validDraft(),
                onConfirm = { confirms++; confirmed = it },
                onCancel = { cancels++ },
            )
        }

        rule.onNodeWithTag("semester_confirm_submit").performScrollTo().performClick()

        assertEquals(1, confirms)
        assertEquals(SemesterConfirmEditor.validate(validState()).confirmed, confirmed)
        assertEquals(0, cancels)
    }

    @Test fun cancel_button_calls_cancel_only() {
        var confirms = 0
        var cancels = 0
        val state = validState()
        rule.setContent {
            SemesterConfirmContent(
                state = state,
                validation = SemesterConfirmEditor.validate(state),
                onStateChange = {},
                onOpenDatePicker = {},
                onConfirm = { confirms++ },
                onCancel = { cancels++ },
            )
        }

        rule.onNodeWithTag("semester_confirm_cancel").performScrollTo().performClick()

        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test fun dismiss_request_calls_cancel_only() {
        var confirms = 0
        var cancels = 0
        rule.setContent {
            SemesterConfirmSheet(
                draft = validDraft(),
                onConfirm = { confirms++ },
                onCancel = { cancels++ },
            )
        }

        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss)
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()

        assertEquals(0, confirms)
        assertEquals(1, cancels)
    }

    @Test fun recomposition_does_not_erase_user_edits() {
        val unrelated = mutableStateOf(0)
        rule.setContent {
            unrelated.value
            SemesterConfirmSheet(validDraft(), onConfirm = {}, onCancel = {})
        }
        rule.onNodeWithTag("semester_confirm_display_name").performTextReplacement("用户编辑")

        unrelated.value++
        rule.waitForIdle()

        rule.onAllNodesWithText("用户编辑", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun new_import_draft_replaces_previous_editor_state() {
        val draft = mutableStateOf(validDraft())
        rule.setContent {
            SemesterConfirmSheet(draft.value, onConfirm = {}, onCancel = {})
        }
        rule.onNodeWithTag("semester_confirm_display_name").performTextReplacement("用户编辑")

        draft.value = validDraft().copy(displayName = "新候选")
        rule.waitForIdle()

        rule.onAllNodesWithText("新候选", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("用户编辑", useUnmergedTree = true).assertCountEquals(0)
    }

    private fun content(
        state: SemesterConfirmEditorState,
        onStateChange: (SemesterConfirmEditorState) -> Unit = {},
        onOpenDatePicker: (SemesterDateField) -> Unit = {},
    ) {
        rule.setContent {
            SemesterConfirmContent(
                state = state,
                validation = SemesterConfirmEditor.validate(state),
                onStateChange = onStateChange,
                onOpenDatePicker = onOpenDatePicker,
                onConfirm = {},
                onCancel = {},
            )
        }
        rule.waitForIdle()
    }

    private fun validState() = SemesterConfirmEditorState(
        displayName = "2026-2027 秋季",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        startDate = LocalDate.of(2026, 8, 30),
        week1Start = LocalDate.of(2026, 9, 7),
        totalWeeksText = "20",
        manualEndDate = LocalDate.of(2027, 1, 15),
        endDateMode = SemesterEndDateMode.MANUAL,
    )

    private fun validDraft() = SemesterImportDraft(
        displayName = "2026-2027 秋季",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        week1Start = LocalDate.of(2026, 9, 7),
        totalWeeks = 20,
        startDate = LocalDate.of(2026, 8, 30),
        endDate = LocalDate.of(2027, 1, 15),
    )
}
