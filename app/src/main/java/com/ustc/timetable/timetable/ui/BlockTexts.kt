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
import kotlin.math.floor

data class CourseCardTextBudget(
    val titleMaxLines: Int,
    val showLocation: Boolean,
    val teacherMaxLines: Int,
    val showTime: Boolean,
)

data class CourseCardTextMetrics(
    val cardHeightDp: Float,
    val cardWidthDp: Float,
    val titleLineHeightDp: Float,
    val locationLineHeightDp: Float,
    val metadataLineHeightDp: Float,
)

object CourseCardTextTokens {
    const val CONTENT_VERTICAL_PADDING_DP = 4f
    const val MAX_TITLE_LINES = 3
    const val LOCATION_LINES = 1
    const val MAX_TEACHER_LINES = 2
    const val TEACHER_MIN_OPTIONAL_WIDTH_DP = 40f
    const val TIME_MIN_OPTIONAL_WIDTH_DP = 52f
    const val MARKER_DIAMETER_DP = 8f
    const val MARKER_GAP_DP = 2f
    const val MARKER_RIGHT_INSET_DP = 2f
}

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

    fun budget(
        metrics: CourseCardTextMetrics,
        hasLocation: Boolean,
        hasTeachers: Boolean,
        markerCount: Int,
    ): CourseCardTextBudget {
        require(metrics.titleLineHeightDp > 0f)
        require(metrics.locationLineHeightDp > 0f)
        require(metrics.metadataLineHeightDp > 0f)
        require(markerCount in 0..2)
        val usableHeight = (metrics.cardHeightDp - CourseCardTextTokens.CONTENT_VERTICAL_PADDING_DP)
            .coerceAtLeast(0f)
        val locationHeight = if (
            hasLocation &&
            usableHeight >= metrics.titleLineHeightDp + metrics.locationLineHeightDp
        ) {
            metrics.locationLineHeightDp
        } else {
            0f
        }
        val titleMaxLines = floor(
            (usableHeight - locationHeight).coerceAtLeast(0f) / metrics.titleLineHeightDp,
        ).toInt().coerceIn(1, CourseCardTextTokens.MAX_TITLE_LINES)
        val afterMandatory = (
            usableHeight - titleMaxLines * metrics.titleLineHeightDp - locationHeight
        ).coerceAtLeast(0f)
        val markerReservation = if (markerCount <= 0) {
            0f
        } else {
            markerCount * CourseCardTextTokens.MARKER_DIAMETER_DP +
                (markerCount - 1) * CourseCardTextTokens.MARKER_GAP_DP +
                CourseCardTextTokens.MARKER_RIGHT_INSET_DP
        }
        val optionalWidth = (metrics.cardWidthDp - markerReservation).coerceAtLeast(0f)
        val teacherMaxLines = when {
            !hasTeachers || optionalWidth < CourseCardTextTokens.TEACHER_MIN_OPTIONAL_WIDTH_DP -> 0
            afterMandatory >= 2f * metrics.metadataLineHeightDp -> 2
            afterMandatory >= metrics.metadataLineHeightDp -> 1
            else -> 0
        }
        val afterTeachers = afterMandatory - teacherMaxLines * metrics.metadataLineHeightDp
        val showTime = optionalWidth >= CourseCardTextTokens.TIME_MIN_OPTIONAL_WIDTH_DP &&
            afterTeachers >= metrics.metadataLineHeightDp
        return CourseCardTextBudget(
            titleMaxLines = titleMaxLines,
            showLocation = locationHeight > 0f,
            teacherMaxLines = teacherMaxLines,
            showTime = showTime,
        )
    }

    /** 卡片内容：名称优先；地点独立一行；教师、时间仅在高度与宽度同时足够时出现。 */
    @Composable
    fun Content(
        block: TimedBlock,
        budget: CourseCardTextBudget,
        contentColor: Color,
    ) {
        Column(Modifier.fillMaxSize().padding(2.dp)) {
            Text(
                block.title,
                style = TimetableTypography.courseTitle,
                color = contentColor,
                maxLines = budget.titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (budget.showLocation && block.location.isNotBlank()) {
                Text(
                    block.location,
                    style = TimetableTypography.courseLocation,
                    color = contentColor,
                    maxLines = CourseCardTextTokens.LOCATION_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (budget.teacherMaxLines > 0 && block.teacherNames.isNotEmpty()) {
                Text(
                    block.teacherNames.joinToString("、"),
                    style = TimetableTypography.courseMetadata,
                    color = contentColor,
                    maxLines = budget.teacherMaxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (budget.showTime) {
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
