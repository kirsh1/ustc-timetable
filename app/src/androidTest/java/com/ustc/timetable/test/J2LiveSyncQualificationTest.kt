package com.ustc.timetable.test

import android.app.NotificationManager
import android.app.Notification
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.ustc.timetable.MainActivity
import com.ustc.timetable.TimetableApp
import com.ustc.timetable.sync.SyncResult
import com.ustc.timetable.notification.SyncNotification
import com.ustc.timetable.timetable.domain.ScheduleChange
import com.ustc.timetable.timetable.domain.WeekPattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import java.util.concurrent.atomic.AtomicInteger

/** Live network qualification is opt-in; normal suite runs never contact the school. */
@RunWith(AndroidJUnit4::class)
class J2LiveSyncQualificationTest {
    @Test fun explicit_history_launch_is_offline_and_background_runner_ignores_viewed() = runBlocking {
        if (InstrumentationRegistry.getArguments().getString("j2History") != "true") return@runBlocking
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun checkpoint(stage: String) = instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "\nJ2_HISTORY_STAGE=$stage\n")
        })
        val container = (instrumentation.targetContext.applicationContext as TimetableApp).container
        val semesters = container.semesters.observeSemesters().first()
        val academic = semesters.single { it.isCurrentAcademicSemester && it.portalLinked }
        val viewed = container.settings.viewedSemesterId.first()
        assertTrue("Select the existing non-current semester in UI first", viewed != academic.id.value)
        val history = semesters.single { it.id.value == viewed }
        suspend fun snapshot() = semesters.associate { semester -> semester.id to listOf(
            container.db.courseDao().coursesForSemester(semester.id.value).sortedBy { it.id }.toString(),
            container.db.courseDao().meetingsForSemester(semester.id.value).sortedBy { it.id }.toString(),
            container.db.manualItemDao().itemsForSemester(semester.id.value).sortedBy { it.id }.toString(),
        ) }
        val before = snapshot()
        checkpoint("snapshot")
        // Test-only observation of the existing shared HTTP client, without logging requests/headers.
        val source = container.ustcPortalRuntime.portalSource
        val fetcherField = source.javaClass.getDeclaredField("fetcher").apply { isAccessible = true }
        val fetcher = fetcherField.get(source)
        val clientField = fetcher.javaClass.getDeclaredField("client").apply { isAccessible = true }
        val original = clientField.get(fetcher) as OkHttpClient
        val calls = AtomicInteger()
        val observed = original.newBuilder().eventListener(object : EventListener() {
            override fun callStart(call: Call) { calls.incrementAndGet() }
        }).build()
        clientField.set(fetcher, observed)
        checkpoint("observer-installed")
        try {
            val launch = instrumentation.uiAutomation.executeShellCommand(
                "am start -W -n com.ustc.timetable/.MainActivity",
            )
            ParcelFileDescriptor.AutoCloseInputStream(launch).bufferedReader().use { reader ->
                assertTrue("Shell launch must succeed", reader.readText().contains("Status: ok"))
            }
            run {
                checkpoint("activity-launched")
                instrumentation.waitForIdleSync()
                var mainResumed = false
                instrumentation.runOnMainSync {
                    mainResumed = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).any { it is MainActivity }
                }
                assertTrue("Production MainActivity must actually be resumed", mainResumed)
                android.os.SystemClock.sleep(2000)
                instrumentation.waitForIdleSync()
                assertEquals("History cold launch must not touch the portal", 0, calls.get())
                assertEquals(viewed, container.settings.viewedSemesterId.first())
                assertEquals(before, snapshot())
                val result = withContext(Dispatchers.IO) { container.ustcPortalRuntime.syncRecorder.run() }
                assertEquals(SyncResult.NoChange, result)
                assertTrue("Explicit real background runner must actually call HTTP", calls.get() > 0)
                assertEquals(academic.id, container.semesters.academicCurrent()?.id)
                assertEquals(viewed, container.settings.viewedSemesterId.first())
                assertEquals(before[history.id], snapshot()[history.id])
                assertEquals(before, snapshot())
                instrumentation.sendStatus(0, Bundle().apply {
                    putString("stream", "\nJ2_HISTORY_LAUNCH_CALLS=0; BACKGROUND_RESULT=NoChange; HTTP_CALLS=${calls.get()}; HISTORY_UNCHANGED=true\n")
                })
            }
        } finally {
            clientField.set(fetcher, original)
        }
    }

    @Test fun explicit_test_only_notification_injection() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val action = InstrumentationRegistry.getArguments().getString("j2Notification") ?: return
        require(action in setOf("post", "remove"))
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = "J2 TEST-ONLY CHANGE INJECTION"
        if (action == "remove") {
            manager.activeNotifications.filter {
                it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == title
            }.forEach { manager.cancel(it.tag, it.id) }
            return
        }
        assertTrue("Permission is required for visible notification qualification", manager.areNotificationsEnabled())
        SyncNotification.postChanges(context, listOf(ScheduleChange.LocationChanged(
            courseName = title, weeks = WeekPattern.of(10), old = "TH-B301", new = "TH-C204",
        )))
        val deadline = android.os.SystemClock.elapsedRealtime() + 5000
        var posted = manager.activeNotifications.firstOrNull {
            it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == title
        }
        while (posted == null && android.os.SystemClock.elapsedRealtime() < deadline) {
            android.os.SystemClock.sleep(50)
            posted = manager.activeNotifications.firstOrNull {
                it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == title
            }
        }
        assertEquals("$title\n第10周教室：TH-B301 → TH-C204",
            posted?.notification?.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
    }

    @Test fun explicit_live_no_change_preserves_manual_data_and_notifications() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (InstrumentationRegistry.getArguments().getString("j2LiveSync") != "true") {
            instrumentation.sendStatus(0, Bundle().apply {
                putString("stream", "\nJ2 live qualification not requested; no network performed.\n")
            })
            return@runBlocking
        }
        val context = instrumentation.targetContext
        val container = (context.applicationContext as TimetableApp).container
        val semesters = container.semesters.observeSemesters().first()
        assertTrue("A real current portal semester is required", semesters.any {
            it.isCurrentAcademicSemester && it.portalLinked
        })
        val before = semesters.associate { semester ->
            semester.id to container.db.manualItemDao().itemsForSemester(semester.id.value).sortedBy { it.id }
        }
        val academic = semesters.single { it.isCurrentAcademicSemester && it.portalLinked }
        assertTrue("Manual-survival qualification requires a manual item in the actual sync target",
            before.getValue(academic.id).isNotEmpty())
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationsBefore = manager.activeNotifications.map { it.key to it.postTime }.sortedBy { it.first }
        val result = withContext(Dispatchers.IO) { container.ustcPortalRuntime.syncRecorder.sync() }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("stream", "\nJ2_REAL_SYNC_RESULT=${result.javaClass.simpleName}\n")
        })
        assertEquals("Real school response must confirm NoChange", SyncResult.NoChange, result)
        val after = semesters.associate { semester ->
            semester.id to container.db.manualItemDao().itemsForSemester(semester.id.value).sortedBy { it.id }
        }
        assertEquals("Sync must preserve every manual entity and local ID", before, after)
        assertEquals("NoChange must not post or replace a notification", notificationsBefore,
            manager.activeNotifications.map { it.key to it.postTime }.sortedBy { it.first })
    }
}
