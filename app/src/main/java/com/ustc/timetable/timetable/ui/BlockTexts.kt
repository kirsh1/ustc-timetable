package com.ustc.timetable.timetable.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.TimedBlock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 课表文案 formatter（SPEC §4.3/§4.4）。a11y 复现 meeting 自身 week pattern（不含 viewedWeek）；
 * domain canonical format 的 ASCII '-' 仅在 UI 文案中转为 '–'。
 */
object BlockTexts {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** 教师/时间共同的最小块宽阈值（B2 correction 8）。 */
    private val minWidthForExtras = 52.dp

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

    /** 卡片内容：名称/地点必显；教师、时间仅在高度与宽度同时足够时出现。 */
    @Composable
    fun Content(block: TimedBlock, showTeacher: Boolean, showTime: Boolean, blockWidth: Dp) {
        val wide = blockWidth > minWidthForExtras
        Text(
            block.title,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            block.location,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showTeacher && wide && block.teacherNames.isNotEmpty()) {
            Text(
                block.teacherNames.joinToString("、"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showTime && wide) {
            Text(
                timeText(block.start, block.endInclusive),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
