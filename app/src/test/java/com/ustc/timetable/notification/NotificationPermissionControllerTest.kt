package com.ustc.timetable.notification

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ustc.timetable.timetable.data.SettingsStore
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotificationPermissionControllerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsStore

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val directory = Files.createTempDirectory("h2-notification-permission")
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { directory.resolve("settings.preferences_pb").toFile() },
        )
        settings = SettingsStore(dataStore)
    }

    @After fun tearDown() {
        scope.cancel()
    }

    @Test
    @Config(sdk = [32])
    fun sdk32_never_requests_permission() = runBlocking {
        assertFalse(controller().shouldRequestNow(areNotificationsEnabled = false))
    }

    @Test
    @Config(sdk = [33])
    fun sdk33_disabled_unrequested_requests_permission() = runBlocking {
        assertTrue(controller().shouldRequestNow(areNotificationsEnabled = false))
    }

    @Test
    @Config(sdk = [36])
    fun sdk36_disabled_unrequested_requests_permission() = runBlocking {
        assertTrue(controller().shouldRequestNow(areNotificationsEnabled = false))
    }

    @Test
    @Config(sdk = [36])
    fun enabled_notifications_do_not_request_permission() = runBlocking {
        assertFalse(controller().shouldRequestNow(areNotificationsEnabled = true))
    }

    @Test
    @Config(sdk = [36])
    fun previously_requested_does_not_request_again() = runBlocking {
        settings.markNotificationRequestShown()

        assertFalse(controller().shouldRequestNow(areNotificationsEnabled = false))
    }

    @Test
    @Config(sdk = [36])
    fun mark_requested_persists_across_controller_recreation() = runBlocking {
        controller().markRequested()
        val recreatedController = NotificationPermissionController(settings)

        assertEquals(true, settings.notificationRequestShown.first())
        assertFalse(recreatedController.shouldRequestNow(areNotificationsEnabled = false))
    }

    @Test
    @Config(sdk = [36])
    fun permission_policy_does_not_change_weekly_sync_setting() = runBlocking {
        settings.setWeeklySyncEnabled(false)

        assertTrue(controller().shouldRequestNow(areNotificationsEnabled = false))
        controller().markRequested()

        assertEquals(false, settings.weeklySyncEnabled.first())
    }

    private fun controller() = NotificationPermissionController(settings)
}
