package com.ustc.timetable.timetable.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableMigrationTest {
    @Test fun migration_1_2_preserves_existing_meeting_and_adds_null_exact_columns() {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration-${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """CREATE TABLE course_meetings (
                                id TEXT NOT NULL PRIMARY KEY,
                                courseId TEXT NOT NULL,
                                weekday INTEGER NOT NULL,
                                startPeriod INTEGER NOT NULL,
                                endPeriod INTEGER NOT NULL,
                                weekPatternMask INTEGER NOT NULL,
                                location TEXT NOT NULL,
                                teacherNamesJoined TEXT NOT NULL,
                                source TEXT NOT NULL
                            )""".trimIndent(),
                        )
                        db.execSQL(
                            "INSERT INTO course_meetings VALUES (?,?,?,?,?,?,?,?,?)",
                            arrayOf<Any?>("m1", "c1", 2, 8, 9, 7L, "TH-A301", "教师甲", "SCHOOL"),
                        )
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        helper.writableDatabase.close()
        helper.close()

        val upgraded = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        TimetableMigrations.MIGRATION_1_2.migrate(db)
                    }
                })
                .build(),
        )
        upgraded.writableDatabase.query(
            "SELECT id, location, exactStartMinutes, exactEndMinutes FROM course_meetings",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("m1", cursor.getString(0))
            assertEquals("TH-A301", cursor.getString(1))
            assertNull(if (cursor.isNull(2)) null else cursor.getInt(2))
            assertNull(if (cursor.isNull(3)) null else cursor.getInt(3))
        }
        upgraded.close()
        context.deleteDatabase(name)
    }
}
