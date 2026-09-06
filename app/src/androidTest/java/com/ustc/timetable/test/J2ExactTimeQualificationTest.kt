package com.ustc.timetable.test

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.TimetableApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only, sanitized device report. It never prints course, account, URL, room, or teacher content. */
@RunWith(AndroidJUnit4::class)
class J2ExactTimeQualificationTest {
    @Test fun report_sanitized_exact_time_state() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = (instrumentation.targetContext.applicationContext as TimetableApp).container
        val semesters = container.semesters.observeSemesters().first()
        val courseCount = semesters.sumOf { container.db.courseDao().coursesForSemester(it.id.value).size }
        val meetings = semesters.flatMap { container.db.courseDao().meetingsForSemester(it.id.value) }
        val exactCount = meetings.count { it.exactStartMinutes != null && it.exactEndMinutes != null }
        val report = listOf(
            "databaseVersion=${container.db.openHelper.readableDatabase.version}",
            "semesters=${semesters.size}",
            "schoolCourses=$courseCount",
            "schoolMeetings=${meetings.size}",
            "exactOverrides=$exactCount",
            "portalLinkedCurrent=${semesters.any { it.portalLinked && it.isCurrentAcademicSemester }}",
        ).joinToString("\n")
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nJ2_EXACT_TIME_SAFE\n$report\n") })
    }
}
