package com.ustc.timetable.sync

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class SyncSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var workManager: WorkManager

    @Before fun setUp() {
        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
        workManager = WorkManager.getInstance(context)
    }

    @After fun tearDown() {
        workManager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    @Test fun periodic_request_interval_is_seven_days() {
        val info = enqueueAndReadSingle()

        assertEquals(TimeUnit.DAYS.toMillis(7), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test fun periodic_request_initial_delay_is_seven_days() {
        val info = enqueueAndReadSingle()

        assertEquals(TimeUnit.DAYS.toMillis(7), info.initialDelayMillis)
    }

    @Test fun periodic_request_requires_connected_network() {
        val info = enqueueAndReadSingle()

        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    }

    @Test fun repeated_enqueue_keeps_single_unique_periodic_work() {
        SyncScheduler.enqueue(context)
        SyncScheduler.enqueue(context)
        SyncScheduler.enqueue(context)

        assertEquals(1, workInfos().size)
    }

    @Test fun cancel_cancels_unique_periodic_work() {
        SyncScheduler.enqueue(context)
        SyncScheduler.cancel(context)

        assertEquals(listOf(WorkInfo.State.CANCELLED), workInfos().map { it.state })
    }

    @Test fun set_enabled_true_enqueues_unique_periodic_work() {
        SyncScheduler.setEnabled(context, enabled = true)

        assertEquals(1, workInfos().size)
    }

    @Test fun set_enabled_false_cancels_unique_periodic_work() {
        SyncScheduler.enqueue(context)

        SyncScheduler.setEnabled(context, enabled = false)

        assertEquals(listOf(WorkInfo.State.CANCELLED), workInfos().map { it.state })
    }

    private fun enqueueAndReadSingle(): WorkInfo {
        SyncScheduler.enqueue(context)
        return workInfos().single()
    }

    private fun workInfos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(SyncScheduler.UNIQUE_NAME).get()
}
