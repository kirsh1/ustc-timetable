package com.ustc.timetable.timetable.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalTime
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.ui.BlockTexts
import com.ustc.timetable.timetable.ui.CourseCardTextMetrics
import com.ustc.timetable.timetable.ui.GridGestureDecision
import com.ustc.timetable.timetable.ui.GridGestureOwner
import com.ustc.timetable.timetable.ui.GridPressTarget
import com.ustc.timetable.timetable.ui.OverviewGestureArbitrator
import com.ustc.timetable.timetable.ui.VerticalOverviewAction
import com.ustc.timetable.timetable.ui.CoursePalette
import com.ustc.timetable.timetable.ui.SchoolCanonicalPresentationKey
import com.ustc.timetable.timetable.ui.SchoolCardMarkers
import com.ustc.timetable.timetable.ui.SchoolGhostProjection
import com.ustc.timetable.timetable.ui.SchoolMarkerKind
import com.ustc.timetable.timetable.ui.SchoolTimedBlock
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.appearance.ResolvedAppearance
import com.ustc.timetable.ui.theme.LocalResolvedAppearance
import com.ustc.timetable.ui.theme.TimetableTypography
import kotlinx.coroutines.withTimeoutOrNull

data class TeachingTimeGroup(
    val start: LocalTime,
    val endInclusive: LocalTime,
)

fun teachingTimeGroups(periods: List<PeriodTime>): List<TeachingTimeGroup> {
    if (periods.isEmpty()) return emptyList()
    val ordered = periods.sortedBy { it.number }
    val groups = mutableListOf<TeachingTimeGroup>()
    var start = ordered.first().start
    var end = ordered.first().end
    for (period in ordered.drop(1)) {
        val gap = java.time.Duration.between(end, period.start).toMinutes()
        if (gap >= 20) {
            groups += TeachingTimeGroup(start, end)
            start = period.start
        }
        end = period.end
    }
    groups += TeachingTimeGroup(start, end)
    return groups
}

internal const val HEADER_HEIGHT_DP: Int = 36

sealed interface GridHitTarget {
    data class School(val meetingId: MeetingId) : GridHitTarget
    data class Manual(val manualItemId: ManualItemId) : GridHitTarget
    data class Empty(val draft: LongPressDraft) : GridHitTarget
}

/**
 * 每周七列课表网格（SPEC §4.1/§4.2/§5.1）。
 * 坐标系唯一：总宽 = 固定 gutter + weight(1f) 七列网格；gridW 即 BoxWithConstraints.maxWidth，
 * 块 x = dayX + column·groupW，绝不二次加减 gutter。
 * gutter 时刻权威 = unique(periodStarts + axis.start + axis.endInclusive)；
 * 课程/手动块均为真正 Compose child（click / semantics / testTag / 长按消费）。
 */
