package com.ustc.timetable.timetable.layout

import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SegmentedTimelineAxisTest {

    private val profile = ScheduleProfile(
        id = "official",
        name = "official",
        isBundledOfficial = true,
        periods = listOf(
            period(1, "07:50", "08:35"),
            period(2, "08:40", "09:25"),
            period(3, "09:45", "10:30"),
            period(4, "10:35", "11:20"),
            period(5, "11:25", "12:10"),
            period(6, "14:00", "14:45"),
            period(7, "14:50", "15:35"),
            period(8, "15:55", "16:40"),
            period(9, "16:45", "17:30"),
            period(10, "17:35", "18:20"),
            period(11, "19:30", "20:15"),
            period(12, "20:20", "21:05"),
            period(13, "21:10", "21:55"),
        ),
    )
    private val segmented = SegmentedTimelineAxis.from(profile)

    @Test fun long_breaks_are_derived_from_profile_and_not_hardcoded() {
        assertEquals(
            listOf(
                LocalTime.of(12, 10) to LocalTime.of(14, 0),
                LocalTime.of(18, 20) to LocalTime.of(19, 30),
            ),
            segmented.compressedIntervals.map { it.start to it.endInclusive },
        )
    }

    @Test fun regular_long_break_occupies_exactly_eight_dp() {
        val axis = segmented.resolve(viewportHeightDp = 700f, compressedGapDp = 8f)
        val gapHeight = 700f * (
            axis.fractionOf(LocalTime.of(14, 0)) -
                axis.fractionOf(LocalTime.of(12, 10))
            )
        assertEquals(8f, gapHeight, 0.001f)
    }

    @Test fun compact_long_break_occupies_exactly_six_dp() {
        val axis = segmented.resolve(viewportHeightDp = 420f, compressedGapDp = 6f)
        val gapHeight = 420f * (
            axis.fractionOf(LocalTime.of(19, 30)) -
                axis.fractionOf(LocalTime.of(18, 20))
            )
        assertEquals(6f, gapHeight, 0.001f)
    }

    @Test fun teaching_time_and_short_breaks_keep_one_linear_scale() {
        val axis = segmented.resolve(viewportHeightDp = 700f, compressedGapDp = 8f)
        val fortyFiveMinutes = axis.fractionOf(LocalTime.of(8, 35)) - axis.fractionOf(LocalTime.of(7, 50))
        val twentyMinutes = axis.fractionOf(LocalTime.of(9, 45)) - axis.fractionOf(LocalTime.of(9, 25))
        assertEquals(45f / 20f, fortyFiveMinutes / twentyMinutes, 0.001f)
    }

    @Test fun compressed_interval_is_reversible_for_arbitrary_manual_minutes() {
        val axis = segmented.resolve(viewportHeightDp = 700f, compressedGapDp = 8f)
        for (time in listOf(LocalTime.of(12, 10), LocalTime.of(12, 43), LocalTime.of(13, 15), LocalTime.of(13, 59), LocalTime.of(14, 0))) {
            assertEquals(time, axis.timeAt(axis.fractionOf(time)))
        }
    }

    @Test fun endpoints_remain_visible_and_bounded() {
        val axis = segmented.resolve(viewportHeightDp = 420f, compressedGapDp = 6f)
        assertEquals(0f, axis.fractionOf(LocalTime.of(7, 50)), 0f)
        assertEquals(1f, axis.fractionOf(LocalTime.of(21, 55)), 0f)
        assertEquals(LocalTime.of(7, 50), axis.timeAt(-1f))
        assertEquals(LocalTime.of(21, 55), axis.timeAt(2f))
    }

    @Test fun school_and_manual_blocks_share_segmented_projection() {
        val axis = segmented.resolve(viewportHeightDp = 700f, compressedGapDp = 8f)
        val school = block("school", LocalTime.of(11, 25), LocalTime.of(12, 10), school = true)
        val manual = block("manual", LocalTime.of(12, 43), LocalTime.of(13, 15), school = false)
        val placed = WeeklyTimetableLayout.place(listOf(school, manual), axis)

        assertEquals(axis.fractionOf(school.start), placed.single { it.block === school }.topFraction, 0f)
        assertEquals(axis.fractionOf(manual.start), placed.single { it.block === manual }.topFraction, 0f)
        assertTrue(placed.single { it.block === manual }.heightFraction > 0f)
    }

    @Test fun long_press_uses_same_inverse_mapping_inside_compressed_interval() {
        val axis = segmented.resolve(viewportHeightDp = 700f, compressedGapDp = 8f)
        val y = axis.fractionOf(LocalTime.of(13, 17))
        val draft = LongPressResolver.resolve(columnFraction = 0.5f, yFraction = y, axis = axis)
        assertEquals(4, draft.weekday)
        assertEquals(LocalTime.of(13, 15), draft.snappedStart)
    }

    private fun period(number: Int, start: String, end: String) =
        PeriodTime(number, LocalTime.parse(start), LocalTime.parse(end))

    private fun block(key: String, start: LocalTime, end: LocalTime, school: Boolean) = object : TimedBlock {
        override val colorKey = key
        override val meetingId = if (school) MeetingId(key) else null
        override val manualItemId = if (school) null else ManualItemId(key)
        override val weekday = 1
        override val start = start
        override val endInclusive = end
        override val weeks = WeekPattern.of(1)
        override val title = key
        override val location = ""
        override val teacherNames = emptyList<String>()
    }
}
