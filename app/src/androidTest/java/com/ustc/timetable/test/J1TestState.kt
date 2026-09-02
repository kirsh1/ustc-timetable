package com.ustc.timetable.test

import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.DebugSeed
import com.ustc.timetable.TimetableApp
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

object J1TestState {
    val app: TimetableApp
        get() = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as TimetableApp

    fun reset() = runBlocking {
        val container = app.container
        container.db.clearAllTables()
        container.profiles.ensureBundledSeeded()
        container.profiles.restoreWorkingToBundled()
        container.settings.setNeedReauth(false)
        container.settings.setShowNonCurrentWeek(false)
        container.settings.setWeeklySyncEnabled(true)
        container.settings.setViewedSemesterId("j1-no-viewed-semester")
    }

    fun seedDebug() = runBlocking {
        val container = app.container
        DebugSeed.seedIfEmpty(
            semesters = container.semesters,
            manual = container.manual,
            db = container.db,
            bundledProfile = container.bundledOfficial,
            now = Instant.parse("2026-09-02T00:00:00Z"),
        )
        val semester = requireNotNull(container.semesters.latestSemester())
        container.settings.setViewedSemesterId(semester.id.value)
    }

    fun seedTwoSemesters(): Pair<Semester, Semester> = runBlocking {
        val container = app.container
        val a = container.semesters.createLocalSemester(
            SemesterDefaults.AUTUMN_2026("j1-semester-a", "placeholder", Instant.parse("2026-09-01T00:00:00Z"))
                .copy(displayName = "J1 学期 A"),
            container.bundledOfficial,
        )
        val b = container.semesters.createLocalSemester(
            SemesterDefaults.AUTUMN_2026("j1-semester-b", "placeholder", Instant.parse("2025-09-01T00:00:00Z"))
                .copy(
                    displayName = "J1 学期 B",
                    academicYear = "2025-2026",
                    week1Start = java.time.LocalDate.of(2025, 9, 1),
                    startDate = java.time.LocalDate.of(2025, 8, 31),
                    endDate = java.time.LocalDate.of(2026, 1, 16),
                ),
            container.bundledOfficial,
        )
        container.db.semesterDao().setExclusiveAcademicCurrent(a.id.value)
        container.settings.setViewedSemesterId(a.id.value)
        a to b
    }

    fun semesters() = runBlocking { app.container.db.semesterDao().allByStartDateDesc() }
    fun viewedSemesterId() = runBlocking { app.container.settings.viewedSemesterId.first() }
}
