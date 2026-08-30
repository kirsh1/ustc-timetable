package com.ustc.timetable.timetable.layout

import java.time.LocalTime
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.WeekPattern

class WeeklyTimetableLayoutTest {

    private val axis = TimelineAxis(LocalTime.of(7, 50), LocalTime.of(21, 55))
    private val t = { h: Int, m: Int -> LocalTime.of(h, m) }

    private data class FakeBlock(
        override val colorKey: String,
        override val meetingId: MeetingId?,
        override val manualItemId: ManualItemId?,
        override val weekday: Int,
        override val start: LocalTime,
        override val endInclusive: LocalTime,
        override val weeks: WeekPattern = WeekPattern.range(1, 1),
        override val title: String = "课",
        override val location: String = "TH-B",
        override val teacherNames: List<String> = emptyList(),
    ) : TimedBlock

    private fun fb(
        weekday: Int, start: LocalTime, end: LocalTime, key: String,
        colorKey: String = "c:$key",
    ) = FakeBlock(colorKey = colorKey, meetingId = MeetingId(key), manualItemId = null, weekday = weekday, start = start, endInclusive = end)

    @Test fun gap5_vs_gap20_height_ratio() {
        val p1 = fb(1, t(9, 25), t(9, 30), "gap5")
        val p2 = fb(1, t(9, 25), t(9, 45), "gap20")
        val h1 = WeeklyTimetableLayout.place(listOf(p1), axis).first().heightFraction
        val h2 = WeeklyTimetableLayout.place(listOf(p2), axis).first().heightFraction
        assertTrue(h1 > 0 && h2 > 0)
        assertEquals(0.25, (h1 / h2).toDouble(), 1e-3)
    }

    @Test fun sevenWeekdays_fixedColumnIndex() {
        val blocks = (1..7).map { fb(it, t(9, 0), t(10, 0), "d$it") }
        val placed = WeeklyTimetableLayout.place(blocks, axis)
        assertEquals(7, placed.size)
        blocks.forEach { assertEquals(1, WeeklyTimetableLayout.place(listOf(it), axis).first().columnsInGroup) }
    }

    @Test fun overlap_twoBlocks_twoColumns_halfWidth() {
        val a = fb(1, t(9, 45), t(10, 30), "a")
        val b = fb(1, t(10, 0), t(10, 45), "b")
        val ps = WeeklyTimetableLayout.place(listOf(a, b), axis)
        assertEquals(2, ps.size)
        assertTrue(ps.all { it.columnsInGroup == 2 })
        assertEquals(0, ps.first { it.block.colorKey == "c:a" }.column)
        assertEquals(1, ps.first { it.block.colorKey == "c:b" }.column)
    }

    @Test fun overlap_threeBlocks_threeColumns() {
        val a = fb(1, t(9, 0), t(10, 0), "a")
        val b = fb(1, t(9, 15), t(10, 15), "b")
        val c = fb(1, t(9, 30), t(10, 30), "c")
        val ps = WeeklyTimetableLayout.place(listOf(a, b, c), axis)
        assertEquals(3, ps.size)
        assertTrue(ps.all { it.columnsInGroup == 3 })
    }

    @Test fun transitiveChain_grouped() {
        val a = fb(1, t(9, 0), t(10, 0), "a")
        val b = fb(1, t(9, 30), t(10, 30), "b")
        val c = fb(1, t(10, 0), t(11, 0), "c")
        val ps = WeeklyTimetableLayout.place(listOf(a, b, c), axis)
        // A 与 C 不直接 overlap，但通过 B 连通 → 同组，且仅 2 列
        assertEquals(3, ps.size)
        assertTrue(ps.all { it.columnsInGroup == 2 })
        assertEquals(0, ps.first { it.block.colorKey == "c:a" }.column)
        assertEquals(1, ps.first { it.block.colorKey == "c:b" }.column)
        assertEquals(0, ps.first { it.block.colorKey == "c:c" }.column)
    }

    @Test fun touchingBlocks_notOverlapped_fullWidth() {
        val a = fb(1, t(9, 0), t(10, 0), "a")
        val b = fb(1, t(10, 0), t(11, 0), "b")
        val ps = WeeklyTimetableLayout.place(listOf(a, b), axis)
        assertTrue(ps.all { it.columnsInGroup == 1 })
    }

    @Test fun sequentialBlocks_shareColumn() {
        val a = fb(1, t(8, 0), t(9, 0), "a")
        val b = fb(1, t(9, 0), t(10, 0), "b")
        val c = fb(1, t(10, 0), t(11, 0), "c")
        val ps = WeeklyTimetableLayout.place(listOf(a, b, c), axis)
        assertTrue(ps.all { it.columnsInGroup == 1 && it.column == 0 })
    }

