package com.ustc.timetable.sync

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.ustc.timetable.notification.SyncNotification
import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.school.ustc.parser.CourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.SemesterMetaParser
import com.ustc.timetable.school.ustc.parser.SemesterMetaResult
import com.ustc.timetable.school.ustc.parser.TimetablePageParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.portal.SchoolPortalSource
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity
import com.ustc.timetable.timetable.domain.ScheduleChange
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SnapshotDiffer
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class WeeklySyncWorkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val shadowNotifications get() = shadowOf(notificationManager)
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val directory = Files.createTempDirectory("h3-worker-settings")
        dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { directory.resolve("settings.preferences_pb").toFile() },
        )
        settings = SettingsStore(dataStore)
    }

    @After fun tearDown() {
        storeScope.cancel()
    }

    @Test fun disabled_worker_does_not_record_time() = runBlocking {
        settings.setWeeklySyncEnabled(false)
        settings.setNeedReauth(false)
        val runner = RecordingRunner(SyncResult.Failed(SyncError.AuthenticationExpired))

        assertSuccess(worker(runner).doWork())
        assertEquals(0, runner.calls)
        assertEquals(0, shadowNotifications.size())
        assertEquals(false, settings.needReauth.first())
    }

    @Test fun no_change_is_silent() = runBlocking {
        assertSuccess(worker(RecordingRunner(SyncResult.NoChange)).doWork())

        assertEquals(0, shadowNotifications.size())
    }

    @Test fun success_empty_is_silent() = runBlocking {
        assertSuccess(worker(RecordingRunner(SyncResult.Success(emptyList()))).doWork())

        assertEquals(0, shadowNotifications.size())
    }

    @Test fun success_with_changes_posts_change_notification() = runBlocking {
        val result = SyncResult.Success(listOf(ScheduleChange.CourseAdded("量子力学")))

        assertSuccess(worker(RecordingRunner(result)).doWork())

        assertEquals(1, shadowNotifications.size())
        assertEquals("课表已更新", shadowNotifications.getNotification(SyncNotification.ID_CHANGES)
            .extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
    }

    @Test fun auth_expired_sets_need_reauth_and_posts_reauth() = runBlocking {
        assertSuccess(worker(RecordingRunner(SyncResult.Failed(SyncError.AuthenticationExpired))).doWork())

        assertEquals(true, settings.needReauth.first())
        assertEquals(1, shadowNotifications.size())
        assertEquals("请重新登录", shadowNotifications.getNotification(SyncNotification.ID_REAUTH)
            .extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
        assertEquals(null, settings.lastSyncFinishedAt.first())
    }

    @Test fun network_failure_is_silent_success() = assertSilentFailure(SyncError.NetworkFailed)

    @Test fun parse_failure_is_silent_success() = assertSilentFailure(SyncError.ParseFailed)

    @Test fun validation_failure_is_silent_success() = assertSilentFailure(SyncError.ValidationFailed)

    @Test fun other_results_do_not_mutate_need_reauth() = runBlocking {
        val results = listOf(
            SyncResult.NoChange,
            SyncResult.Success(emptyList()),
            SyncResult.Success(listOf(ScheduleChange.CourseRemoved("课程A"))),
            SyncResult.Failed(SyncError.NetworkFailed),
            SyncResult.Failed(SyncError.ParseFailed),
            SyncResult.Failed(SyncError.ValidationFailed),
        )
        results.forEach { result ->
            settings.setNeedReauth(false)
            assertSuccess(worker(RecordingRunner(result)).doWork())
            assertEquals("result=$result", false, settings.needReauth.first())
        }
        assertEquals(null, settings.lastSyncFinishedAt.first())
    }

    @Test fun manual_only_semester_is_silent_background_noop() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val profileId = "manual-profile"
            db.scheduleProfileDao().insert(ScheduleProfileEntity(profileId, "Manual", true, "[]"))
            val manualOnly = SemesterDefaults.AUTUMN_2026("manual-only", profileId, Instant.EPOCH)
                .copy(portalLinked = false, isCurrentAcademicSemester = true)
            db.semesterDao().insert(Mappers.toEntity(manualOnly))
            val portal = CountingPortal()
            val engine = SyncEngine(
                portal = portal,
                selectionParser = neverSelectionParser(),
                timetableParser = neverTimetableParser(),
                metaParser = neverMetaParser(),
                normalizer = UstcSnapshotNormalizer(),
                differ = SnapshotDiffer(),
                db = db,
                clock = Clock.systemUTC(),
            )

            assertSuccess(worker(RecordingRunner { SyncEngineBackgroundRunner(engine).run() }).doWork())
            assertEquals(0, portal.calls)
            assertEquals(0, shadowNotifications.size())
            assertEquals(false, settings.needReauth.first())
        } finally {
            db.close()
        }
    }

    @Test fun worker_does_not_request_notification_permission() = runBlocking {
        val application = context as Application
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        shadowNotifications.setNotificationsEnabled(false)
        val before = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)

        assertSuccess(
            worker(RecordingRunner(SyncResult.Success(listOf(ScheduleChange.CourseAdded("课程A"))))).doWork(),
        )

        assertEquals(PackageManager.PERMISSION_DENIED, before)
        assertEquals(before, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
        assertEquals(0, shadowNotifications.size())
    }

    @Test fun runner_exception_propagates() {
        val failure = IllegalStateException("storage bug")
        val worker = worker(RecordingRunner { throw failure })

        val thrown = assertThrows(IllegalStateException::class.java) {
            runBlocking { worker.doWork() }
        }
        assertEquals(failure, thrown)
    }

    private fun worker(runner: BackgroundSyncRunner): WeeklySyncWorker =
        TestListenableWorkerBuilder.from(context, WeeklySyncWorker::class.java)
            .setWorkerFactory(WeeklySyncWorkerFactory(runner, settings))
            .build()

    private fun assertSilentFailure(error: SyncError) = runBlocking {
        settings.setNeedReauth(false)
        assertSuccess(worker(RecordingRunner(SyncResult.Failed(error))).doWork())
        assertEquals(0, shadowNotifications.size())
        assertEquals(false, settings.needReauth.first())
    }

    private fun assertSuccess(result: ListenableWorker.Result) =
        assertEquals(ListenableWorker.Result.success(), result)

    private class RecordingRunner(
        private val block: suspend () -> SyncResult,
    ) : BackgroundSyncRunner {
        constructor(result: SyncResult) : this({ result })

        var calls = 0
            private set

        override suspend fun run(): SyncResult {
            calls++
            return block()
        }
    }

    private class CountingPortal : SchoolPortalSource {
        var calls = 0
            private set

        override suspend fun fetchCourseSelectionPage(): UstcPortalPage {
            calls++
            error("portal must not be called")
        }

        override suspend fun fetchTimetablePage(): UstcPortalPage {
            calls++
            error("portal must not be called")
        }
    }

    private fun neverSelectionParser() = object : CourseSelectionPageParser {
        override fun parse(page: UstcPortalPage): List<UstcCourseSummary> = error("parser must not be called")
    }

    private fun neverTimetableParser() = object : TimetablePageParser {
        override fun parse(page: UstcPortalPage): List<UstcTimetableEntry> = error("parser must not be called")
    }

    private fun neverMetaParser() = object : SemesterMetaParser {
        override fun parse(selection: UstcPortalPage, timetable: UstcPortalPage): SemesterMetaResult =
            error("parser must not be called")
    }
}
