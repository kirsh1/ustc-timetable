package com.ustc.timetable.timetable.layout

import com.ustc.timetable.timetable.ui.OverlapEntry
import java.time.Instant
import java.time.LocalTime

data class OverlapSlice(val start: LocalTime, val end: LocalTime, val left: Float, val right: Float)
data class SliceRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val area: Float get() = (right - left) * (bottom - top)
}
data class SegmentedBlock(val block: TimedBlock, val slices: List<OverlapSlice>) {
    fun contains(dayFraction: Float, time: LocalTime): Boolean = slices.any {
        dayFraction >= it.left && dayFraction < it.right && time >= it.start && time < it.end
    }

    /** Largest axis-projected rectangle contained in the union, never its bounding box. */
    fun contentRect(axis: ReversibleTimelineAxis): SliceRect {
        val candidates = buildList {
            for (first in slices.indices) {
                var left = 0f
                var right = 1f
                for (last in first..slices.lastIndex) {
                    if (last > first && slices[last - 1].end != slices[last].start) break
                    left = maxOf(left, slices[last].left)
                    right = minOf(right, slices[last].right)
                    if (right <= left) break
                    add(SliceRect(left, axis.fractionOf(slices[first].start), right, axis.fractionOf(slices[last].end)))
                }
            }
        }
        return candidates.sortedWith(compareByDescending<SliceRect> { it.area }.thenBy { it.top }.thenBy { it.left }).first()
    }
}

object SegmentedOverlapLayout {
    fun place(blocks: List<TimedBlock>): List<SegmentedBlock> {
        require(blocks.all { it.weekday in 1..7 && it.endInclusive > it.start })
        val sorted = blocks.sortedWith(compareBy<TimedBlock>({ it.weekday }, { it.start }, { it.endInclusive },
            { OverlapEntry(it, Instant.EPOCH).key }))
        return sorted.groupBy { it.weekday }.values.flatMap { day ->
            val slices = day.associateWith { mutableListOf<OverlapSlice>() }
            val boundaries = day.flatMap { listOf(it.start, it.endInclusive) }.distinct().sorted()
            boundaries.zipWithNext().forEach { (start, end) ->
                val present = day.filter { it.start < end && it.endInclusive > start }
                present.forEachIndexed { index, block ->
                    val next = OverlapSlice(start, end, index.toFloat() / present.size, (index + 1f) / present.size)
                    val bucket = slices.getValue(block)
                    val previous = bucket.lastOrNull()
                    if (previous != null && previous.end == start && previous.left == next.left && previous.right == next.right) {
                        bucket[bucket.lastIndex] = previous.copy(end = end)
                    } else bucket += next
                }
            }
            day.map { SegmentedBlock(it, slices.getValue(it)) }
        }
    }
}
