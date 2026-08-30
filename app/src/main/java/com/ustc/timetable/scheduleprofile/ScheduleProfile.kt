package com.ustc.timetable.scheduleprofile

import java.time.LocalTime

data class PeriodTime(val number: Int, val start: LocalTime, val end: LocalTime) {
    init {
        require(end > start) { "period $number inverted: $start-$end" }
    }
}

/** 闭区间时间范围；contains 语义直接来自 [ClosedRange]，不自行 shadow。 */
data class LocalTimeRange(
    override val start: LocalTime,
    override val endInclusive: LocalTime,
) : ClosedRange<LocalTime> {
    init {
        require(start <= endInclusive) { "inverted range: $start..$endInclusive" }
    }
}

/**
 * 作息 profile（SPEC §3.5）。固定 13 节、节号严格 1..13、单调不重叠。
 * 5/20 分钟课间间隔是官方 asset 的事实数据，不是构造器规则（custom profile 允许用户改作息）。
 */
data class ScheduleProfile(
    val id: String,
    val name: String,
    val isBundledOfficial: Boolean,
    val periods: List<PeriodTime>,
) {
    init {
        require(periods.size == 13) { "profile must define exactly 13 periods, got ${periods.size}" }
        require(periods.map { it.number } == (1..13).toList()) { "period numbers must be exactly 1..13 in order" }
        periods.zipWithNext().forEach { (a, b) ->
            require(b.start > a.end) { "period ${b.number} must start after period ${a.number} ends" }
        }
    }

    /** 第 [startPeriod]..[endPeriod] 节的真实时间区间；越界/倒序抛 IllegalArgumentException。 */
    fun timeRange(startPeriod: Int, endPeriod: Int): LocalTimeRange {
        require(startPeriod in 1..13 && endPeriod in startPeriod..13) { "bad periods $startPeriod-$endPeriod" }
        return LocalTimeRange(periods[startPeriod - 1].start, periods[endPeriod - 1].end)
    }

    /** 最早节次开始到最晚节次结束；时间轴边界与手动项目合法性均以此为准。 */
    fun dayWindow(): LocalTimeRange = LocalTimeRange(periods.minOf { it.start }, periods.maxOf { it.end })
}
