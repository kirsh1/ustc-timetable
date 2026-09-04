package com.ustc.timetable.timetable.layout

import com.ustc.timetable.scheduleprofile.LocalTimeRange
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import java.time.Duration
import java.time.LocalTime
import kotlin.math.roundToLong

/** 可正反映射的时间坐标系；绘制、布局与手势反解必须依赖同一实例。 */
interface ReversibleTimelineAxis {
    val start: LocalTime
    val endInclusive: LocalTime
    fun fractionOf(time: LocalTime): Float
    fun timeAt(fraction: Float): LocalTime
}

/**
 * 从学期绑定作息推导的分段轴定义。达到 [teachingGroupThresholdMinutes] 的教学组间隔占固定视觉高度，
 * 其余教学时间与短课间共享同一真实分钟比例。
 */
class SegmentedTimelineAxis private constructor(
    val start: LocalTime,
    val endInclusive: LocalTime,
    val compressedIntervals: List<LocalTimeRange>,
) {
    fun resolve(viewportHeightDp: Float, compressedGapDp: Float): ResolvedSegmentedTimelineAxis =
        ResolvedSegmentedTimelineAxis(
            start = start,
            endInclusive = endInclusive,
            compressedIntervals = compressedIntervals,
            viewportHeightDp = viewportHeightDp,
            compressedGapDp = compressedGapDp,
        )

    companion object {
        const val TEACHING_GROUP_THRESHOLD_MINUTES = 20L

        fun from(
            profile: ScheduleProfile,
            teachingGroupThresholdMinutes: Long = TEACHING_GROUP_THRESHOLD_MINUTES,
        ): SegmentedTimelineAxis {
            require(teachingGroupThresholdMinutes >= 0)
            val periods = profile.periods.sortedBy { it.number }
            val gaps = periods.zipWithNext().mapNotNull { (before, after) ->
                val minutes = Duration.between(before.end, after.start).toMinutes()
                if (minutes >= teachingGroupThresholdMinutes) {
                    LocalTimeRange(before.end, after.start)
                } else {
                    null
                }
            }
            val window = profile.dayWindow()
            return SegmentedTimelineAxis(window.start, window.endInclusive, gaps)
        }
    }
}

/** 绑定到具体 viewport 后的轴；固定 dp 压缩段由此转成稳定 fraction，并保持严格可逆。 */
class ResolvedSegmentedTimelineAxis internal constructor(
    override val start: LocalTime,
    override val endInclusive: LocalTime,
    compressedIntervals: List<LocalTimeRange>,
    private val viewportHeightDp: Float,
    private val compressedGapDp: Float,
) : ReversibleTimelineAxis {

    private data class Segment(
        val start: LocalTime,
        val end: LocalTime,
        val visualDp: Float,
    ) {
        val minutes: Long = Duration.between(start, end).toMinutes()
    }

    private val segments: List<Segment>

    init {
        require(viewportHeightDp > 0f && viewportHeightDp.isFinite())
        require(compressedGapDp >= 0f && compressedGapDp.isFinite())
        val compressedMinutes = compressedIntervals.sumOf {
            Duration.between(it.start, it.endInclusive).toMinutes()
        }
        val totalMinutes = Duration.between(start, endInclusive).toMinutes()
        val linearMinutes = totalMinutes - compressedMinutes
        val compressedVisualDp = compressedGapDp * compressedIntervals.size
        require(linearMinutes > 0L && viewportHeightDp > compressedVisualDp)
        val linearDpPerMinute = (viewportHeightDp - compressedVisualDp) / linearMinutes

        segments = buildList {
            var cursor = start
            compressedIntervals.forEach { gap ->
                if (cursor < gap.start) {
                    val minutes = Duration.between(cursor, gap.start).toMinutes()
                    add(Segment(cursor, gap.start, minutes * linearDpPerMinute))
                }
                add(Segment(gap.start, gap.endInclusive, compressedGapDp))
                cursor = gap.endInclusive
            }
            if (cursor < endInclusive) {
                val minutes = Duration.between(cursor, endInclusive).toMinutes()
                add(Segment(cursor, endInclusive, minutes * linearDpPerMinute))
            }
        }
    }

    override fun fractionOf(time: LocalTime): Float {
        val clamped = when {
            time < start -> start
            time > endInclusive -> endInclusive
            else -> time
        }
        var visual = 0f
        for (segment in segments) {
            if (clamped >= segment.end) {
                visual += segment.visualDp
                continue
            }
            if (clamped > segment.start) {
                val elapsed = Duration.between(segment.start, clamped).toMinutes()
                visual += segment.visualDp * elapsed / segment.minutes.toFloat()
            }
            break
        }
        return (visual / viewportHeightDp).coerceIn(0f, 1f)
    }

    override fun timeAt(fraction: Float): LocalTime {
        val target = fraction.coerceIn(0f, 1f) * viewportHeightDp
        var visual = 0f
        for (segment in segments) {
            val endVisual = visual + segment.visualDp
            if (target <= endVisual) {
                val ratio = if (segment.visualDp == 0f) 0f else (target - visual) / segment.visualDp
                val minutes = (segment.minutes * ratio.coerceIn(0f, 1f)).roundToLong()
                return segment.start.plusMinutes(minutes)
            }
            visual = endVisual
        }
        return endInclusive
    }
}
