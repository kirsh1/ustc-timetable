package com.ustc.timetable.timetable.ui

import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.TimedBlock
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object BlockTexts {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun weekdayText(weekday: Int): String = when (weekday) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; else -> "周日"
    }

    fun weekText(weeks: WeekPattern): String {
        val f = weeks.format()
        val parts = f.split(',').map { seg ->
            if ("-" in seg) seg.replace("-", "–") else seg
        }
        val joined = parts.joinToString(",")
        return if (joined.contains('-') || joined.contains(",") || joined.contains("–"))
            "第${joined}周"
        else "第${joined}周"
    }

    fun timeText(start: LocalTime, end: LocalTime): String =
        "${start.format(timeFmt)}–${end.format(timeFmt)}"

    /** a11y 仅复现 meeting 自身的 weekPattern，不使用 viewedWeek。 */
    fun a11y(block: TimedBlock): String {
        val wday = weekdayText(block.weekday)
        val t = timeText(block.start, block.endInclusive)
        val w = weekText(block.weeks)
        val segs = mutableListOf<String>()
        segs += block.title
        segs += "$wday $t"
        segs += w
        if (block.location.isNotBlank()) segs += block.location
        if (block.teacherNames.isNotEmpty() && block.teacherNames.joinToString("、").isNotBlank()) {
            segs += block.teacherNames.joinToString("、")
        }
        return segs.joinToString("，")
    }

    @Composable
    fun Content(block: TimedBlock, showTeacher: Boolean, showTime: Boolean, blockWidth: Dp) {
        val showT = showTeacher && blockWidth > 52.dp
        val showTm = showTime && blockWidth > 52.dp
        Text(
            block.title,
            fontWeight = FontWeight.Bold,
            maxLines = if (showT || showTm) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            block.location,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showT && block.teacherNames.isNotEmpty()) {
            Text(
                block.teacherNames.joinToString("、"),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showTm) {
            Text(
                timeText(block.start, block.endInclusive),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
