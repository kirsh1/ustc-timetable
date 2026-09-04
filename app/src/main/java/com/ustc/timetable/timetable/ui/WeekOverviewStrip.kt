package com.ustc.timetable.timetable.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.layout.PlacedBlock
import com.ustc.timetable.timetable.layout.TimedBlock
import com.ustc.timetable.timetable.layout.TimelineAxis
import com.ustc.timetable.timetable.layout.SegmentedTimelineAxis
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import com.ustc.timetable.appearance.ResolvedAppearance
import com.ustc.timetable.ui.theme.LocalResolvedAppearance

data class WeekOverviewPageUiState(
    val week: Int,
    val placedBlocks: List<PlacedBlock>,
)

internal fun weekOverviewVisibilityScrollDelta(
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Float = when {
    itemOffset < viewportStart -> (itemOffset - viewportStart).toFloat()
    itemOffset + itemSize > viewportEnd -> (itemOffset + itemSize - viewportEnd).toFloat()
    else -> 0f
}

/** 同一 raw school/manual 数据的 active-only projection；不消费主网格可能包含 ghost 的最终 projection。 */
internal fun buildWeekOverviewPages(
    semester: Semester,
    rawBlocks: List<TimedBlock>,
    axis: TimelineAxis,
): List<WeekOverviewPageUiState> = (1..semester.totalWeeks).map { week ->
    WeekOverviewPageUiState(
        week = week,
        placedBlocks = WeeklyTimetableLayout.place(rawBlocks.filter { it.weeks.contains(week) }, axis),
    )
}

@Composable
internal fun WeekOverviewStrip(
    pages: List<WeekOverviewPageUiState>,
    viewedWeek: Int,
    naturalWeek: Int?,
    onWeekSelected: (Int) -> Unit,
    axis: SegmentedTimelineAxis,
    modifier: Modifier = Modifier,
) {
    val initialIndex = if (pages.isEmpty()) 0 else (viewedWeek - 1).coerceIn(pages.indices)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val density = LocalDensity.current
    LaunchedEffect(viewedWeek, pages.size) {
        if (pages.isEmpty()) return@LaunchedEffect
        val targetIndex = (viewedWeek - 1).coerceIn(pages.indices)
        val layout = listState.layoutInfo
        val visible = layout.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        val cardWidth = with(density) { 72.dp.roundToPx() }
        val itemSpacing = with(density) { 6.dp.roundToPx() }
        val target = visible.firstOrNull { it.index == targetIndex }
        val targetOffset = target?.offset ?: run {
            val anchor = visible.first()
            anchor.offset + (targetIndex - anchor.index) * (cardWidth + itemSpacing)
        }
        val delta = weekOverviewVisibilityScrollDelta(
            itemOffset = targetOffset,
            itemSize = target?.size ?: cardWidth,
            viewportStart = layout.viewportStartOffset,
            viewportEnd = layout.viewportEndOffset,
        )
        if (delta != 0f) {
            listState.animateScrollBy(
                value = delta,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            )
        }
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth().height(82.dp).testTag("week_overview_strip"),
    ) {
        items(pages, key = { it.week }) { page ->
            val selected = page.week == viewedWeek
            val shape = RoundedCornerShape(8.dp)
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                shape = shape,
                modifier = Modifier
                    .width(72.dp)
                    .height(78.dp)
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                    .testTag("week_overview_card:${page.week}")
                    .clickable { onWeekSelected(page.week) },
            ) {
                Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 ${page.week} 周", style = MaterialTheme.typography.labelSmall)
                        if (naturalWeek == page.week) {
                            Box(
                                Modifier
                                    .padding(start = 3.dp)
                                    .size(4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.tertiary)
                                    .testTag("week_overview_natural:${page.week}"),
                            )
                        }
                    }
                    if (selected) Box(Modifier.testTag("week_overview_selected:${page.week}"))
                    WeekOverviewMiniMap(page, axis)
                }
            }
        }
    }
}

@Composable
internal fun WeekOverviewMiniMap(page: WeekOverviewPageUiState, axis: SegmentedTimelineAxis) {
    val dark = LocalResolvedAppearance.current == ResolvedAppearance.DARK
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(top = 3.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .testTag("week_overview_minimap:${page.week}"),
    ) {
        val dayWidth = maxWidth / 7
        val resolvedAxis = axis.resolve(maxHeight.value, compressedGapDp = 6f)
        page.placedBlocks.forEach { placed ->
            val groupWidth = dayWidth / placed.columnsInGroup
            val x = dayWidth * (placed.weekday - 1) + groupWidth * placed.column
            val y = maxHeight * resolvedAxis.fractionOf(placed.block.start)
            val height = (
                maxHeight * (
                    resolvedAxis.fractionOf(placed.block.endInclusive) -
                        resolvedAxis.fractionOf(placed.block.start)
                    )
                ).coerceAtLeast(2.dp)
            val identity = placed.block.meetingId?.let { "school:${it.value}" }
                ?: placed.block.manualItemId?.let { "manual:${it.value}" }
                ?: placed.block.colorKey
            Box(
                Modifier
                    .offset(x = x, y = y)
                    .width((groupWidth - 1.dp).coerceAtLeast(1.dp))
                    .height(height)
                    .clip(RoundedCornerShape(1.dp))
                    .background(CoursePalette.containerColor(CoursePalette.colorIndexFor(placed.block.colorKey), dark))
                    .testTag("week_overview_block:${page.week}:$identity"),
            )
        }
    }
}
