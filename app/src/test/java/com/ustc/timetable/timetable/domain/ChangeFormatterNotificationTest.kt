package com.ustc.timetable.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChangeFormatterNotificationTest {
    @Test fun location_single_week_exact_text() {
        assertEquals(
            listOf("高等无机化学", "第10周教室：TH-B301 → TH-C204"),
            lines(ScheduleChange.LocationChanged("高等无机化学", WeekPattern.of(10), "TH-B301", "TH-C204")),
        )
    }

    @Test fun location_range_week_exact_text() {
        assertEquals(
            listOf("课程A", "第7–12周教室：A → B"),
            lines(ScheduleChange.LocationChanged("课程A", WeekPattern.range(7, 12), "A", "B")),
        )
    }

    @Test fun sparse_week_format() {
        assertEquals(
            listOf("课程A", "第2,4,6周教室：A → B"),
            lines(ScheduleChange.LocationChanged("课程A", WeekPattern.of(2, 4, 6), "A", "B")),
        )
    }

    @Test fun course_added_lines() = assertEquals(
        listOf("课程A", "新增课程"), lines(ScheduleChange.CourseAdded("课程A")),
    )

    @Test fun course_removed_lines() = assertEquals(
        listOf("课程A", "课程已移除"), lines(ScheduleChange.CourseRemoved("课程A")),
    )

    @Test fun meeting_added_lines() = assertEquals(
        listOf("课程A", "新增安排：周五 · 第3–5节 · 第7–12周 · TH-B301 · 刘斯"),
        lines(ScheduleChange.MeetingAdded("课程A", summary())),
    )

    @Test fun meeting_removed_lines() = assertEquals(
        listOf("课程A", "移除安排：周五 · 第3–5节 · 第7–12周 · TH-B301 · 刘斯"),
        lines(ScheduleChange.MeetingRemoved("课程A", summary())),
    )

    @Test fun time_changed_lines() = assertEquals(
        listOf("课程A", "第7–12周时间：第3–5节 → 第6–7节"),
        lines(ScheduleChange.TimeChanged("课程A", 5, "3-5", "6-7", WeekPattern.range(7, 12))),
    )

    @Test fun teacher_changed_lines() = assertEquals(
        listOf("课程A", "第7–12周教师：吴长征 → 刘斯"),
        lines(ScheduleChange.TeacherChanged("课程A", WeekPattern.range(7, 12), "吴长征", "刘斯")),
    )

    @Test fun weekpattern_changed_lines() = assertEquals(
        listOf("课程A", "周五周次：第2–6周 → 第7–12周"),
        lines(ScheduleChange.WeekPatternChanged("课程A", 5, WeekPattern.range(2, 6), WeekPattern.range(7, 12))),
    )

    @Test fun missing_location_and_teacher_use_dash_in_summary() = assertEquals(
        listOf("课程A", "新增安排：周五 · 第3节 · 第10周 · — · —"),
        lines(
            ScheduleChange.MeetingAdded(
                "课程A",
                MeetingSummary(5, 3, 3, WeekPattern.of(10), "", emptyList()),
            ),
        ),
    )

    private fun lines(change: ScheduleChange) = ChangeFormatter.notificationLines(change)

    private fun summary() = MeetingSummary(
        weekday = 5,
        startPeriod = 3,
        endPeriod = 5,
        weeks = WeekPattern.range(7, 12),
        location = "TH-B301",
        teacherNames = listOf("刘斯"),
    )
}
