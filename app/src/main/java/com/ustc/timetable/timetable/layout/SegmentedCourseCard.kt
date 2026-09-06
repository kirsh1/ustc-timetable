package com.ustc.timetable.timetable.layout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ustc.timetable.appearance.ResolvedAppearance
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.timetable.ui.*
import com.ustc.timetable.ui.theme.LocalResolvedAppearance
import com.ustc.timetable.ui.theme.TimetableTypography

data class OverlapMarkerRegion(val kind: OverlapMarkerKind, val rect: Rect)

/** Geometry shared by drawing and pointer dispatch. Units are chosen by the caller. */
fun overlapMarkerRegions(shape: SegmentedBlock, kinds: Set<OverlapMarkerKind>, dayWidth: Float,
    height: Float, axis: ReversibleTimelineAxis, unit: Float = 1f): List<OverlapMarkerRegion> {
    if (kinds.isEmpty()) return emptyList()
    val last = shape.slices.last()
    val width = (last.right - last.left) * dayWidth
    val availableHeight = (axis.fractionOf(last.end) - axis.fractionOf(last.start)) * height
    if (width < (kinds.size * 9 + 4) * unit || availableHeight < 12 * unit) return emptyList()
    val right = last.right * dayWidth - 3 * unit
    val bottom = axis.fractionOf(last.end) * height - 3 * unit
    return kinds.sortedBy { it.ordinal }.mapIndexed { index, kind ->
        val left = right - (kinds.size - index) * 9 * unit
        OverlapMarkerRegion(kind, Rect(left, bottom - 8 * unit, left + 8 * unit, bottom))
    }
}

