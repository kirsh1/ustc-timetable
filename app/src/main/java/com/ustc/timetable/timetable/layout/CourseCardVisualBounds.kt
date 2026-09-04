package com.ustc.timetable.timetable.layout

data class CourseCardVisualBounds(val topDp: Float, val bottomDp: Float)

fun courseCardVisualBounds(
    logicalTopDp: Float,
    logicalBottomDp: Float,
    insetDp: Float,
): CourseCardVisualBounds {
    require(logicalBottomDp >= logicalTopDp)
    require(insetDp >= 0f)
    val available = logicalBottomDp - logicalTopDp
    val applied = insetDp.coerceAtMost(available / 2f)
    return CourseCardVisualBounds(logicalTopDp + applied, logicalBottomDp - applied)
}
