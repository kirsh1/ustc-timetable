package com.ustc.timetable.timetable.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.data.db.entity.ManualItemEntity
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.data.db.entity.SemesterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SemesterDao {
    @Insert suspend fun insert(s: SemesterEntity)

    @Query("SELECT * FROM semesters") fun observeAll(): Flow<List<SemesterEntity>>

    @Query("SELECT * FROM semesters WHERE id = :id") suspend fun byId(id: String): SemesterEntity?

    @Query("SELECT * FROM semesters ORDER BY startDateEpochDay DESC") suspend fun allByStartDateDesc(): List<SemesterEntity>

    @Query("SELECT * FROM semesters WHERE isCurrentAcademicSemester = 1 AND portalLinked = 1 LIMIT 1")
    suspend fun academicCurrentPortalLinked(): SemesterEntity?

    @Query("UPDATE semesters SET isCurrentAcademicSemester = 0")
    suspend fun clearAcademicCurrentFlags()

    @Query("UPDATE semesters SET isCurrentAcademicSemester = 1 WHERE id = :id")
    suspend fun markAcademicCurrent(id: String)

    /**
     * exclusive 切换（SPEC §3.2/§8.2）：先验证目标存在，再清全部标记、置目标 true。
     * 目标不存在直接抛出，数据库完全不变——不会把现有 academic-current 清成 false。
     */
    @Transaction
    suspend fun setExclusiveAcademicCurrent(id: String) {
        if (byId(id) == null) throw IllegalArgumentException("semester not found: $id")
        clearAcademicCurrentFlags()
        markAcademicCurrent(id)
    }

    @Query("UPDATE semesters SET sourceFingerprint = :fingerprint, lastSyncedAtEpochMilli = :syncedAtEpochMilli WHERE id = :id")
    suspend fun updateSyncMeta(id: String, fingerprint: String, syncedAtEpochMilli: Long)
}

@Dao
interface CourseDao {
    @Insert suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert suspend fun insertMeetings(meetings: List<CourseMeetingEntity>)

    /** 只删 source=SCHOOL；meetings 经 FK CASCADE 一并删除。manual_items 永不触碰。 */
    @Query("DELETE FROM courses WHERE semesterId = :semesterId AND source = 'SCHOOL'")
    suspend fun deleteSchoolCourses(semesterId: String)

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun coursesForSemester(semesterId: String): List<CourseEntity>

    @Query("SELECT * FROM course_meetings WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)")
    suspend fun meetingsForSemester(semesterId: String): List<CourseMeetingEntity>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    fun observeCourses(semesterId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course_meetings WHERE courseId IN (SELECT id FROM courses WHERE semesterId = :semesterId)")
    fun observeMeetings(semesterId: String): Flow<List<CourseMeetingEntity>>
}

@Dao
interface ManualItemDao {
    @Insert suspend fun insert(item: ManualItemEntity)

    @Query(
        "UPDATE manual_items SET title = :title, location = :location, note = :note, weekday = :weekday, " +
            "startMinutes = :startMinutes, endMinutes = :endMinutes, weekPatternMask = :mask, " +
            "updatedAtEpochMilli = :updatedAt WHERE id = :id",
    )
    suspend fun update(
        id: String, title: String, location: String?, note: String?, weekday: Int,
        startMinutes: Int, endMinutes: Int, mask: Long, updatedAt: Long,
    )

    @Query("DELETE FROM manual_items WHERE id = :id") suspend fun delete(id: String)

    @Query("SELECT * FROM manual_items WHERE semesterId = :semesterId")
    suspend fun itemsForSemester(semesterId: String): List<ManualItemEntity>

    @Query("SELECT * FROM manual_items WHERE semesterId = :semesterId")
    fun observe(semesterId: String): Flow<List<ManualItemEntity>>
}

@Dao
interface ScheduleProfileDao {
    @Insert suspend fun insert(profile: ScheduleProfileEntity)

    @Query("SELECT * FROM schedule_profiles WHERE id = :id") suspend fun byId(id: String): ScheduleProfileEntity?

    @Query("SELECT * FROM schedule_profiles WHERE id = :id")
    fun observe(id: String): Flow<ScheduleProfileEntity?>
}
