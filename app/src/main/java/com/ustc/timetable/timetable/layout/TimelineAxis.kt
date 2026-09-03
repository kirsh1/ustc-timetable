package com.ustc.timetable.timetable.layout

import java.time.Duration
import java.time.LocalTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile

/**
 * 真实分钟时间轴（SPEC §4.1）。fraction = Duration(start→t)/Duration(start→end)，clamp 到 [0,1]。
 * axisOf 仅从学期绑定 profile 的 dayWindow 推导——绝不 hardcode 07:50/21:55。
 */
data class TimelineAxis(
    override val start: LocalTime,
    override val endInclusive: LocalTime,
) : ReversibleTimelineAxis {
    init {
        require(endInclusive > start) { "inverted axis: $start..$endInclusive" }
    }

    private val totalMinutes: Long = Duration.between(start, endInclusive).toMinutes()

    /** t 在轴上的归一坐标，落在 [0,1]。 */
    override fun fractionOf(time: LocalTime): Float {
        val pos = Duration.between(start, time).toMinutes().toDouble()
        val raw = if (totalMinutes == 0L) 0.0 else pos / totalMinutes
        return raw.coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * fraction 处的时刻（D1 将对结果做 snapDownTo5Minutes）。totalMinutes*fraction
     * 作 round 到最近整分钟后再 start.plusMinutes，避免 Float 误差对 snap 的连锁影响。
     */
    override fun timeAt(fraction: Float): LocalTime {
        val f = fraction.coerceIn(0f, 1f).toDouble()
        val minutes = (totalMinutes * f).let { kotlin.math.round(it).toLong() }
        val t = start.plusMinutes(minutes)
        return when {
            t.isBefore(start) -> start
            t.isAfter(endInclusive) -> endInclusive
            else -> t
        }
    }

    fun timeAtRaw(fraction: Float): LocalTime = timeAt(fraction)

    companion object {
        fun axisOf(profile: ScheduleProfile): TimelineAxis {
            val w = profile.dayWindow()
            return TimelineAxis(w.start, w.endInclusive)
        }
    }
}
