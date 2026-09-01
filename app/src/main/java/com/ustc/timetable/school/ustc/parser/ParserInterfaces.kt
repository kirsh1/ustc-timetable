package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry

interface CourseSelectionPageParser {
    fun parse(page: UstcPortalPage): List<UstcCourseSummary>
}

interface TimetablePageParser {
    fun parse(page: UstcPortalPage): List<UstcTimetableEntry>
}

data class SemesterMetaResult(
    val meta: UstcSemesterMetaPartial,
    val isConfident: Boolean,
)

interface SemesterMetaParser {
    fun parse(
        selection: UstcPortalPage,
        timetable: UstcPortalPage,
    ): SemesterMetaResult
}
