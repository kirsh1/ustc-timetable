package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import com.ustc.timetable.school.ustc.auth.WebViewLoginContract
import com.ustc.timetable.sync.ManualSyncController
import com.ustc.timetable.sync.ManualSyncEvent
import com.ustc.timetable.sync.ManualSyncState
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.manual.ManualEditorHost
import com.ustc.timetable.manual.ManualOverlayState
import com.ustc.timetable.manual.createEditManualEditorTarget
import com.ustc.timetable.manual.createNewManualEditorTarget
import com.ustc.timetable.manual.resolveManualEditorInitial
import com.ustc.timetable.timetable.layout.WeeklyTimetableGrid
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import java.time.Clock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** 详情选择只保存 MeetingId，并始终对当前 emission 重新查表；stale id 不保留旧 model。 */
internal fun resolveSchoolCourseDetail(
    selectedMeetingId: MeetingId?,
    detailsByMeetingId: Map<MeetingId, CourseDetailUiModel>,
): CourseDetailUiModel? = selectedMeetingId?.let(detailsByMeetingId::get)

/** Route：collect state → 无状态 Screen；Screen 不读 Room/DataStore。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TimetableRoute(
    viewModel: TimetableViewModel,
    manualRepository: ManualItemRepository,
    clock: Clock,
    manualSyncController: ManualSyncController? = null,
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val manualSyncState = if (manualSyncController == null) {
        ManualSyncState.Idle
    } else {
        manualSyncController.state.collectAsState().value
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val reloginLauncher = rememberLauncherForActivityResult(WebViewLoginContract()) { successful ->
        manualSyncController?.let { handleManualSyncLoginResult(it, successful) }
    }
    LaunchedEffect(manualSyncController) {
        manualSyncController?.events?.collect { event ->
            snackbarHostState.showSnackbar(manualSyncEventMessage(event))
        }
    }
    var semesterSheetOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val overlays = remember(state.semester?.id) { ManualOverlayState() }
    Box(Modifier.fillMaxSize()) {
        TimetableScreen(
            state = state,
            onPrevWeek = viewModel::onPrevWeek,
            onNextWeek = viewModel::onNextWeek,
            onWeekSelected = viewModel::onWeekSelected,
            onSemesterTitleClick = { semesterSheetOpen = true },
            onSchoolBlockClick = overlays::openSchool,
            onManualBlockClick = { id, pageWeek ->
                val target = createEditManualEditorTarget(state, id, pageWeek)
                if (target == null) overlays.dismissManual() else overlays.openManual(target)
            },
            onEmptyLongPress = { pageWeek, columnFraction, yFraction ->
                val target = createNewManualEditorTarget(state, pageWeek, columnFraction, yFraction)
                if (target == null) overlays.dismissManual() else overlays.openManual(target)
            },
            manualSyncAvailable = manualSyncController != null,
            manualSyncState = manualSyncState,
            onRefreshClick = { manualSyncController?.start() },
            onReloginClick = { reloginLauncher.launch(Unit) },
            onCancelAuthExpired = { manualSyncController?.onCancelAuthExpired() },
            onSettingsClick = onSettingsClick,
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    if (semesterSheetOpen && state.semester != null) {
        com.ustc.timetable.semester.SemesterSwitcherSheet(
            semesters = state.availableSemesters,
            viewedSemesterId = state.semester!!.id,
            onSelect = {
                semesterSheetOpen = false
                viewModel.onSemesterSelected(it)
            },
            onDismiss = { semesterSheetOpen = false },
        )
    }
    val target = overlays.manualTarget
    val editorInitial = target?.let { resolveManualEditorInitial(it, state) }
    LaunchedEffect(target, state.semester?.id, state.profile?.id, state.manualItemsById.keys) {
        if (target != null && editorInitial == null) overlays.dismissManual()
    }
    if (target != null && editorInitial != null) {
        ManualEditorHost(
            target = target,
            semester = state.semester!!,
            profile = state.profile!!,
            existingItem = (editorInitial as? com.ustc.timetable.manual.ManualEditorInitial.Edit)?.item,
            manual = manualRepository,
            clock = clock,
            onDismiss = overlays::dismissManual,
        )
    }
    val selectedDetail = resolveSchoolCourseDetail(overlays.selectedSchoolMeetingId, state.courseDetailsByMeetingId)
    val selectedProfile = state.profile
    if (selectedDetail != null && selectedProfile != null) {
        CourseDetailSheet(
            detail = selectedDetail,
            profile = selectedProfile,
            onDismiss = overlays::dismissSchool,
        )
    }
}

/** 首页（frozen §5.1 + C1）：顶栏 + 周标题行（箭头/选择器）+ HorizontalPager 逐页渲染该周 projection。 */
@Composable
fun TimetableScreen(
    state: TimetableUiState,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onWeekSelected: (Int) -> Unit,
    onSemesterTitleClick: () -> Unit = {},
    onSchoolBlockClick: (MeetingId) -> Unit = {},
    onManualBlockClick: (ManualItemId, pageWeek: Int) -> Unit = { _, _ -> },
    onEmptyLongPress: (pageWeek: Int, columnFraction: Float, yFraction: Float) -> Unit = { _, _, _ -> },
    manualSyncAvailable: Boolean = false,
    manualSyncState: ManualSyncState = ManualSyncState.Idle,
    onRefreshClick: () -> Unit = {},
    onReloginClick: () -> Unit = {},
    onCancelAuthExpired: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val semester = state.semester
    val profile = state.profile
    if (semester == null || profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无课表") }
        return
    }
    var sheetOpen by remember { mutableStateOf(false) }
    val axis = WeeklyTimetableLayout.axisOf(profile)
    Column(Modifier.fillMaxSize()) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("timetable_top_header"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${semester.displayName} ▼",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("semester_name").clickable { onSemesterTitleClick() },
            )
            Spacer(Modifier.weight(1f))
            if (state.canSyncViewed && manualSyncAvailable) {
                val refreshEnabled = manualSyncState == ManualSyncState.Idle
                Box(
                    modifier = Modifier
                        .testTag("refresh")
                        .clickable(enabled = refreshEnabled, onClick = onRefreshClick)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (manualSyncState == ManualSyncState.Syncing) {
                        CircularProgressIndicator(Modifier.size(20.dp).testTag("refresh_progress"), strokeWidth = 2.dp)
                    } else {
                        Text(
                            "↻",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (refreshEnabled) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            },
                        )
                    }
                }
            }
            Text(
                "⚙",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("settings").clickable(onClick = onSettingsClick).padding(start = 8.dp),
            )
        }
        // 周标题行
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "‹",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (state.viewedWeek > 1) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .testTag("prev_week")
                        .clickable(enabled = state.viewedWeek > 1) { onPrevWeek() }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
                Text(
                    "第 ${state.viewedWeek} 周",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .testTag("viewed_week")
                        .clickable { sheetOpen = true },
                )
                Text(
                    "›",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (state.viewedWeek < semester.totalWeeks) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier
                        .testTag("next_week")
                        .clickable(enabled = state.viewedWeek < semester.totalWeeks) { onNextWeek() }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }
            state.weekDates?.let {
                Text(
                    "${it.start.monthValue}.${it.start.dayOfMonth} - ${it.endInclusive.monthValue}.${it.endInclusive.dayOfMonth}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.testTag("week_dates"),
                )
            }
        }
        // C1 correction 5：每页渲染自己的 page projection；periodStarts/axis 来自绑定 profile
        key(semester.id) {
            val pagerState = rememberPagerState(initialPage = state.viewedWeek - 1, pageCount = { state.weekPages.size })
            // C1 correction 6：仅真实用户 drag 完成 settle 后回写 VM
            val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
            var userDragged by remember { mutableStateOf(false) }
            LaunchedEffect(isDragged) { if (isDragged) userDragged = true }
            LaunchedEffect(userDragged, pagerState.settledPage, pagerState.isScrollInProgress) {
                if (userDragged && !pagerState.isScrollInProgress) {
                    onWeekSelected(pagerState.settledPage + 1)
                    userDragged = false
                }
            }
            // 程序化同步：arrow/picker/natural 更新只驱动 pager，不标记为用户选择
            LaunchedEffect(state.viewedWeek) {
                if (!userDragged && pagerState.settledPage != state.viewedWeek - 1) {
                    pagerState.animateScrollToPage(state.viewedWeek - 1)
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().testTag("week_pager"),
                key = { page -> page + 1 },
            ) { pageIndex ->
                val page = state.weekPages[pageIndex]
                Box(Modifier.fillMaxSize().testTag("week_page_${page.week}")) {
                    WeeklyTimetableGrid(
                        weekDates = page.weekDates,
                        axis = axis,
                        periodStarts = profile.periods.map { it.start },
                        placedSchool = page.placedSchool,
                        placedManual = page.placedManual,
                        showNonCurrentWeek = state.showNonCurrentWeek,
                        viewedWeek = page.week,
                        nowLine = page.nowLine,
                        today = state.today,
                        onSchoolBlockClick = onSchoolBlockClick,
                        onManualBlockClick = { id -> onManualBlockClick(id, page.week) },
                        onEmptyLongPress = { x, y -> onEmptyLongPress(page.week, x, y) },
                        periods = profile.periods,
                    )
                }
            }
        }
    }
    if (sheetOpen) {
        WeekSwitcherSheet(
            totalWeeks = semester.totalWeeks,
            naturalWeek = state.naturalWeek,
            viewedWeek = state.viewedWeek,
            onWeekSelected = { week ->
                sheetOpen = false
                onWeekSelected(week)
            },
            onDismiss = { sheetOpen = false },
        )
    }
    if (manualSyncState == ManualSyncState.AwaitingReauth) {
        AuthExpiredDialog(onCancel = onCancelAuthExpired, onRelogin = onReloginClick)
    }
}

