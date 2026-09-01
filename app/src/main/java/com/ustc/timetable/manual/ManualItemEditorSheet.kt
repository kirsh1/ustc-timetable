package com.ustc.timetable.manual

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.scheduleprofile.LocalTimeRange
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualItemEditorSheet(
    draft: EditorDraft,
    viewedWeek: Int,
    totalWeeks: Int,
    dayWindow: LocalTimeRange,
    validationError: String?,
    canDelete: Boolean,
    onDraftChange: (EditorDraft) -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ManualItemEditorContent(
            draft = draft,
            viewedWeek = viewedWeek,
            totalWeeks = totalWeeks,
            dayWindow = dayWindow,
            validationError = validationError,
            canDelete = canDelete,
            onDraftChange = onDraftChange,
            onSave = onSave,
            onDeleteRequest = onDeleteRequest,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManualItemEditorContent(
    draft: EditorDraft,
    viewedWeek: Int,
    totalWeeks: Int,
    dayWindow: LocalTimeRange,
    validationError: String?,
    canDelete: Boolean,
    onDraftChange: (EditorDraft) -> Unit,
    onSave: () -> Unit,
    onDeleteRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingStart by remember { mutableStateOf<Boolean?>(null) }
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("editor_content")
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("手动课表项目", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChange(draft.copy(title = it)) },
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth().testTag("editor_title"),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.location,
            onValueChange = { onDraftChange(draft.copy(location = it)) },
            label = { Text("地点") },
            modifier = Modifier.fillMaxWidth().testTag("editor_location"),
            singleLine = true,
        )
        OutlinedTextField(
            value = draft.note,
            onValueChange = { onDraftChange(draft.copy(note = it)) },
            label = { Text("备注") },
            modifier = Modifier.fillMaxWidth().testTag("editor_note"),
        )

        Text("星期", style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WEEKDAY_LABELS.forEachIndexed { index, label ->
                val weekday = index + 1
                FilterChip(
                    selected = draft.weekday == weekday,
                    onClick = { onDraftChange(draft.copy(weekday = weekday)) },
                    label = { Text(label) },
                    modifier = Modifier.testTag("weekday_$weekday"),
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = { editingStart = true },
                modifier = Modifier.testTag("start_time"),
            ) { Text("开始时间 ${draft.startTime.format(TIME_FORMAT)}") }
            TextButton(
                onClick = { editingStart = false },
                modifier = Modifier.testTag("end_time"),
            ) { Text("结束时间 ${draft.endTime.format(TIME_FORMAT)}") }
        }
        Text(
            "作息窗口 ${dayWindow.start.format(TIME_FORMAT)}–${dayWindow.endInclusive.format(TIME_FORMAT)}",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("周次", style = MaterialTheme.typography.titleSmall)
        WeekModeChoice("仅当前周", WeekMode.CURRENT_ONLY, draft, onDraftChange)
        WeekModeChoice("连续区间", WeekMode.CONTINUOUS, draft, onDraftChange)
        WeekModeChoice("自定义", WeekMode.CUSTOM, draft, onDraftChange)

        when (draft.mode) {
            WeekMode.CURRENT_ONLY -> Text("第 $viewedWeek 周")
            WeekMode.CONTINUOUS -> {
                WeekSelector(
                    tag = "continuous_start",
                    week = draft.continuousStart,
                    totalWeeks = totalWeeks,
                    onWeek = { onDraftChange(draft.copy(continuousStart = it)) },
                )
                WeekSelector(
                    tag = "continuous_end",
                    week = draft.continuousEnd,
                    totalWeeks = totalWeeks,
                    onWeek = { onDraftChange(draft.copy(continuousEnd = it)) },
                )
            }
            WeekMode.CUSTOM -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxWidth().height(210.dp).testTag("custom_weeks_grid"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items((1..totalWeeks).toList(), key = { it }) { week ->
                    FilterChip(
                        selected = week in draft.customWeeks,
                        onClick = {
                            val weeks = if (week in draft.customWeeks) {
                                draft.customWeeks - week
                            } else {
                                draft.customWeeks + week
                            }
                            onDraftChange(draft.copy(customWeeks = weeks))
                        },
                        label = { Text(week.toString()) },
                        modifier = Modifier.testTag("week_$week"),
                    )
                }
            }
        }

        if (validationError != null) {
            Text(validationError, color = MaterialTheme.colorScheme.error)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (canDelete) {
                TextButton(onClick = onDeleteRequest, modifier = Modifier.testTag("editor_delete")) {
                    Text("删除")
                }
            }
            TextButton(onClick = onDismiss) { Text("取消") }
            Button(
                onClick = onSave,
                enabled = validationError == null,
                modifier = Modifier.testTag("editor_save"),
            ) { Text("保存") }
        }
    }

    editingStart?.let { isStart ->
        val initialTime = if (isStart) draft.startTime else draft.endTime
        val pickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { editingStart = null },
            confirmButton = {
                TextButton(onClick = {
                    onDraftChange(applyPickedTime(draft, isStart, pickerState.hour, pickerState.minute))
                    editingStart = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { editingStart = null }) { Text("取消") }
            },
            text = { TimePicker(state = pickerState) },
        )
    }
}

@Composable
private fun WeekModeChoice(
    label: String,
    mode: WeekMode,
    draft: EditorDraft,
    onDraftChange: (EditorDraft) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = draft.mode == mode,
            onClick = { onDraftChange(draft.copy(mode = mode)) },
            modifier = Modifier.testTag("week_mode_${mode.name.lowercase()}"),
        )
        Text(label)
    }
}

@Composable
private fun WeekSelector(
    tag: String,
    week: Int,
    totalWeeks: Int,
    onWeek: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = { onWeek((week - 1).coerceAtLeast(1)) }, enabled = week > 1) { Text("−") }
        Text("第 $week 周")
        TextButton(onClick = { onWeek((week + 1).coerceAtMost(totalWeeks)) }, enabled = week < totalWeeks) { Text("+") }
    }
}

internal fun applyPickedTime(
    draft: EditorDraft,
    editingStart: Boolean,
    hour: Int,
    minute: Int,
): EditorDraft {
    val picked = LocalTime.of(hour, minute)
    return if (editingStart) draft.copy(startTime = picked) else draft.copy(endTime = picked)
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAY_LABELS = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
