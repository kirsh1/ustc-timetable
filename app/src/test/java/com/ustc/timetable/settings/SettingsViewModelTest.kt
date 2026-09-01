package com.ustc.timetable.settings

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.ustc.timetable.notification.NotificationPermissionController
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.school.ustc.auth.SessionBlob
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.domain.SemesterDefaults
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private lateinit var db: TimetableDatabase
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var session: FakeSessionAccess
    private lateinit var checker: FakeChecker
    private val collectors = mutableListOf<CoroutineScope>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dir = Files.createTempDirectory("i3-settings")
        dataStore = PreferenceDataStoreFactory.create(scope = scope, produceFile = { dir.resolve("settings.preferences_pb").toFile() })
        settings = SettingsStore(dataStore)
        val bundled = OfficialProfileLoader.load(context)
        profiles = ScheduleProfileRepository(db, settings, bundled)
        semesters = SemesterRepository(db, profiles)
        session = FakeSessionAccess()
        checker = FakeChecker(false)
    }

    @After fun tearDown() {
        collectors.forEach { it.cancel() }; Dispatchers.resetMain(); db.close(); scope.cancel()
    }

    @Test fun show_non_current_week_toggle_reflects_and_persists() = runBlocking {
        val vm = vm(); vm.onToggleShowNonCurrentWeek(true)
        await { settings.showNonCurrentWeek.first() }
        await { vm.state.value.showNonCurrentWeek }
        assertTrue(settings.showNonCurrentWeek.first())
    }

    @Test fun weekly_sync_toggle_persists() = runBlocking {
        val vm = vm(); vm.onToggleWeeklySync(false); await { !vm.state.value.weeklySyncEnabled }
        vm.onToggleWeeklySync(true); await { vm.state.value.weeklySyncEnabled }
        assertTrue(settings.weeklySyncEnabled.first())
    }

    @Test fun scheduler_called_only_when_runtime_available() = runBlocking {
        val scheduler = RecordingScheduler(); val wired = vm(scheduler = scheduler); wired.onToggleWeeklySync(false)
        await { !settings.weeklySyncEnabled.first() && scheduler.values == listOf(false) }
        vm().onToggleWeeklySync(false)
        assertEquals(listOf(false), scheduler.values)
    }

    @Test fun settings_entry_requests_notification_permission_once() = runBlocking {
        val vm = vm()
        assertEquals(SettingsEvent.RequestNotificationPermission, eventFrom(vm) { vm.onSyncSectionEntered() })
    }

    @Test fun enabling_weekly_sync_requests_permission_once() = runBlocking {
        settings.setWeeklySyncEnabled(false); val vm = vm(); val event = async { vm.events.first() }
        vm.onToggleWeeklySync(true)
        assertEquals(SettingsEvent.RequestNotificationPermission, event.await())
    }

    @Test fun repeated_entry_and_toggle_do_not_duplicate_permission_event() = runBlocking {
        val vm = vm(); assertEquals(SettingsEvent.RequestNotificationPermission, eventFrom(vm) { vm.onSyncSectionEntered() })
        vm.onSyncSectionEntered(); vm.onToggleWeeklySync(true)
        assertNull(withTimeoutOrNullShort { vm.events.first() })
    }

    @Test fun permission_marked_only_when_launcher_is_invoked() = runBlocking {
        val vm = vm(); assertEquals(SettingsEvent.RequestNotificationPermission, eventFrom(vm) { vm.onSyncSectionEntered() })
        assertFalse(settings.notificationRequestShown.first())
        vm.onNotificationPermissionRequestLaunched(); await { settings.notificationRequestShown.first() }
        assertTrue(settings.notificationRequestShown.first())
    }

    @Test fun denial_does_not_disable_weekly_sync() = runBlocking {
        settings.setWeeklySyncEnabled(true); val vm = vm(); vm.onNotificationPermissionRequestLaunched()
        assertTrue(settings.weeklySyncEnabled.first())
    }

    @Test fun denied_hint_shown_after_request() = runBlocking {
        settings.markNotificationRequestShown(); val vm = vm(); await { vm.state.value.showNotificationDeniedHint }
        assertTrue(vm.state.value.showNotificationDeniedHint)
    }

    @Test fun denied_hint_opens_system_notification_settings() = runBlocking {
        val vm = vm(); val event = async { vm.events.first() }; vm.onNotificationHintClick()
        assertEquals(SettingsEvent.OpenNotificationSettings, event.await())
    }

    @Test fun enabled_hides_hint() = runBlocking {
        settings.markNotificationRequestShown(); checker.enabled = true; val vm = vm(); vm.refreshNotificationsEnabled()
        await { vm.state.value.notificationsEnabled }; assertFalse(vm.state.value.showNotificationDeniedHint)
    }

    @Test @Config(sdk = [32]) fun sdk32_hides_permission_hint() = runBlocking {
        assertFalse(shouldShowNotificationDeniedHint(sdk = 32, notificationsEnabled = false, requestShown = true))
    }

    @Test fun last_sync_null_displays_dash() = runBlocking { assertEquals("—", vm().state.value.lastSyncText) }

    @Test fun last_sync_preloaded_timestamp_displays_deterministically() = runBlocking {
        settings.setLastSyncFinishedAt(Instant.parse("2026-09-01T02:03:00Z").toEpochMilli())
        val vm = vm(); await { vm.state.value.lastSyncText != "—" }
        assertEquals("2026-09-01 10:03", vm.state.value.lastSyncText)
    }

    @Test fun i3_never_writes_last_sync_finished_at() = runBlocking {
        val vm = vm(); vm.onSyncSectionEntered(); vm.onToggleWeeklySync(false); vm.onToggleShowNonCurrentWeek(true)
        await { !settings.weeklySyncEnabled.first() && settings.showNonCurrentWeek.first() }
        assertNull(settings.lastSyncFinishedAt.first())
    }

    @Test fun login_state_not_logged_in() = runBlocking { val vm = vm(); await { !vm.state.value.sessionLoading }; assertEquals(SchoolLoginUiState.NotLoggedIn, vm.state.value.loginState) }

    @Test fun login_state_logged_in_uses_session_captured_at() = runBlocking {
        session.blob = SessionBlob(emptyList(), Instant.parse("2026-09-01T02:03:00Z")); val vm = vm(); await { !vm.state.value.sessionLoading }
        assertEquals("已登录（2026-09-01 10:03）", vm.state.value.loginText)
    }

    @Test fun need_reauth_overrides_stored_session_as_expired() = runBlocking {
        session.blob = SessionBlob(emptyList(), Instant.EPOCH); settings.setNeedReauth(true); val vm = vm(); await { vm.state.value.loginState == SchoolLoginUiState.Expired }
        assertEquals("已失效", vm.state.value.loginText)
    }

    @Test fun clear_login_clears_session_and_need_reauth() = runBlocking {
        session.blob = SessionBlob(emptyList(), Instant.EPOCH); settings.setNeedReauth(true); val vm = vm(); vm.onClearLogin()
        await { session.blob == null && !settings.needReauth.first() }; assertEquals(1, session.clearCalls)
    }

    @Test fun clear_login_preserves_local_data() = runBlocking {
        seedSemester("local", portal = false); val before = db.semesterDao().allByStartDateDesc(); val vm = vm(); vm.onClearLogin(); await { session.clearCalls == 1 }
        assertEquals(before, db.semesterDao().allByStartDateDesc())
    }

    @Test fun clear_login_does_not_change_weekly_sync_preference() = runBlocking {
        settings.setWeeklySyncEnabled(true); val vm = vm(); vm.onClearLogin(); await { session.clearCalls == 1 }; assertTrue(settings.weeklySyncEnabled.first())
    }

    @Test fun relogin_success_clears_need_reauth() = runBlocking {
        settings.setNeedReauth(true); session.blob = SessionBlob(emptyList(), Instant.EPOCH); val vm = vm(reloginAvailable = true); vm.onReloginResult(true)
        await { !settings.needReauth.first() }; assertFalse(settings.needReauth.first())
    }

    @Test fun explicit_settings_relogin_does_not_auto_sync() = runBlocking {
        val sync = RecordingSync(); val vm = vm(sync = sync, reloginAvailable = true); vm.onReloginResult(true); assertEquals(0, sync.calls)
    }

    @Test fun sync_now_disabled_without_portal_target() = runBlocking { seedSemester("manual", false); val vm = vm(sync = RecordingSync()); await { vm.state.value.semestersLoaded }; assertFalse(vm.state.value.syncNowEnabled) }

    @Test fun sync_now_disabled_without_runtime() = runBlocking { seedSemester("school", true); val vm = vm(); await { vm.state.value.semestersLoaded }; assertFalse(vm.state.value.syncNowEnabled) }

    @Test fun sync_now_calls_controller_when_eligible() = runBlocking {
        seedSemester("school", true); val sync = RecordingSync(); val vm = vm(sync = sync); await { vm.state.value.syncNowEnabled }; vm.onSyncNow(); assertEquals(1, sync.calls)
    }

    @Test fun viewed_history_does_not_change_sync_target() = runBlocking {
        seedSemester("current", true); seedSemester("history", true, current = false); settings.setViewedSemesterId("history")
        val sync = RecordingSync(); val vm = vm(sync = sync); await { vm.state.value.syncNowEnabled }; vm.onSyncNow(); assertEquals(1, sync.calls)
    }

    @Test fun apply_action_allowed_for_manual_academic_current() = runBlocking {
        seedSemester("manual-current", portal = false); val vm = vm(); await { vm.state.value.canApplyWorkingToAcademicCurrent }
        assertTrue(vm.state.value.canApplyWorkingToAcademicCurrent)
    }

    @Test fun apply_action_absent_without_academic_current() = runBlocking {
        seedSemester("history", portal = true, current = false); val vm = vm(); await { vm.state.value.semestersLoaded }
        assertFalse(vm.state.value.canApplyWorkingToAcademicCurrent)
    }

    @Test fun historical_view_does_not_redirect_apply_target() = runBlocking {
        seedSemester("current", portal = false); seedSemester("history", portal = true, current = false); settings.setViewedSemesterId("history")
        val historyBefore = db.semesterDao().byId("history")!!.profileId
        val vm = vm(); await { vm.state.value.canApplyWorkingToAcademicCurrent }; vm.applyWorkingToAcademicCurrent()
        await { db.semesterDao().byId("current")!!.profileId != OfficialProfileLoader.BUNDLED_PROFILE_ID }
        assertEquals(historyBefore, db.semesterDao().byId("history")!!.profileId)
    }

    @Test fun relogin_click_emits_request_when_runtime_available() = runBlocking {
        val vm = vm(reloginAvailable = true)
        assertEquals(SettingsEvent.RequestRelogin, eventFrom(vm) { vm.onReloginClick() })
    }

    private fun vm(scheduler: WeeklySyncScheduling? = null, sync: SettingsManualSync? = null, reloginAvailable: Boolean = false): SettingsViewModel {
        val model = SettingsViewModel(
            settings, profiles, semesters, NotificationPermissionController(settings), checker, session, scheduler, sync, reloginAvailable, ZoneId.of("Asia/Shanghai"), "1.0-test",
        )
        collectors += CoroutineScope(Dispatchers.Unconfined).also { collector -> collector.launch { model.state.collect {} } }
        return model
    }

    private suspend fun seedSemester(id: String, portal: Boolean, current: Boolean = true) {
        profiles.ensureBundledSeeded()
        val s = SemesterDefaults.AUTUMN_2026(id, OfficialProfileLoader.BUNDLED_PROFILE_ID, Instant.EPOCH).copy(portalLinked = portal, isCurrentAcademicSemester = current)
        db.semesterDao().insert(Mappers.toEntity(s))
    }

    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(5_000) { while (!condition()) kotlinx.coroutines.yield() }
    private suspend fun eventFrom(vm: SettingsViewModel, action: () -> Unit): SettingsEvent {
        val deferred = kotlinx.coroutines.CoroutineScope(Dispatchers.Main).async { vm.events.first() }
        kotlinx.coroutines.yield()
        action()
        return deferred.await()
    }
    private suspend fun <T> withTimeoutOrNullShort(block: suspend () -> T): T? = kotlinx.coroutines.withTimeoutOrNull(150) { block() }

    private class FakeSessionAccess(var blob: SessionBlob? = null) : SettingsSessionAccess { var clearCalls = 0; override suspend fun load() = blob; override suspend fun clear() { clearCalls++; blob = null } }
    private class FakeChecker(var enabled: Boolean) : NotificationsEnabledChecker { override fun areEnabled() = enabled }
    private class RecordingScheduler : WeeklySyncScheduling { val values = mutableListOf<Boolean>(); override fun setEnabled(enabled: Boolean) { values += enabled } }
    private class RecordingSync : SettingsManualSync { var calls = 0; override fun start() { calls++ } }
}
