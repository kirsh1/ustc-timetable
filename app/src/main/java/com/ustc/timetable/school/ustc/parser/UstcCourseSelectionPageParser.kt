package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

class UstcCourseSelectionPageParser : CourseSelectionPageParser {
    override fun parse(page: UstcPortalPage): List<UstcCourseSummary> = parsePortalPayload {
        val lessons = page.requiredJson(UstcEndpointId.SELECTED_LESSONS).requiredArray()
        if (lessons.isEmpty()) parseFailure()

        val courses = lessons.map { it.requiredObject().toCourseSummary() }
        if (courses.map(UstcCourseSummary::courseCode).distinct().size != courses.size) parseFailure()
        courses.sortedBy(UstcCourseSummary::courseCode)
    }

    private fun JsonObject.toCourseSummary(): UstcCourseSummary {
        val course = requiredObject("course")
        return UstcCourseSummary(
            courseCode = requiredString("code"),
            name = course.requiredString("nameZh"),
            credits = course.optionalDouble("credits"),
            department = optionalNamedValue("openDepartment"),
            courseType = optionalNamedValue("courseType"),
            teacherSummary = optionalNames("teachers"),
            weeksText = optionalNestedString("weekText", "textZh"),
        )
    }

    private fun JsonObject.optionalNamedValue(name: String): String? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        return value.requiredObject().optionalString("nameZh")
    }

    private fun JsonObject.optionalNestedString(container: String, name: String): String? {
        val value = get(container) ?: return null
        if (value is JsonNull) return null
        return value.requiredObject().optionalString(name)
    }

    private fun JsonObject.optionalNames(name: String): String? {
        val value = get(name) ?: return null
        if (value is JsonNull) return null
        val names = value.requiredArray()
            .map { it.requiredObject().requiredString("nameZh") }
            .distinct()
        return names.takeIf(List<String>::isNotEmpty)?.joinToString("、")
    }
}
