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
    val locationMaxLines: Int = 1,
)

data class CourseCardTextMetrics(
    val cardHeightDp: Float,
    val cardWidthDp: Float,
    val titleLineHeightDp: Float,
    val locationLineHeightDp: Float,
    val metadataLineHeightDp: Float,
    val locationRequiredLines: Int = 1,
    val titleRequiredLines: Int = 3,
    val reservedTopDp: Float = 0f,
    val reservedBottomDp: Float = 0f,
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
        require(metrics.reservedTopDp >= 0f)
        require(metrics.reservedBottomDp >= 0f)
        val usableHeight = (
            metrics.cardHeightDp - CourseCardTextTokens.CONTENT_VERTICAL_PADDING_DP -
                metrics.reservedTopDp - metrics.reservedBottomDp
        )
            .coerceAtLeast(0f)
        val firstLocationHeight = if (
            hasLocation &&
            usableHeight >= metrics.titleLineHeightDp + metrics.locationLineHeightDp
        ) {
            metrics.locationLineHeightDp
        } else {
            0f
        }
        val titleMaxLines = floor(
            (usableHeight - firstLocationHeight).coerceAtLeast(0f) / metrics.titleLineHeightDp,
        ).toInt().coerceIn(1, metrics.titleRequiredLines.coerceIn(1, CourseCardTextTokens.MAX_TITLE_LINES))
        val locationLines = if (firstLocationHeight > 0f) {
            floor((usableHeight - titleMaxLines * metrics.titleLineHeightDp) / metrics.locationLineHeightDp)
                .toInt().coerceAtLeast(1).coerceAtMost(metrics.locationRequiredLines.coerceAtLeast(1))
        } else 0
        val locationHeight = locationLines * metrics.locationLineHeightDp
        val afterMandatory = (
            usableHeight - titleMaxLines * metrics.titleLineHeightDp - locationHeight
        ).coerceAtLeast(0f)
        // Markers have their own footer outside the supplied text height; text keeps full width.
        val optionalWidth = metrics.cardWidthDp.coerceAtLeast(0f)
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
            locationMaxLines = locationLines,
        )
    }

    /** 卡片内容：名称优先；地点独立一行；教师、时间仅在高度与宽度同时足够时出现。 */
    @Composable
    fun Content(
        block: TimedBlock,
        budget: CourseCardTextBudget,
        contentColor: Color,
        reservedTopDp: Float = 0f,
        reservedBottomDp: Float = 0f,
    ) {
        Column(
            Modifier.fillMaxSize().padding(
                start = 2.dp,
                top = (2f + reservedTopDp).dp,
                end = 2.dp,
                bottom = (2f + reservedBottomDp).dp,
            ),
        ) {
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
                    maxLines = budget.locationMaxLines.coerceAtLeast(1),
                    softWrap = true,
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
