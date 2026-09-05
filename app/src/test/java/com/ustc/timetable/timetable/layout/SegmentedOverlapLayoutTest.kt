package com.ustc.timetable.timetable.layout

import com.ustc.timetable.timetable.ui.UiManualTimedBlock
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class SegmentedOverlapLayoutTest {
    private fun time(hour: Int) = LocalTime.of(hour, 0)
    private fun block(id: String, start: Int, end: Int) = UiManualTimedBlock("manual:$id", ManualItemId(id), 1,
        time(start), time(end), WeekPattern.of(1), id, "room", emptyList())
    @Test fun partial_overlap_only_splits_intersection() {
        val result = SegmentedOverlapLayout.place(listOf(block("a", 10, 12), block("b", 11, 13)))
        assertEquals(listOf(OverlapSlice(time(10), time(11), 0f, 1f), OverlapSlice(time(11), time(12), 0f, .5f)), result[0].slices)
        assertEquals(listOf(OverlapSlice(time(11), time(12), .5f, 1f), OverlapSlice(time(12), time(13), 0f, 1f)), result[1].slices)
    }
    @Test fun touching_events_each_get_full_width() {
        val result = SegmentedOverlapLayout.place(listOf(block("a", 10, 11), block("b", 11, 12)))
        assertTrue(result.all { it.slices.single().left == 0f && it.slices.single().right == 1f })
    }
    @Test fun nested_and_triple_conflicts_are_deterministic() {
        val input = listOf(block("a", 9, 14), block("b", 10, 13), block("c", 11, 12))
        assertEquals(SegmentedOverlapLayout.place(input), SegmentedOverlapLayout.place(input.reversed()))
        val a = SegmentedOverlapLayout.place(input).first()
        assertEquals(1f, a.slices.first().right, 0f)
        assertEquals(1f / 3, a.slices.single { it.start == time(11) }.right, .0001f)
        assertEquals(1f, a.slices.last().right, 0f)
    }
    @Test fun shape_hit_does_not_cover_other_events_rectangle() {
        val a = SegmentedOverlapLayout.place(listOf(block("a", 10, 12), block("b", 11, 13))).first()
        assertTrue(a.contains(.75f, LocalTime.of(10, 30)))
        assertFalse(a.contains(.75f, LocalTime.of(11, 30)))
        assertFalse(a.contains(.25f, time(12)))
    }
    @Test fun content_rectangle_is_contained_and_deterministic() {
        val a = SegmentedOverlapLayout.place(listOf(block("a", 10, 12), block("b", 11, 13))).first()
        val rect = a.contentRect(TimelineAxis(time(10), time(14)))
        assertEquals(SliceRect(0f, 0f, 1f, .25f), rect)
    }
}
