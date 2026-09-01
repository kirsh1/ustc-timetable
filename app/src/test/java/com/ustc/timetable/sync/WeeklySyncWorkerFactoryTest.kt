package com.ustc.timetable.sync

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ustc.timetable.timetable.data.SettingsStore
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class WeeklySyncWorkerFactoryTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var settings: SettingsStore
    private val runner = BackgroundSyncRunner { SyncResult.NoChange }

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val directory = Files.createTempDirectory("h3-worker-factory")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() },
            ),
        )
    }

    @After fun tearDown() {
        scope.cancel()
    }

    @Test fun factory_creates_weekly_sync_worker() {
        val worker = TestListenableWorkerBuilder.from(context, WeeklySyncWorker::class.java)
            .setWorkerFactory(WeeklySyncWorkerFactory(runner, settings))
            .build()

        assertEquals(WeeklySyncWorker::class.java.name, worker.javaClass.name)
    }

    @Test fun factory_returns_null_for_unknown_worker() {
        var capturedParameters: WorkerParameters? = null
        val capturingFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker {
                capturedParameters = workerParameters
                return WeeklySyncWorker(appContext, workerParameters, runner, settings)
            }
        }
        TestListenableWorkerBuilder.from(context, WeeklySyncWorker::class.java)
            .setWorkerFactory(capturingFactory)
            .build()

        val created = WeeklySyncWorkerFactory(runner, settings).createWorker(
            context,
            "com.example.UnknownWorker",
            requireNotNull(capturedParameters),
        )

        assertNull(created)
    }
}
