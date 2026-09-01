package com.ustc.timetable.sync

import com.ustc.timetable.school.ustc.parser.NormalizedSchoolSnapshot
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.MeetingId
import java.util.UUID

/** Assigns persistence-only identities while preserving the normalized business graph. */
object FreshLocalIds {
    fun assign(snapshot: NormalizedSchoolSnapshot): NormalizedSchoolSnapshot {
        require(snapshot.courses.map { it.id }.toSet().size == snapshot.courses.size) {
            "normalized course ids must be unique"
        }
        val newCourseIdByOld = snapshot.courses.associate { course ->
            course.id to CourseId(UUID.randomUUID().toString())
        }
        snapshot.meetings.forEach { meeting ->
            require(meeting.courseId in newCourseIdByOld) {
                "meeting ${meeting.id.value} references unknown course ${meeting.courseId.value}"
            }
        }

        return snapshot.copy(
            courses = snapshot.courses.map { course -> course.copy(id = newCourseIdByOld.getValue(course.id)) },
            meetings = snapshot.meetings.map { meeting ->
                meeting.copy(
                    id = MeetingId(UUID.randomUUID().toString()),
                    courseId = newCourseIdByOld.getValue(meeting.courseId),
                )
            },
        )
    }
}
