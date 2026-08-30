package com.ustc.timetable.timetable.layout

import java.time.LocalTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile

/** 可布局块（来自 CourseMeeting 或 ManualScheduleItem 的时间投影）。 */
interface TimedBlock {
    /** 稳定业务 identity：学校块 `semesterId:sourceCourseKey`；手动块 `manual:id`；仅用于 tie-break，不用于 overlap。 */
    val colorKey: String
    val meetingId: com.ustc.timetable.timetable.domain.MeetingId?
    val manualItemId: com.ustc.timetable.timetable.domain.ManualItemId?
    val weekday: Int                       // 1..7 = 周一..周日
    val start: LocalTime
    val endInclusive: LocalTime            // 半开区间语义下“结束时刻”（显示为 LocalTime）
    val weeks: com.ustc.timetable.timetable.domain.WeekPattern
    val title: String
    val location: String
}

/** 布局结果：authority 固定为 block.weekday（不另存独立可变 weekday）。 */
data class PlacedBlock(
    val block: TimedBlock,
    val column: Int,
    val columnsInGroup: Int,
    val topFraction: Float,
    val heightFraction: Float,
) {
    val weekday: Int get() = block.weekday
}

/**
 * 每周课表真实时间布局（纯函数，SPEC §4.2）。
 * 按 weekday 分组；组内按 (start,end,stableIdentity) 排序；
 * 传递闭包 overlap group 再 greedy 列染色；columnsInGroup 仅为该组所需列数。
 */
object WeeklyTimetableLayout {

    fun axisOf(profile: ScheduleProfile): TimelineAxis = TimelineAxis.axisOf(profile)

    /** 向下吸附到最近的 5 分钟倍数，并清除 seconds/nanos。 */
    fun snapDownTo5Minutes(t: LocalTime): LocalTime {
        val flooredMin = t.hour * 60 + (t.minute / 5) * 5
        return LocalTime.of(flooredMin / 60, flooredMin % 60)
    }

    fun place(blocks: List<TimedBlock>, axis: TimelineAxis): List<PlacedBlock> {
        require(blocks.all { it.weekday in 1..7 && it.endInclusive > it.start })

        val out = mutableListOf<PlacedBlock>()
        for (weekday in 1..7) {
            val day = blocks.filter { it.weekday == weekday }
            if (day.isEmpty()) continue

            val sorted = day.sortedWith(
                compareBy<TimedBlock>({ it.start }, { it.endInclusive }, { stableKey(it) }),
            )

            // sweep：传递闭包 overlap group
            var group = mutableListOf<TimedBlock>()
            var groupEnd: LocalTime? = null
            fun flush() {
                if (group.isEmpty()) return
                out += layGroup(group, axis)
                group = mutableListOf()
                groupEnd = null
            }
            for (b in sorted) {
                if (group.isNotEmpty() && !b.start.isBefore(groupEnd)) {
                    flush()
                }
                group += b
                groupEnd = if (groupEnd == null || b.endInclusive.isAfter(groupEnd)) b.endInclusive else groupEnd
            }
            flush()
        }
        return out
    }

    private fun layGroup(group: List<TimedBlock>, axis: TimelineAxis): List<PlacedBlock> {
        data class ColLast(var lastEnd: LocalTime)
        val cols = mutableListOf<ColLast>()
        val placements = mutableListOf<Pair<TimedBlock, Int>>()

        for (b in group) {
            var col = cols.indexOfFirst { !it.lastEnd.isAfter(b.start) }
            if (col == -1) {
                cols += ColLast(b.endInclusive)
                col = cols.size - 1
            } else {
                cols[col].lastEnd = b.endInclusive
            }
            placements += b to col
        }
        val width = cols.size
        return placements.map { (b, col) ->
            val top = axis.fractionOf(b.start)
            val bottom = axis.fractionOf(b.endInclusive)
            PlacedBlock(b, col, width, top, bottom - top)
        }
    }

    private fun stableKey(b: TimedBlock): String =
        b.meetingId?.value ?: b.manualItemId?.value ?: b.colorKey
}
