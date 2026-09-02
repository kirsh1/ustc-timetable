package com.ustc.timetable.timetable.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.time.LocalDate
import java.time.LocalTime
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.ui.BlockTexts
import com.ustc.timetable.timetable.ui.CoursePalette
import com.ustc.timetable.scheduleprofile.PeriodTime

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

private const val GUTTER_WIDTH_DP: Int = 44
private const val HEADER_HEIGHT_DP: Int = 32
private const val TEACHER_MIN_HEIGHT_DP: Int = 60
private const val TIME_MIN_HEIGHT_DP: Int = 90
private const val OPTIONAL_TEXT_MIN_WIDTH_DP: Int = 52

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
    onEmptyLongPress: (columnFraction: Float, yFraction: Float) -> Unit,
    periods: List<PeriodTime> = emptyList(),
) {
    val gutterWidth = GUTTER_WIDTH_DP.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutterWidth))
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
            Row(Modifier.fillMaxSize()) {
                TimeGutter(
                    periodStarts = periodStarts,
                    teachingGroups = teachingGroups,
                    axis = axis,
                    bodyHeight = bodyHeight,
                    modifier = Modifier
                        .width(gutterWidth)
                        .fillMaxHeight()
                        .testTag("time_gutter"),
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("timetable_grid")
                        .drawBehind {
                            val lineColor = Color(0xFF64748B).copy(alpha = 0.16f)
                            val dividerColor = Color(0xFF64748B).copy(alpha = 0.12f)
                            for (day in 1..6) {
                                val x = size.width * day / 7f
                                drawLine(dividerColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                            }
                            periodStarts.forEach { time ->
                                val y = size.height * axis.fractionOf(time)
                                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                            }
                            teachingGroups.zipWithNext().forEach { (before, after) ->
                                val top = size.height * axis.fractionOf(before.endInclusive)
                                val bottom = size.height * axis.fractionOf(after.start)
                                drawRect(
                                    color = Color(0xFF94A3B8).copy(alpha = 0.07f),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, top),
                                    size = androidx.compose.ui.geometry.Size(size.width, (bottom - top).coerceAtLeast(0f)),
                                )
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { press ->
                                onEmptyLongPress(
                                    (press.x / size.width.toFloat()).coerceIn(0f, 0.999f),
                                    (press.y / size.height.toFloat()).coerceIn(0f, 0.999f),
                                )
                            })
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
                        SchoolBlockNode(pb, viewedWeek, showNonCurrentWeek, gridWidth, bodyHeight, onSchoolBlockClick)
                    }
                    for (pb in placedManual) {
                        if (!pb.block.weeks.contains(viewedWeek) && !showNonCurrentWeek) continue
                        require(pb.block.manualItemId != null) {
                            "manual list contains block without manualItemId: ${pb.block.colorKey}"
                        }
                        require(pb.block.meetingId == null) {
                            "manual list contains school block: ${pb.block.colorKey}"
                        }
                        ManualBlockNode(pb, viewedWeek, showNonCurrentWeek, gridWidth, bodyHeight, onManualBlockClick)
                    }
                    if (nowLine != null) {
                        Box(
                            Modifier
                                .offset(y = bodyHeight * axis.fractionOf(nowLine))
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(Color(0xFFB3261E))
                                .testTag("now_line"),
                        )
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
    onClick: (MeetingId) -> Unit,
) {
    val id = pb.block.meetingId!!
    BlockNode(pb, viewedWeek, showNonCurrentWeek, gridW, gridH, "school_block:${id.value}") { onClick(id) }
}

@Composable
private fun BoxScope.ManualBlockNode(
    pb: PlacedBlock,
    viewedWeek: Int,
    showNonCurrentWeek: Boolean,
    gridW: Dp,
    gridH: Dp,
    onClick: (ManualItemId) -> Unit,
) {
    val id = pb.block.manualItemId!!
    BlockNode(pb, viewedWeek, showNonCurrentWeek, gridW, gridH, "manual_block:${id.value}") { onClick(id) }
}

/** 单块：绝对定位 + 稳定 tag + a11y 全描述 + 点击；onLongPress 空实现消费长按（不冒泡到空白区新建）。 */
@Composable
private fun BoxScope.BlockNode(
    pb: PlacedBlock,
    viewedWeek: Int,
    showNonCurrentWeek: Boolean,
    gridW: Dp,
    gridH: Dp,
    tag: String,
    onClick: () -> Unit,
) {
    val columnWidth = gridW / 7f
    val groupWidth = columnWidth / pb.columnsInGroup
    val x = columnWidth * (pb.block.weekday - 1) + groupWidth * pb.column
    val y = gridH * pb.topFraction
    val h = gridH * pb.heightFraction
    val hasOptionalTextWidth = groupWidth > OPTIONAL_TEXT_MIN_WIDTH_DP.dp
    val paletteIndex = CoursePalette.colorIndexFor(pb.block.colorKey)
    val alpha = CoursePalette.alphaFor(pb.block.weeks.contains(viewedWeek), showNonCurrentWeek)
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width = groupWidth, height = h)
            .graphicsLayer { this.alpha = alpha }
            .clip(shape)
            .background(CoursePalette.containerColor(paletteIndex), shape)
            .pointerInput(tag) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { /* 消费长按：块上长按不得触发空白区新建 */ },
                )
            }
            .semantics { contentDescription = BlockTexts.a11y(pb.block) }
            .testTag(tag),
    ) {
        BlockTexts.Content(
            block = pb.block,
            titleMaxLines = BlockTexts.titleMaxLinesFor(h.value, pb.block.location.isNotBlank()),
            showLocation = h > 42.dp,
            showTeacher = h > TEACHER_MIN_HEIGHT_DP.dp && hasOptionalTextWidth,
            showTime = h > TIME_MIN_HEIGHT_DP.dp && hasOptionalTextWidth,
            contentColor = CoursePalette.onContainerColor(paletteIndex),
        )
    }
}

@Composable
private fun WeekHeaderCells(weekDates: LocalDateRange, today: LocalDate?, modifier: Modifier) {
    Row(modifier) {
        val names = listOf("一", "二", "三", "四", "五", "六", "日")
        for (dow in 0..6) {
            val date = weekDates.start.plusDays(dow.toLong())
            Box(
                Modifier
                    .weight(1f)
                    .height(HEADER_HEIGHT_DP.dp)
                    .testTag(if (date == today) "today_header" else "header_$dow")
                    .padding(2.dp),
            ) {
                Text("${names[dow]} ${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TimeGutter(
    periodStarts: List<LocalTime>,
    teachingGroups: List<TeachingTimeGroup>,
    axis: TimelineAxis,
    bodyHeight: Dp,
    modifier: Modifier,
) {
    Box(modifier) {
        val labels = (periodStarts + teachingGroups.flatMap { listOf(it.start, it.endInclusive) } + axis.start + axis.endInclusive).toSet().sorted()
        for (time in labels) {
            Layout(
                content = {
                Text(
                    time.toString(),
                        Modifier.testTag("time_label:$time"),
                    style = MaterialTheme.typography.labelSmall,
                )
                },
                modifier = Modifier.fillMaxSize(),
            ) { measurables, constraints ->
                val label = measurables.single().measure(
                    constraints.copy(minWidth = 0, minHeight = 0),
                )
                val anchor = (bodyHeight.roundToPx() * axis.fractionOf(time)).roundToInt()
                val y = (anchor - label.height / 2)
                    .coerceIn(0, (bodyHeight.roundToPx() - label.height).coerceAtLeast(0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    label.placeRelative(4.dp.roundToPx(), y)
                }
            }
        }
    }
}
