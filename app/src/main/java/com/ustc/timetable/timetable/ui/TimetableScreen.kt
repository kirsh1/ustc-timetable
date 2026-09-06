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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.PlatformTextStyle
import com.ustc.timetable.ui.AppIcons
import com.ustc.timetable.ui.theme.LocalTimetableSpacing
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
import com.ustc.timetable.timetable.layout.FixedTimeRail
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import com.ustc.timetable.timetable.layout.LongPressDraft
import com.ustc.timetable.timetable.layout.SegmentedTimelineAxis
import java.time.Clock
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

internal const val WEEK_BAR_CONTENT_HEIGHT_DP = 44
internal const val WEEK_NUMBER_FONT_SP = 16
internal const val WEEK_NUMBER_LINE_HEIGHT_SP = 20
internal const val WEEK_RANGE_FONT_SP = 12
internal const val WEEK_RANGE_LINE_HEIGHT_SP = 16

/** 详情选择只保存 MeetingId，并始终对当前 emission 重新查表；stale id 不保留旧 model。 */
internal fun resolveSchoolCourseDetail(
    selectedMeetingId: MeetingId?,
    detailsByMeetingId: Map<MeetingId, CourseDetailUiModel>,
): CourseDetailUiModel? = selectedMeetingId?.let(detailsByMeetingId::get)

data class SchoolDetailSelection(val pageWeek: Int, val representativeMeetingId: MeetingId)

internal fun resolveSchoolCourseDetailPager(selection: SchoolDetailSelection?, state: TimetableUiState): CourseDetailPagerModel? {
    if (selection == null) return null
    val page = state.weekPages.firstOrNull { it.week == selection.pageWeek } ?: return null
    val representative = page.placedSchool.firstOrNull { it.block.meetingId == selection.representativeMeetingId }
        ?.block as? SchoolTimedBlock ?: return null
    return CourseDetailPager.build(
        representative,
        page.attachedSchoolGhostsByPresentationKey[SchoolGhostProjection.canonicalPresentationKey(representative)],
        state.courseDetailsByMeetingId,
    )
}

