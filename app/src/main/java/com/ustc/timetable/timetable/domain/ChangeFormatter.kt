package com.ustc.timetable.timetable.domain

object ChangeFormatter {
    fun notificationLines(change: ScheduleChange): List<String> = when (change) {
        is ScheduleChange.CourseAdded -> listOf(change.courseName, "新增课程")
        is ScheduleChange.CourseRemoved -> listOf(change.courseName, "课程已移除")
        is ScheduleChange.MeetingAdded -> listOf(change.courseName, "新增安排：${formatSummary(change.summary)}")
        is ScheduleChange.MeetingRemoved -> listOf(change.courseName, "移除安排：${formatSummary(change.summary)}")
        is ScheduleChange.TimeChanged -> listOf(
            change.courseName,
            "${formatWeeks(change.weeks)}时间：${formatPeriods(change.oldPeriods)} → ${formatPeriods(change.newPeriods)}",
        )
        is ScheduleChange.LocationChanged -> listOf(
            change.courseName,
            "${formatWeeks(change.weeks)}教室：${valueOrDash(change.old)} → ${valueOrDash(change.new)}",
        )
        is ScheduleChange.TeacherChanged -> listOf(
            change.courseName,
            "${formatWeeks(change.weeks)}教师：${valueOrDash(change.old)} → ${valueOrDash(change.new)}",
        )
        is ScheduleChange.WeekPatternChanged -> listOf(
            change.courseName,
            "${formatWeekday(change.weekday)}周次：${formatWeeks(change.old)} → ${formatWeeks(change.new)}",
        )
    }

    private fun formatSummary(summary: MeetingSummary): String = listOf(
        formatWeekday(summary.weekday),
        formatPeriodRange(summary.startPeriod, summary.endPeriod),
        formatWeeks(summary.weeks),
        valueOrDash(summary.location),
        summary.teacherNames.takeIf { it.isNotEmpty() }?.joinToString("、") ?: "—",
    ).joinToString(" · ")

    private fun formatWeekday(weekday: Int): String = when (weekday) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        7 -> "周日"
        else -> error("weekday out of range: $weekday")
    }

    private fun formatPeriodRange(start: Int, end: Int): String =
        if (start == end) "第${start}节" else "第${start}–${end}节"

    private fun formatPeriods(span: String): String = "第${span.replace('-', '–')}节"

    private fun formatWeeks(weeks: WeekPattern): String = "第${weeks.format().replace('-', '–')}周"

    private fun valueOrDash(value: String): String = value.ifBlank { "—" }
}
