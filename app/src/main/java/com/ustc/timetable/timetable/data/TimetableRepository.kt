package com.ustc.timetable.timetable.data

import androidx.room.withTransaction
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.SemesterId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 学校课表观察（SPEC §7.3 数据边界）。
 * 每次 emission 在同一数据库事务内读取 courses + meetings，保证向 observer 暴露的是
 * 一致的快照（绝不出现 new course + old meeting 或空瞬态）。
 */
class TimetableRepository(private val db: TimetableDatabase) {

    fun observeSchool(semesterId: SemesterId): Flow<Pair<List<Course>, List<CourseMeeting>>> =
        db.invalidationTracker
            .createFlow("courses", "course_meetings")
            .map { loadSnapshot(semesterId) }

    private suspend fun loadSnapshot(semesterId: SemesterId): Pair<List<Course>, List<CourseMeeting>> =
        db.withTransaction {
            val courses = db.courseDao().coursesForSemester(semesterId.value)
                .map { Mappers.toDomain(it, semesterId) }
            val meetings = db.courseDao().meetingsForSemester(semesterId.value)
                .map(Mappers::toDomain)
            courses to meetings
        }
}
