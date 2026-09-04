package com.ustc.timetable.scheduleprofile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.ui.AppIcons
import java.time.LocalTime

data class EditablePeriod(val number: Int, val start: String, val end: String)
fun PeriodTime.toEditable() = EditablePeriod(number, start.toString(), end.toString())

internal object ProfileEditorValidator {
    fun parse(periods: List<EditablePeriod>): List<PeriodTime>? = try {
        if (periods.size != 13 || periods.map { it.number } != (1..13).toList()) return null
        val parsed = periods.map { PeriodTime(it.number, LocalTime.parse(it.start), LocalTime.parse(it.end)) }
        ScheduleProfile("validation", "validation", false, parsed)
        parsed
    } catch (_: IllegalArgumentException) { null }

    fun isValid(periods: List<EditablePeriod>) = parse(periods) != null
}

@Composable
fun ProfileEditorScreen(profile: ScheduleProfile, onSave: (List<PeriodTime>) -> Unit, onBack: () -> Unit) {
    var periods by remember(profile.id) { mutableStateOf(profile.periods.map(PeriodTime::toEditable)) }
    var invalid by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("profile_editor_back")) {
                Icon(AppIcons.Back, contentDescription = "返回")
            }
            Text("学校作息时间", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = {
                val parsed = ProfileEditorValidator.parse(periods)
                invalid = parsed == null
                if (parsed != null) onSave(parsed)
            }, modifier = Modifier.testTag("profile_editor_save")) { Text("保存") }
        }
        if (invalid) Text("作息时间无效", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize().testTag("profile_period_list")) {
            itemsIndexed(periods) { index, period ->
                Row(Modifier.fillMaxWidth().testTag("profile_period_row").padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("第${period.number}节", Modifier.weight(.8f))
                    OutlinedTextField(period.start, { value -> periods = periods.toMutableList().also { it[index] = period.copy(start = value) } }, label = { Text("开始时间") }, singleLine = true, modifier = Modifier.weight(1f).testTag("period_${period.number}_start"))
                    OutlinedTextField(period.end, { value -> periods = periods.toMutableList().also { it[index] = period.copy(end = value) } }, label = { Text("结束时间") }, singleLine = true, modifier = Modifier.weight(1f).testTag("period_${period.number}_end"))
                }
            }
        }
    }
}