@Composable
fun BoxScope.SegmentedCourseCard(
    entry: OverlapEntry,
    shape: SegmentedBlock,
    gridWidth: Dp,
    gridHeight: Dp,
    axis: ReversibleTimelineAxis,
    viewedWeek: Int,
    showOtherWeeks: Boolean,
    paletteSeed: Long,
    periods: List<PeriodTime>,
    attachment: OverlapAttachment,
    onBodyClick: () -> Unit,
    onMarkerClick: (OverlapMarkerKind) -> Unit,
) {
    val block = entry.block
    val dayWidth = gridWidth / 7
    val left = dayWidth * (block.weekday - 1)
    val density = LocalDensity.current
    val kinds = attachment.kinds
    val regions = overlapMarkerRegions(shape, kinds, dayWidth.value, gridHeight.value, axis)
    val content = shape.contentRect(axis)
    val contentTop = gridHeight * content.top + 1.dp
    val contentWidth = (dayWidth * (content.right - content.left) - 2.dp).coerceAtLeast(0.dp)
    val footer = if (regions.any { it.rect.top < gridHeight.value * content.bottom && it.rect.bottom > gridHeight.value * content.top &&
            it.rect.left < dayWidth.value * content.right && it.rect.right > dayWidth.value * content.left }) 12.dp else 0.dp
    val contentHeight = (gridHeight * (content.bottom - content.top) - 2.dp - footer).coerceAtLeast(0.dp)
    val pills = boundaryTimePills(block.start, block.endInclusive, periods)
    val pillHeight = 11.dp
    val pillContentClearance = 2.dp
    val reservedTop = if (pills.start != null) pillHeight + pillContentClearance else 0.dp
    val reservedBottom = if (pills.end != null) pillHeight + pillContentClearance else 0.dp
    val measurer = rememberTextMeasurer()
    val constraints = Constraints(maxWidth = with(density) { (contentWidth - 4.dp).roundToPx().coerceAtLeast(1) })
    val titleLines = measurer.measure(block.title, TimetableTypography.courseTitle, constraints = constraints).lineCount
    val locationLines = if (block.location.isBlank()) 0 else measurer.measure(block.location, TimetableTypography.courseLocation, constraints = constraints).lineCount
    val budget = BlockTexts.budget(CourseCardTextMetrics(contentHeight.value, contentWidth.value,
        with(density) { TimetableTypography.courseTitle.lineHeight.toDp().value },
        with(density) { TimetableTypography.courseLocation.lineHeight.toDp().value },
        with(density) { TimetableTypography.courseMetadata.lineHeight.toDp().value },
        titleRequiredLines = titleLines,
        locationRequiredLines = locationLines,
        reservedTopDp = reservedTop.value,
        reservedBottomDp = reservedBottom.value,
    ), block.location.isNotBlank(), block.teacherNames.isNotEmpty(), 0)
    val dark = LocalResolvedAppearance.current == ResolvedAppearance.DARK
    val index = CoursePalette.colorIndexFor(block.colorKey, paletteSeed)
    val alpha = CoursePalette.alphaFor(viewedWeek in block.weeks, showOtherWeeks)
    val background = CoursePalette.containerColor(index, dark).copy(alpha = alpha)
    val foreground = CoursePalette.onContainerColor(index, dark).copy(alpha = alpha)
    Box(Modifier.offset(x = left).size(dayWidth, gridHeight).drawBehind {
        var union = Path()
        shape.slices.forEachIndexed { i, slice ->
            val top = axis.fractionOf(slice.start) * size.height + if (i == 0) 1.dp.toPx() else 0f
            val bottom = axis.fractionOf(slice.end) * size.height - if (i == shape.slices.lastIndex) 1.dp.toPx() else 0f
            val inset = minOf(1.dp.toPx(), (slice.right - slice.left) * size.width / 2)
            if (bottom > top) {
                val part = Path().apply { addRect(Rect(slice.left * size.width + inset, top, slice.right * size.width - inset, bottom)) }
                union = Path.combine(PathOperation.Union, union, part)
            }
        }
        // Round the merged outer contour once: slice joins remain continuous.
        drawIntoCanvas { canvas ->
            canvas.drawPath(union, Paint().apply {
                color = background
                isAntiAlias = true
                pathEffect = PathEffect.cornerPathEffect(6.dp.toPx())
            })
        }
    })
    val tag = block.manualItemId?.let { "manual_block:${it.value}" } ?: "school_block:${block.meetingId!!.value}"
    Box(Modifier.offset(x = left + dayWidth * content.left + 1.dp, y = contentTop).size(contentWidth, contentHeight)
        .testTag(tag).semantics {
            contentDescription = BlockTexts.a11y(block) + if (kinds.isEmpty()) "" else "，跨周其他内容：${attachment.crossWeek.size}，本周完全冲突：${attachment.currentConflicts.size}，安排变体：${attachment.sameCourseVariants.size}"
            onClick { if (regions.isEmpty() && kinds.isNotEmpty()) onMarkerClick(OverlapMarkerKind.ALL_CONTENT) else onBodyClick(); true }
        }) {
        BlockTexts.Content(
            block,
            budget,
            foreground,
            reservedTopDp = reservedTop.value,
            reservedBottomDp = reservedBottom.value,
        )
    }
    regions.forEach { region ->
        val color = MaterialTheme.colorScheme.primary
        Canvas(Modifier.offset(x = left + region.rect.left.dp, y = region.rect.top.dp).size(region.rect.width.dp, region.rect.height.dp)
            .testTag("overlap_marker:${region.kind.name}").semantics {
                contentDescription = when (region.kind) {
                    OverlapMarkerKind.CROSS_WEEK -> "其他周的不同课程或事项"
                    OverlapMarkerKind.VARIANT -> "同课程的其他安排"
                    OverlapMarkerKind.CURRENT_CONFLICT -> "本周完全重叠的其他内容"
                    OverlapMarkerKind.ALL_CONTENT -> "全部相关内容"
                }
                onClick { onMarkerClick(region.kind); true }
            }) {
            val stroke = Stroke(1.dp.toPx())
            when (region.kind) {
                OverlapMarkerKind.VARIANT -> drawCircle(color, size.minDimension * .36f, style = stroke)
                OverlapMarkerKind.CROSS_WEEK -> {
                    val side = size.minDimension * .6f
                    drawRect(color, Offset(size.width - side, 0f), androidx.compose.ui.geometry.Size(side, side), style = stroke)
                    drawRect(color, Offset(0f, size.height - side), androidx.compose.ui.geometry.Size(side, side), style = stroke)
                }
                OverlapMarkerKind.CURRENT_CONFLICT -> {
                    drawLine(color, Offset(size.width / 2, size.height), Offset(size.width / 2, size.height / 2), 1.dp.toPx())
                    drawLine(color, Offset(size.width / 2, size.height / 2), Offset(0f, 0f), 1.dp.toPx())
                    drawLine(color, Offset(size.width / 2, size.height / 2), Offset(size.width, 0f), 1.dp.toPx())
                }
                OverlapMarkerKind.ALL_CONTENT -> Unit // Whole-card fallback; never emitted as a marker.
            }
        }
    }
    val pillWidth = 30.dp
    pills.start?.let { label ->
        val first = shape.slices.first()
        val availableWidth = (dayWidth * (first.right - first.left) - 2.dp).coerceAtLeast(1.dp)
        val width = minOf(pillWidth, availableWidth)
        BoundaryTimePill(
            label = label,
            tag = "boundary_time:start",
            edge = BoundaryTimePillEdge.START,
            containerColor = CoursePalette.boundaryPillContainerColor(index, dark).copy(alpha = alpha),
            contentColor = CoursePalette.boundaryPillContentColor(index, dark).copy(alpha = alpha),
            modifier = Modifier.offset(
                x = left + dayWidth * ((first.left + first.right) / 2f) - width / 2,
                y = gridHeight * axis.fractionOf(first.start) + 1.dp,
            ).size(width, pillHeight),
        )
    }
    pills.end?.let { label ->
        val last = shape.slices.last()
        val availableWidth = (dayWidth * (last.right - last.left) - 2.dp).coerceAtLeast(1.dp)
        val width = minOf(pillWidth, availableWidth)
        BoundaryTimePill(
            label = label,
            tag = "boundary_time:end",
            edge = BoundaryTimePillEdge.END,
            containerColor = CoursePalette.boundaryPillContainerColor(index, dark).copy(alpha = alpha),
            contentColor = CoursePalette.boundaryPillContentColor(index, dark).copy(alpha = alpha),
            modifier = Modifier.offset(
                x = left + dayWidth * ((last.left + last.right) / 2f) - width / 2,
                y = gridHeight * axis.fractionOf(last.end) - pillHeight - 1.dp,
            ).size(width, pillHeight),
        )
    }
}
