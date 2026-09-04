package com.ustc.timetable.timetable.layout

import kotlin.math.ceil

const val TIME_RAIL_TEXT_PADDING_DP: Float = 4f
const val TIME_RAIL_DIVIDER_DP: Float = 0.5f

data class TimeRailWidthPx(
    val widestLabelPx: Int,
    val leftPaddingPx: Int,
    val rightPaddingPx: Int,
    val dividerPx: Int,
) {
    val totalPx: Int = widestLabelPx + leftPaddingPx + rightPaddingPx + dividerPx
}

fun measuredTimeRailWidthPx(
    renderedLabelWidthsPx: List<Float>,
    density: Float,
): TimeRailWidthPx {
    require(density > 0f)
    require(renderedLabelWidthsPx.all { it >= 0f && it.isFinite() })
    val paddingPx = ceil(TIME_RAIL_TEXT_PADDING_DP * density).toInt()
    return TimeRailWidthPx(
        widestLabelPx = ceil(renderedLabelWidthsPx.maxOrNull() ?: 0f).toInt(),
        leftPaddingPx = paddingPx,
        rightPaddingPx = paddingPx,
        dividerPx = ceil(TIME_RAIL_DIVIDER_DP * density).toInt(),
    )
}
