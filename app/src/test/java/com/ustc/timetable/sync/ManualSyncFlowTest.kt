package com.ustc.timetable.sync

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.cash.turbine.test
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.ScheduleChange
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.ui.AuthExpiredDialog
import com.ustc.timetable.timetable.ui.TimetableScreen
import com.ustc.timetable.timetable.ui.TimetableUiState
import com.ustc.timetable.timetable.ui.TimetableWeekPageUiState
import com.ustc.timetable.timetable.ui.handleManualSyncLoginResult
import com.ustc.timetable.timetable.ui.manualSyncFailureMessage
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ManualSyncFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun no_change_is_silent() = runTest {
        val controller = controller(QueueRunner(SyncResult.NoChange), this)
        controller.events.test {
            controller.start()
            advanceUntilIdle()
            assertEquals(ManualSyncState.Idle, controller.state.value)
            expectNoEvents()
        }
    }

    @Test fun success_with_changes_emits_count() = runTest {
        val result = SyncResult.Success(listOf(ScheduleChange.CourseAdded("A"), ScheduleChange.CourseRemoved("B")))
        val controller = controller(QueueRunner(result), this)
        controller.events.test {
            controller.start()
            assertEquals(ManualSyncEvent.Updated(2), awaitItem())
            assertEquals(ManualSyncState.Idle, controller.state.value)
        }
    }

    @Test fun success_empty_changes_is_silent() = runTest {
        val controller = controller(QueueRunner(SyncResult.Success(emptyList())), this)
        controller.events.test {
            controller.start()
            advanceUntilIdle()
            assertEquals(ManualSyncState.Idle, controller.state.value)
            expectNoEvents()
        }
    }

    @Test fun network_failure_emits_safe_failure() = runTest {
        assertFailureEvent(SyncError.NetworkFailed)
        assertEquals("同步失败，请检查网络后重试", manualSyncFailureMessage(SyncError.NetworkFailed))
    }

    @Test fun parse_failure_emits_safe_failure() = runTest {
        assertFailureEvent(SyncError.ParseFailed)
        assertEquals("无法解析学校课表，已保留本地课表", manualSyncFailureMessage(SyncError.ParseFailed))
    }

    @Test fun validation_failure_emits_safe_failure() = runTest {
        assertFailureEvent(SyncError.ValidationFailed)
        assertEquals("学校课表数据校验失败，已保留本地课表", manualSyncFailureMessage(SyncError.ValidationFailed))
    }

    @Test fun auth_expired_enters_dialog() = runTest {
        val controller = controller(QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired)), this)

        controller.start()
        advanceUntilIdle()

        assertEquals(ManualSyncState.AwaitingReauth, controller.state.value)
    }

    @Test fun cancel_dialog_does_not_retry() = runTest {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = controller(runner, this)
        controller.start()
        advanceUntilIdle()

        controller.onCancelAuthExpired()
        advanceUntilIdle()

        assertEquals(ManualSyncState.Idle, controller.state.value)
        assertEquals(1, runner.calls)
    }

    @Test fun canceled_webview_keeps_dialog() = runTest {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = controller(runner, this)
        controller.start()
        advanceUntilIdle()

        handleManualSyncLoginResult(controller, successful = false)
        advanceUntilIdle()

        assertEquals(ManualSyncState.AwaitingReauth, controller.state.value)
        assertEquals(1, runner.calls)
    }

    @Test fun relogin_success_retries_once() = runTest {
        val runner = QueueRunner(
            SyncResult.Failed(SyncError.AuthenticationExpired),
            SyncResult.Success(listOf(ScheduleChange.CourseAdded("A"))),
        )
        val controller = controller(runner, this)
        controller.start()
        advanceUntilIdle()

        handleManualSyncLoginResult(controller, successful = true)
        advanceUntilIdle()

        assertEquals(2, runner.calls)
        assertEquals(ManualSyncState.Idle, controller.state.value)
    }

    @Test fun duplicate_result_ok_does_not_retry_twice() = runTest {
        val retryGate = CompletableDeferred<SyncResult>()
        var calls = 0
        val runner = ManualSyncRunner {
            when (++calls) {
                1 -> SyncResult.Failed(SyncError.AuthenticationExpired)
                else -> retryGate.await()
            }
        }
        val controller = controller(runner, this)
        controller.start()
        advanceUntilIdle()

        controller.onReloginSuccess()
        runCurrent()
        controller.onReloginSuccess()

        assertEquals(2, calls)
        retryGate.complete(SyncResult.NoChange)
        advanceUntilIdle()
    }

    @Test fun second_auth_expired_returns_to_dialog() = runTest {
        val runner = QueueRunner(
            SyncResult.Failed(SyncError.AuthenticationExpired),
            SyncResult.Failed(SyncError.AuthenticationExpired),
        )
        val controller = controller(runner, this)
        controller.start()
        advanceUntilIdle()

        controller.onReloginSuccess()
        advanceUntilIdle()

        assertEquals(2, runner.calls)
        assertEquals(ManualSyncState.AwaitingReauth, controller.state.value)
    }

    @Test fun double_tap_runs_once() = runTest {
        val gate = CompletableDeferred<SyncResult>()
        var calls = 0
        val controller = controller(ManualSyncRunner { calls++; gate.await() }, this)

        controller.start()
        controller.start()
        runCurrent()

        assertEquals(1, calls)
        gate.complete(SyncResult.NoChange)
        advanceUntilIdle()
    }

    @Test fun start_while_awaiting_reauth_is_ignored() = runTest {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = controller(runner, this)
        controller.start()
        advanceUntilIdle()

        controller.start()
        advanceUntilIdle()

        assertEquals(1, runner.calls)
        assertEquals(ManualSyncState.AwaitingReauth, controller.state.value)
    }

    @Test fun auth_expired_dialog_has_frozen_copy_and_actions() {
        var canceled = 0
        var relogin = 0
        compose.setContent {
            AuthExpiredDialog(onCancel = { canceled++ }, onRelogin = { relogin++ })
        }

        compose.onAllNodesWithText("登录状态已失效").assertCountEquals(1)
        compose.onAllNodesWithText("已有课表不会受到影响").assertCountEquals(1)
        compose.onNodeWithText("重新登录").performClick()
        compose.waitForIdle()
        assertEquals(0, canceled)
        assertEquals(1, relogin)
    }

    @Test fun refresh_visibility_current_portal_linked() {
        compose.setContent { screen(canSyncViewed = true, available = true) }
        compose.onNodeWithTag("refresh").assertIsEnabled()
    }

    @Test fun refresh_hidden_history() {
        compose.setContent { screen(canSyncViewed = false, available = true) }
        compose.onAllNodesWithTag("refresh").assertCountEquals(0)
    }

    @Test fun refresh_hidden_manual_only() {
        compose.setContent { screen(canSyncViewed = false, available = true) }
        compose.onAllNodesWithTag("refresh").assertCountEquals(0)
    }

    @Test fun refresh_hidden_without_runtime_dependency() {
        compose.setContent { screen(canSyncViewed = true, available = false) }
        compose.onAllNodesWithTag("refresh").assertCountEquals(0)
    }

    @Test fun refresh_disabled_while_syncing() {
        var clicks = 0
        compose.setContent {
            screen(
                canSyncViewed = true,
                available = true,
                manualState = ManualSyncState.Syncing,
                onRefresh = { clicks++ },
            )
        }

        compose.onNodeWithTag("refresh").assertIsNotEnabled()
        compose.onAllNodesWithTag("refresh_progress").assertCountEquals(1)
        assertEquals(0, clicks)
    }

    @Test fun no_timetable_loading_regression_and_existing_grid_remains_rendered() {
        val state = timetableState(canSyncViewed = true)
        compose.setContent {
            TimetableScreen(
                state = state,
                onPrevWeek = {},
                onNextWeek = {},
                onWeekSelected = {},
                manualSyncAvailable = true,
                manualSyncState = ManualSyncState.Syncing,
            )
        }

        assertEquals(false, state.isLoading)
        compose.onAllNodesWithTag("timetable_grid").assertCountEquals(1)
        compose.onAllNodesWithTag("refresh_progress").assertCountEquals(1)
    }

    @Test fun auth_expired_keeps_grid_state() {
        compose.setContent {
            TimetableScreen(
                state = timetableState(canSyncViewed = true),
                onPrevWeek = {}, onNextWeek = {}, onWeekSelected = {},
                manualSyncAvailable = true,
                manualSyncState = ManualSyncState.AwaitingReauth,
            )
        }

        compose.onAllNodesWithTag("timetable_grid").assertCountEquals(1)
        compose.onAllNodesWithText("登录状态已失效").assertCountEquals(1)
    }

    @Test fun clicking_refresh_does_not_mutate_viewed_or_academic_current() {
        val state = timetableState(canSyncViewed = true)
        val semesterBefore = state.semester
        var clicks = 0
        compose.setContent { screen(state, available = true, onRefresh = { clicks++ }) }

        compose.onNodeWithTag("refresh").performClick()
        compose.waitForIdle()

        assertEquals(1, clicks)
        assertSame(semesterBefore, state.semester)
        assertEquals(true, state.semester!!.isCurrentAcademicSemester)
    }

    @Test fun no_settings_needReauth_mutation_or_dependency() {
        val constructorTypes = ManualSyncController::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }

        assertEquals(false, constructorTypes.any { it.contains("SettingsStore") || it.contains("needReauth") })
    }

    private suspend fun kotlinx.coroutines.test.TestScope.assertFailureEvent(error: SyncError) {
        val controller = controller(QueueRunner(SyncResult.Failed(error)), this)
        controller.events.test {
            controller.start()
            assertEquals(ManualSyncEvent.FailedOther(error), awaitItem())
            assertEquals(ManualSyncState.Idle, controller.state.value)
        }
    }

    private fun controller(runner: ManualSyncRunner, scope: kotlinx.coroutines.CoroutineScope) =
        ManualSyncController(runner, scope)

    private class QueueRunner(vararg results: SyncResult) : ManualSyncRunner {
        private val queue = ArrayDeque(results.toList())
        var calls = 0
        override suspend fun sync(): SyncResult {
            calls++
            return queue.removeFirst()
        }
    }

    @Composable
    private fun screen(
        state: TimetableUiState = timetableState(canSyncViewed = true),
        canSyncViewed: Boolean = state.canSyncViewed,
        available: Boolean,
        manualState: ManualSyncState = ManualSyncState.Idle,
        onRefresh: () -> Unit = {},
    ) {
        TimetableScreen(
            state = if (state.canSyncViewed == canSyncViewed) state else state.copy(canSyncViewed = canSyncViewed),
            onPrevWeek = {},
            onNextWeek = {},
            onWeekSelected = {},
            manualSyncAvailable = available,
            manualSyncState = manualState,
            onRefreshClick = onRefresh,
        )
    }

    private fun timetableState(canSyncViewed: Boolean): TimetableUiState {
        val profile = ScheduleProfile(
            "profile", "profile", false,
            (1..13).map { period ->
                PeriodTime(period, LocalTime.of(6 + period, 0), LocalTime.of(6 + period, 45))
            },
        )
        val semester = SemesterDefaults.AUTUMN_2026("semester", "profile").copy(portalLinked = canSyncViewed)
        val pages = (1..semester.totalWeeks).map { week ->
            val start = semester.week1Start.plusWeeks((week - 1).toLong())
            TimetableWeekPageUiState(week, LocalDateRange(start, start.plusDays(6)), emptyList(), emptyList(), null)
        }
        return TimetableUiState(
            semester = semester,
            viewedWeek = 1,
            profile = profile,
            weekPages = pages,
            today = LocalDate.of(2026, 8, 31),
            isAcademicCurrentViewed = true,
            canSyncViewed = canSyncViewed,
            isLoading = false,
        )
    }
}
