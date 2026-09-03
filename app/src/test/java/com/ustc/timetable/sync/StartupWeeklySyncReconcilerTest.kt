package com.ustc.timetable.sync

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.settings.WeeklySyncScheduling
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class StartupWeeklySyncReconcilerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var storeScope: CoroutineScope
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val directory = Files.createTempDirectory("phase4-startup")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = storeScope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() },
            ),
        )
    }

    @After fun tearDown() = storeScope.cancel()

    @Test fun startup_reconciles_enabled_without_runner_invocation() = runBlocking { runReconcileTest(true) }

    @Test fun startup_reconciles_disabled_without_runner_invocation() = runBlocking { runReconcileTest(false) }

    private suspend fun CoroutineScope.runReconcileTest(enabled: Boolean) {
        val values = mutableListOf<Boolean>()
        settings.setWeeklySyncEnabled(enabled)

        StartupWeeklySyncReconciler(settings, WeeklySyncScheduling { values += it }, this).start()
        withTimeout(2_000) {
            while (values.isEmpty()) delay(1)
        }

        assertEquals(listOf(enabled), values)
    }
}
