package com.ustc.timetable.semester

import com.ustc.timetable.timetable.domain.Term
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class SemesterEndDateMode { AUTO, MANUAL }

data class SemesterConfirmEditorState(
    val displayName: String,
    val academicYear: String,
    val term: Term?,
    val startDate: LocalDate?,
    val week1Start: LocalDate?,
    val totalWeeksText: String,
    @Deprecated("Task 2 removes this compatibility field; use manualEndDate/effectiveEndDate")
    val endDate: LocalDate?,
    val manualEndDate: LocalDate? = endDate,
    val endDateMode: SemesterEndDateMode = if (endDate == null) {
        SemesterEndDateMode.AUTO
    } else {
        SemesterEndDateMode.MANUAL
    },
)

data class SemesterConfirmErrors(
    val displayName: String? = null,
    val academicYear: String? = null,
    val term: String? = null,
    val startDate: String? = null,
    val week1Start: String? = null,
    val totalWeeks: String? = null,
    val endDate: String? = null,
    val dateOrder: String? = null,
)

data class SemesterConfirmValidation(
    val confirmed: ConfirmedSemesterMeta?,
    val errors: SemesterConfirmErrors,
) {
    val canConfirm: Boolean get() = confirmed != null
}

object SemesterConfirmEditor {
    fun fromDraft(draft: SemesterImportDraft) = SemesterConfirmEditorState(
        displayName = draft.displayName.orEmpty(),
        academicYear = draft.academicYear.orEmpty(),
        term = draft.term,
        startDate = draft.startDate,
        week1Start = draft.week1Start,
        totalWeeksText = draft.totalWeeks?.toString().orEmpty(),
        endDate = draft.endDate,
        manualEndDate = draft.endDate,
        endDateMode = if (draft.endDate == null) SemesterEndDateMode.AUTO else SemesterEndDateMode.MANUAL,
    )

    fun automaticEndDate(week1Start: LocalDate?, totalWeeksText: String): LocalDate? {
        val totalWeeks = totalWeeksText.trim().toIntOrNull()
        if (week1Start == null || totalWeeks == null || totalWeeks !in 1..63) return null
        return week1Start.plusWeeks(totalWeeks.toLong()).minusDays(1)
    }

    fun effectiveEndDate(state: SemesterConfirmEditorState): LocalDate? =
        when (state.endDateMode) {
            SemesterEndDateMode.AUTO -> automaticEndDate(state.week1Start, state.totalWeeksText)
            SemesterEndDateMode.MANUAL -> state.manualEndDate
        }

    fun withWeek1Start(
        state: SemesterConfirmEditorState,
        value: LocalDate,
    ): SemesterConfirmEditorState = state.copy(week1Start = value)

    fun withTotalWeeksText(
        state: SemesterConfirmEditorState,
        value: String,
    ): SemesterConfirmEditorState = state.copy(totalWeeksText = value)

    fun withManualEndDate(
        state: SemesterConfirmEditorState,
        value: LocalDate,
    ): SemesterConfirmEditorState = state.copy(
        endDate = value,
        manualEndDate = value,
        endDateMode = SemesterEndDateMode.MANUAL,
    )

    fun restoreAutomaticEndDate(state: SemesterConfirmEditorState): SemesterConfirmEditorState =
        state.copy(
            endDate = null,
            manualEndDate = null,
            endDateMode = SemesterEndDateMode.AUTO,
        )

    fun validate(state: SemesterConfirmEditorState): SemesterConfirmValidation {
        val weeks = state.totalWeeksText.trim().toIntOrNull()
        val effectiveEndDate = effectiveEndDate(state)
        val dateOrder = when {
            state.startDate != null && effectiveEndDate != null && state.startDate > effectiveEndDate ->
                "学期开始日期不得晚于结束日期"
            state.startDate != null && effectiveEndDate != null && state.week1Start != null &&
                state.week1Start !in state.startDate..effectiveEndDate ->
                "第1教学周日期必须位于学期起止日期内"
            else -> null
        }
        val errors = SemesterConfirmErrors(
            displayName = if (state.displayName.trim().isEmpty()) "学期名称不能为空" else null,
            academicYear = if (state.academicYear.trim().isEmpty()) "学年不能为空" else null,
            term = if (state.term == null) "请选择学期类型" else null,
            startDate = if (state.startDate == null) "请选择学期开始日期" else null,
            week1Start = when {
                state.week1Start == null -> "请选择第1教学周周一"
                state.week1Start.dayOfWeek != DayOfWeek.MONDAY -> "第1教学周必须从周一开始"
                else -> null
            },
            totalWeeks = if (weeks !in 1..63) "教学周数必须为 1–63" else null,
            endDate = if (effectiveEndDate == null) "请选择学期结束日期" else null,
            dateOrder = dateOrder,
        )
        val hasErrors = listOf(
            errors.displayName,
            errors.academicYear,
            errors.term,
            errors.startDate,
            errors.week1Start,
            errors.totalWeeks,
            errors.endDate,
            errors.dateOrder,
        ).any { it != null }
        val confirmed = if (hasErrors) null else ConfirmedSemesterMeta(
            displayName = state.displayName.trim(),
            academicYear = state.academicYear.trim(),
            term = requireNotNull(state.term),
            week1Start = requireNotNull(state.week1Start),
            totalWeeks = requireNotNull(weeks),
            startDate = requireNotNull(state.startDate),
            endDate = requireNotNull(effectiveEndDate),
        )
        return SemesterConfirmValidation(confirmed, errors)
    }
}

internal fun ConfirmedSemesterMeta.isValidSemesterConfirmation(): Boolean =
    SemesterConfirmEditor.validate(
        SemesterConfirmEditorState(
            displayName = displayName,
            academicYear = academicYear,
            term = term,
            startDate = startDate,
            week1Start = week1Start,
            totalWeeksText = totalWeeks.toString(),
            endDate = endDate,
            manualEndDate = endDate,
            endDateMode = SemesterEndDateMode.MANUAL,
        ),
    ).canConfirm

internal fun LocalDate.toDatePickerUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun Long.fromDatePickerUtcMillis(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
