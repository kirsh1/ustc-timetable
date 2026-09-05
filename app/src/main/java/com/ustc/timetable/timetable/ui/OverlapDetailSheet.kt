package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.ui.AppIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlapDetailSheet(pages: List<OverlapDetailPage>, profile: ScheduleProfile, onDismiss: () -> Unit, onEdit: (ManualItemId) -> Unit) {
    if (pages.isEmpty()) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        key(pages.map { it.identity }) {
            val pager = rememberPagerState(pageCount = { pages.size })
            val scope = rememberCoroutineScope()
            Column(Modifier.fillMaxWidth()) {
                if (pages.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0,
                        modifier = Modifier.testTag("overlap_detail_previous")) { Icon(AppIcons.ChevronLeft, "上一项") }
                    Text("${pager.currentPage + 1} / ${pages.size}", Modifier.testTag("overlap_detail_indicator"))
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < pages.lastIndex,
                        modifier = Modifier.testTag("overlap_detail_next")) { Icon(AppIcons.ChevronRight, "下一项") }
                }
                HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(max = 560.dp), key = { pages[it].identity }) { index ->
                    val page = pages[index]
                    page.school?.let { CourseDetailContent(it, profile) }
                    page.manual?.let { item ->
                        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).testTag("overlap_manual_detail")) {
                            Text(item.title, style = MaterialTheme.typography.titleLarge)
                            Text("周${item.weekday} ${item.startTime}–${item.endTime}")
                            Text("第${item.weekPattern.format()}周")
                            Text("地点：${item.location.orEmpty()}")
                            item.note?.let { Text(it) }
                            TextButton(onClick = { onEdit(item.id) }, modifier = Modifier.testTag("overlap_edit_manual")) { Text("编辑此事项") }
                        }
                    }
                }
            }
        }
    }
}
