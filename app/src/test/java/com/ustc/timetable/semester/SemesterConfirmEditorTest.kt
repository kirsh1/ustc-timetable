package com.ustc.timetable.semester

import com.ustc.timetable.timetable.domain.Term
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemesterConfirmEditorTest {

    @Test fun draft_prefills_every_recognized_field() {
        val state = SemesterConfirmEditor.fromDraft(
            SemesterImportDraft(
                displayName = "2026-2027 秋季",
                academicYear = "2026-2027",
                term = Term.SPRING,
                week1Start = LocalDate.of(2026, 9, 7),
                totalWeeks = 20,
                startDate = LocalDate.of(2026, 8, 30),
                endDate = LocalDate.of(2027, 1, 15),
            ),
        )

        assertEquals("2026-2027 秋季", state.displayName)
        assertEquals("2026-2027", state.academicYear)
        assertEquals(Term.SPRING, state.term)
        assertEquals(LocalDate.of(2026, 8, 30), state.startDate)
        assertEquals(LocalDate.of(2026, 9, 7), state.week1Start)
        assertEquals("20", state.totalWeeksText)
        assertEquals(LocalDate.of(2027, 1, 15), state.endDate)
    }

    @Test fun missing_draft_fields_remain_blank() {
        val state = SemesterConfirmEditor.fromDraft(
            SemesterImportDraft(null, null, null, null, null, null, null),
        )

        assertEquals("", state.displayName)
        assertEquals("", state.academicYear)
        assertNull(state.term)
        assertNull(state.startDate)
        assertNull(state.week1Start)
        assertEquals("", state.totalWeeksText)
        assertNull(state.endDate)
    }

    @Test fun no_autumn_2026_values_are_invented() {
        val state = SemesterConfirmEditor.fromDraft(
            SemesterImportDraft(null, null, null, null, null, null, null),
        )

        assertFalse(state.displayName.contains("2026"))
        assertNull(state.term)
        assertNull(state.week1Start)
        assertNull(state.startDate)
        assertNull(state.endDate)
    }

    @Test fun total_weeks_1_and_63_are_valid() {
        assertTrue(SemesterConfirmEditor.validate(validState(totalWeeks = "1")).canConfirm)
        assertTrue(SemesterConfirmEditor.validate(validState(totalWeeks = "63")).canConfirm)
    }

    @Test fun total_weeks_invalid_text_is_rejected() {
        for (raw in listOf("", "20a", "-1")) {
            val result = SemesterConfirmEditor.validate(validState(totalWeeks = raw))
            assertFalse("raw=$raw", result.canConfirm)
            assertEquals("教学周数必须为 1–63", result.errors.totalWeeks)
        }
    }

    @Test fun total_weeks_outside_1_to_63_is_rejected() {
        for (raw in listOf("0", "64")) {
            val result = SemesterConfirmEditor.validate(validState(totalWeeks = raw))
            assertFalse("raw=$raw", result.canConfirm)
            assertEquals("教学周数必须为 1–63", result.errors.totalWeeks)
        }
    }

    @Test fun any_required_field_missing_blocks_confirm() {
        val states = listOf(
            validState().copy(displayName = ""),
            validState().copy(academicYear = ""),
            validState().copy(term = null),
            validState().copy(startDate = null),
            validState().copy(week1Start = null),
            validState().copy(totalWeeksText = ""),
            validState().copy(endDate = null),
        )

        assertTrue(states.all { !SemesterConfirmEditor.validate(it).canConfirm })
    }

    @Test fun required_field_errors_use_frozen_copy() {
        val errors = SemesterConfirmEditor.validate(
            SemesterConfirmEditorState(" ", " ", null, null, null, "", null),
        ).errors

        assertEquals("学期名称不能为空", errors.displayName)
        assertEquals("学年不能为空", errors.academicYear)
        assertEquals("请选择学期类型", errors.term)
        assertEquals("请选择学期开始日期", errors.startDate)
        assertEquals("请选择第1教学周周一", errors.week1Start)
        assertEquals("教学周数必须为 1–63", errors.totalWeeks)
        assertEquals("请选择学期结束日期", errors.endDate)
    }

    @Test fun non_monday_week1_start_is_rejected() {
        val result = SemesterConfirmEditor.validate(
            validState().copy(week1Start = LocalDate.of(2026, 9, 8)),
        )

        assertFalse(result.canConfirm)
        assertEquals("第1教学周必须从周一开始", result.errors.week1Start)
    }

    @Test fun start_after_end_is_rejected() {
        val result = SemesterConfirmEditor.validate(
            validState().copy(
                startDate = LocalDate.of(2027, 1, 16),
                endDate = LocalDate.of(2027, 1, 15),
            ),
        )

        assertFalse(result.canConfirm)
        assertEquals("学期开始日期不得晚于结束日期", result.errors.dateOrder)
    }

    @Test fun week1_start_outside_date_range_is_rejected() {
        for (week1 in listOf(LocalDate.of(2026, 8, 24), LocalDate.of(2027, 1, 18))) {
            val result = SemesterConfirmEditor.validate(validState().copy(week1Start = week1))
            assertFalse(result.canConfirm)
            assertEquals("第1教学周日期必须位于学期起止日期内", result.errors.dateOrder)
        }
    }

    @Test fun valid_confirm_emits_exact_confirmed_metadata() {
        val result = SemesterConfirmEditor.validate(validState())

        assertTrue(result.canConfirm)
        assertEquals(
            ConfirmedSemesterMeta(
                displayName = "2026-2027 秋季",
                academicYear = "2026-2027",
                term = Term.AUTUMN,
                week1Start = LocalDate.of(2026, 9, 7),
                totalWeeks = 20,
                startDate = LocalDate.of(2026, 8, 30),
                endDate = LocalDate.of(2027, 1, 15),
            ),
            result.confirmed,
        )
    }

    @Test fun confirm_trims_outer_text_but_preserves_internal_text() {
        val result = SemesterConfirmEditor.validate(
            validState().copy(
                displayName = "  2026  秋季  ",
                academicYear = "  2026 - 2027  ",
                totalWeeksText = " 20 ",
            ),
        )

        assertEquals("2026  秋季", result.confirmed?.displayName)
        assertEquals("2026 - 2027", result.confirmed?.academicYear)
        assertEquals(20, result.confirmed?.totalWeeks)
    }

    @Test fun utc_date_roundtrip_is_timezone_independent() {
        val original = TimeZone.getDefault()
        try {
            val date = LocalDate.of(2026, 9, 7)
            for (zone in listOf("Asia/Shanghai", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(zone)))
                assertEquals(date, date.toDatePickerUtcMillis().fromDatePickerUtcMillis())
            }
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun date_picker_selection_updates_only_target_field() {
        val before = validState()
        val selected = LocalDate.of(2026, 9, 14)

        val after = before.withDate(SemesterDateField.WEEK1_START, selected)

        assertEquals(selected, after.week1Start)
        assertEquals(before.startDate, after.startDate)
        assertEquals(before.endDate, after.endDate)
        assertEquals(before.copy(week1Start = selected), after)
    }

    private fun validState(totalWeeks: String = "20") = SemesterConfirmEditorState(
        displayName = "2026-2027 秋季",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        startDate = LocalDate.of(2026, 8, 30),
        week1Start = LocalDate.of(2026, 9, 7),
        totalWeeksText = totalWeeks,
        endDate = LocalDate.of(2027, 1, 15),
    )
}
