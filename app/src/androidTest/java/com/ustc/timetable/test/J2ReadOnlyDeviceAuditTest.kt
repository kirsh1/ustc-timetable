package com.ustc.timetable.test

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.TimetableApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Explicitly selected audit only: no reset, seed, network call, session export or writes. */
@RunWith(AndroidJUnit4::class)
class J2ReadOnlyDeviceAuditTest {
    @Test fun report_non_secret_persistence_state() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val container = (instrumentation.targetContext.applicationContext as TimetableApp).container
        fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val semesters = container.semesters.observeSemesters().first().sortedBy { it.id.value }
        val lines = mutableListOf("semesters=${semesters.size}")
        lines += "sessionPresent=${container.ustcPortalRuntime.sessionManager.hasSession()}"
        lines += "weeklySync=${container.settings.weeklySyncEnabled.first()}"
        lines += "notificationRequestShown=${container.settings.notificationRequestShown.first()}"
        lines += "lastSync=${container.settings.lastSyncFinishedAt.first()}"
        val viewed = container.settings.viewedSemesterId.first()
        for ((index, semester) in semesters.withIndex()) {
            val courses = container.db.courseDao().coursesForSemester(semester.id.value).sortedBy { it.id }
            val meetings = container.db.courseDao().meetingsForSemester(semester.id.value).sortedBy { it.id }
            val manuals = container.db.manualItemDao().itemsForSemester(semester.id.value).sortedBy { it.id }
            lines += "semester[$index]:idHash=${digest(semester.id.value)},current=${semester.isCurrentAcademicSemester},viewed=${semester.id.value==viewed},portal=${semester.portalLinked},week1=${semester.week1Start},weeks=${semester.totalWeeks},profile=${semester.profileId.value}"
            lines += "school[$index]:courses=${courses.size},meetings=${meetings.size},digest=${digest((courses+meetings).joinToString())}"
            lines += "manual[$index]:count=${manuals.size},digest=${digest(manuals.joinToString())}"
            for ((manualIndex, item) in manuals.withIndex()) {
                lines += "manual[$index/$manualIndex]:idHash=${digest(item.id)},day=${item.weekday},start=${item.startMinutes},end=${item.endMinutes},weekMask=${item.weekPatternMask}"
            }
        }
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\nJ2_SAFE_AUDIT\n${lines.joinToString("\n")}\n") })
    }
}
