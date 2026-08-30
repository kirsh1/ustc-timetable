package com.ustc.timetable.timetable.data.db

import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.data.db.entity.ManualItemEntity
import com.ustc.timetable.timetable.data.db.entity.SemesterEntity
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.Term
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** domain ↔ entity 纯映射。未知 term/source 显式失败，绝不静默填默认值。 */
object Mappers {

    // ---- Semester ----

    fun toEntity(s: Semester): SemesterEntity = SemesterEntity(
        id = s.id.value,
        displayName = s.displayName,
        academicYear = s.academicYear,
        term = s.term.name,
        week1StartEpochDay = s.week1Start.toEpochDay(),
        totalWeeks = s.totalWeeks,
        startDateEpochDay = s.startDate.toEpochDay(),
        endDateEpochDay = s.endDate.toEpochDay(),
        importedAtEpochMilli = s.importedAt.toEpochMilli(),
        lastSyncedAtEpochMilli = s.lastSyncedAt?.toEpochMilli(),
        isCurrentAcademicSemester = s.isCurrentAcademicSemester,
        portalLinked = s.portalLinked,
        profileId = s.profileId.value,
        sourceFingerprint = s.sourceFingerprint,
    )

    fun toDomain(e: SemesterEntity): Semester = Semester(
        id = SemesterId(e.id),
        displayName = e.displayName,
        academicYear = e.academicYear,
        term = Term.valueOf(e.term),
        week1Start = LocalDate.ofEpochDay(e.week1StartEpochDay),
        totalWeeks = e.totalWeeks,
        startDate = LocalDate.ofEpochDay(e.startDateEpochDay),
        endDate = LocalDate.ofEpochDay(e.endDateEpochDay),
        importedAt = Instant.ofEpochMilli(e.importedAtEpochMilli),
        lastSyncedAt = e.lastSyncedAtEpochMilli?.let(Instant::ofEpochMilli),
        isCurrentAcademicSemester = e.isCurrentAcademicSemester,
        portalLinked = e.portalLinked,
        profileId = ProfileId(e.profileId),
        sourceFingerprint = e.sourceFingerprint,
    )

    // ---- Course ----

    fun toEntity(c: Course, semesterId: String): CourseEntity = CourseEntity(
        id = c.id.value,
        semesterId = semesterId,
        sourceCourseKey = c.sourceCourseKey,
        courseCode = c.courseCode,
        name = c.name,
        credits = c.credits,
        courseType = c.courseType,
        source = c.source.name,
    )

    fun toDomain(e: CourseEntity, semesterId: SemesterId): Course = Course(
        id = CourseId(e.id),
        semesterId = semesterId,
        sourceCourseKey = e.sourceCourseKey,
        courseCode = e.courseCode,
        name = e.name,
        credits = e.credits,
        courseType = e.courseType,
        source = ItemSource.valueOf(e.source),
    )

    // ---- CourseMeeting ----

    fun toEntity(m: CourseMeeting): CourseMeetingEntity = CourseMeetingEntity(
        id = m.id.value,
        courseId = m.courseId.value,
        weekday = m.weekday,
        startPeriod = m.startPeriod,
        endPeriod = m.endPeriod,
        weekPatternMask = m.weekPattern.mask,
        location = m.location,
        teacherNamesJoined = joinTeachers(m.teacherNames),
        source = m.source.name,
    )

    fun toDomain(e: CourseMeetingEntity): CourseMeeting = CourseMeeting(
        id = MeetingId(e.id),
        courseId = CourseId(e.courseId),
        weekday = e.weekday,
        startPeriod = e.startPeriod,
        endPeriod = e.endPeriod,
        weekPattern = WeekPattern(e.weekPatternMask),
        location = e.location,
        teacherNames = splitTeachers(e.teacherNamesJoined),
        source = ItemSource.valueOf(e.source),
    )

    // ---- ManualScheduleItem ----

    fun toEntity(m: ManualScheduleItem): ManualItemEntity = ManualItemEntity(
        id = m.id.value,
        semesterId = m.semesterId.value,
        title = m.title,
        weekday = m.weekday,
        startMinutes = m.startTime.hour * 60 + m.startTime.minute,
        endMinutes = m.endTime.hour * 60 + m.endTime.minute,
        weekPatternMask = m.weekPattern.mask,
        location = m.location,
        note = m.note,
        createdAtEpochMilli = m.createdAt.toEpochMilli(),
        updatedAtEpochMilli = m.updatedAt.toEpochMilli(),
    )

    fun toDomain(e: ManualItemEntity): ManualScheduleItem = ManualScheduleItem(
        id = ManualItemId(e.id),
        semesterId = SemesterId(e.semesterId),
        title = e.title,
        weekday = e.weekday,
        startTime = LocalTime.of(e.startMinutes / 60, e.startMinutes % 60),
        endTime = LocalTime.of(e.endMinutes / 60, e.endMinutes % 60),
        weekPattern = WeekPattern(e.weekPatternMask),
        location = e.location,
        note = e.note,
        createdAt = Instant.ofEpochMilli(e.createdAtEpochMilli),
        updatedAt = Instant.ofEpochMilli(e.updatedAtEpochMilli),
    )
}
