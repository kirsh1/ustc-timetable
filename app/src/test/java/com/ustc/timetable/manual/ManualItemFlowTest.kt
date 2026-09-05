package com.ustc.timetable.manual

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.ustc.timetable.scheduleprofile.OfficialProfileLoader
import com.ustc.timetable.scheduleprofile.ScheduleProfileRepository
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.SettingsStore
import com.ustc.timetable.timetable.data.TimetableRepository
import com.ustc.timetable.timetable.data.db.Mappers
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.Semester
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.layout.WeeklyTimetableLayout
import com.ustc.timetable.timetable.layout.LongPressDraft
import com.ustc.timetable.timetable.ui.TimetableScreen
import com.ustc.timetable.timetable.ui.TimetableUiState
import com.ustc.timetable.timetable.ui.TimetableViewModel
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestDispatcher
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ManualItemFlowTest {

    @get:Rule val rule = createComposeRule()

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-09-08T02:00:00Z")
    private val clock = Clock.fixed(now, zone)
    private val nowFlow = MutableStateFlow(now)
    private val storeJob = SupervisorJob()
    private val storeScope = CoroutineScope(Dispatchers.IO + storeJob)
    private val collectorJobs = mutableListOf<Job>()

    private lateinit var db: TimetableDatabase
    private lateinit var settings: SettingsStore
    private lateinit var profiles: ScheduleProfileRepository
    private lateinit var semesters: SemesterRepository
    private lateinit var timetable: TimetableRepository
    private lateinit var manual: ManualItemRepository
    private lateinit var semester: Semester
    private lateinit var mainDispatcher: TestDispatcher
    private lateinit var viewModelStore: ViewModelStore

    @Before fun setUp() = runBlocking {
        mainDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(mainDispatcher)
        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, TimetableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val directory = Files.createTempDirectory("d3-manual-flow")
        settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = storeScope,
                produceFile = { directory.resolve("settings.preferences_pb").toFile() },
            ),
        )
        profiles = ScheduleProfileRepository(db, settings, OfficialProfileLoader.load(context))
        profiles.ensureBundledSeeded()
        semesters = SemesterRepository(db, profiles)
        timetable = TimetableRepository(db)
        manual = ManualItemRepository(db, clock)
        viewModelStore = ViewModelStore()
        semester = SemesterDefaults.AUTUMN_2026("viewed", OfficialProfileLoader.BUNDLED_PROFILE_ID, now)
        db.semesterDao().insert(Mappers.toEntity(semester))
    }

    @After fun tearDown() = runBlocking {
        collectorJobs.forEach { it.cancelAndJoin() }
        viewModelStore.clear()
        storeJob.cancelAndJoin()
        mainDispatcher.scheduler.advanceUntilIdle()
        db.close()
        Dispatchers.resetMain()
    }

    private fun newViewModel(): TimetableViewModel = ViewModelProvider(
        viewModelStore,
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == TimetableViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return TimetableViewModel(
                    semestersRepo = semesters,
                    timetableRepo = timetable,
                    manualRepo = manual,
                    profilesRepo = profiles,
                    settings = settings,
                    clock = clock,
                    nowTicks = nowFlow,
                ) as T
            }
        },
    )[TimetableViewModel::class.java]

    private fun runningViewModel(): TimetableViewModel {
        val model = newViewModel()
        val collectorJob = SupervisorJob()
        CoroutineScope(Dispatchers.Unconfined + collectorJob).launch { model.state.collect {} }
        collectorJobs += collectorJob
        return model
    }

    private suspend fun awaitUntil(condition: suspend () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) {
                mainDispatcher.scheduler.runCurrent()
                delay(20)
            }
        }
    }

    private suspend fun usable(model: TimetableViewModel): TimetableUiState {
        awaitUntil { !model.state.value.isLoading && model.state.value.semester != null }
        return model.state.value
    }

    private fun item(
        id: String,
        weeks: WeekPattern = WeekPattern.of(2),
        title: String = id,
        semesterId: SemesterId = semester.id,
    ) = ManualScheduleItem(
        id = ManualItemId(id),
        semesterId = semesterId,
        title = title,
        weekday = 6,
        startTime = LocalTime.of(14, 20),
        endTime = LocalTime.of(15, 5),
        weekPattern = weeks,
        location = null,
        note = null,
        createdAt = now,
        updatedAt = now,
    )

    private suspend fun seedManual(value: ManualScheduleItem) {
        manual.add(value)
    }

    private suspend fun seedSchool(id: String = "school") {
        val courseId = CourseId("course-$id")
        db.applySchoolSnapshot(
            semester.id,
            listOf(Course(courseId, semester.id, "key-$id", "CODE", "学校课程", 2.0, null)),
            listOf(CourseMeeting(MeetingId(id), courseId, 3, 1, 2, WeekPattern.of(2), "教室", listOf("教师"))),
            "fp-$id",
            now,
        )
    }

    private suspend fun stored(): List<ManualScheduleItem> =
        db.manualItemDao().itemsForSemester(semester.id.value).map(Mappers::toDomain)

    private fun newTarget(state: TimetableUiState, week: Int = 5): ManualEditorTarget.New {
        return createNewManualEditorTarget(
            state,
            week,
            LongPressDraft(weekday = 6, snappedStart = LocalTime.of(14, 20)),
        )!!
    }

    private fun editTarget(state: TimetableUiState, id: String, week: Int = 5): ManualEditorTarget.Edit =
        createEditManualEditorTarget(state, ManualItemId(id), week)!!

    private fun session(
        state: TimetableUiState,
        target: ManualEditorTarget,
        onDismiss: () -> Unit = {},
    ) = ManualEditorSession(
        target = target,
        semester = state.semester!!,
        profile = state.profile!!,
        existingItem = (target as? ManualEditorTarget.Edit)?.let { state.manualItemsById[it.manualItemId] },
        manual = manual,
        clock = clock,
        onDismiss = onDismiss,
    )

    @Test fun manual_lookup_contains_hidden_items() = runBlocking {
        seedManual(item("visible", WeekPattern.of(2)))
        seedManual(item("hidden", WeekPattern.of(7)))
        val state = usable(runningViewModel())
        assertEquals(setOf(ManualItemId("visible"), ManualItemId("hidden")), state.manualItemsById.keys)
        assertTrue(state.weekPages.single { it.week == 2 }.placedManual.none { it.block.manualItemId == ManualItemId("hidden") })
    }

    @Test fun manual_lookup_is_independent_of_show_non_current_toggle() = runBlocking {
        seedManual(item("hidden", WeekPattern.of(7)))
        val model = runningViewModel()
        val before = usable(model).manualItemsById
        model.onToggleShowNonCurrentWeek(true)
        awaitUntil { model.state.value.showNonCurrentWeek }
        assertEquals(before, model.state.value.manualItemsById)
    }

    @Test fun empty_longpress_opens_editor_prefilled() = runBlocking {
        val state = usable(runningViewModel())
        val target = newTarget(state, week = 5)
        val initial = resolveManualEditorInitial(target, state) as ManualEditorInitial.New
        assertEquals(6, initial.weekday)
        assertEquals(LocalTime.of(14, 20), initial.startTime)
        assertEquals(5, initial.viewedWeek)
    }

    @Test fun empty_longpress_uses_exact_page_week() = runBlocking {
        val state = usable(runningViewModel())
        var capturedWeek: Int? = null
        rule.setContent {
            TimetableScreen(
                state = state,
                onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {},
                onEmptyLongPress = { week, _ -> capturedWeek = week },
            )
        }
        rule.onNodeWithTag("timetable_grid").performTouchInput { longClick(center) }
        assertEquals(2, capturedWeek)
    }

    @Test fun manual_click_carries_exact_page_week() = runBlocking {
        seedManual(item("tap", WeekPattern.of(2)))
        val state = usable(runningViewModel())
        var captured: Pair<ManualItemId, Int>? = null
        rule.setContent {
            TimetableScreen(
                state = state,
                onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {},
                onManualBlockClick = { id, week -> captured = id to week },
            )
        }
        rule.onNodeWithTag("manual_block:tap").performClick()
        assertEquals(ManualItemId("tap") to 2, captured)
    }

    @Test fun manual_block_click_opens_existing() = runBlocking {
        seedManual(item("edit", WeekPattern.of(7)))
        val state = usable(runningViewModel())
        val target = editTarget(state, "edit", week = 5)
        val initial = resolveManualEditorInitial(target, state) as ManualEditorInitial.Edit
        assertEquals(ManualItemId("edit"), target.manualItemId)
        assertEquals(ManualItemId("edit"), initial.item.id)
        assertEquals(5, initial.viewedWeek)
    }

    @Test fun ghost_manual_click_preserves_original_week_pattern() = runBlocking {
        seedManual(item("ghost", WeekPattern.of(7)))
        // This test checks editing a ghost, not an asynchronous settings-button action.
        // Seed the preference before collecting the ViewModel, just like the manual row.
        settings.setShowNonCurrentWeek(true)
        val model = runningViewModel()
        try {
            awaitUntil { model.state.value.showNonCurrentWeek && model.state.value.manualItemsById.isNotEmpty() }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("ghost-state diagnostic: ${model.state.value}; stored=${stored()}", error)
        }
        val state = model.state.value
        val editor = session(state, editTarget(state, "ghost", week = 5)).editor
        assertEquals(WeekMode.CUSTOM, editor.draft.value.mode)
        assertEquals(WeekPattern.of(7), editor.buildItem().weekPattern)
    }

    @Test fun stale_manual_id_does_not_open_editor() = runBlocking {
        val state = usable(runningViewModel())
        assertNull(createEditManualEditorTarget(state, ManualItemId("missing"), 2))
    }

    @Test fun edit_never_resolves_item_from_other_semester() = runBlocking {
        val otherId = SemesterId("other")
        val foreign = item("foreign", semesterId = otherId)
        val state = usable(runningViewModel()).copy(manualItemsById = mapOf(foreign.id to foreign))
        assertNull(createEditManualEditorTarget(state, foreign.id, 2))
    }

    @Test fun save_new_adds_and_closes() = runBlocking {
        val state = usable(runningViewModel())
        var dismisses = 0
        val editorSession = session(state, newTarget(state), onDismiss = { dismisses++ })
        editorSession.editor.update { it.copy(title = "新建") }
        assertTrue(editorSession.save())
        assertEquals(1, dismisses)
        assertEquals(listOf("新建"), stored().map { it.title })
    }

    @Test fun saved_new_item_appears_via_repository_flow() = runBlocking {
        val model = runningViewModel()
        val state = usable(model)
        val editorSession = session(state, newTarget(state))
        editorSession.editor.update { it.copy(title = "flow-new") }
        editorSession.save()
        awaitUntil { model.state.value.manualItemsById.values.any { it.title == "flow-new" } }
        assertEquals(stored().single().id, model.state.value.manualItemsById.values.single().id)
    }

    @Test fun edit_save_updates_same_id_via_repository_flow() = runBlocking {
        seedManual(item("same", title = "旧"))
        val model = runningViewModel()
        val state = usable(model)
        val editorSession = session(state, editTarget(state, "same"))
        editorSession.editor.update { it.copy(title = "新") }
        editorSession.save()
        awaitUntil { model.state.value.manualItemsById[ManualItemId("same")]?.title == "新" }
        assertEquals(listOf("same"), stored().map { it.id.value })
    }

    @Test fun double_save_does_not_duplicate() = runBlocking {
        val state = usable(runningViewModel())
        var dismisses = 0
        val editorSession = session(state, newTarget(state), onDismiss = { dismisses++ })
        editorSession.editor.update { it.copy(title = "once") }
        assertTrue(editorSession.save())
        assertFalse(editorSession.save())
        assertEquals(1, stored().size)
        assertEquals(1, dismisses)
    }

    @Test fun delete_requires_confirmation() = runBlocking {
        seedManual(item("delete"))
        val state = usable(runningViewModel())
        var dismisses = 0
        rule.setContent {
            ManualEditorHost(
                target = editTarget(state, "delete"),
                semester = state.semester!!,
                profile = state.profile!!,
                existingItem = state.manualItemsById.getValue(ManualItemId("delete")),
                manual = manual,
                clock = clock,
                onDismiss = { dismisses++ },
            )
        }
        rule.onNodeWithTag("editor_delete").performScrollTo().performClick()
        rule.onAllNodesWithText("删除手动项目").assertCountEquals(1)
        rule.onAllNodesWithText("确认删除这个手动项目吗？").assertCountEquals(1)
        assertEquals(1, stored().size)
        assertEquals(0, dismisses)
    }

    @Test fun delete_cancel_keeps_item_and_editor() = runBlocking {
        seedManual(item("cancel"))
        val state = usable(runningViewModel())
        var dismisses = 0
        rule.setContent {
            ManualEditorHost(
                editTarget(state, "cancel"), state.semester!!, state.profile!!,
                state.manualItemsById.getValue(ManualItemId("cancel")), manual, clock,
                onDismiss = { dismisses++ },
            )
        }
        rule.onNodeWithTag("editor_delete").performScrollTo().performClick()
        rule.onNodeWithTag("delete_cancel").performClick()
        rule.onAllNodesWithText("删除手动项目").assertCountEquals(0)
        rule.onNodeWithTag("editor_save").assertExists()
        assertEquals(1, stored().size)
        assertEquals(0, dismisses)
    }

    @Test fun delete_confirm_removes_item_and_closes() = runBlocking {
        seedManual(item("confirm"))
        val state = usable(runningViewModel())
        var dismisses = 0
        rule.setContent {
            ManualEditorHost(
                editTarget(state, "confirm"), state.semester!!, state.profile!!,
                state.manualItemsById.getValue(ManualItemId("confirm")), manual, clock,
                onDismiss = { dismisses++ },
            )
        }
        rule.onNodeWithTag("editor_delete").performScrollTo().performClick()
        rule.onNodeWithTag("delete_confirm").performClick()
        rule.waitUntil(5_000) { runBlocking { stored().isEmpty() } && dismisses == 1 }
        assertEquals(1, dismisses)
    }

    @Test fun double_delete_confirmation_deletes_once() = runBlocking {
        seedManual(item("once-delete"))
        val state = usable(runningViewModel())
        var dismisses = 0
        val editorSession = session(state, editTarget(state, "once-delete"), onDismiss = { dismisses++ })
        assertTrue(editorSession.delete())
        assertFalse(editorSession.delete())
        assertTrue(stored().isEmpty())
        assertEquals(1, dismisses)
    }

    @Test fun delete_failure_keeps_editor_open() = runBlocking {
        seedManual(item("stale-delete"))
        val state = usable(runningViewModel())
        var dismisses = 0
        rule.setContent {
            ManualEditorHost(
                editTarget(state, "stale-delete"), state.semester!!, state.profile!!,
                state.manualItemsById.getValue(ManualItemId("stale-delete")), manual, clock,
                onDismiss = { dismisses++ },
            )
        }
        db.manualItemDao().delete("stale-delete")
        rule.onNodeWithTag("editor_delete").performScrollTo().performClick()
        rule.onNodeWithTag("delete_confirm").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("editor_save").assertExists()
        assertEquals(0, dismisses)
    }

    @Test fun school_and_manual_overlays_are_exclusive() = runBlocking {
        seedManual(item("exclusive"))
        val state = usable(runningViewModel())
        val overlays = ManualOverlayState()
        overlays.openManual(editTarget(state, "exclusive"))
        assertNull(overlays.selectedSchoolMeetingId)
        overlays.openSchool(MeetingId("school"))
        assertNull(overlays.manualTarget)
        assertEquals(MeetingId("school"), overlays.selectedSchoolMeetingId)
        overlays.openManual(editTarget(state, "exclusive"))
        assertNull(overlays.selectedSchoolMeetingId)
    }

    @Test fun semester_switch_and_profile_change_close_editor() = runBlocking {
        seedManual(item("guard"))
        val state = usable(runningViewModel())
        val target = editTarget(state, "guard")
        assertNull(resolveManualEditorInitial(target, state.copy(semester = state.semester!!.copy(id = SemesterId("B")))))
        assertNull(resolveManualEditorInitial(target, state.copy(profile = state.profile!!.copy(id = "other-profile"))))
    }

    @Test fun externally_deleted_target_closes_editor() = runBlocking {
        seedManual(item("external"))
        val state = usable(runningViewModel())
        val target = editTarget(state, "external")
        assertNull(resolveManualEditorInitial(target, state.copy(manualItemsById = emptyMap())))
    }

    @Test fun editor_week_context_does_not_change_on_natural_week_tick() = runBlocking {
        seedManual(item("week", WeekPattern.of(7)))
        val state = usable(runningViewModel())
        val target = editTarget(state, "week", week = 5)
        val changedState = state.copy(viewedWeek = 9, naturalWeek = 9)
        val initial = resolveManualEditorInitial(target, changedState) as ManualEditorInitial.Edit
        assertEquals(5, initial.viewedWeek)
    }

    @Test fun dismiss_and_reopen_new_editor_gets_fresh_draft() = runBlocking {
        val state = usable(runningViewModel())
        val target = newTarget(state)
        val first = session(state, target)
        first.editor.update { it.copy(title = "unsaved") }
        val reopened = session(state, target)
        assertEquals("", reopened.editor.draft.value.title)
    }

    @Test fun dismiss_and_reopen_edit_editor_reloads_current_item() = runBlocking {
        seedManual(item("reload", title = "database"))
        val state = usable(runningViewModel())
        val target = editTarget(state, "reload")
        val first = session(state, target)
        first.editor.update { it.copy(title = "unsaved") }
        val reopened = session(state, target)
        assertEquals("database", reopened.editor.draft.value.title)
    }

    @Test fun manual_block_longpress_does_not_open_new_editor() = runBlocking {
        seedManual(item("hold-manual"))
        val state = usable(runningViewModel())
        var emptyPresses = 0
        rule.setContent {
            TimetableScreen(
                state, {}, {}, {},
                onEmptyLongPress = { _, _ -> emptyPresses++ },
            )
        }
        rule.onNodeWithTag("manual_block:hold-manual").performTouchInput { longClick(center) }
        assertEquals(0, emptyPresses)
    }

    @Test fun school_block_longpress_does_not_open_new_editor() = runBlocking {
        seedSchool("hold-school")
        val state = usable(runningViewModel())
        var emptyPresses = 0
        rule.setContent {
            TimetableScreen(
                state, {}, {}, {},
                onEmptyLongPress = { _, _ -> emptyPresses++ },
            )
        }
        rule.onNodeWithTag("school_block:hold-school").performTouchInput { longClick(center) }
        assertEquals(0, emptyPresses)
    }
}
