package com.ustc.timetable.timetable.domain

import kotlinx.serialization.Serializable

@Serializable
data class FingerprintedSemesterMeta(
    val displayName: String,
    val academicYear: String,
    val term: String,
    val week1StartEpochDay: Long,
    val totalWeeks: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
)

@Serializable
data class FingerprintedCourse(
    val sourceCourseKey: String,
    val courseCode: String,
    val name: String,
    val credits: Double?,
    val courseType: String?,
)

@Serializable
data class FingerprintedMeeting(
    val sourceCourseKey: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPatternMask: Long,
    val location: String,
    val teacherNames: List<String>,
)

@Serializable
data class FingerprintedSchoolContent(
    val semesterMeta: FingerprintedSemesterMeta,
    val courses: List<FingerprintedCourse>,
    val meetings: List<FingerprintedMeeting>,
) {
    companion object {
        fun of(
            semester: Semester,
            courses: List<Course>,
            meetings: List<CourseMeeting>,
        ): FingerprintedSchoolContent {
            require(courses.map { it.id }.toSet().size == courses.size) { "duplicate CourseId" }
            require(courses.map { it.sourceCourseKey }.toSet().size == courses.size) {
                "duplicate sourceCourseKey"
            }
            require(courses.all { it.source == ItemSource.SCHOOL }) { "non-school course" }
            require(meetings.all { it.source == ItemSource.SCHOOL }) { "non-school meeting" }

            val coursesById = courses.associateBy { it.id }
            val fingerprintedCourses = courses.map { course ->
                FingerprintedCourse(
                    sourceCourseKey = course.sourceCourseKey,
                    courseCode = course.courseCode,
                    name = course.name,
                    credits = course.credits,
                    courseType = course.courseType,
                )
            }
            val fingerprintedMeetings = meetings.map { meeting ->
                val course = requireNotNull(coursesById[meeting.courseId]) { "orphan meeting" }
                FingerprintedMeeting(
                    sourceCourseKey = course.sourceCourseKey,
                    weekday = meeting.weekday,
                    startPeriod = meeting.startPeriod,
                    endPeriod = meeting.endPeriod,
                    weekPatternMask = meeting.weekPattern.mask,
                    location = meeting.location,
                    teacherNames = meeting.teacherNames,
                )
            }

            return FingerprintedSchoolContent(
                semesterMeta = FingerprintedSemesterMeta(
                    displayName = semester.displayName,
                    academicYear = semester.academicYear,
                    term = semester.term.name,
                    week1StartEpochDay = semester.week1Start.toEpochDay(),
                    totalWeeks = semester.totalWeeks,
                    startDateEpochDay = semester.startDate.toEpochDay(),
                    endDateEpochDay = semester.endDate.toEpochDay(),
                ),
                courses = fingerprintedCourses,
                meetings = fingerprintedMeetings,
            )
        }
    }
}
