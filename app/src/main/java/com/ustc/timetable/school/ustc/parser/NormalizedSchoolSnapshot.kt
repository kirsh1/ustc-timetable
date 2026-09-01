package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseMeeting

data class NormalizationIssue(
    val severity: Severity,
    val message: String,
) {
    enum class Severity { WARNING, HARD }
}

data class NormalizedSchoolSnapshot(
    val courses: List<Course>,
    val meetings: List<CourseMeeting>,
    val issues: List<NormalizationIssue>,
)
