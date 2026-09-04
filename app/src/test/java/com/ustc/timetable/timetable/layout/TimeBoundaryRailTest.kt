package com.ustc.timetable.timetable.layout

import com.ustc.timetable.scheduleprofile.PeriodTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeBoundaryRailTest {
    private val periods = listOf(
        p(1, "07:50", "08:35"), p(2, "08:40", "09:25"), p(3, "09:45", "10:30"),
    )

    @Test fun every_profile_period_start_is_represented() {
        val times = timeBoundaryMarks(periods).flatMap(TimeBoundaryMark::times)
        periods.forEach { assertTrue(it.start in times) }
    }

    @Test fun every_profile_period_end_is_represented() {
        val times = timeBoundaryMarks(periods).flatMap(TimeBoundaryMark::times)
        periods.forEach { assertTrue(it.end in times) }
    }

    @Test fun short_break_end_and_next_start_form_one_cluster() {
        assertTrue(timeBoundaryMarks(periods).contains(TimeBoundaryMark.PeriodGap(LocalTime.of(8, 35), LocalTime.of(8, 40))))
    }

    @Test fun no_duplicate_time_boundary_labels() {
        val times = timeBoundaryMarks(periods).flatMap(TimeBoundaryMark::times)
        assertEquals(times.distinct(), times)
    }

    @Test fun custom_profile_drives_all_boundary_labels() {
        val custom = listOf(p(1, "07:53", "08:38"), p(2, "08:44", "09:29"))
        assertEquals(
            listOf(LocalTime.of(7, 53), LocalTime.of(8, 38), LocalTime.of(8, 44), LocalTime.of(9, 29)),
            timeBoundaryMarks(custom).flatMap(TimeBoundaryMark::times),
        )
    }

    @Test fun existing_time_boundary_positions_remain_unchanged() {
        assertEquals(
            listOf("07:50", "08:35", "08:40", "09:25", "09:45", "10:30"),
            timeBoundaryMarks(periods).flatMap(TimeBoundaryMark::times).map(LocalTime::toString),
        )
    }

    private fun p(number: Int, start: String, end: String) = PeriodTime(number, LocalTime.parse(start), LocalTime.parse(end))
}
