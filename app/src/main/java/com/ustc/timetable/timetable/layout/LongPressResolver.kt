package com.ustc.timetable.timetable.layout

import java.time.LocalTime
import kotlin.math.floor

data class LongPressDraft(
    val weekday: Int,
    val snappedStart: LocalTime,
)

object LongPressResolver {

    fun resolve(
        columnFraction: Float,
        yFraction: Float,
        axis: TimelineAxis,
    ): LongPressDraft {
        require(columnFraction.isFinite()) { "columnFraction must be finite" }
        require(yFraction.isFinite()) { "yFraction must be finite" }

        val x = columnFraction.coerceIn(0f, Math.nextDown(1f))
        val weekday = floor(x.toDouble() * 7.0).toInt() + 1

        val y = yFraction.coerceIn(0f, 1f)
        val raw = axis.timeAt(y)
        val floored = WeeklyTimetableLayout.snapDownTo5Minutes(raw)
        val snapped = when {
            floored < axis.start -> axis.start
            floored > axis.endInclusive -> axis.endInclusive
            else -> floored
        }

        return LongPressDraft(
            weekday = weekday,
            snappedStart = snapped,
        )
    }
}