private val SchoolDetailSelectionSaver = Saver<SchoolDetailSelection?, List<Any>>(
    save = { selection -> selection?.let { listOf(it.pageWeek, it.representativeMeetingId.value) } ?: emptyList() },
    restore = { if (it.isEmpty()) null else SchoolDetailSelection(it[0] as Int, MeetingId(it[1] as String)) },
)

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
    val routeScope = rememberCoroutineScope()
    val reloginLauncher = rememberLauncherForActivityResult(WebViewLoginContract()) { successful ->
        manualSyncController?.let { handleManualSyncLoginResult(it, successful) }
    }
    LaunchedEffect(manualSyncController) {
        manualSyncController?.events?.collect { event ->
            snackbarHostState.showSnackbar(manualSyncEventMessage(event))
        }
    }
    val overlays = remember(state.semester?.id) { ManualOverlayState() }
    var overlapSelection by remember(state.semester?.id) { mutableStateOf<OverlapDetailSelection?>(null) }
    var schoolSelection by rememberSaveable(state.semester?.id, stateSaver = SchoolDetailSelectionSaver) {
        mutableStateOf<SchoolDetailSelection?>(null)
    }
    Box(Modifier.fillMaxSize()) {
        TimetableScreen(
            state = state,
            onPrevWeek = viewModel::onPrevWeek,
            onNextWeek = viewModel::onNextWeek,
            onWeekSelected = viewModel::onWeekSelected,
            onSchoolBlockClick = { id, pageWeek ->
                overlays.dismissManual()
                schoolSelection = null
                val entry = state.weekPages.getOrNull(pageWeek - 1)?.overlapProjection?.retained?.firstOrNull { it.block.meetingId == id }
                overlapSelection = entry?.let { OverlapDetailSelection(pageWeek,it.key,OverlapMarkerKind.ALL_CONTENT,true,it.identity,it.identity) }
            },
            onManualBlockClick = { id, pageWeek ->
                schoolSelection = null
                overlays.dismissManual()
                val entry = state.manualItemsById[id]?.let { OverlapEntry(manualTimedBlock(it),it.createdAt) }
                overlapSelection = entry?.let { OverlapDetailSelection(pageWeek,it.key,OverlapMarkerKind.ALL_CONTENT,true,it.identity,it.identity) }
            },
            onEmptyLongPress = { pageWeek, draft ->
                overlapSelection = null
                schoolSelection = null
                val target = createNewManualEditorTarget(state, pageWeek, draft)
                if (target == null) overlays.dismissManual() else overlays.openManual(target)
            },
            manualSyncAvailable = manualSyncController != null,
            manualSyncState = manualSyncState,
            onRefreshClick = { manualSyncController?.start() },
            onReloginClick = { reloginLauncher.launch(Unit) },
            onCancelAuthExpired = { manualSyncController?.onCancelAuthExpired() },
            onSettingsClick = onSettingsClick,
            onOverlapMarkerClick = { week, key, kind ->
                schoolSelection = null
                overlays.dismissManual()
                val identity = state.weekPages.getOrNull(week - 1)?.overlapProjection?.retained?.firstOrNull { it.key == key }?.identity
                overlapSelection = OverlapDetailSelection(week, key, kind, representativeIdentity = identity, selectedIdentity = identity)
            },
        )
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
    val target = overlays.manualTarget
    val overlap = overlapSelection
    val projection = overlap?.let { state.weekPages.getOrNull(it.week - 1)?.overlapProjection }
    val representative = state.manualItemsById.values.firstOrNull { "manual:${it.id.value}" == overlap?.representativeIdentity }
        ?.let { OverlapEntry(manualTimedBlock(it),it.createdAt) }
        ?: projection?.retained?.firstOrNull { it.key == overlap.representativeKey }
    val projectedPages = if (overlap != null && representative != null && projection != null) {
        if (overlap.isBody) OverlapDetail.forBody(representative,projection,overlap.week,state.courseDetailsByMeetingId,state.manualItemsById)
        else OverlapDetail.build(representative, projection.attachments[representative.key] ?: OverlapAttachment(), overlap.kind, overlap.week,
            state.courseDetailsByMeetingId, state.manualItemsById)
    } else emptyList()
    val overlapPages = OverlapDetail.retainSelectedManual(projectedPages, overlap?.selectedIdentity, state.manualItemsById)
    LaunchedEffect(overlap, overlapPages, target, state.manualItemsById) {
        val selected = overlap?.selectedIdentity
        val deleted = selected?.startsWith("manual:") == true && state.manualItemsById.keys.none { "manual:${it.value}" == selected }
        if (target == null && (overlapPages.isEmpty() || deleted)) overlapSelection = null
    }
    if (target == null && overlap != null && overlapPages.isNotEmpty() && state.profile != null) {
        OverlapDetailSheet(overlapPages, requireNotNull(state.profile), { overlapSelection = null },
            selectedIdentity = overlap.selectedIdentity,
            onPageSelected = { identity -> overlapSelection = overlapSelection?.copy(selectedIdentity = identity) },
            onEdit = { id ->
                val editTarget = createEditManualEditorTarget(state, id, overlap.week)
                if (editTarget != null) {
                    overlapSelection = overlapSelection?.copy(selectedIdentity = "manual:${id.value}")
                    overlays.openManual(editTarget)
                }
            },
            onDelete = { id ->
                routeScope.launch {
                    runCatching { check(manualRepository.delete(id) == 1) }
                        .onFailure { snackbarHostState.showSnackbar("删除失败，请重试") }
                }
            },
        )
    }
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
    val selectedDetail = resolveSchoolCourseDetailPager(schoolSelection, state)
    LaunchedEffect(schoolSelection, selectedDetail) {
        if (selectedDetail == null) schoolSelection = null
    }
    val selectedProfile = state.profile
    if (selectedDetail != null && selectedProfile != null) {
        CourseDetailSheet(
            pager = selectedDetail,
            profile = selectedProfile,
            onDismiss = { schoolSelection = null },
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
    onSchoolBlockClick: (MeetingId, pageWeek: Int) -> Unit = { _, _ -> },
    onManualBlockClick: (ManualItemId, pageWeek: Int) -> Unit = { _, _ -> },
    onEmptyLongPress: (pageWeek: Int, draft: LongPressDraft) -> Unit = { _, _ -> },
    manualSyncAvailable: Boolean = false,
    manualSyncState: ManualSyncState = ManualSyncState.Idle,
    onRefreshClick: () -> Unit = {},
    onReloginClick: () -> Unit = {},
    onCancelAuthExpired: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOverlapMarkerClick: (Int, String, OverlapMarkerKind) -> Unit = { _, _, _ -> },
) {
    if (state.isLoading) {
        com.ustc.timetable.ui.AppLoading()
        return
    }
    val semester = state.semester
    val profile = state.profile
    if (semester == null || profile == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无课表") }
        return
    }
    var sheetOpen by remember { mutableStateOf(false) }
    var overviewExpanded by remember(semester.id) { mutableStateOf(false) }
    val axis = WeeklyTimetableLayout.axisOf(profile)
    val segmentedAxis = remember(profile) { SegmentedTimelineAxis.from(profile) }
    val spacing = LocalTimetableSpacing.current
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        PrimaryWeekBar(
            viewedWeek = state.viewedWeek,
            totalWeeks = semester.totalWeeks,
            weekDates = state.weekDates,
            overviewExpanded = overviewExpanded,
            onPrevWeek = onPrevWeek,
            onNextWeek = onNextWeek,
            onWeekClick = { sheetOpen = true },
            onOverviewClick = { overviewExpanded = !overviewExpanded },
            onSettingsClick = onSettingsClick,
            horizontalPadding = spacing.compactHorizontal,
        )
        if (overviewExpanded) {
            WeekOverviewStrip(
                coursePaletteSeed = state.coursePaletteSeed,
                pages = state.weekOverviewPages,
                viewedWeek = state.viewedWeek,
                naturalWeek = state.naturalWeek,
                onWeekSelected = onWeekSelected,
                axis = segmentedAxis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
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
            Row(Modifier.fillMaxSize().testTag("timetable_viewport")) {
                FixedTimeRail(
                    periods = profile.periods,
                    segmentedAxis = segmentedAxis,
                    modifier = Modifier.fillMaxHeight(),
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxHeight().testTag("week_pager"),
                    key = { page -> page + 1 },
                ) { pageIndex ->
                    val page = state.weekPages[pageIndex]
                    Box(Modifier.fillMaxSize().testTag("week_page_${page.week}")) {
                        WeeklyTimetableGrid(
                            coursePaletteSeed = state.coursePaletteSeed,
                            weekDates = page.weekDates,
                            axis = axis,
                            periodStarts = profile.periods.map { it.start },
                            placedSchool = page.placedSchool,
                            placedManual = page.placedManual,
                            showNonCurrentWeek = state.showNonCurrentWeek,
                            viewedWeek = page.week,
                            nowLine = page.nowLine,
                            today = state.today,
                            onSchoolBlockClick = { id -> onSchoolBlockClick(id, page.week) },
                            onManualBlockClick = { id -> onManualBlockClick(id, page.week) },
                            onEmptyLongPress = { draft -> onEmptyLongPress(page.week, draft) },
                            onVerticalOverviewAction = { action ->
                                overviewExpanded = action == VerticalOverviewAction.EXPAND
                            },
                            schoolMarkersByPresentationKey = page.schoolMarkersByPresentationKey,
                            periods = profile.periods,
                            segmentedAxis = segmentedAxis,
                            showTimeRail = false,
                            overlapProjection = page.overlapProjection,
                            onOverlapMarkerClick = { key, kind -> onOverlapMarkerClick(page.week, key, kind) },
                        )
                    }
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
        if (state.naturalWeek != null && state.viewedWeek != state.naturalWeek) {
            SmallFloatingActionButton(
                onClick = { onWeekSelected(state.naturalWeek) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("return_to_current_week"),
            ) {
                Icon(AppIcons.MyLocation, contentDescription = "返回本周")
            }
        }
    }
    if (manualSyncState == ManualSyncState.AwaitingReauth) {
        AuthExpiredDialog(onCancel = onCancelAuthExpired, onRelogin = onReloginClick)
    }
}

@Composable
private fun PrimaryWeekBar(
    viewedWeek: Int,
    totalWeeks: Int,
    weekDates: com.ustc.timetable.timetable.domain.LocalDateRange?,
    overviewExpanded: Boolean,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onWeekClick: () -> Unit,
    onOverviewClick: () -> Unit,
    onSettingsClick: () -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(WEEK_BAR_CONTENT_HEIGHT_DP.dp)
            .testTag("timetable_top_bar"),
    ) {
        val showArrows = maxWidth >= 360.dp
        val centeredTitle = MaterialTheme.typography.titleMedium.copy(
            fontSize = WEEK_NUMBER_FONT_SP.sp,
            lineHeight = WEEK_NUMBER_LINE_HEIGHT_SP.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
        val centeredRange = MaterialTheme.typography.labelMedium.copy(
            fontSize = WEEK_RANGE_FONT_SP.sp,
            lineHeight = WEEK_RANGE_LINE_HEIGHT_SP.sp,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
        Row(
            Modifier.fillMaxSize().padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showArrows) IconButton(onPrevWeek, Modifier.size(44.dp).testTag("prev_week"), enabled = viewedWeek > 1) {
                Icon(AppIcons.ChevronLeft, "上一周")
            } else Box(Modifier.testTag("prev_week_compact_hidden"))
            Row(
                modifier = Modifier.height(WEEK_BAR_CONTENT_HEIGHT_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .height(WEEK_BAR_CONTENT_HEIGHT_DP.dp)
                        .testTag("viewed_week")
                        .clickable(onClick = onWeekClick)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("第 $viewedWeek 周", modifier = Modifier.testTag("week_number_text"), style = centeredTitle)
                }
                weekDates?.let {
                    Box(
                        Modifier
                            .height(WEEK_BAR_CONTENT_HEIGHT_DP.dp)
                            .testTag("week_dates")
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${it.start.monthValue}.${it.start.dayOfMonth} - ${it.endInclusive.monthValue}.${it.endInclusive.dayOfMonth}",
                            style = centeredRange,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("week_range_text"),
                        )
                    }
                }
            }
            if (showArrows) IconButton(onNextWeek, Modifier.size(44.dp).testTag("next_week"), enabled = viewedWeek < totalWeeks) {
                Icon(AppIcons.ChevronRight, "下一周")
            } else Box(Modifier.testTag("next_week_compact_hidden"))
            Spacer(Modifier.weight(1f))
            IconButton(onOverviewClick, Modifier.size(44.dp).testTag("week_overview_toggle")) {
                Icon(AppIcons.WeekOverview, if (overviewExpanded) "收起周缩略图" else "展开周缩略图")
            }
            IconButton(onSettingsClick, Modifier.size(44.dp).testTag("settings")) { Icon(AppIcons.Settings, "设置") }
        }
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
