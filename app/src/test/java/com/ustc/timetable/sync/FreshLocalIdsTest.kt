package com.ustc.timetable.sync

import com.ustc.timetable.school.ustc.parser.NormalizedSchoolSnapshot
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshLocalIdsTest {
    @Test fun assign_generates_unique_local_ids() {
        val result = FreshLocalIds.assign(snapshot())

        assertEquals(2, result.courses.map { it.id }.toSet().size)
        assertEquals(2, result.meetings.map { it.id }.toSet().size)
        assertTrue(result.courses.zip(snapshot().courses).all { (new, old) -> new.id != old.id })
        assertTrue(result.meetings.zip(snapshot().meetings).all { (new, old) -> new.id != old.id })
    }

    @Test fun assign_preserves_course_meeting_referential_integrity() {
        val original = snapshot()
        val result = FreshLocalIds.assign(original)
        val remap = original.courses.zip(result.courses).associate { (old, new) -> old.id to new.id }

        assertEquals(
            original.meetings.map { remap.getValue(it.courseId) },
            result.meetings.map { it.courseId },
        )
        assertTrue(result.meetings.all { meeting -> result.courses.any { it.id == meeting.courseId } })
    }

    @Test fun assign_preserves_business_fields() {
        val original = snapshot()
        val result = FreshLocalIds.assign(original)

        assertEquals(original.issues, result.issues)
        original.courses.zip(result.courses).forEach { (old, new) ->
            assertEquals(old.copy(id = new.id), new)
        }
        original.meetings.zip(result.meetings).forEach { (old, new) ->
            assertEquals(old.copy(id = new.id, courseId = new.courseId), new)
        }
    }

    @Test fun unresolved_meeting_course_is_rejected() {
        val invalid = snapshot().copy(
            meetings = listOf(snapshot().meetings.first().copy(courseId = CourseId("ghost"))),
        )

        assertThrows(IllegalArgumentException::class.java) { FreshLocalIds.assign(invalid) }
    }

    @Test fun duplicate_input_course_id_is_rejected() {
        val original = snapshot()
        val invalid = original.copy(courses = original.courses.map { it.copy(id = CourseId("duplicate")) })

        assertThrows(IllegalArgumentException::class.java) { FreshLocalIds.assign(invalid) }
    }

    private fun snapshot(): NormalizedSchoolSnapshot {
        val semesterId = SemesterId("semester")
        val courses = listOf(
            Course(CourseId("temp-a"), semesterId, "A", "A", "课程A", 2.0, "专业"),
            Course(CourseId("temp-b"), semesterId, "B", "B", "课程B", 3.0, null),
        )
        val meetings = listOf(
            CourseMeeting(MeetingId("temp-ma"), courses[0].id, 1, 1, 2, WeekPattern.range(1, 4), "A101", listOf("甲")),
            CourseMeeting(MeetingId("temp-mb"), courses[1].id, 2, 3, 4, WeekPattern.range(5, 8), "B202", listOf("乙")),
        )
        return NormalizedSchoolSnapshot(courses, meetings, emptyList())
    }
}
