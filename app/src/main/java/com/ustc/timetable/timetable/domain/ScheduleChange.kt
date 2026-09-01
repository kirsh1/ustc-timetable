package com.ustc.timetable.timetable.domain

data class MeetingSummary(
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: WeekPattern,
    val location: String,
    val teacherNames: List<String>,
)

sealed interface ScheduleChange {
    data class CourseAdded(val courseName: String) : ScheduleChange

    data class CourseRemoved(val courseName: String) : ScheduleChange

    data class MeetingAdded(
        val courseName: String,
        val summary: MeetingSummary,
    ) : ScheduleChange

    data class MeetingRemoved(
        val courseName: String,
        val summary: MeetingSummary,
    ) : ScheduleChange

    data class TimeChanged(
        val courseName: String,
        val weekday: Int,
        val oldPeriods: String,
        val newPeriods: String,
        val weeks: WeekPattern,
    ) : ScheduleChange

    data class LocationChanged(
        val courseName: String,
        val weeks: WeekPattern,
        val old: String,
        val new: String,
    ) : ScheduleChange

    data class TeacherChanged(
        val courseName: String,
        val weeks: WeekPattern,
        val old: String,
        val new: String,
    ) : ScheduleChange

    data class WeekPatternChanged(
        val courseName: String,
        val weekday: Int,
        val old: WeekPattern,
        val new: WeekPattern,
    ) : ScheduleChange
}
