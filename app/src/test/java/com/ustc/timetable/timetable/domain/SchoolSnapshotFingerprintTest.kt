package com.ustc.timetable.timetable.domain

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolSnapshotFingerprintTest {
    @Test fun factory_rejects_duplicate_course_id() {
        assertThrows(IllegalArgumentException::class.java) {
            content(courses = listOf(course(id = "same", key = "C1"), course(id = "same", key = "C2")), meetings = emptyList())
        }
    }

    @Test fun factory_rejects_duplicate_source_course_key() {
        assertThrows(IllegalArgumentException::class.java) {
            content(courses = listOf(course(id = "1", key = "C1"), course(id = "2", key = "C1")), meetings = emptyList())
        }
    }

    @Test fun factory_rejects_orphan_meeting() {
        assertThrows(IllegalArgumentException::class.java) {
            content(meetings = listOf(meeting(courseId = "missing")))
        }
    }

    @Test fun factory_excludes_course_and_meeting_ids() {
        val value = content()

        assertEquals("C1", value.courses.single().sourceCourseKey)
        assertEquals("C1", value.meetings.single().sourceCourseKey)
        assertTrue(value.toString().contains("course-1").not())
        assertTrue(value.toString().contains("meeting-1").not())
    }

    @Test fun remapped_local_ids_produce_same_fingerprinted_content() {
        val first = content()
        val second = content(
            courses = listOf(course(id = "different-course")),
            meetings = listOf(meeting(id = "different-meeting", courseId = "different-course")),
        )

        assertEquals(first, second)
    }

    @Test fun fingerprint_is_order_independent() {
        val original = content(
            courses = listOf(course("course-2", "C2"), course("course-1", "C1")),
            meetings = listOf(meeting("m2", "course-2", weekday = 2), meeting("m1", "course-1", weekday = 1)),
        )
        val permuted = original.copy(courses = original.courses.reversed(), meetings = original.meetings.reversed())

        assertEquals(SchoolSnapshotFingerprint.compute(original), SchoolSnapshotFingerprint.compute(permuted))
    }

    @Test fun fingerprint_is_64_lowercase_hex() {
        assertTrue(Regex("^[0-9a-f]{64}$").matches(SchoolSnapshotFingerprint.compute(content())))
    }

    @Test fun fingerprint_excludes_course_id() = assertSameFingerprint(
        content(),
        content(courses = listOf(course(id = "other")), meetings = listOf(meeting(courseId = "other"))),
    )

    @Test fun fingerprint_excludes_meeting_id() = assertSameFingerprint(
        content(),
        content(meetings = listOf(meeting(id = "other"))),
    )

    @Test fun fingerprint_excludes_semester_id() = assertSameFingerprint(
        content(), content(semester = semester(id = "other")),
    )

    @Test fun fingerprint_excludes_importedAt() = assertSameFingerprint(
        content(), content(semester = semester(importedAt = Instant.parse("2030-01-01T00:00:00Z"))),
    )

    @Test fun fingerprint_excludes_lastSyncedAt() = assertSameFingerprint(
        content(), content(semester = semester(lastSyncedAt = Instant.parse("2030-01-01T00:00:00Z"))),
    )

    @Test fun fingerprint_excludes_sourceFingerprint() = assertSameFingerprint(
        content(), content(semester = semester(sourceFingerprint = "other")),
    )

    @Test fun fingerprint_excludes_academic_current() = assertSameFingerprint(
        content(), content(semester = semester(isCurrent = false)),
    )

    @Test fun fingerprint_excludes_portalLinked() = assertSameFingerprint(
        content(), content(semester = semester(portalLinked = false)),
    )

    @Test fun fingerprint_excludes_profileId() = assertSameFingerprint(
        content(), content(semester = semester(profileId = "other")),
    )

    @Test fun fingerprint_sensitive_to_semester_displayName() = assertDifferentFingerprint(
        content(), content(semester = semester(displayName = "other")),
    )

    @Test fun fingerprint_sensitive_to_academicYear() = assertDifferentFingerprint(
        content(), content(semester = semester(academicYear = "2030-2031")),
    )

    @Test fun fingerprint_sensitive_to_term() = assertDifferentFingerprint(
        content(), content(semester = semester(term = Term.SPRING)),
    )

    @Test fun fingerprint_sensitive_to_week1Start() = assertDifferentFingerprint(
        content(), content(semester = semester(week1Start = LocalDate.of(2026, 9, 7))),
    )

    @Test fun fingerprint_sensitive_to_totalWeeks() = assertDifferentFingerprint(
        content(), content(semester = semester(totalWeeks = 19)),
    )

    @Test fun fingerprint_sensitive_to_startDate() = assertDifferentFingerprint(
        content(), content(semester = semester(startDate = LocalDate.of(2026, 8, 31))),
    )

    @Test fun fingerprint_sensitive_to_endDate() = assertDifferentFingerprint(
        content(), content(semester = semester(endDate = LocalDate.of(2027, 1, 14))),
    )

    @Test fun fingerprint_sensitive_to_sourceCourseKey() = assertDifferentFingerprint(
        content(), content(courses = listOf(course(key = "C2")), meetings = listOf(meeting()).map { it.copy(courseId = CourseId("course-1")) }),
    )

    @Test fun fingerprint_sensitive_to_courseCode() = assertDifferentFingerprint(
        content(), content(courses = listOf(course(code = "OTHER"))),
    )

    @Test fun fingerprint_sensitive_to_courseName() = assertDifferentFingerprint(
        content(), content(courses = listOf(course(name = "另一课程"))),
    )

    @Test fun fingerprint_sensitive_to_credits() = assertDifferentFingerprint(
        content(), content(courses = listOf(course(credits = 4.0))),
    )

    @Test fun fingerprint_sensitive_to_courseType() = assertDifferentFingerprint(
        content(), content(courses = listOf(course(courseType = "选修"))),
    )

    @Test fun fingerprint_sensitive_to_weekday() = assertDifferentFingerprint(
        content(), content(meetings = listOf(meeting(weekday = 2))),
    )

    @Test fun fingerprint_sensitive_to_periods() = assertDifferentFingerprint(
        content(), content(meetings = listOf(meeting(start = 4, end = 6))),
    )

    @Test fun fingerprint_sensitive_to_weekPattern() = assertDifferentFingerprint(
        content(), content(meetings = listOf(meeting(weeks = WeekPattern.range(7, 12)))),
    )

    @Test fun fingerprint_sensitive_to_location() = assertDifferentFingerprint(
        content(), content(meetings = listOf(meeting(location = "TH-C204"))),
    )

    @Test fun fingerprint_sensitive_to_teachers() = assertDifferentFingerprint(
        content(), content(meetings = listOf(meeting(teachers = listOf("教师B")))),
    )

    private fun assertSameFingerprint(first: FingerprintedSchoolContent, second: FingerprintedSchoolContent) {
        assertEquals(SchoolSnapshotFingerprint.compute(first), SchoolSnapshotFingerprint.compute(second))
    }

    private fun assertDifferentFingerprint(first: FingerprintedSchoolContent, second: FingerprintedSchoolContent) {
        assertNotEquals(SchoolSnapshotFingerprint.compute(first), SchoolSnapshotFingerprint.compute(second))
    }

    private fun content(
        semester: Semester = semester(),
        courses: List<Course> = listOf(course()),
        meetings: List<CourseMeeting> = listOf(meeting()),
    ) = FingerprintedSchoolContent.of(semester, courses, meetings)

    private fun semester(
        id: String = "semester-1",
        displayName: String = "2026-2027 秋季",
        academicYear: String = "2026-2027",
        term: Term = Term.AUTUMN,
        week1Start: LocalDate = LocalDate.of(2026, 8, 31),
        totalWeeks: Int = 20,
        startDate: LocalDate = LocalDate.of(2026, 8, 30),
        endDate: LocalDate = LocalDate.of(2027, 1, 15),
        importedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
        lastSyncedAt: Instant? = null,
        isCurrent: Boolean = true,
        portalLinked: Boolean = true,
        profileId: String = "profile-1",
        sourceFingerprint: String? = "old",
    ) = Semester(
        SemesterId(id), displayName, academicYear, term, week1Start, totalWeeks, startDate, endDate,
        importedAt, lastSyncedAt, isCurrent, portalLinked, ProfileId(profileId), sourceFingerprint,
    )

    private fun course(
        id: String = "course-1",
        key: String = "C1",
        code: String = "C1",
        name: String = "课程A",
        credits: Double? = 3.0,
        courseType: String? = "专业课",
    ) = Course(CourseId(id), SemesterId("semester-1"), key, code, name, credits, courseType)

    private fun meeting(
        id: String = "meeting-1",
        courseId: String = "course-1",
        weekday: Int = 1,
        start: Int = 3,
        end: Int = 5,
        weeks: WeekPattern = WeekPattern.range(2, 6),
        location: String = "TH-B301",
        teachers: List<String> = listOf("教师A"),
    ) = CourseMeeting(MeetingId(id), CourseId(courseId), weekday, start, end, weeks, location, teachers)
}
