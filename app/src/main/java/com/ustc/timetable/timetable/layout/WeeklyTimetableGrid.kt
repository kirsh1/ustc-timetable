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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.ui.BlockTexts
import com.ustc.timetable.timetable.ui.CoursePalette

/**
 * 每周七列课表网格（SPEC §4.1/§4.2/§5.1）。
 * 整体宽度 = gutterWidth + 七列网格（剩余空间）；gridW = maxWidth（gutter 右侧全部宽度），
 * 块 x = dayX + column*groupW（绝不二次加减 gutter，B2-local correction 3）。
 * header 行首 Spacer 与 TimeGutter 同宽，保证星期列与课程七列严格对齐。
 * 课程/手动块均为真正的 Compose child（click / semantics / testTag / 长按消费）。
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
) {
    val gutterWidth = 44.dp
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutterWidth))
            WeekHeaderCells(weekDates, today, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxSize()) {
            TimeGutter(periodStarts, axis, Modifier.width(gutterWidth).fillMaxHeight())
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .testTag("timetable_grid")
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { offset ->
                            onEmptyLongPress(
                                (offset.x / size.width.toFloat()).coerceIn(0f, 0.999f),
                                (offset.y / size.height.toFloat()).coerceIn(0f, 0.999f),
                            )
                        })
                    },
            ) {
                val gridW = maxWidth
                val gridH = maxHeight

                for (pb in placedSchool) {
                    val isCurrent = pb.block.weeks.contains(viewedWeek)
                    if (!isCurrent && !showNonCurrentWeek) continue
                    // provenance guard：显式校验，不用 !!（B2-local correction 6）
                    require(pb.block.meetingId != null) { "school block missing meetingId: ${pb.block.colorKey}" }
                    require(pb.block.manualItemId == null) { "school block has manualItemId: ${pb.block.colorKey}" }
                    BlockNode(pb, isCurrent, showNonCurrentWeek, gridW, gridH) { onSchoolBlockClick(pb.block.meetingId!!) }
                }
                for (pb in placedManual) {
                    val isCurrent = pb.block.weeks.contains(viewedWeek)
                    if (!isCurrent && !showNonCurrentWeek) continue
                    require(pb.block.manualItemId != null) { "manual block missing manualItemId: ${pb.block.colorKey}" }
                    require(pb.block.meetingId == null) { "manual block has meetingId: ${pb.block.colorKey}" }
                    BlockNode(pb, isCurrent, showNonCurrentWeek, gridW, gridH) { onManualBlockClick(pb.block.manualItemId!!) }
                }
                if (nowLine != null) {
                    Box(
                        Modifier
                            .offset(y = maxHeight * axis.fractionOf(nowLine))
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(Color.Red)
                            .testTag("now_line"),
                    )
                }
            }
        }
    }
}

/** 单个课程/手动块节点：绝对定位 + 稳定 testTag + a11y + 点击；onLongPress 空实现用于消费长按（correction 7）。 */
@Composable
private fun BoxScope.BlockNode(
    pb: PlacedBlock,
    isCurrentWeek: Boolean,
    showNonCurrentWeek: Boolean,
    gridW: androidx.compose.ui.unit.Dp,
    gridH: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val tag: String = pb.block.meetingId?.let { "school_block:${it.value}" }
        ?: pb.block.manualItemId?.let { "manual_block:${it.value}" }
        ?: error("block has neither meetingId nor manualItemId: ${pb.block.colorKey}")
    val colW = gridW / 7f
    val groupW = colW / pb.columnsInGroup
    val x = colW * (pb.block.weekday - 1) + groupW * pb.column
    val y = gridH * pb.topFraction
    val h = gridH * pb.heightFraction
    val colorIdx = CoursePalette.colorIndexFor(pb.block.colorKey)
    val alpha = CoursePalette.alphaFor(isCurrentWeek, showNonCurrentWeek)
    Box(
        Modifier
            .offset(x = x, y = y)
            .size(width = groupW, height = h)
            .graphicsLayer { this.alpha = alpha }
            .background(CoursePalette.containerColor(colorIdx), RoundedCornerShape(4.dp))
            .pointerInput(tag) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { /* 消费长按：块上长按不得触发空白区新建（B2 correction 7） */ },
                )
            }
            .semantics { contentDescription = BlockTexts.a11y(pb.block) }
            .testTag(tag),
    ) {
        BlockTexts.Content(pb.block, showTeacher = h > 60.dp, showTime = h > 90.dp, blockWidth = groupW)
    }
}

@Composable
private fun WeekHeaderCells(weekDates: LocalDateRange, today: LocalDate?, modifier: Modifier) {
    Row(modifier) {
        val dowTexts = listOf("一", "二", "三", "四", "五", "六", "日")
        for (dow in 0..6) {
            val date = weekDates.start.plusDays(dow.toLong())
            val isToday = date == today
            Box(
                Modifier
                    .weight(1f)
                    .height(32.dp)
                    .testTag(if (isToday) "today_header" else "header_$dow")
                    .padding(2.dp),
            ) { Text("${dowTexts[dow]} ${date.dayOfMonth}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun TimeGutter(periodStarts: List<LocalTime>, axis: TimelineAxis, modifier: Modifier) {
    BoxWithConstraints(modifier) {
        val gridH = maxHeight
        // authoritative label set = unique(periodStarts + axis.start + axis.endInclusive) sorted（correction 4）
        val labels = (periodStarts + axis.start + axis.endInclusive).toSet().sorted()
        Column {
            for (t in labels) {
                val y = gridH * axis.fractionOf(t)
                Text(
                    t.toString(),
                    Modifier
                        .offset(y = y.coerceAtMost(gridH - 12.dp))
                        .padding(start = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
