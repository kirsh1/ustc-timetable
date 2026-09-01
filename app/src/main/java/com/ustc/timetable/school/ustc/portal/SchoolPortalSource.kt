package com.ustc.timetable.school.ustc.portal

import com.ustc.timetable.school.ustc.dto.UstcPortalPage

interface SchoolPortalSource {
    suspend fun fetchCourseSelectionPage(): UstcPortalPage

    suspend fun fetchTimetablePage(): UstcPortalPage
}
