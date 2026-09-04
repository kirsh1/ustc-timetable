package com.ustc.timetable.timetable.layout

import androidx.compose.ui.graphics.Color
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedTimelineAxisV2Test {
    @Test fun teaching_group_gap_uses_neutral_theme_outline_instead_of_primary_tint() {
        val outline = Color(0xFF49454F)
        val primary = Color(0xFFD0BCFF)
        val gap = teachingGroupGapColor(outline)
        assertEquals(outline.copy(alpha = 0.18f), gap)
        assertTrue(gap != primary.copy(alpha = gap.alpha))
    }
    private val profile = ScheduleProfile(
        id = "official-v2",
        name = "official-v2",
        isBundledOfficial = true,
        periods = listOf(
            p(1, "07:50", "08:35"), p(2, "08:40", "09:25"),
            p(3, "09:45", "10:30"), p(4, "10:35", "11:20"), p(5, "11:25", "12:10"),
            p(6, "14:00", "14:45"), p(7, "14:50", "15:35"),
            p(8, "15:55", "16:40"), p(9, "16:45", "17:30"), p(10, "17:35", "18:20"),
            p(11, "19:30", "20:15"), p(12, "20:20", "21:05"), p(13, "21:10", "21:55"),
        ),
    )

    @Test fun all_official_group_gaps_have_equal_visual_height() {
        val resolved = SegmentedTimelineAxis.from(profile).resolve(700f, 8f)
        val gaps = listOf("09:25" to "09:45", "12:10" to "14:00", "15:35" to "15:55", "18:20" to "19:30")
        val heights = gaps.map { (a, b) ->
            700f * (resolved.fractionOf(LocalTime.parse(b)) - resolved.fractionOf(LocalTime.parse(a)))
        }
        heights.forEach { assertEquals(8f, it, 0.001f) }
    }

    @Test fun intra_group_minutes_remain_linear() {
        val axis = SegmentedTimelineAxis.from(profile).resolve(700f, 8f)
        val lesson = axis.fractionOf(LocalTime.of(8, 35)) - axis.fractionOf(LocalTime.of(7, 50))
        val shortBreak = axis.fractionOf(LocalTime.of(8, 40)) - axis.fractionOf(LocalTime.of(8, 35))
        assertEquals(9f, lesson / shortBreak, 0.001f)
    }

    @Test fun group_gap_mapping_is_reversible() {
        val axis = SegmentedTimelineAxis.from(profile).resolve(700f, 8f)
        listOf("09:25", "09:31", "09:39", "09:45", "15:41", "15:55").forEach {
            val time = LocalTime.parse(it)
            assertEquals(time, axis.timeAt(axis.fractionOf(time)))
        }
    }

    @Test fun existing_segmented_axis_roundtrip_remains_unchanged() {
        val axis = SegmentedTimelineAxis.from(profile).resolve(700f, 8f)
        listOf("07:50", "08:35", "09:31", "12:10", "14:00", "18:20", "21:55").forEach {
            val time = LocalTime.parse(it)
            assertEquals(time, axis.timeAt(axis.fractionOf(time)))
        }
    }

    @Test fun long_press_inside_group_gap_returns_unique_time() {
        val axis = SegmentedTimelineAxis.from(profile).resolve(700f, 8f)
        val first = LongPressResolver.resolve(.1f, axis.fractionOf(LocalTime.of(9, 31)), axis)
        val second = LongPressResolver.resolve(.1f, axis.fractionOf(LocalTime.of(9, 39)), axis)
        assertEquals(LocalTime.of(9, 30), first.snappedStart)
        assertEquals(LocalTime.of(9, 35), second.snappedStart)
        assertTrue(first.snappedStart != second.snappedStart)
    }

    private fun p(number: Int, start: String, end: String) =
        PeriodTime(number, LocalTime.parse(start), LocalTime.parse(end))
}
