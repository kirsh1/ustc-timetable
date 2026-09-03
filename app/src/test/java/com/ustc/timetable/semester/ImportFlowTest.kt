package com.ustc.timetable.semester

import android.app.Application
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.scheduleprofile.toEntity
import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.school.ustc.parser.CourseSelectionPageParser
import com.ustc.timetable.school.ustc.parser.SemesterMetaParser
import com.ustc.timetable.school.ustc.parser.SemesterMetaResult
import com.ustc.timetable.school.ustc.parser.TimetablePageParser
import com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizer
import com.ustc.timetable.school.ustc.portal.SchoolPortalSource
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.asFailure
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.domain.FingerprintedSchoolContent
import com.ustc.timetable.timetable.domain.SchoolSnapshotFingerprint
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.Term
import com.ustc.timetable.timetable.ui.FirstLaunchRoute
import com.ustc.timetable.timetable.ui.FirstLaunchViewModel
import com.ustc.timetable.timetable.ui.LoginImportLauncher
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class ImportFlowTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-09-01T02:03:04Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private lateinit var db: TimetableDatabase
    private lateinit var storeScope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var settings: SettingsStore
    private lateinit var working: ScheduleProfile
    private lateinit var oldSemester: com.ustc.timetable.timetable.domain.Semester

    @Before fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val directory = Files.createTempDirectory("i2-import-settings")
            dataStore = PreferenceDataStoreFactory.create(
                scope = storeScope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() },
            )
            settings = SettingsStore(dataStore)
            working = OfficialProfileLoader.load(context)
            db.scheduleProfileDao().insert(working.toEntity())
            oldSemester = SemesterDefaults.AUTUMN_2026("old", working.id, Instant.EPOCH)
                .copy(isCurrentAcademicSemester = true, portalLinked = false)
            db.semesterDao().insert(Mappers.toEntity(oldSemester))
            settings.setViewedSemesterId("old")
        }
    }

    @After fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    @Test fun login_result_starts_fetch_once() = runBlocking {
        val fixture = fixture()

        fixture.vm.onLoginResultOk()

        assertEquals(1, fixture.portal.selectionCalls)
        assertEquals(1, fixture.portal.timetableCalls)
        assertEquals(1, fixture.selectionParser.calls)
        assertEquals(1, fixture.timetableParser.calls)
        assertEquals(1, fixture.metaParser.calls)
    }

    @Test fun duplicate_login_result_does_not_duplicate_import() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fixture = fixture(selectionGate = gate)
        val first = async { fixture.vm.onLoginResultOk() }
        while (fixture.vm.step.value != ImportStep.Fetching) kotlinx.coroutines.yield()

        fixture.vm.onLoginResultOk()
        gate.complete(Unit)
        first.await()

        assertEquals(1, fixture.portal.selectionCalls)
        assertEquals(2, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun confident_complete_meta_skips_confirmation() = runBlocking {
        val fixture = fixture(meta = completePartial(), confident = true)

        fixture.vm.onLoginResultOk()

        assertEquals(ImportStep.Done(SemesterId("new")), fixture.vm.step.value)
    }

    @Test fun confident_but_incomplete_meta_requires_confirmation() = runBlocking {
        val partial = completePartial().copy(totalWeeks = null)
        val fixture = fixture(meta = partial, confident = true)

        fixture.vm.onLoginResultOk()

        assertEquals(ImportStep.ConfirmMeta(partial.toExpectedDraft()), fixture.vm.step.value)
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun unconfident_meta_preserves_recognized_values_and_keeps_missing_null() = runBlocking {
        val partial = UstcSemesterMetaPartial(
            displayName = "识别学期",
            academicYear = null,
            term = Term.SPRING,
            week1Start = null,
            totalWeeks = 18,
            startDate = LocalDate.of(2027, 2, 1),
            endDate = null,
        )
        val fixture = fixture(meta = partial, confident = false)

        fixture.vm.onLoginResultOk()

        assertEquals(ImportStep.ConfirmMeta(partial.toExpectedDraft()), fixture.vm.step.value)
    }

    @Test fun no_2026_autumn_fallback_for_imported_missing_meta() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)

        fixture.vm.onLoginResultOk()

        val draft = (fixture.vm.step.value as ImportStep.ConfirmMeta).draft
        assertEquals(SemesterImportDraft(null, null, null, null, null, null, null), draft)
        assertNotEquals("2026-2027 秋季", draft.displayName)
        assertNotEquals(LocalDate.of(2026, 8, 31), draft.week1Start)
    }

    @Test fun confirmation_rejects_non_monday_week1start() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)
        fixture.vm.onLoginResultOk()

        fixture.vm.onMetaConfirmed(confirmed().copy(week1Start = LocalDate.of(2026, 9, 8)))

        assertEquals(ImportStep.Error(SyncError.ValidationFailed), fixture.vm.step.value)
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun confirmation_rejects_incomplete_meta() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)
        fixture.vm.onLoginResultOk()

        fixture.vm.onMetaConfirmed(confirmed().copy(displayName = " "))

        assertEquals(ImportStep.Error(SyncError.ValidationFailed), fixture.vm.step.value)
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun duplicate_confirmation_commits_once() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)
        fixture.vm.onLoginResultOk()

        fixture.vm.onMetaConfirmed(confirmed())
        fixture.vm.onMetaConfirmed(confirmed())

        assertEquals(2, db.semesterDao().allByStartDateDesc().size)
        assertEquals(2, db.scheduleProfileDao().all().size)
    }

    @Test fun confirm_forwards_to_import_flow() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)
        fixture.vm.onLoginResultOk()

        fixture.vm.onMetaConfirmed(confirmed())

        assertEquals(ImportStep.Done(SemesterId("new")), fixture.vm.step.value)
        assertEquals("2026-2027 秋季（确认）", db.semesterDao().byId("new")?.displayName)
    }

    @Test fun cancel_confirmation_is_zero_write() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)
        fixture.vm.onLoginResultOk()
        val before = snapshot()

        fixture.vm.onMetaCancelled()

        assertEquals(ImportStep.AwaitingLogin, fixture.vm.step.value)
        assertEquals(before, snapshot())
    }

    @Test fun cancel_preserves_previous_academic_current_and_viewed_semester() = runBlocking {
        val fixture = fixture(meta = emptyPartial(), confident = false)
        fixture.vm.onLoginResultOk()

        fixture.vm.onMetaCancelled()

        assertTrue(requireNotNull(db.semesterDao().byId("old")).isCurrentAcademicSemester)
        assertEquals("old", settings.viewedSemesterId.first())
    }

    @Test fun confirmation_commit_survives_composition_disposal_after_room_commit() {
        val viewedWriteEntered = AtomicBoolean(false)
        val viewedWriteFinished = AtomicBoolean(false)
        val releaseViewedWrite = CompletableDeferred<Unit>()
        val fixture = fixture(
            meta = completePartial(),
            confident = false,
            viewedSemesterWriter = { id ->
                viewedWriteEntered.set(true)
                releaseViewedWrite.await()
                settings.setViewedSemesterId(id)
                viewedWriteFinished.set(true)
            },
        )
        runBlocking { fixture.vm.onLoginResultOk() }
        val showRoute = mutableStateOf(true)
        val firstLaunch = firstLaunchVm(LoginImportLauncher {})

        compose.setContent {
            if (showRoute.value) FirstLaunchRoute(firstLaunch, fixture.vm)
        }
        compose.onNodeWithTag("semester_confirm_submit").assertExists()
        compose.runOnIdle { fixture.vm.confirmMeta(confirmed()) }
        compose.waitUntil(timeoutMillis = 5_000) { viewedWriteEntered.get() }
        assertNotNull(runBlocking { db.semesterDao().byId("new") })

        compose.runOnIdle { showRoute.value = false }
        releaseViewedWrite.complete(Unit)
        compose.waitUntil(timeoutMillis = 5_000) { viewedWriteFinished.get() }

        assertEquals("new", runBlocking { settings.viewedSemesterId.first() })
        assertEquals(ImportStep.Done(SemesterId("new")), fixture.vm.step.value)
    }

    @Test fun typed_failure_is_actionable_and_composed_retry_succeeds() {
        runBlocking {
            db.clearAllTables()
            db.scheduleProfileDao().insert(working.toEntity())
        }
        val fixture = fixture(
            portalErrors = listOf(SyncError.NetworkFailed, null),
        )
        runBlocking { fixture.vm.onLoginResultOk() }
        assertEquals(ImportStep.Error(SyncError.NetworkFailed), fixture.vm.step.value)
        val firstLaunch = firstLaunchVm(LoginImportLauncher(fixture.vm::startLoginImport))

        compose.setContent { FirstLaunchRoute(firstLaunch, fixture.vm) }
        compose.onNodeWithText("导入失败，请重试登录并导入").assertIsDisplayed()
        compose.onNodeWithTag("first_launch_import").performClick()
        compose.waitUntil(timeoutMillis = 5_000) { fixture.vm.step.value is ImportStep.Done }

        assertEquals(ImportStep.Done(SemesterId("new")), fixture.vm.step.value)
        assertEquals("new", runBlocking { settings.viewedSemesterId.first() })
    }

    @Test fun activity_recreation_during_suspended_fetch_does_not_strand_retained_import() {
        val fetchGate = CompletableDeferred<Unit>()
        val fixture = fixture(selectionGate = fetchGate)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = fixture.vm as T
        }
        val retained = ViewModelProvider(controller.get(), factory)[ImportFlowViewModel::class.java]

        retained.startLoginImport()
        compose.waitUntil(timeoutMillis = 5_000) { retained.step.value == ImportStep.Fetching }

        controller.configurationChange()
        val afterRecreation = ViewModelProvider(controller.get(), factory)[ImportFlowViewModel::class.java]
        assertSame(retained, afterRecreation)
        fetchGate.complete(Unit)
        compose.waitUntil(timeoutMillis = 5_000) { retained.step.value is ImportStep.Done }

        assertEquals(ImportStep.Done(SemesterId("new")), retained.step.value)
        controller.pause().stop().destroy()
    }

    @Test fun successful_import_creates_portal_linked_semester() = runBlocking {
        val fixture = fixture()

        fixture.vm.onLoginResultOk()

        val imported = requireNotNull(db.semesterDao().byId("new"))
        assertTrue(imported.portalLinked)
        assertTrue(imported.isCurrentAcademicSemester)
        assertEquals(now.toEpochMilli(), imported.importedAtEpochMilli)
    }

    @Test fun successful_import_switches_exclusive_academic_current() = runBlocking {
        fixture().vm.onLoginResultOk()

        assertFalse(requireNotNull(db.semesterDao().byId("old")).isCurrentAcademicSemester)
        assertTrue(requireNotNull(db.semesterDao().byId("new")).isCurrentAcademicSemester)
    }

    @Test fun successful_import_preserves_old_semesters() = runBlocking {
        val before = db.semesterDao().byId("old")

        fixture().vm.onLoginResultOk()

        val after = requireNotNull(db.semesterDao().byId("old"))
        assertEquals(before?.copy(isCurrentAcademicSemester = false), after)
    }

    @Test fun successful_import_sets_viewed_after_db_commit() = runBlocking {
        fixture().vm.onLoginResultOk()

        assertNotNull(db.semesterDao().byId("new"))
        assertEquals("new", settings.viewedSemesterId.first())
    }

    @Test fun imported_semester_points_to_private_profile_clone() = runBlocking {
        fixture().vm.onLoginResultOk()

        val imported = requireNotNull(db.semesterDao().byId("new"))
        val profile = requireNotNull(db.scheduleProfileDao().byId(imported.profileId))
        assertFalse(profile.isBundledOfficial)
        assertEquals("profile.import", profile.id)
        assertEquals(working.name, profile.name)
        assertEquals(working.toEntity().periodsJson, profile.periodsJson)
    }

    @Test fun working_profile_itself_is_not_mutated() = runBlocking {
        val before = db.scheduleProfileDao().byId(working.id)

        fixture().vm.onLoginResultOk()

        assertEquals(before, db.scheduleProfileDao().byId(working.id))
    }

    @Test fun no_profile_row_exists_before_atomic_commit() = runBlocking {
        val before = db.scheduleProfileDao().all()
        val fixture = fixture(meta = emptyPartial(), confident = false)

        fixture.vm.onLoginResultOk()

        assertEquals(before, db.scheduleProfileDao().all())
        assertEquals("old", settings.viewedSemesterId.first())
    }

    @Test fun imported_snapshot_gets_h1_fingerprint() = runBlocking {
        fixture().vm.onLoginResultOk()

        val semester = Mappers.toDomain(requireNotNull(db.semesterDao().byId("new")))
        val courses = db.courseDao().coursesForSemester("new").map { Mappers.toDomain(it, semester.id) }
        val meetings = db.courseDao().meetingsForSemester("new").map(Mappers::toDomain)
        val expected = SchoolSnapshotFingerprint.compute(
            FingerprintedSchoolContent.of(semester, courses, meetings),
        )
        assertEquals(expected, semester.sourceFingerprint)
    }

    @Test fun imported_school_rows_have_fresh_local_ids_and_valid_references() = runBlocking {
        val fixture = fixture()
        val normalized = UstcSnapshotNormalizer().normalize(
            fixture.selectionParser.rows,
            fixture.timetableParser.rows,
            completePartial(),
            SemesterId("new"),
        )

        fixture.vm.onLoginResultOk()

        val courses = db.courseDao().coursesForSemester("new")
        val meetings = db.courseDao().meetingsForSemester("new")
        assertTrue(courses.none { stored -> normalized.courses.any { it.id.value == stored.id } })
        assertTrue(meetings.none { stored -> normalized.meetings.any { it.id.value == stored.id } })
        assertTrue(meetings.all { meeting -> courses.any { it.id == meeting.courseId } })
    }

    @Test fun empty_snapshot_is_validation_failure_and_zero_write() = runBlocking {
        val fixture = fixture(selection = emptyList(), timetable = emptyList())
        val before = snapshot()

        fixture.vm.onLoginResultOk()

        assertEquals(ImportStep.Error(SyncError.ValidationFailed), fixture.vm.step.value)
        assertEquals(before, snapshot())
    }

    @Test fun parse_failure_is_zero_write() = assertKnownFailureIsZeroWrite(SyncError.ParseFailed, FailureStage.PARSE)

    @Test fun auth_failure_is_zero_write() = assertKnownFailureIsZeroWrite(SyncError.AuthenticationExpired, FailureStage.PORTAL)

    @Test fun network_failure_is_zero_write() = assertKnownFailureIsZeroWrite(SyncError.NetworkFailed, FailureStage.PORTAL)

    @Test fun validation_failure_is_zero_write() = assertKnownFailureIsZeroWrite(SyncError.ValidationFailed, FailureStage.NORMALIZE)

    @Test fun transaction_failure_leaves_no_profile_semester_or_school_rows() = runBlocking {
        val fixture = fixture(provisionalId = "old")

        assertThrows(Exception::class.java) { runBlocking { fixture.vm.onLoginResultOk() } }

        assertNull(db.scheduleProfileDao().byId("profile.import"))
        assertTrue(db.courseDao().coursesForSemester("old").isEmpty())
        assertTrue(db.courseDao().meetingsForSemester("old").isEmpty())
        assertEquals(1, db.semesterDao().allByStartDateDesc().size)
    }

    @Test fun transaction_failure_preserves_previous_academic_current() = runBlocking {
        val before = db.semesterDao().byId("old")

        assertThrows(Exception::class.java) {
            runBlocking { fixture(provisionalId = "old").vm.onLoginResultOk() }
        }

        assertEquals(before, db.semesterDao().byId("old"))
        assertTrue(requireNotNull(db.semesterDao().byId("old")).isCurrentAcademicSemester)
    }

    @Test fun transaction_failure_does_not_change_viewed_semester() = runBlocking {
        assertThrows(Exception::class.java) {
            runBlocking { fixture(provisionalId = "old").vm.onLoginResultOk() }
        }

        assertEquals("old", settings.viewedSemesterId.first())
    }

    private fun assertKnownFailureIsZeroWrite(error: SyncError, stage: FailureStage) = runBlocking {
        val before = snapshot()
        val fixture = when (stage) {
            FailureStage.PORTAL -> fixture(portalError = error)
            FailureStage.PARSE -> fixture(parserError = error)
            FailureStage.NORMALIZE -> fixture(selection = selectionRows() + selectionRows())
        }

        fixture.vm.onLoginResultOk()

        assertEquals(ImportStep.Error(error), fixture.vm.step.value)
        assertEquals(before, snapshot())
    }

    private suspend fun snapshot() = DatabaseSnapshot(
        semesters = db.semesterDao().allByStartDateDesc(),
        profiles = db.scheduleProfileDao().all(),
        oldCourses = db.courseDao().coursesForSemester("old"),
        oldMeetings = db.courseDao().meetingsForSemester("old"),
        viewed = settings.viewedSemesterId.first(),
    )

    private fun fixture(
        meta: UstcSemesterMetaPartial = completePartial(),
        confident: Boolean = true,
        selection: List<UstcCourseSummary> = selectionRows(),
        timetable: List<UstcTimetableEntry> = timetableRows(),
        portalError: SyncError? = null,
        portalErrors: List<SyncError?>? = null,
        parserError: SyncError? = null,
        selectionGate: CompletableDeferred<Unit>? = null,
        provisionalId: String = "new",
        viewedSemesterWriter: suspend (String) -> Unit = settings::setViewedSemesterId,
    ): Fixture {
        val portal = FakePortal(ArrayDeque(portalErrors ?: listOf(portalError)), selectionGate)
        val selectionParser = FakeSelectionParser(selection, parserError)
        val timetableParser = FakeTimetableParser(timetable)
        val metaParser = FakeMetaParser(meta, confident)
        val vm = ImportFlowViewModel(
            portal = portal,
            selectionParser = selectionParser,
            timetableParser = timetableParser,
            metaParser = metaParser,
            normalizer = UstcSnapshotNormalizer(),
            db = db,
            settings = settings,
            workingProfile = { working },
            clock = clock,
            provisionalSemesterId = { SemesterId(provisionalId) },
            privateProfileId = { "profile.import" },
            setViewedSemesterId = viewedSemesterWriter,
            importDispatcher = Dispatchers.Default,
        )
        return Fixture(vm, portal, selectionParser, timetableParser, metaParser)
    }

    private fun firstLaunchVm(launcher: LoginImportLauncher): FirstLaunchViewModel {
        val profiles = ScheduleProfileRepository(db, settings, working)
        return FirstLaunchViewModel(
            semesters = SemesterRepository(db, profiles),
            settings = settings,
            bundledOfficial = working,
            clock = clock,
            loginImportLauncher = launcher,
        )
    }

    private fun completePartial() = UstcSemesterMetaPartial(
        displayName = "2026-2027 秋季（学校）",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        week1Start = LocalDate.of(2026, 9, 7),
        totalWeeks = 18,
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2027, 1, 15),
    )

    private fun emptyPartial() = UstcSemesterMetaPartial(null, null, null, null, null, null, null)

    private fun confirmed() = ConfirmedSemesterMeta(
        displayName = "2026-2027 秋季（确认）",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        week1Start = LocalDate.of(2026, 9, 7),
        totalWeeks = 18,
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2027, 1, 15),
    )

    private fun selectionRows() = listOf(
        UstcCourseSummary("C001", "量子力学", 4.0, "物理学院", "专业", "教师甲", "1-4周"),
    )

    private fun timetableRows() = listOf(
        UstcTimetableEntry("量子力学", "C001", "周一", "1-2", "1-4周", "TH-A101", "教师甲"),
    )

    private fun UstcSemesterMetaPartial.toExpectedDraft() = SemesterImportDraft(
        displayName, academicYear, term, week1Start, totalWeeks, startDate, endDate,
    )

    private data class Fixture(
        val vm: ImportFlowViewModel,
        val portal: FakePortal,
        val selectionParser: FakeSelectionParser,
        val timetableParser: FakeTimetableParser,
        val metaParser: FakeMetaParser,
    )

    private data class DatabaseSnapshot(
        val semesters: List<com.ustc.timetable.timetable.data.db.entity.SemesterEntity>,
        val profiles: List<com.ustc.timetable.timetable.data.db.entity.ScheduleProfileEntity>,
        val oldCourses: List<com.ustc.timetable.timetable.data.db.entity.CourseEntity>,
        val oldMeetings: List<com.ustc.timetable.timetable.data.db.entity.CourseMeetingEntity>,
        val viewed: String?,
    )

    private enum class FailureStage { PORTAL, PARSE, NORMALIZE }

    private class FakePortal(
        private val errors: ArrayDeque<SyncError?>,
        private val selectionGate: CompletableDeferred<Unit>?,
    ) : SchoolPortalSource {
        var selectionCalls = 0
        var timetableCalls = 0

        override suspend fun fetchCourseSelectionPage(): UstcPortalPage {
            selectionCalls++
            selectionGate?.await()
            errors.removeFirstOrNull()?.let { throw it.asFailure() }
            return UstcPortalPage("selection", "fixture://selection")
        }

        override suspend fun fetchTimetablePage(): UstcPortalPage {
            timetableCalls++
            return UstcPortalPage("timetable", "fixture://timetable")
        }
    }

    private class FakeSelectionParser(
        val rows: List<UstcCourseSummary>,
        private val error: SyncError?,
    ) : CourseSelectionPageParser {
        var calls = 0
        override fun parse(page: UstcPortalPage): List<UstcCourseSummary> {
            calls++
            error?.let { throw it.asFailure() }
            return rows
        }
    }

    private class FakeTimetableParser(
        val rows: List<UstcTimetableEntry>,
    ) : TimetablePageParser {
        var calls = 0
        override fun parse(page: UstcPortalPage): List<UstcTimetableEntry> {
            calls++
            return rows
        }
    }

    private class FakeMetaParser(
        private val meta: UstcSemesterMetaPartial,
        private val confident: Boolean,
    ) : SemesterMetaParser {
        var calls = 0
        override fun parse(selection: UstcPortalPage, timetable: UstcPortalPage): SemesterMetaResult {
            calls++
            return SemesterMetaResult(meta, confident)
        }
    }
}
