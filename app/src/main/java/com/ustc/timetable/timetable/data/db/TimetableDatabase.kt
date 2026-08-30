package com.ustc.timetable.timetable.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ustc.timetable.timetable.data.db.dao.CourseDao
import com.ustc.timetable.timetable.data.db.dao.ManualItemDao
import com.ustc.timetable.timetable.data.db.dao.SemesterDao
import com.ustc.timetable.timetable.data.db.dao.ScheduleProfileDao
import com.ustc.timetable.timetable.data.db.entity.CourseEntity
import com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity
import com.ustc.timetable.timetable.data.db.entity.ManualItemEntity
import com.ustc.timetable.timetable.data.db.entity.SemesterEntity
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity

@Database(
    entities = [
        SemesterEntity::class,
        ScheduleProfileEntity::class,
        CourseEntity::class,
        CourseMeetingEntity::class,
        ManualItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class TimetableDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun courseDao(): CourseDao
    abstract fun manualItemDao(): ManualItemDao
    abstract fun scheduleProfileDao(): ScheduleProfileDao

    companion object {
        const val NAME = "ustc_timetable.db"
    }
}
