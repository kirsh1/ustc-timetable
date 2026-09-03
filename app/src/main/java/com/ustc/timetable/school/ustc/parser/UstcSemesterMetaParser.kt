package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial

class UstcSemesterMetaParser : SemesterMetaParser {
    override fun parse(
        selection: UstcPortalPage,
        timetable: UstcPortalPage,
    ): SemesterMetaResult = parsePortalPayload {
        val selectedLessons = selection.requiredJson(UstcEndpointId.SELECTED_LESSONS).requiredArray()
        if (selectedLessons.isEmpty()) parseFailure()
        val layout = timetable.requiredJson(UstcEndpointId.TIMETABLE_LAYOUT)
            .requiredObject()
            .requiredResultObject()
        if (layout.requiredArray("courseUnitList").isEmpty()) parseFailure()
        val datum = timetable.requiredJson(UstcEndpointId.TIMETABLE_DATUM)
            .requiredObject()
            .requiredResultObject()
        if (
            datum.requiredArray("lessonList").isEmpty() ||
            datum.requiredArray("scheduleList").isEmpty() ||
            datum.requiredArray("scheduleGroupList").isEmpty()
        ) {
            parseFailure()
        }
        val digest = timetable.requiredJson(UstcEndpointId.WEEK_INDICES_DIGEST)
            .requiredObject()
            .requiredResultObject()
        if (digest.isEmpty()) parseFailure()

        SemesterMetaResult(
            meta = UstcSemesterMetaPartial(
                displayName = null,
                academicYear = null,
                term = null,
                week1Start = null,
                totalWeeks = null,
                startDate = null,
                endDate = null,
            ),
            isConfident = false,
        )
    }
}
