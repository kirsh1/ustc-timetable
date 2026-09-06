package com.ustc.timetable.timetable.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object TimetableMigrations {
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE course_meetings ADD COLUMN exactStartMinutes INTEGER DEFAULT NULL")
            db.execSQL("ALTER TABLE course_meetings ADD COLUMN exactEndMinutes INTEGER DEFAULT NULL")
        }
    }
}