internal fun handleManualSyncLoginResult(controller: ManualSyncController, successful: Boolean) {
    if (successful) controller.onReloginSuccess() else controller.onReloginCanceled()
}

internal fun manualSyncEventMessage(event: ManualSyncEvent): String = when (event) {
    is ManualSyncEvent.Updated -> "课表已更新：${event.changeCount} 处变化"
    is ManualSyncEvent.FailedOther -> manualSyncFailureMessage(event.error)
}

internal fun manualSyncFailureMessage(error: SyncError): String = when (error) {
    SyncError.NetworkFailed -> "同步失败，请检查网络后重试"
    SyncError.ParseFailed -> "无法解析学校课表，已保留本地课表"
    SyncError.ValidationFailed -> "学校课表数据校验失败，已保留本地课表"
    SyncError.AuthenticationExpired -> "登录状态已失效"
}

/** 周选择器（C1 correction 8/9）：1..totalWeeks 网格；natural/viewed 用独立 marker child，不共用 tag。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WeekSwitcherSheet(
    totalWeeks: Int,
    naturalWeek: Int?,
    viewedWeek: Int,
    onWeekSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        WeekSwitcherContent(totalWeeks, naturalWeek, viewedWeek, onWeekSelected)
    }
}

/** 周网格本体（独立于 ModalBottomSheet 窗口，便于确定性测试）。 */
@Composable
internal fun WeekSwitcherContent(
    totalWeeks: Int,
    naturalWeek: Int?,
    viewedWeek: Int,
    onWeekSelected: (Int) -> Unit,
) {
    run {
        Text(
            "选择周次",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items((1..totalWeeks).toList()) { week ->
                val isNatural = naturalWeek == week
                val isViewed = viewedWeek == week
                Box(
                    Modifier
                        .padding(4.dp)
                        .testTag("week_$week")
                        .clickable { onWeekSelected(week) },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = when {
                            isViewed -> MaterialTheme.colorScheme.primaryContainer
                            isNatural -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "$week",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { contentDescription = "第${week}周" },
                            )
                        }
                    }
                    // 独立 marker child：同一周可同时 natural + viewed（correction 9）
                    if (isNatural) {
                        Text(
                            "今",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("natural_week_$week").align(Alignment.TopStart).padding(2.dp),
                        )
                    }
                    if (isViewed) {
                        Text(
                            "●",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("viewed_week_$week").align(Alignment.BottomEnd).padding(2.dp),
                        )
                    }
                }
            }
        }
    }
}
