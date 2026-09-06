package com.ustc.timetable.timetable.domain

import java.time.LocalTime

@JvmInline value class MeetingId(val value: String)

/**
 * 实际上课安排（canonical，SPEC §3.3）。同一门课不同周/教师/教室由 normalizer 拆成多条 meeting。
 * 节次编号固定 1..13（校方作息）；weekPattern 不允许空集；只允许 SCHOOL source。
 */
data class CourseMeeting(
    val id: MeetingId,
    val courseId: CourseId,
    val weekday: Int,              // 1..7 = 周一..周日
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPattern: WeekPattern,
    val location: String,
    val teacherNames: List<String>,
    val source: ItemSource = ItemSource.SCHOOL,
    val exactStartTime: LocalTime? = null,
    val exactEndTime: LocalTime? = null,
) {
    init {
        require(source == ItemSource.SCHOOL) { "CourseMeeting.source must be SCHOOL: $source" }
        require(weekday in 1..7) { "weekday out of range: $weekday" }
        require(startPeriod in 1..13) { "startPeriod out of range: $startPeriod" }
        require(endPeriod in startPeriod..13) { "endPeriod out of range: $startPeriod-$endPeriod" }
        require(weekPattern != WeekPattern.EMPTY) { "weekPattern must not be empty" }
        require((exactStartTime == null) == (exactEndTime == null)) {
            "exact start and end must either both be present or both be absent"
        }
        if (exactStartTime != null) {
            require(requireNotNull(exactEndTime) > exactStartTime) {
                "exact end must be after exact start: $exactStartTime-$exactEndTime"
            }
        }
    }
}
