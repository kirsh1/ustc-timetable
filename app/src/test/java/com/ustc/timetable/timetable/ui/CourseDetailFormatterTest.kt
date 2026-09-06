package com.ustc.timetable.timetable.ui

import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CourseDetailFormatterTest {

    /** 自定义作息：第 3 节 10:10 起、第 5 节 12:34 止——证明无官方时间硬编码。 */
    private fun customProfile(): ScheduleProfile = ScheduleProfile(
        "profile.test.custom",
        "custom",
        false,
        listOf(
            PeriodTime(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
            PeriodTime(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
            PeriodTime(3, LocalTime.of(10, 10), LocalTime.of(10, 55)),
            PeriodTime(4, LocalTime.of(11, 0), LocalTime.of(11, 45)),
            PeriodTime(5, LocalTime.of(11, 49), LocalTime.of(12, 34)),
            PeriodTime(6, LocalTime.of(13, 0), LocalTime.of(13, 45)),
            PeriodTime(7, LocalTime.of(13, 50), LocalTime.of(14, 35)),
            PeriodTime(8, LocalTime.of(14, 40), LocalTime.of(15, 25)),
            PeriodTime(9, LocalTime.of(15, 30), LocalTime.of(16, 15)),
            PeriodTime(10, LocalTime.of(16, 20), LocalTime.of(17, 5)),
            PeriodTime(11, LocalTime.of(17, 10), LocalTime.of(17, 55)),
            PeriodTime(12, LocalTime.of(18, 0), LocalTime.of(18, 45)),
            PeriodTime(13, LocalTime.of(18, 50), LocalTime.of(19, 35)),
        ),
    )

    private val official = com.ustc.timetable.scheduleprofile.ScheduleProfile(
        "profile.bundled.ustc.2026-autumn", "official", true,
        listOf(
            PeriodTime(1, LocalTime.of(7, 50), LocalTime.of(8, 35)),
            PeriodTime(2, LocalTime.of(8, 40), LocalTime.of(9, 25)),
            PeriodTime(3, LocalTime.of(9, 45), LocalTime.of(10, 30)),
            PeriodTime(4, LocalTime.of(10, 35), LocalTime.of(11, 20)),
            PeriodTime(5, LocalTime.of(11, 25), LocalTime.of(12, 10)),
            PeriodTime(6, LocalTime.of(14, 0), LocalTime.of(14, 45)),
            PeriodTime(7, LocalTime.of(14, 50), LocalTime.of(15, 35)),
            PeriodTime(8, LocalTime.of(15, 55), LocalTime.of(16, 40)),
            PeriodTime(9, LocalTime.of(16, 45), LocalTime.of(17, 30)),
            PeriodTime(10, LocalTime.of(17, 35), LocalTime.of(18, 20)),
            PeriodTime(11, LocalTime.of(19, 30), LocalTime.of(20, 15)),
            PeriodTime(12, LocalTime.of(20, 20), LocalTime.of(21, 5)),
            PeriodTime(13, LocalTime.of(21, 10), LocalTime.of(21, 55)),
        ),
    )

    private fun meeting(weekday: Int = 5, start: Int = 3, end: Int = 5) = CourseMeeting(
        MeetingId("m"), CourseId("c"), weekday, start, end, WeekPattern.range(7, 12), "TH-B301", listOf("刘斯"),
    )

    // ---- weekdayName ----

    @Test fun weekday_names_1_to_7() {
        assertEquals("周一", CourseDetailFormatter.weekdayName(1))
        assertEquals("周二", CourseDetailFormatter.weekdayName(2))
        assertEquals("周三", CourseDetailFormatter.weekdayName(3))
        assertEquals("周四", CourseDetailFormatter.weekdayName(4))
        assertEquals("周五", CourseDetailFormatter.weekdayName(5))
        assertEquals("周六", CourseDetailFormatter.weekdayName(6))
        assertEquals("周日", CourseDetailFormatter.weekdayName(7))
    }

    @Test fun weekday_invalid_throws() {
        assertThrows(IllegalArgumentException::class.java) { CourseDetailFormatter.weekdayName(0) }
        assertThrows(IllegalArgumentException::class.java) { CourseDetailFormatter.weekdayName(8) }
    }

    // ---- formatWeeks（detail spacing：第 7–12 周） ----

    @Test fun formatWeeks_range() {
        assertEquals("第 7–12 周", CourseDetailFormatter.formatWeeks(WeekPattern.range(7, 12)))
    }

    @Test fun formatWeeks_single() {
        assertEquals("第 10 周", CourseDetailFormatter.formatWeeks(WeekPattern.of(10)))
    }

    @Test fun formatWeeks_sparse() {
        assertEquals("第 2,4,6 周", CourseDetailFormatter.formatWeeks(WeekPattern.of(2, 4, 6)))
    }

    @Test fun formatWeeks_mixed_ranges() {
        assertEquals("第 2–6,8,10–12 周", CourseDetailFormatter.formatWeeks(WeekPattern.parse("2-6,8,10-12")))
    }

    // ---- formatMeetingTime：绑定 profile 为唯一时间权威 ----

    @Test fun formatMeetingTime_uses_bound_profile() {
        assertEquals("周五 · 09:45–12:10", CourseDetailFormatter.formatMeetingTime(meeting(), official))
    }

    @Test fun formatMeetingTime_custom_profile_not_hardcoded() {
        assertEquals("周五 · 10:10–12:34", CourseDetailFormatter.formatMeetingTime(meeting(), customProfile()))
    }

    @Test fun formatMeetingTime_prefers_exact_minutes() {
        val exact = meeting().copy(
            exactStartTime = LocalTime.of(16, 10),
            exactEndTime = LocalTime.of(17, 50),
        )
        assertEquals("周五 · 16:10–17:50", CourseDetailFormatter.formatMeetingTime(exact, official))
    }

    // ---- credits / code / teachers / location ----

    @Test fun credits_null_is_dash() {
        assertEquals("—", CourseDetailFormatter.formatCredits(null))
    }

    @Test fun credits_integral_omits_decimal_zero() {
        assertEquals("3", CourseDetailFormatter.formatCredits(3.0))
    }

    @Test fun credits_fraction_preserved() {
        assertEquals("2.5", CourseDetailFormatter.formatCredits(2.5))
    }

    @Test fun blank_course_code_is_dash() {
        assertEquals("—", CourseDetailFormatter.formatCourseCode(""))
        assertEquals("—", CourseDetailFormatter.formatCourseCode("  "))
    }

    @Test fun teachers_join_with_chinese_separator() {
        assertEquals("刘斯、吴长征", CourseDetailFormatter.formatTeachers(listOf("刘斯", "吴长征")))
    }

    @Test fun missing_teacher_and_location_are_dash() {
        assertEquals("—", CourseDetailFormatter.formatTeachers(emptyList()))
        assertEquals("—", CourseDetailFormatter.formatLocation(""))
    }
}