    @Test fun overlap_group_then_later_isolated_block_gets_full_width() {
        val a = fb(1, t(9, 0), t(10, 0), "a")
        val b = fb(1, t(9, 30), t(10, 30), "b")
        val c = fb(1, t(14, 0), t(15, 0), "c")
        val ps = WeeklyTimetableLayout.place(listOf(a, b, c), axis)
        assertEquals(2, ps.first { it.block.colorKey == "c:a" }.columnsInGroup)
        assertEquals(2, ps.first { it.block.colorKey == "c:b" }.columnsInGroup)
        assertEquals(1, ps.first { it.block.colorKey == "c:c" }.columnsInGroup)
    }

    @Test fun two_separate_overlap_groups_compute_width_independently() {
        val a = fb(1, t(9, 0), t(10, 0), "a")
        val b = fb(1, t(9, 30), t(10, 30), "b")
        val c = fb(1, t(14, 0), t(15, 0), "c")
        val d = fb(1, t(14, 30), t(15, 30), "d")
        val ps = WeeklyTimetableLayout.place(listOf(a, b, c, d), axis)
        assertTrue(ps.filter { it.start_shouldStillBeInFirstGroup(it) }.all { it.columnsInGroup == 2 })
        assertTrue(ps.filter { it.start_shouldStillBeInSecondGroup(it) }.all { it.columnsInGroup == 2 })
    }

    private fun PlacedBlock.start_shouldStillBeInFirstGroup(p: PlacedBlock) = p.block.start.hour < 12
    private fun PlacedBlock.start_shouldStillBeInSecondGroup(p: PlacedBlock) = p.block.start.hour >= 14

    @Test fun input_permutation_produces_same_layout() {
        val a = fb(1, t(9, 0), t(10, 0), "a")
        val b = fb(1, t(9, 30), t(10, 30), "b")
        val c = fb(1, t(10, 0), t(11, 0), "c")
        val s1 = byIdentity(WeeklyTimetableLayout.place(listOf(a, b, c), axis))
        val s2 = byIdentity(WeeklyTimetableLayout.place(listOf(c, b, a), axis))
        assertEquals(s1, s2)
    }

    @Test fun equal_start_end_blocks_receive_deterministic_columns() {
        val a = fb(1, t(9, 0), t(10, 0), "aa")
        val b = fb(1, t(9, 0), t(10, 0), "bb")
        val s1 = byIdentity(WeeklyTimetableLayout.place(listOf(a, b), axis))
        val s2 = byIdentity(WeeklyTimetableLayout.place(listOf(b, a), axis))
        assertEquals(s1, s2)
        // aa 字典序更小，稳定排在 aa=col0, bb=col1
        assertEquals(0, s1["aa"]!!.column)
        assertEquals(1, s1["bb"]!!.column)
    }

    private fun byIdentity(placed: List<PlacedBlock>): Map<String, PlacedBlock> =
        placed.associateBy { it.block.meetingId!!.value }

    @Test fun clamp_outOfAxis() {
        val early = fb(1, t(7, 0), t(8, 30), "early")
        val late = fb(1, t(21, 0), t(23, 0), "late")
        val ps = WeeklyTimetableLayout.place(listOf(early, late), axis)
        assertEquals(0f, ps.first { it.block.colorKey == "c:early" }.topFraction, 0.0001f)
        assertEquals(1f, ps.first { it.block.colorKey == "c:late" }.topFraction + ps.first { it.block.colorKey == "c:late" }.heightFraction, 0.0001f)
        ps.forEach { assertTrue(it.heightFraction >= 0) }
    }

    @Test fun snapDown_1423_to_1420() {
        assertEquals(LocalTime.of(14, 20), WeeklyTimetableLayout.snapDownTo5Minutes(LocalTime.of(14, 23)))
        assertEquals(LocalTime.of(14, 25), WeeklyTimetableLayout.snapDownTo5Minutes(LocalTime.of(14, 25)))
    }

    @Test fun snap_clears_seconds_and_nanos() {
        assertEquals(LocalTime.of(14, 20), WeeklyTimetableLayout.snapDownTo5Minutes(LocalTime.of(14, 23, 59, 999_999_999)))
        assertEquals(LocalTime.of(6, 0), WeeklyTimetableLayout.snapDownTo5Minutes(LocalTime.of(6, 0, 0, 1)))
    }

    @Test fun invalid_weekday_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyTimetableLayout.place(listOf(fb(0, t(9, 0), t(10, 0), "x")), axis)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyTimetableLayout.place(listOf(fb(8, t(9, 0), t(10, 0), "x")), axis)
        }
    }

    @Test fun inverted_or_zero_duration_block_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyTimetableLayout.place(listOf(fb(1, t(10, 0), t(10, 0), "x")), axis)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WeeklyTimetableLayout.place(listOf(fb(1, t(11, 0), t(10, 0), "x")), axis)
        }
    }

    @Test fun emptyInput_emptyOutput() {
        assertTrue(WeeklyTimetableLayout.place(emptyList(), axis).isEmpty())
    }

    @Test fun placedBlock_weekday_is_block_weekday_derived() {
        val b = fb(3, t(9, 0), t(10, 0), "x")
        val p = WeeklyTimetableLayout.place(listOf(b), axis).first()
        assertEquals(b.weekday, p.weekday)
    }
}
