package com.ustc.timetable.timetable.data.db

import androidx.room.withTransaction
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.SemesterId
import java.time.Instant

/**
 * SPEC §8.2 唯一学校快照写路径：单个 database-level Room 事务。
 * 顺序：validate target/input → delete 该学期 source=SCHOOL courses（meetings 经 CASCADE）→
 * insert courses → insert meetings → update semester.sourceFingerprint/lastSyncedAt。
 * 校验先于 destructive delete；任一步失败整个事务回滚，本地旧课表保持完整原样。
 * MANUAL rows 永不触碰。fetch/parse/normalize/fingerprint 留在事务之外。
 */
suspend fun TimetableDatabase.applySchoolSnapshot(
    semesterId: SemesterId,
    courses: List<Course>,
    meetings: List<CourseMeeting>,
    fingerprint: String,
    syncedAt: Instant,
) = withTransaction {
    require(semesterDao().byId(semesterId.value) != null) { "target semester missing: $semesterId" }
    courses.forEach { c ->
        require(c.semesterId == semesterId) {
            "course ${c.id.value} belongs to ${c.semesterId}, not $semesterId"
        }
        require(c.source == ItemSource.SCHOOL) { "course ${c.id.value} source must be SCHOOL" }
    }
    val courseIds = courses.map { it.id.value }.toSet()
    meetings.forEach { m ->
        require(m.courseId.value in courseIds) {
            "meeting ${m.id.value} references course ${m.courseId.value} outside supplied course set"
        }
        require(m.source == ItemSource.SCHOOL) { "meeting ${m.id.value} source must be SCHOOL" }
    }
    courseDao().deleteSchoolCourses(semesterId.value)
    courseDao().insertCourses(courses.map { Mappers.toEntity(it, semesterId.value) })
    courseDao().insertMeetings(meetings.map { Mappers.toEntity(it) })
    semesterDao().updateSyncMeta(semesterId.value, fingerprint, syncedAt.toEpochMilli())
}
