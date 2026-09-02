package com.ustc.timetable.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.sync.ManualSyncController
import com.ustc.timetable.sync.ManualSyncRunner
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncResult
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.ui.TimetableScreen
import com.ustc.timetable.timetable.ui.TimetableUiState
import com.ustc.timetable.timetable.ui.TimetableWeekPageUiState
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReauthUiTest {
    @get:Rule val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @After fun close() { scope.cancel() }

    @Test
    fun auth_expired_keeps_grid_visible_and_opens_dialog() {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = ManualSyncController(runner, scope)
        compose.setContent { Harness(controller) }
        compose.onNodeWithTag("refresh").performClick()
        compose.onNodeWithTag("timetable_grid").assertIsDisplayed()
        compose.onNodeWithText("登录状态已失效").assertIsDisplayed()
        compose.onNodeWithText("已有课表不会受到影响").assertIsDisplayed()
        assertEquals(1, runner.calls)
    }

    @Test
    fun relogin_result_ok_retries_exactly_once() {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired), SyncResult.NoChange)
        val controller = ManualSyncController(runner, scope)
        compose.setContent { Harness(controller, onRelogin = controller::onReloginSuccess) }
        compose.onNodeWithTag("refresh").performClick()
        compose.onNodeWithText("重新登录").performClick()
        compose.waitUntil(10_000) { runner.calls == 2 }
        compose.onNodeWithText("登录状态已失效").assertDoesNotExist()
        assertEquals(2, runner.calls)
    }

    @Test
    fun cancel_reauth_does_not_retry() {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = ManualSyncController(runner, scope)
        compose.setContent { Harness(controller) }
        compose.onNodeWithTag("refresh").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("登录状态已失效").assertDoesNotExist()
        compose.onNodeWithTag("timetable_grid").assertIsDisplayed()
        assertEquals(1, runner.calls)
    }

    @Composable
    private fun Harness(
        controller: ManualSyncController,
        onRelogin: () -> Unit = controller::onReloginSuccess,
    ) {
        val syncState by controller.state.collectAsState()
        MaterialTheme {
            TimetableScreen(
                state = state(),
                onPrevWeek = {},
                onNextWeek = {},
                onWeekSelected = {},
                manualSyncAvailable = true,
                manualSyncState = syncState,
                onRefreshClick = controller::start,
                onReloginClick = onRelogin,
                onCancelAuthExpired = controller::onCancelAuthExpired,
            )
        }
    }

    private fun state(): TimetableUiState {
        val semester = SemesterDefaults.AUTUMN_2026("reauth-semester", "reauth-profile", Instant.EPOCH)
            .copy(portalLinked = true)
        val profile = ScheduleProfile(
            id = "reauth-profile",
            name = "J1",
            isBundledOfficial = false,
            periods = (1..13).map { n ->
                val start = LocalTime.of(7, 30).plusMinutes(n * 50L)
                PeriodTime(n, start, start.plusMinutes(45))
            },
        )
        val page = TimetableWeekPageUiState(
            week = 1,
            weekDates = LocalDateRange(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 6)),
            placedSchool = emptyList(),
            placedManual = emptyList(),
            nowLine = null,
        )
        return TimetableUiState(
            semester = semester,
            viewedWeek = 1,
            profile = profile,
            weekPages = listOf(page),
            availableSemesters = listOf(semester),
            canSyncViewed = true,
            isLoading = false,
        )
    }

    private class QueueRunner(vararg results: SyncResult) : ManualSyncRunner {
        private val queue = ArrayDeque(results.toList())
        var calls = 0
        override suspend fun sync(): SyncResult {
            calls++
            return queue.removeFirst()
        }
    }
}
