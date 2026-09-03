package com.ustc.timetable.sync

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ustc.timetable.timetable.data.SettingsStore
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SyncExecutionRecorderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val directory = Files.createTempDirectory("phase4-sync-report")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() },
            ),
        )
    }

    @After fun tearDown() = scope.cancel()

    @Test fun actual_nochange_records_finished_time() = runBlocking {
        val finished = Instant.parse("2026-09-01T10:15:30Z")
        val recorder = SyncExecutionRecorder(
            SyncExecutionSource { SyncExecutionReport(SyncResult.NoChange, true, finished) },
            settings,
        )

        assertEquals(SyncResult.NoChange, recorder.sync())
        assertEquals(finished.toEpochMilli(), settings.lastSyncFinishedAt.first())
    }

    @Test fun success_records_finished_time() = runBlocking {
        assertRecords(SyncResult.Success(emptyList()))
    }

    @Test fun typed_failure_records_finished_time() = runBlocking {
        assertRecords(SyncResult.Failed(SyncError.AuthenticationExpired))
    }

    @Test fun no_target_does_not_record_time() = runBlocking {
        val recorder = SyncExecutionRecorder(
            SyncExecutionSource { SyncExecutionReport(SyncResult.NoChange, false, null) },
            settings,
        )

        assertEquals(SyncResult.NoChange, recorder.run())
        assertEquals(null, settings.lastSyncFinishedAt.first())
    }

    @Test fun manual_and_background_share_same_recording_semantics() = runBlocking {
        val finished = Instant.parse("2026-09-01T10:15:30Z")
        var calls = 0
        val recorder = SyncExecutionRecorder(
            SyncExecutionSource {
                calls++
                SyncExecutionReport(SyncResult.NoChange, true, finished.plusSeconds(calls.toLong()))
            },
            settings,
        )
        val manual: ManualSyncRunner = recorder
        val background: BackgroundSyncRunner = recorder

        manual.sync()
        assertEquals(finished.plusSeconds(1).toEpochMilli(), settings.lastSyncFinishedAt.first())
        background.run()
        assertEquals(finished.plusSeconds(2).toEpochMilli(), settings.lastSyncFinishedAt.first())
    }

    private suspend fun assertRecords(result: SyncResult) {
        val finished = Instant.parse("2026-09-01T10:15:30Z")
        val recorder = SyncExecutionRecorder(
            SyncExecutionSource { SyncExecutionReport(result, true, finished) },
            settings,
        )

        assertEquals(result, recorder.run())
        assertEquals(finished.toEpochMilli(), settings.lastSyncFinishedAt.first())
    }
}
