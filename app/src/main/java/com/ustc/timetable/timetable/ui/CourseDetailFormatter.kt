package com.ustc.timetable.timetable.ui

import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.domain.effectiveTimeRange
import java.time.format.DateTimeFormatter

/**
 * 课程详情文案（frozen §4.4 detail 风格）。
 * ChangeFormatter 属 H1（ScheduleChange→通知文案），此处刻意不混职责；
 * detail 周次带空格（第 7–12 周），与 B2 a11y 紧凑风格（第7–12周）是不同 presentation context。
 */
object CourseDetailFormatter {

    private val hhmm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun weekdayName(weekday: Int): String = when (weekday) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"
        else -> throw IllegalArgumentException("weekday out of range: $weekday")
    }

    fun formatWeeks(pattern: WeekPattern): String =
        "第 " + pattern.format().replace("-", "–") + " 周"

    /** 节次 → 真实时间：唯一 authority = viewed 学期绑定 profile；绝不 hardcode 官方作息。 */
    fun formatMeetingTime(meeting: CourseMeeting, profile: ScheduleProfile): String {
        val range = meeting.effectiveTimeRange(profile)
        return "${weekdayName(meeting.weekday)} · ${range.start.format(hhmm)}–${range.endInclusive.format(hhmm)}"
    }

    fun formatTeachers(names: List<String>): String =
        if (names.isEmpty()) "—" else names.joinToString("、")

    fun formatLocation(location: String): String = location.ifBlank { "—" }

    fun formatCourseCode(courseCode: String): String = courseCode.ifBlank { "—" }

    /** null → "—"（frozen §4.4）；整数 credit 省略小数零（3.0 → "3"）。 */
    fun formatCredits(credits: Double?): String = when {
        credits == null -> "—"
        credits == credits.toLong().toDouble() -> credits.toLong().toString()
        else -> credits.toString()
    }
}
