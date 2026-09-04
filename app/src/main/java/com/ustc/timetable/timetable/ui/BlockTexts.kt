package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.TimedBlock
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.ustc.timetable.ui.theme.TimetableTypography

/**
 * 课表文案 formatter（SPEC §4.3/§4.4）。a11y 复现 meeting 自身 week pattern（不含 viewedWeek）；
 * domain canonical format 的 ASCII '-' 仅在 UI 文案中转为 '–'。
 */
object BlockTexts {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun weekdayText(weekday: Int): String = when (weekday) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; else -> "周日"
    }

    fun weekText(weeks: WeekPattern): String =
        "第" + weeks.format().split(",").joinToString(",") { seg -> seg.replace("-", "–") } + "周"

    fun timeText(start: LocalTime, end: LocalTime): String =
        "${start.format(hhmm)}–${end.format(hhmm)}"

    /** 完整无障碍描述；location/教师为空时省略段，不产生连续逗号。 */
    fun a11y(block: TimedBlock): String = buildList {
        add(block.title)
        add("${weekdayText(block.weekday)} ${timeText(block.start, block.endInclusive)}")
        add(weekText(block.weeks))
        if (block.location.isNotBlank()) add(block.location)
        if (block.teacherNames.isNotEmpty()) add(block.teacherNames.joinToString("、"))
    }.joinToString("，")

    fun titleMaxLinesFor(cardHeightDp: Float, hasLocation: Boolean): Int = when {
        cardHeightDp < 42f -> 1
        cardHeightDp < if (hasLocation) 96f else 72f -> 2
        else -> 3
    }

    /** 卡片内容：名称优先；地点独立一行；教师、时间仅在高度与宽度同时足够时出现。 */
    @Composable
    fun Content(
        block: TimedBlock,
        titleMaxLines: Int,
        showLocation: Boolean,
        showTeacher: Boolean,
        showTime: Boolean,
        contentColor: Color,
    ) {
        Column(Modifier.fillMaxSize().padding(2.dp)) {
            Text(
                block.title,
                style = TimetableTypography.courseTitle,
                color = contentColor,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (showLocation && block.location.isNotBlank()) {
                Text(
                    block.location,
                    style = TimetableTypography.courseLocation,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showTeacher && block.teacherNames.isNotEmpty()) {
                Text(
                    block.teacherNames.joinToString("、"),
                    style = TimetableTypography.courseMetadata,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (showTime) {
                Text(
                    timeText(block.start, block.endInclusive),
                    style = TimetableTypography.courseMetadata,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
