package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.layout.WeeklyTimetableGrid
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout

/** Route：collect state → 无状态 Screen；Screen 不读 Room/DataStore。 */
@Composable
fun TimetableRoute(viewModel: TimetableViewModel) {
    val state by viewModel.state.collectAsState()
    TimetableScreen(
        state = state,
        onPrevWeek = viewModel::onPrevWeek,
        onNextWeek = viewModel::onNextWeek,
    )
}

/** 首页（frozen §5.1）：顶栏学期名 + （条件显示 ↻）+ ⚙；周标题行；B2 七列网格。无 Dashboard/底部导航。 */
@Composable
fun TimetableScreen(
    state: TimetableUiState,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val semester = state.semester
    val profile = state.profile
    val weekDates = state.weekDates
    if (semester == null || profile == null || weekDates == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无课表") }
        return
    }
    val axis = WeeklyTimetableLayout.axisOf(profile)
    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${semester.displayName} ▼", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("semester_name"))
            Spacer(Modifier.weight(1f))
            if (state.canSyncViewed) {
                Text(
                    "↻",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),  // B3 尚未接线 → disabled 外观
                    modifier = Modifier.testTag("refresh").padding(horizontal = 8.dp),
                )
            }
            Text(
                "⚙",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.testTag("settings").padding(start = 8.dp),
            )
        }
        // 周标题行
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .testTag("prev_week")
                        .clickable { onPrevWeek() }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text("第 ${state.viewedWeek} 周", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("viewed_week"))
                Text(
                    "›",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .testTag("next_week")
                        .clickable { onNextWeek() }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            Text(
                "${weekDates.start.monthValue}.${weekDates.start.dayOfMonth} - ${weekDates.endInclusive.monthValue}.${weekDates.endInclusive.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.testTag("week_dates"),
            )
        }
        // B2 网格：gutter 时刻权威 = 绑定 profile 节次开始
        WeeklyTimetableGrid(
            weekDates = weekDates,
            axis = axis,
            periodStarts = profile.periods.map { it.start },
            placedSchool = state.placedSchool,
            placedManual = state.placedManual,
            showNonCurrentWeek = state.showNonCurrentWeek,
            viewedWeek = state.viewedWeek,
            nowLine = state.nowLine,
            today = state.today,
            onSchoolBlockClick = {},   // C3 课程详情
            onManualBlockClick = {},   // D3 手动编辑
            onEmptyLongPress = { _, _ -> },  // D1 LongPressResolver
        )
    }
}