@Composable
fun WeeklyTimetableGrid(
    weekDates: LocalDateRange,
    axis: TimelineAxis,
    periodStarts: List<LocalTime>,
    placedSchool: List<PlacedBlock>,
    placedManual: List<PlacedBlock>,
    showNonCurrentWeek: Boolean,
    viewedWeek: Int,
    nowLine: LocalTime?,
    today: LocalDate?,
    onSchoolBlockClick: (MeetingId) -> Unit,
    onManualBlockClick: (ManualItemId) -> Unit,
    onEmptyLongPress: (LongPressDraft) -> Unit,
    onVerticalOverviewAction: (VerticalOverviewAction) -> Unit = {},
    schoolMarkersByPresentationKey: Map<SchoolCanonicalPresentationKey, SchoolCardMarkers> = emptyMap(),
    periods: List<PeriodTime> = emptyList(),
    segmentedAxis: SegmentedTimelineAxis? = null,
    showTimeRail: Boolean = true,
    coursePaletteSeed: Long = com.ustc.timetable.timetable.data.DEFAULT_COURSE_PALETTE_SEED,
) {
    val gutterWidth = if (showTimeRail) {
        rememberMeasuredTimeRailWidth(
            periods = periods,
            fallbackPeriodStarts = periodStarts,
            axisStart = axis.start,
            axisEndInclusive = axis.endInclusive,
        )
    } else {
        0.dp
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            if (showTimeRail) Spacer(Modifier.width(gutterWidth))
            WeekHeaderCells(weekDates, today, Modifier.weight(1f))
        }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("timetable_body"),
        ) {
            val bodyHeight = maxHeight
            val gridWidth = (maxWidth - gutterWidth).coerceAtLeast(0.dp)
            val teachingGroups = teachingTimeGroups(periods)
            val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            val dividerColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
            val compressedGapColor = teachingGroupGapColor(MaterialTheme.colorScheme.outlineVariant)
            val viewConfiguration = LocalViewConfiguration.current
            val renderingAxis: ReversibleTimelineAxis = segmentedAxis?.resolve(
                viewportHeightDp = bodyHeight.value,
                compressedGapDp = compressedGapDpForHeight(bodyHeight.value),
            ) ?: axis
            Row(Modifier.fillMaxSize()) {
                if (showTimeRail) {
                    TimeGutter(
                        periods = periods,
                        fallbackPeriodStarts = periodStarts,
                        axis = renderingAxis,
                        bodyHeight = bodyHeight,
                        modifier = Modifier
                            .width(gutterWidth)
                            .fillMaxHeight()
                            .testTag("time_gutter"),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("timetable_grid")
                        .drawBehind {
                            for (day in 1..6) {
                                val x = size.width * day / 7f
                                drawLine(dividerColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                            }
                            periodStarts.forEach { time ->
                                val y = size.height * renderingAxis.fractionOf(time)
                                drawLine(gridLineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                            }
                            teachingGroups.zipWithNext().forEach { (before, after) ->
                                val top = size.height * renderingAxis.fractionOf(before.endInclusive)
                                val bottom = size.height * renderingAxis.fractionOf(after.start)
                                drawRect(
                                    color = compressedGapColor,
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                                    size = androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(0f)),
                                )
                            }
                        }
                        .pointerInput(
                            placedSchool,
                            placedManual,
                            viewedWeek,
                            showNonCurrentWeek,
                            renderingAxis,
                            viewConfiguration,
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val target = hitTargetAt(
                                    xPx = down.position.x,
                                    yPx = down.position.y,
                                    gridWidthPx = size.width.toFloat(),
                                    bodyHeightPx = size.height.toFloat(),
                                    school = placedSchool,
                                    manual = placedManual,
                                    viewedWeek = viewedWeek,
                                    showNonCurrentWeek = showNonCurrentWeek,
                                    axis = renderingAxis,
                                )
                                val arbitrator = OverviewGestureArbitrator(viewConfiguration.touchSlop)
                                arbitrator.onDown(target.pressTarget())
                                val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                                var eventTime = down.uptimeMillis
                                var timeoutHandled = false
                                var finished = false

                                fun dispatch(decision: GridGestureDecision) {
                                    when (decision) {
                                        GridGestureDecision.Click -> when (target) {
                                            is GridHitTarget.School -> onSchoolBlockClick(target.meetingId)
                                            is GridHitTarget.Manual -> onManualBlockClick(target.manualItemId)
                                            is GridHitTarget.Empty -> Unit
                                        }
                                        GridGestureDecision.LongPress -> {
                                            if (target is GridHitTarget.Empty) onEmptyLongPress(target.draft)
                                        }
                                        is GridGestureDecision.ChangeOverview -> onVerticalOverviewAction(decision.action)
                                        GridGestureDecision.ConsumeCardLongPress,
                                        GridGestureDecision.None,
                                        GridGestureDecision.YieldToHorizontalPager,
                                        -> Unit
                                    }
                                }

                                while (!finished) {
                                    val remaining = (deadline - eventTime).coerceAtLeast(0L)
                                    val event = if (timeoutHandled) {
                                        awaitPointerEvent()
                                    } else {
                                        withTimeoutOrNull(remaining) { awaitPointerEvent() }
                                    }
                                    if (event == null) {
                                        dispatch(arbitrator.onLongPressTimeout())
                                        timeoutHandled = true
                                        continue
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        arbitrator.onCancel()
                                        break
                                    }
                                    eventTime = change.uptimeMillis
                                    dispatch(
                                        arbitrator.onMove(
                                            totalDxPx = change.position.x - down.position.x,
                                            totalDyPx = change.position.y - down.position.y,
                                        ),
                                    )
                                    if (
                                        arbitrator.owner == GridGestureOwner.VERTICAL_OWNED ||
                                        arbitrator.owner == GridGestureOwner.LONG_PRESS_OWNED
                                    ) {
                                        change.consume()
                                    }
                                    if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                        dispatch(arbitrator.onUp())
                                        finished = true
                                    }
                                }
                            }
                        },
                ) {
                    for (pb in placedSchool) {
                        if (!pb.block.weeks.contains(viewedWeek) && !showNonCurrentWeek) continue
                        require(pb.block.meetingId != null) {
                            "school list contains block without meetingId: ${pb.block.colorKey}"
                        }
                        require(pb.block.manualItemId == null) {
                            "school list contains manual block: ${pb.block.colorKey}"
                        }
                        SchoolBlockNode(
                            coursePaletteSeed = coursePaletteSeed,
                            pb = pb,
                            viewedWeek = viewedWeek,
                            showNonCurrentWeek = showNonCurrentWeek,
                            gridW = gridWidth,
                            gridH = bodyHeight,
                            axis = renderingAxis,
                            markersByPresentationKey = schoolMarkersByPresentationKey,
                            onClick = onSchoolBlockClick,
                        )
                    }
                    for (pb in placedManual) {
                        if (!pb.block.weeks.contains(viewedWeek) && !showNonCurrentWeek) continue
                        require(pb.block.manualItemId != null) {
                            "manual list contains block without manualItemId: ${pb.block.colorKey}"
                        }
                        require(pb.block.meetingId == null) {
                            "manual list contains school block: ${pb.block.colorKey}"
                        }
                        ManualBlockNode(pb, viewedWeek, showNonCurrentWeek, gridWidth, bodyHeight, renderingAxis, coursePaletteSeed, onManualBlockClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.SchoolBlockNode(
    pb: PlacedBlock,
    viewedWeek: Int,
    showNonCurrentWeek: Boolean,
    gridW: Dp,
    gridH: Dp,
    axis: ReversibleTimelineAxis,
    markersByPresentationKey: Map<SchoolCanonicalPresentationKey, SchoolCardMarkers>,
    coursePaletteSeed: Long,
    onClick: (MeetingId) -> Unit,
) {
    val id = pb.block.meetingId!!
    val schoolBlock = pb.block as? SchoolTimedBlock
    val markers = schoolBlock?.let { markersByPresentationKey[SchoolGhostProjection.canonicalPresentationKey(it)] }
        ?.kinds
        .orEmpty()
    BlockNode(pb, viewedWeek, showNonCurrentWeek, gridW, gridH, axis, "school_block:${id.value}", markers, coursePaletteSeed) { onClick(id) }
}

@Composable
private fun BoxScope.ManualBlockNode(
    pb: PlacedBlock,
    viewedWeek: Int,
    showNonCurrentWeek: Boolean,
    gridW: Dp,
    gridH: Dp,
    axis: ReversibleTimelineAxis,
    coursePaletteSeed: Long,
    onClick: (ManualItemId) -> Unit,
) {
    val id = pb.block.manualItemId!!
    BlockNode(pb, viewedWeek, showNonCurrentWeek, gridW, gridH, axis, "manual_block:${id.value}", emptySet(), coursePaletteSeed) { onClick(id) }
}

/** 单块：绝对定位 + 稳定 tag + a11y 全描述 + 点击；onLongPress 空实现消费长按（不冒泡到空白区新建）。 */
@Composable
private fun BoxScope.BlockNode(
    pb: PlacedBlock,
    viewedWeek: Int,
    showNonCurrentWeek: Boolean,
    gridW: Dp,
    gridH: Dp,
    axis: ReversibleTimelineAxis,
    tag: String,
    markerKinds: Set<SchoolMarkerKind>,
    coursePaletteSeed: Long,
    onClick: () -> Unit,
) {
    val columnWidth = gridW / 7f
    val groupWidth = columnWidth / pb.columnsInGroup
    val x = columnWidth * (pb.block.weekday - 1) + groupWidth * pb.column
    val logicalY = gridH * axis.fractionOf(pb.block.start)
    val logicalBottom = gridH * axis.fractionOf(pb.block.endInclusive)
    val visual = courseCardVisualBounds(logicalY.value, logicalBottom.value, insetDp = 1f)
    val logicalHeight = (logicalBottom - logicalY).coerceAtLeast(0.dp)
    val visualYOffset = (visual.topDp - logicalY.value).dp
    val visualHeight = (visual.bottomDp - visual.topDp).dp
    val horizontalInset = 1.dp.coerceAtMost(groupWidth / 2f)
    val visualWidth = (groupWidth - horizontalInset * 2f).coerceAtLeast(0.dp)
    val density = LocalDensity.current
    val textBudget = BlockTexts.budget(
        metrics = CourseCardTextMetrics(
            cardHeightDp = visualHeight.value,
            cardWidthDp = visualWidth.value,
            titleLineHeightDp = with(density) { TimetableTypography.courseTitle.lineHeight.toDp().value },
            locationLineHeightDp = with(density) { TimetableTypography.courseLocation.lineHeight.toDp().value },
            metadataLineHeightDp = with(density) { TimetableTypography.courseMetadata.lineHeight.toDp().value },
        ),
        hasLocation = pb.block.location.isNotBlank(),
        hasTeachers = pb.block.teacherNames.isNotEmpty(),
        markerCount = markerKinds.size,
    )
    val paletteIndex = CoursePalette.colorIndexFor(pb.block.colorKey, coursePaletteSeed)
    val dark = LocalResolvedAppearance.current == ResolvedAppearance.DARK
    val alpha = CoursePalette.alphaFor(pb.block.weeks.contains(viewedWeek), showNonCurrentWeek)
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .offset(x = x, y = logicalY)
            .size(width = groupWidth, height = logicalHeight)
            .semantics {
                contentDescription = BlockTexts.a11y(pb.block)
                onClick {
                    onClick()
                    true
                }
            }
            .testTag(tag),
    ) {
        Box(
            Modifier
                .offset(x = horizontalInset, y = visualYOffset)
                .size(width = visualWidth, height = visualHeight)
                .graphicsLayer { this.alpha = alpha }
                .clip(shape)
                .background(CoursePalette.containerColor(paletteIndex, dark), shape),
        ) {
            BlockTexts.Content(
                block = pb.block,
                budget = textBudget,
                contentColor = CoursePalette.onContainerColor(paletteIndex, dark),
            )
            if (markerKinds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp),
                ) {
                    markerKinds.sortedBy { it.ordinal }.forEach { kind ->
                        SchoolMarker(
                            kind = kind,
                            tag = "school_marker:${kind.name.lowercase()}:${pb.block.meetingId!!.value}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SchoolMarker(kind: SchoolMarkerKind, tag: String) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier
            .padding(start = 1.dp)
            .size(7.dp)
            .testTag(tag),
    ) {
        when (kind) {
            SchoolMarkerKind.DIFFERENT_COURSE -> {
                val side = size.minDimension * 0.62f
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - side, 0f),
                    size = androidx.compose.ui.geometry.Size(side, side),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - side),
                    size = androidx.compose.ui.geometry.Size(side, side),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            SchoolMarkerKind.SAME_COURSE_VARIANT -> drawCircle(
                color = color,
                radius = size.minDimension * 0.36f,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

private fun GridHitTarget.pressTarget(): GridPressTarget = when (this) {
    is GridHitTarget.Empty -> GridPressTarget.EMPTY
    is GridHitTarget.School -> GridPressTarget.SCHOOL_CARD
    is GridHitTarget.Manual -> GridPressTarget.MANUAL_CARD
}

private fun hitTargetAt(
    xPx: Float,
    yPx: Float,
    gridWidthPx: Float,
    bodyHeightPx: Float,
    school: List<PlacedBlock>,
    manual: List<PlacedBlock>,
    viewedWeek: Int,
    showNonCurrentWeek: Boolean,
    axis: ReversibleTimelineAxis,
): GridHitTarget {
    val visible = (school + manual).filter { placed ->
        placed.block.weeks.contains(viewedWeek) || showNonCurrentWeek
    }
    val hit = visible.asReversed().firstOrNull { placed ->
        val dayWidth = gridWidthPx / 7f
        val groupWidth = dayWidth / placed.columnsInGroup
        val left = dayWidth * (placed.block.weekday - 1) + groupWidth * placed.column
        val right = left + groupWidth
        val top = bodyHeightPx * axis.fractionOf(placed.block.start)
        val bottom = bodyHeightPx * axis.fractionOf(placed.block.endInclusive)
        xPx in left..right && yPx in top..bottom
    }
    hit?.block?.meetingId?.let { return GridHitTarget.School(it) }
    hit?.block?.manualItemId?.let { return GridHitTarget.Manual(it) }
    return GridHitTarget.Empty(
        LongPressResolver.resolve(
            columnFraction = (xPx / gridWidthPx).coerceIn(0f, 0.999f),
            yFraction = (yPx / bodyHeightPx).coerceIn(0f, 0.999f),
            axis = axis,
        ),
    )
}

@Composable
private fun WeekHeaderCells(weekDates: LocalDateRange, today: LocalDate?, modifier: Modifier) {
    Row(modifier) {
        val names = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        for (dow in 0..6) {
            val date = weekDates.start.plusDays(dow.toLong())
            Column(
                Modifier
                    .weight(1f)
                    .height(HEADER_HEIGHT_DP.dp)
                    .testTag(if (date == today) "today_header" else "header_$dow")
                    .padding(2.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                val color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                Text(names[dow], style = MaterialTheme.typography.labelSmall, color = color, textAlign = TextAlign.Center)
                Text(
                    "${date.monthValue}-${date.dayOfMonth.toString().padStart(2, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun FixedTimeRail(
    periods: List<PeriodTime>,
    segmentedAxis: SegmentedTimelineAxis,
    modifier: Modifier = Modifier,
) {
    val railWidth = rememberMeasuredTimeRailWidth(
        periods = periods,
        fallbackPeriodStarts = periods.map { it.start },
        axisStart = periods.minByOrNull { it.number }?.start,
        axisEndInclusive = periods.maxByOrNull { it.number }?.end,
    )
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier
            .width(railWidth)
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = TIME_RAIL_DIVIDER_DP.dp.toPx(),
                )
            }
            .testTag("fixed_time_rail"),
    ) {
        Spacer(Modifier.height(HEADER_HEIGHT_DP.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val resolved = segmentedAxis.resolve(maxHeight.value, compressedGapDpForHeight(maxHeight.value))
            TimeGutter(
                periods = periods,
                fallbackPeriodStarts = periods.map { it.start },
                axis = resolved,
                bodyHeight = maxHeight,
                modifier = Modifier.fillMaxSize().testTag("time_gutter"),
            )
        }
    }
}

@Composable
private fun rememberMeasuredTimeRailWidth(
    periods: List<PeriodTime>,
    fallbackPeriodStarts: List<LocalTime>,
    axisStart: LocalTime?,
    axisEndInclusive: LocalTime?,
): Dp {
    val labelTimes = if (periods.isEmpty()) {
        (fallbackPeriodStarts + listOfNotNull(axisStart, axisEndInclusive)).distinct().sorted()
    } else {
        timeBoundaryMarks(periods).flatMap(TimeBoundaryMark::times)
    }
    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelSmall
    val density = LocalDensity.current
    val renderedWidths = labelTimes.map { time ->
        textMeasurer.measure(text = time.toString(), style = textStyle).size.width.toFloat()
    }
    val width = measuredTimeRailWidthPx(renderedWidths, density.density)
    return with(density) { width.totalPx.toDp() }
}

@Composable
private fun TimeGutter(
    periods: List<PeriodTime>,
    fallbackPeriodStarts: List<LocalTime>,
    axis: ReversibleTimelineAxis,
    bodyHeight: Dp,
    modifier: Modifier,
) {
    Box(modifier) {
        val marks = if (periods.isEmpty()) {
            (fallbackPeriodStarts + axis.start + axis.endInclusive).distinct().sorted().map(TimeBoundaryMark::AxisStart)
        } else {
            timeBoundaryMarks(periods)
        }
        marks.forEach { mark ->
            val anchor = mark.times.map(axis::fractionOf).average().toFloat()
            PositionedGutterMark(mark, anchor, bodyHeight)
        }
    }
}

@Composable
private fun PositionedGutterMark(mark: TimeBoundaryMark, anchorFraction: Float, bodyHeight: Dp) {
    Layout(
        content = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                mark.times.forEach { time ->
                    Text(
                        time.toString(),
                        Modifier.testTag("time_boundary:$time"),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val label = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val anchor = (bodyHeight.roundToPx() * anchorFraction).roundToInt()
        val y = (anchor - label.height / 2).coerceIn(0, (bodyHeight.roundToPx() - label.height).coerceAtLeast(0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            label.placeRelative(TIME_RAIL_TEXT_PADDING_DP.dp.roundToPx(), y)
        }
    }
}

@Composable
private fun PositionedGutterLabel(
    text: String,
    tag: String,
    anchorFraction: Float,
    bodyHeight: Dp,
) {
    Layout(
        content = {
                Text(
                    text,
                    Modifier.testTag(tag),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                )
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val label = measurables.single().measure(
            constraints.copy(minWidth = 0, minHeight = 0),
        )
        val anchor = (bodyHeight.roundToPx() * anchorFraction).roundToInt()
        val y = (anchor - label.height / 2)
            .coerceIn(0, (bodyHeight.roundToPx() - label.height).coerceAtLeast(0))
        layout(constraints.maxWidth, constraints.maxHeight) {
            label.placeRelative(4.dp.roundToPx(), y)
        }
    }
}

internal fun compressedGapDpForHeight(viewportHeightDp: Float): Float =
    if (viewportHeightDp < 520f) 6f else 8f

internal fun teachingGroupGapColor(outlineVariant: androidx.compose.ui.graphics.Color) =
    outlineVariant.copy(alpha = 0.18f)
