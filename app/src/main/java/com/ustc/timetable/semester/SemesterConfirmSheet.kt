package com.ustc.timetable.semester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.domain.Term
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal enum class SemesterDateField {
    START_DATE,
    WEEK1_START,
    END_DATE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterConfirmSheet(
    draft: SemesterImportDraft,
    onConfirm: (ConfirmedSemesterMeta) -> Unit,
    onCancel: () -> Unit,
) {
    var state by rememberSaveable(
        draft.displayName,
        draft.academicYear,
        draft.term,
        draft.startDate,
        draft.week1Start,
        draft.totalWeeks,
        draft.endDate,
        stateSaver = SemesterConfirmEditorStateSaver,
    ) { mutableStateOf(SemesterConfirmEditor.fromDraft(draft)) }
    var openDateField by rememberSaveable { mutableStateOf<SemesterDateField?>(null) }
    val validation = SemesterConfirmEditor.validate(state)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        modifier = Modifier.testTag("semester_confirm_sheet"),
    ) {
        SemesterConfirmContent(
            state = state,
            validation = validation,
            onStateChange = { state = it },
            onOpenDatePicker = { openDateField = it },
            onConfirm = { validation.confirmed?.let(onConfirm) },
            onCancel = onCancel,
        )
    }

    openDateField?.let { field ->
        key(field) {
            val pickerState = rememberDatePickerState(
                initialSelectedDateMillis = state.dateForPicker(field)?.toDatePickerUtcMillis(),
            )
            DatePickerDialog(
                onDismissRequest = { openDateField = null },
                confirmButton = {
                    TextButton(onClick = {
                        pickerState.selectedDateMillis?.fromDatePickerUtcMillis()?.let { selected ->
                            state = state.withConfirmedDate(field, selected)
                        }
                        openDateField = null
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { openDateField = null }) { Text("取消") }
                },
            ) {
                DatePicker(
                    state = pickerState,
                    modifier = Modifier.testTag("semester_date_picker"),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SemesterConfirmContent(
    state: SemesterConfirmEditorState,
    validation: SemesterConfirmValidation,
    onStateChange: (SemesterConfirmEditorState) -> Unit,
    onOpenDatePicker: (SemesterDateField) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("确认学期信息", style = MaterialTheme.typography.titleLarge)
        Text(
            "请核对学校课表所属学期，未识别的项目需要手动补充。",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = { onStateChange(state.copy(displayName = it)) },
            label = { Text("学期名称") },
            isError = validation.errors.displayName != null,
            supportingText = errorText(validation.errors.displayName),
            modifier = Modifier.fillMaxWidth().testTag("semester_confirm_display_name"),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.academicYear,
            onValueChange = { onStateChange(state.copy(academicYear = it)) },
            label = { Text("学年") },
            isError = validation.errors.academicYear != null,
            supportingText = errorText(validation.errors.academicYear),
            modifier = Modifier.fillMaxWidth().testTag("semester_confirm_academic_year"),
            singleLine = true,
        )

        Text("学期类型", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TERM_CHOICES.forEachIndexed { index, choice ->
                SegmentedButton(
                    selected = state.term == choice.term,
                    onClick = { onStateChange(state.copy(term = choice.term)) },
                    shape = SegmentedButtonDefaults.itemShape(index, TERM_CHOICES.size),
                    label = { Text(choice.label) },
                    modifier = Modifier.testTag("semester_confirm_term_${choice.tag}"),
                )
            }
        }
        validation.errors.term?.let { ErrorText(it) }

        SemesterDateButton(
            label = "学期开始日期",
            date = state.startDate,
            error = validation.errors.startDate,
            tag = "semester_confirm_start_date",
            onClick = { onOpenDatePicker(SemesterDateField.START_DATE) },
        )
        SemesterDateButton(
            label = "第1教学周周一",
            date = state.week1Start,
            error = validation.errors.week1Start,
            tag = "semester_confirm_week1_start",
            onClick = { onOpenDatePicker(SemesterDateField.WEEK1_START) },
        )
        OutlinedTextField(
            value = state.totalWeeksText,
            onValueChange = {
                onStateChange(SemesterConfirmEditor.withTotalWeeksText(state, it))
            },
            label = { Text("教学周数") },
            isError = validation.errors.totalWeeks != null,
            supportingText = errorText(validation.errors.totalWeeks),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("semester_confirm_total_weeks"),
            singleLine = true,
        )
        SemesterDateButton(
            label = "学期结束日期",
            date = SemesterConfirmEditor.effectiveEndDate(state),
            error = validation.errors.endDate,
            tag = "semester_confirm_end_date",
            onClick = { onOpenDatePicker(SemesterDateField.END_DATE) },
        )
        Text(
            text = if (state.endDateMode == SemesterEndDateMode.AUTO) "自动计算" else "已手动修改",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("semester_confirm_end_date_mode"),
        )
        if (state.endDateMode == SemesterEndDateMode.MANUAL) {
            TextButton(
                onClick = {
                    onStateChange(SemesterConfirmEditor.restoreAutomaticEndDate(state))
                },
                modifier = Modifier.testTag("semester_confirm_restore_auto_end"),
            ) {
                Text("恢复自动计算")
            }
        }
        validation.errors.dateOrder?.let { ErrorText(it) }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("semester_confirm_cancel"),
            ) { Text("取消") }
            Button(
                onClick = onConfirm,
                enabled = validation.canConfirm,
                modifier = Modifier.testTag("semester_confirm_submit"),
            ) { Text("确认并导入") }
        }
    }
}

@Composable
private fun SemesterDateButton(
    label: String,
    date: LocalDate?,
    error: String?,
    tag: String,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().testTag(tag),
        ) { Text(date?.format(DATE_FORMAT) ?: "请选择") }
        error?.let { ErrorText(it) }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

private fun errorText(error: String?): (@Composable () -> Unit)? =
    error?.let { { ErrorText(it) } }

internal fun SemesterConfirmEditorState.dateForPicker(field: SemesterDateField): LocalDate? = when (field) {
    SemesterDateField.START_DATE -> startDate
    SemesterDateField.WEEK1_START -> week1Start
    SemesterDateField.END_DATE -> SemesterConfirmEditor.effectiveEndDate(this)
}

internal fun SemesterConfirmEditorState.withConfirmedDate(
    field: SemesterDateField,
    date: LocalDate,
): SemesterConfirmEditorState = when (field) {
    SemesterDateField.START_DATE -> copy(startDate = date)
    SemesterDateField.WEEK1_START -> SemesterConfirmEditor.withWeek1Start(this, date)
    SemesterDateField.END_DATE -> SemesterConfirmEditor.withManualEndDate(this, date)
}

private data class TermChoice(val term: Term, val label: String, val tag: String)

private val TERM_CHOICES = listOf(
    TermChoice(Term.AUTUMN, "秋季", "autumn"),
    TermChoice(Term.SPRING, "春季", "spring"),
    TermChoice(Term.SUMMER, "夏季", "summer"),
)

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

private val SemesterConfirmEditorStateSaver = Saver<SemesterConfirmEditorState, List<String>>(
    save = { state ->
        listOf(
            state.displayName,
            state.academicYear,
            state.term?.name.orEmpty(),
            state.startDate?.toEpochDay()?.toString().orEmpty(),
            state.week1Start?.toEpochDay()?.toString().orEmpty(),
            state.totalWeeksText,
            state.manualEndDate?.toEpochDay()?.toString().orEmpty(),
            state.endDateMode.name,
        )
    },
    restore = { saved ->
        SemesterConfirmEditorState(
            displayName = saved[0],
            academicYear = saved[1],
            term = saved[2].takeIf(String::isNotEmpty)?.let(Term::valueOf),
            startDate = saved[3].toLongOrNull()?.let(LocalDate::ofEpochDay),
            week1Start = saved[4].toLongOrNull()?.let(LocalDate::ofEpochDay),
            totalWeeksText = saved[5],
            manualEndDate = saved[6].toLongOrNull()?.let(LocalDate::ofEpochDay),
            endDateMode = SemesterEndDateMode.valueOf(saved[7]),
        )
    },
)
