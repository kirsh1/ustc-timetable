package com.ustc.timetable.semester

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId

/**
 * 学期切换（frozen §5.3 + C2）：纯本地 selector，仅写 viewedSemesterId；
 * viewed / academic-current / portal 三个概念分开标注，互不混用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterSwitcherSheet(
    semesters: List<Semester>,
    viewedSemesterId: SemesterId,
    onSelect: (SemesterId) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        SemesterSwitcherContent(semesters, viewedSemesterId, onSelect)
    }
}

/** 列表本体（独立于 Dialog 窗口，便于确定性测试；C1 已验证该拆分模式）。 */
@Composable
internal fun SemesterSwitcherContent(
    semesters: List<Semester>,
    viewedSemesterId: SemesterId,
    onSelect: (SemesterId) -> Unit,
) {
    Text(
        "切换学期",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    LazyColumn(Modifier.padding(bottom = 16.dp)) {
        items(semesters, key = { it.id.value }) { semester ->
            val id = semester.id
            val isViewed = viewedSemesterId == id
            val isCurrent = semester.isCurrentAcademicSemester
            val isPortal = semester.portalLinked
            val status = buildString {
                if (isCurrent) append("当前学期")
                if (isPortal) {
                    if (isNotEmpty()) append(" · ")
                    append("学校")
                } else {
                    if (isNotEmpty()) append(" · ")
                    append("本地")
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .testTag("semester_item:${id.value}")
                    .clickable { onSelect(id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(semester.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        status.ifEmpty { if (isPortal) "学校" else "本地" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 三个概念独立 marker child（unmerged 树断言，C1 merge 教训）
                if (isViewed) {
                    Text(
                        "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("viewed_semester:${id.value}").padding(start = 8.dp),
                    )
                }
                if (isCurrent) {
                    Box(Modifier.testTag("academic_current:${id.value}").padding(start = 4.dp)) {
                        Text("●", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                if (isPortal) {
                    Box(Modifier.testTag("portal_linked:${id.value}").padding(start = 4.dp)) {
                        Text("校", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    Box(Modifier.testTag("local_semester:${id.value}").padding(start = 4.dp)) {
                        Text("本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
