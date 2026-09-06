package com.ustc.timetable.timetable.domain

import java.time.Instant
import java.time.LocalTime
import com.ustc.timetable.scheduleprofile.LocalTimeRange
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainModelInvariantTest {

    private val sem = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = "p1")

    private fun validCourse(
        id: CourseId = CourseId("c1"),
        source: ItemSource = ItemSource.SCHOOL,
        sourceCourseKey: String = "name:高等无机化学",
        name: String = "高等无机化学",
        credits: Double? = 3.0,
    ) = Course(id, sem.id, sourceCourseKey, "CHEM5013P", name, credits, null, source)

    private fun validMeeting(
        id: MeetingId = MeetingId("m1"),
        courseId: CourseId = CourseId("c1"),
        weekday: Int = 5,
        startPeriod: Int = 3,
        endPeriod: Int = 5,
        weekPattern: WeekPattern = WeekPattern.range(2, 12),
        location: String = "TH-B301",
        teacherNames: List<String> = listOf("刘斯"),
        source: ItemSource = ItemSource.SCHOOL,
        exactStartTime: LocalTime? = null,
        exactEndTime: LocalTime? = null,
    ) = CourseMeeting(
        id, courseId, weekday, startPeriod, endPeriod, weekPattern, location, teacherNames, source,
        exactStartTime, exactEndTime,
    )

    private fun validManual(
        id: ManualItemId = ManualItemId("i1"),
        source: ItemSource = ItemSource.MANUAL,
        title: String = "固态电池专题讲座",
        weekday: Int = 6,
        startTime: LocalTime = LocalTime.of(14, 20),
        endTime: LocalTime = LocalTime.of(16, 0),
        weekPattern: WeekPattern = WeekPattern.of(5),
    ) = ManualScheduleItem(
        id, sem.id, title, weekday, startTime, endTime, weekPattern, null, null, Instant.EPOCH, Instant.EPOCH, source,
    )

    // ---- source 与实体类型一致（SPEC §3.3/§3.4 + A4 correction） ----

    @Test fun course_source_defaults_school_and_constructs() {
        assertEquals(ItemSource.SCHOOL, validCourse().source)
    }

    @Test fun manual_source_defaults_manual_and_constructs() {
        assertEquals(ItemSource.MANUAL, validManual().source)
    }

    @Test fun course_rejects_manual_source() {
        assertThrows(IllegalArgumentException::class.java) { validCourse(source = ItemSource.MANUAL) }
    }

    @Test fun meeting_rejects_manual_source() {
        assertThrows(IllegalArgumentException::class.java) { validMeeting(source = ItemSource.MANUAL) }
    }

    @Test fun manual_rejects_school_source() {
        assertThrows(IllegalArgumentException::class.java) { validManual(source = ItemSource.SCHOOL) }
    }

    // ---- WeekPattern 非空集 ----

    @Test fun meeting_rejects_empty_week_pattern() {
        assertThrows(IllegalArgumentException::class.java) { validMeeting(weekPattern = WeekPattern.EMPTY) }
    }

    @Test fun manual_rejects_empty_week_pattern() {
        assertThrows(IllegalArgumentException::class.java) { validManual(weekPattern = WeekPattern.EMPTY) }
    }

    // ---- CourseMeeting 结构校验 ----

    @Test fun meeting_rejects_weekday_zero() {
        assertThrows(IllegalArgumentException::class.java) { validMeeting(weekday = 0) }
    }

    @Test fun meeting_rejects_period_zero() {
        assertThrows(IllegalArgumentException::class.java) { validMeeting(startPeriod = 0) }
    }

    @Test fun meeting_rejects_period_fourteen() {
        assertThrows(IllegalArgumentException::class.java) { validMeeting(endPeriod = 14) }
    }

    @Test fun meeting_rejects_inverted_periods() {
        assertThrows(IllegalArgumentException::class.java) { validMeeting(startPeriod = 5, endPeriod = 3) }
    }

    @Test fun exact_time_pair_is_atomic_and_ordered() {
        assertThrows(IllegalArgumentException::class.java) {
            validMeeting(exactStartTime = LocalTime.of(16, 10))
        }
        assertThrows(IllegalArgumentException::class.java) {
            validMeeting(
                exactStartTime = LocalTime.of(17, 50),
                exactEndTime = LocalTime.of(16, 10),
            )
        }
    }

    @Test fun effective_range_prefers_exact_pair_and_standard_meeting_uses_profile() {
        val periods = (1..13).map { number ->
            val start = LocalTime.of(7, 0).plusMinutes((number - 1) * 55L)
            PeriodTime(number, start, start.plusMinutes(45))
        }
        val profile = ScheduleProfile(
            id = "p",
            name = "fixture",
            isBundledOfficial = false,
            periods = periods,
        )
        assertEquals(
            LocalTimeRange(LocalTime.of(16, 10), LocalTime.of(17, 50)),
            validMeeting(exactStartTime = LocalTime.of(16, 10), exactEndTime = LocalTime.of(17, 50))
                .effectiveTimeRange(profile),
        )
        assertEquals(
            LocalTimeRange(periods[2].start, periods[4].end),
            validMeeting().effectiveTimeRange(profile),
        )
    }

    // ---- ManualScheduleItem 结构校验 ----

    @Test fun manual_rejects_blank_title() {
        assertThrows(IllegalArgumentException::class.java) { validManual(title = "  ") }
    }

    @Test fun manual_rejects_bad_weekday() {
        assertThrows(IllegalArgumentException::class.java) { validManual(weekday = 8) }
    }

    @Test fun manual_rejects_equal_start_end() {
        assertThrows(IllegalArgumentException::class.java) { validManual(endTime = LocalTime.of(14, 20)) }
    }

    @Test fun manual_allows_arbitrary_minutes_not_on_period_boundary() {
        val m = validManual()
        assertEquals(LocalTime.of(14, 20), m.startTime)
        assertEquals(LocalTime.of(16, 0), m.endTime)
    }

    // ---- Course identity/content 最小校验 ----

    @Test fun course_rejects_blank_name() {
        assertThrows(IllegalArgumentException::class.java) { validCourse(name = " ") }
    }

    @Test fun course_rejects_blank_sourceCourseKey() {
        assertThrows(IllegalArgumentException::class.java) { validCourse(sourceCourseKey = "") }
    }

    @Test fun course_credits_nullable() {
        assertEquals(null, validCourse(credits = null).credits)
    }
}
