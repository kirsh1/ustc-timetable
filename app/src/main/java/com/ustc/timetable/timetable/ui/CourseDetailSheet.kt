package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import com.ustc.timetable.ui.AppIcons
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.scheduleprofile.ScheduleProfile

/**
 * 学校课程详情（frozen §4.4，read-only）：课程名 / 点击 meeting 的时间-周次-地点-教师 /
 * 课程号-学分 / 完整安排（全部 meeting）。无任何编辑操作；D3 的 manual editor 不复用本 Sheet。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailSheet(
    pager: CourseDetailPagerModel,
    profile: ScheduleProfile,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CourseDetailPagerContent(pager, profile)
    }
}

@Composable
internal fun CourseDetailPagerContent(pager: CourseDetailPagerModel, profile: ScheduleProfile) {
    if (pager.pages.isEmpty()) return
    key(pager.pages.first().anchorKey) {
        val pagerState = rememberPagerState(pageCount = { pager.pages.size })
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxWidth()) {
            if (pager.pages.size > 1) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.testTag("course_detail_previous"),
                    ) { Icon(AppIcons.ChevronLeft, "上一门课程") }
                    Text("${pagerState.currentPage + 1} / ${pager.pages.size}", Modifier.testTag("course_detail_indicator"))
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < pager.pages.lastIndex,
                        modifier = Modifier.testTag("course_detail_next"),
                    ) { Icon(AppIcons.ChevronRight, "下一门课程") }
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag("course_detail_pager"),
                key = { pager.pages[it].stableCourseIdentity },
            ) { index ->
                Column(Modifier.fillMaxWidth().testTag("course_detail_page:$index")) {
                    CourseDetailContent(pager.pages[index].detail, profile)
                }
            }
        }
    }
}

/** 详情本体（独立于 Dialog 窗口，C1/C2 已验证的 wrapper+content 拆分模式）。 */
@Composable
internal fun CourseDetailContent(
    detail: CourseDetailUiModel,
    profile: ScheduleProfile,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("course_detail"),
    ) {
        Text(detail.course.name, style = MaterialTheme.typography.titleLarge)
        // 点击的 meeting（selected 区）
        Text(
            CourseDetailFormatter.formatMeetingTime(detail.selectedMeeting, profile),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(CourseDetailFormatter.formatWeeks(detail.selectedMeeting.weekPattern), style = MaterialTheme.typography.titleSmall)
        LabeledRow("地点", CourseDetailFormatter.formatLocation(detail.selectedMeeting.location))
        LabeledRow("教师", CourseDetailFormatter.formatTeachers(detail.selectedMeeting.teacherNames))
        LabeledRow("课程号", CourseDetailFormatter.formatCourseCode(detail.course.courseCode))
        LabeledRow("学分", CourseDetailFormatter.formatCredits(detail.course.credits))
        // 完整安排：该课程全部 meeting（含当前周不可见的），教师 · 周次 · 地点
        Text(
            "完整安排",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        )
        detail.allMeetings.sortedWith(detailMeetingOrder()).forEach { meeting ->
            Text(
                "${CourseDetailFormatter.formatTeachers(meeting.teacherNames)} · " +
                    "${CourseDetailFormatter.formatWeeks(meeting.weekPattern)} · " +
                    CourseDetailFormatter.formatLocation(meeting.location),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
