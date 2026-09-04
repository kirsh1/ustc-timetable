package com.ustc.timetable.timetable.layout

import com.ustc.timetable.scheduleprofile.PeriodTime
import java.time.LocalTime

sealed interface TimeBoundaryMark {
    val times: List<LocalTime>

    data class AxisStart(val time: LocalTime) : TimeBoundaryMark {
        override val times = listOf(time)
    }

    data class PeriodGap(val previousEnd: LocalTime, val nextStart: LocalTime) : TimeBoundaryMark {
        override val times = if (previousEnd == nextStart) listOf(previousEnd) else listOf(previousEnd, nextStart)
    }

    data class AxisEnd(val time: LocalTime) : TimeBoundaryMark {
        override val times = listOf(time)
    }
}

fun timeBoundaryMarks(periods: List<PeriodTime>): List<TimeBoundaryMark> {
    if (periods.isEmpty()) return emptyList()
    val ordered = periods.sortedBy { it.number }
    return buildList {
        add(TimeBoundaryMark.AxisStart(ordered.first().start))
        ordered.zipWithNext().forEach { (before, after) ->
            add(TimeBoundaryMark.PeriodGap(before.end, after.start))
        }
        add(TimeBoundaryMark.AxisEnd(ordered.last().end))
    }
}
