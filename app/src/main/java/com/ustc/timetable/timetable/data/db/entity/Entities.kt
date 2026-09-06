package com.ustc.timetable.timetable.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_profiles")
data class ScheduleProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isBundledOfficial: Boolean,
    val periodsJson: String,
)

@Entity(
    tableName = "semesters",
    foreignKeys = [ForeignKey(
        entity = ScheduleProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profileId"],
    )],
)
data class SemesterEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val academicYear: String,
    val term: String,
    val week1StartEpochDay: Long,
    val totalWeeks: Int,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long,
    val importedAtEpochMilli: Long,
    val lastSyncedAtEpochMilli: Long?,
    val isCurrentAcademicSemester: Boolean,
    val portalLinked: Boolean,
    val profileId: String,
    val sourceFingerprint: String?,
)

@Entity(
    tableName = "courses",
    indices = [Index("semesterId")],
    foreignKeys = [ForeignKey(
        entity = SemesterEntity::class,
        parentColumns = ["id"],
        childColumns = ["semesterId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val sourceCourseKey: String,
    val courseCode: String,
    val name: String,
    val credits: Double?,
    val courseType: String?,
    val source: String,
)

@Entity(
    tableName = "course_meetings",
    indices = [Index("courseId")],
    foreignKeys = [ForeignKey(
        entity = CourseEntity::class,
        parentColumns = ["id"],
        childColumns = ["courseId"],
        onDelete = ForeignKey.CASCADE,
    )],
)
data class CourseMeetingEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weekPatternMask: Long,
    val location: String,
    val teacherNamesJoined: String,
    val source: String,
    val exactStartMinutes: Int? = null,
    val exactEndMinutes: Int? = null,
)

@Entity(tableName = "manual_items", indices = [Index("semesterId")])
data class ManualItemEntity(
    @PrimaryKey val id: String,
    val semesterId: String,
    val title: String,
    val weekday: Int,
    val startMinutes: Int,
    val endMinutes: Int,
    val weekPatternMask: Long,
    val location: String?,
    val note: String?,
    val createdAtEpochMilli: Long,
    val updatedAtEpochMilli: Long,
)
