package com.ustc.timetable.timetable.ui

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.TimetableRepository
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.ProfileId
import com.ustc.timetable.timetable.domain.Term
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class FirstLaunchViewModelTest {
    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var scope: CoroutineScope
    private lateinit var bundled: ScheduleProfile
    private val collectors = mutableListOf<CoroutineScope>()
    private val instant = Instant.parse("2026-09-01T03:04:05Z")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dir = Files.createTempDirectory("i1-vm")
        settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { dir.resolve("settings.preferences_pb").toFile() })
        bundled = OfficialProfileLoader.load(context)
        profiles = ScheduleProfileRepository(db, settings, bundled)
        semesters = SemesterRepository(db, profiles)
    }

    @After fun tearDown() { collectors.forEach { it.cancel() }; Dispatchers.resetMain(); db.close(); scope.cancel() }

    @Test fun initial_state_is_loading_not_empty() {
        assertEquals(FirstLaunchGate.Loading, FirstLaunchViewModel(semesters, settings, bundled, clock()).state.value.gate)
    }

    @Test fun empty_database_shows_first_launch() = runBlocking {
        val vm = vm(); await { vm.state.value.gate == FirstLaunchGate.Empty }
    }

    @Test fun existing_semester_skips_first_launch() = runBlocking {
        profiles.ensureBundledSeeded(); db.semesterDao().insert(Mappers.toEntity(base("existing").copy(profileId = ProfileId(OfficialProfileLoader.BUNDLED_PROFILE_ID))))
        val vm = vm(); await { vm.state.value.gate == FirstLaunchGate.Ready }
    }

    @Test fun semester_insert_after_vm_start_switches_to_ready_without_recreation() = runBlocking {
        val vm = vm(); await { vm.state.value.gate == FirstLaunchGate.Empty }
        profiles.ensureBundledSeeded(); db.semesterDao().insert(Mappers.toEntity(base("inserted").copy(profileId = ProfileId(OfficialProfileLoader.BUNDLED_PROFILE_ID))))
        await { vm.state.value.gate == FirstLaunchGate.Ready }
    }

    @Test fun manual_fallback_creates_exact_autumn_2026_metadata_and_uses_clock() = runBlocking {
        val vm = vm(); await { vm.state.value.gate == FirstLaunchGate.Empty }; vm.onSkipManualCreation(); await { db.semesterDao().allByStartDateDesc().size == 1 }
        val row = db.semesterDao().allByStartDateDesc().single().let(Mappers::toDomain)
        assertEquals("2026-2027 秋季", row.displayName)
        assertEquals("2026-2027", row.academicYear)
        assertEquals(Term.AUTUMN, row.term)
        assertEquals(LocalDate.of(2026, 8, 30), row.startDate)
        assertEquals(LocalDate.of(2026, 8, 31), row.week1Start)
        assertEquals(LocalDate.of(2027, 1, 15), row.endDate)
        assertEquals(20, row.totalWeeks)
        assertEquals(instant, row.importedAt)
        assertNull(row.lastSyncedAt)
        assertTrue(row.isCurrentAcademicSemester)
        assertFalse(row.portalLinked)
        assertNull(row.sourceFingerprint)
    }

    @Test fun custom_working_profile_is_ignored_and_pointer_unchanged() = runBlocking {
        val custom = profiles.saveWorkingEdited(bundled, bundled.periods.mapIndexed { i, p -> if (i == 0) p.copy(start = p.start.plusMinutes(1)) else p })
        val vm = vm(); await { vm.state.value.gate == FirstLaunchGate.Empty }; vm.onSkipManualCreation(); await { db.semesterDao().allByStartDateDesc().size == 1 }
        val created = db.semesterDao().allByStartDateDesc().single()
        val clone = db.scheduleProfileDao().byId(created.profileId)!!
        assertEquals(bundled.periods, com.ustc.timetable.scheduleprofile.decodePeriods(clone.periodsJson))
        assertEquals(custom.id, settings.activeWorkingProfileId.first())
    }

    @Test fun viewed_id_written_only_after_successful_room_creation() = runBlocking {
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val vm = vm(create = { base, source -> entered.complete(Unit); release.await(); semesters.createInitialLocalSemesterIfEmpty(base, source) })
        await { vm.state.value.gate == FirstLaunchGate.Empty }; vm.onSkipManualCreation(); entered.await()
        assertNull(settings.viewedSemesterId.first())
        release.complete(Unit); await { settings.viewedSemesterId.first() != null }
        assertEquals(db.semesterDao().allByStartDateDesc().single().id, settings.viewedSemesterId.first())
    }

    @Test fun existing_database_noop_does_not_overwrite_viewed_id() = runBlocking {
        settings.setViewedSemesterId("sentinel")
        val winner = base("winner")
        val vm = vm(create = { _, _ ->
            profiles.ensureBundledSeeded(); db.semesterDao().insert(Mappers.toEntity(winner.copy(profileId = ProfileId(OfficialProfileLoader.BUNDLED_PROFILE_ID))))
            null
        })
        await { vm.state.value.gate == FirstLaunchGate.Empty }; vm.onSkipManualCreation(); await { vm.state.value.gate == FirstLaunchGate.Ready }
        assertEquals("sentinel", settings.viewedSemesterId.first())
    }

    @Test fun room_failure_does_not_write_viewed_id() = runBlocking {
        settings.setViewedSemesterId("sentinel")
        val vm = vm(create = { _, _ -> throw IllegalStateException("room failed") })
        await { vm.state.value.gate == FirstLaunchGate.Empty }; vm.onSkipManualCreation(); await { !vm.state.value.isCreatingManualSemester }
        assertEquals("sentinel", settings.viewedSemesterId.first())
    }

    @Test fun double_tap_manual_creation_creates_once_and_disables_both_actions() = runBlocking {
        var calls = 0; val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val vm = vm(create = { base, source -> calls++; entered.complete(Unit); release.await(); semesters.createInitialLocalSemesterIfEmpty(base, source) })
        await { vm.state.value.gate == FirstLaunchGate.Empty }; vm.onSkipManualCreation(); vm.onSkipManualCreation(); entered.await()
        assertTrue(vm.state.value.isCreatingManualSemester)
        assertFalse(vm.state.value.actionsEnabled)
        assertEquals(1, calls)
        release.complete(Unit); await { vm.state.value.gate == FirstLaunchGate.Ready }
    }

    @Test fun unavailable_import_click_shows_exact_message_without_database_write() = runBlocking {
        val vm = vm(); await { vm.state.value.gate == FirstLaunchGate.Empty }
        val event = async { vm.events.first() }; vm.onLoginAndImport()
        assertEquals(FirstLaunchEvent.ShowSnackbar("导入功能将在门户接入后可用"), event.await())
        assertEquals(0, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun available_import_seam_fires_once() = runBlocking {
        var calls = 0; val vm = vm(launcher = LoginImportLauncher { calls++ }); await { vm.state.value.gate == FirstLaunchGate.Empty }
        vm.onLoginAndImport(); assertEquals(1, calls)
    }

    @Test fun manual_fallback_enters_blank_timetable() = runBlocking {
        val firstLaunch = vm(); await { firstLaunch.state.value.gate == FirstLaunchGate.Empty }
        firstLaunch.onSkipManualCreation(); await { firstLaunch.state.value.gate == FirstLaunchGate.Ready }

        val timetable = timetableVm()
        await { !timetable.state.value.isLoading }
        assertTrue(timetable.state.value.semester != null)
        assertTrue(timetable.state.value.placedSchool.isEmpty())
        assertTrue(timetable.state.value.courseDetailsByMeetingId.isEmpty())
    }

    @Test fun manual_fallback_timetable_is_not_syncable() = runBlocking {
        val firstLaunch = vm(); await { firstLaunch.state.value.gate == FirstLaunchGate.Empty }
        firstLaunch.onSkipManualCreation(); await { firstLaunch.state.value.gate == FirstLaunchGate.Ready }

        val timetable = timetableVm()
        await { !timetable.state.value.isLoading }
        assertFalse(timetable.state.value.canSyncViewed)
    }

    private fun vm(
        launcher: LoginImportLauncher? = null,
        create: suspend (Semester, ScheduleProfile) -> Semester? = semesters::createInitialLocalSemesterIfEmpty,
    ): FirstLaunchViewModel {
        val model = FirstLaunchViewModel(semesters, settings, bundled, clock(), launcher, create)
        collectors += CoroutineScope(Dispatchers.Unconfined).also { collector -> collector.launch { model.state.collect {} } }
        return model
    }

    private fun base(id: String) = SemesterDefaults.AUTUMN_2026(id, "replaced", instant)
    private fun timetableVm(): TimetableViewModel {
        val model = TimetableViewModel(
            semesters,
            TimetableRepository(db),
            ManualItemRepository(db, clock()),
            profiles,
            settings,
            clock(),
            MutableStateFlow(instant),
        )
        collectors += CoroutineScope(Dispatchers.Unconfined).also { collector -> collector.launch { model.state.collect {} } }
        return model
    }
    private fun clock() = Clock.fixed(instant, ZoneOffset.UTC)
    private suspend fun await(condition: suspend () -> Boolean) = withTimeout(5_000) { while (!condition()) kotlinx.coroutines.yield() }
}
