package com.ustc.timetable.school.ustc.dto

import com.ustc.timetable.timetable.domain.Term
import java.time.LocalDate

data class UstcCourseSummary(
    val courseCode: String,
    val name: String,
    val credits: Double?,
    val department: String?,
    val courseType: String?,
    val teacherSummary: String?,
    val weeksText: String?,
)

data class UstcTimetableEntry(
    val courseName: String,
    val courseCode: String?,
    val weekdayText: String,
    val periodText: String,
    val weekText: String,
    val locationText: String,
    val teacherText: String,
    val sourceAssignmentKey: String? = null,
)

data class UstcSemesterMetaPartial(
    val displayName: String?,
    val academicYear: String?,
    val term: Term?,
    val week1Start: LocalDate?,
    val totalWeeks: Int?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
)
