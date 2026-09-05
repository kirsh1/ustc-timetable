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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustc.timetable.sync.ManualSyncController
import com.ustc.timetable.sync.ManualSyncRunner
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncResult
import com.ustc.timetable.settings.SettingsCallbacks
import com.ustc.timetable.settings.SettingsScreen
import com.ustc.timetable.settings.SettingsUiState
import com.ustc.timetable.timetable.ui.AuthExpiredDialog
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
    fun auth_expired_keeps_settings_visible_and_opens_dialog() {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = ManualSyncController(runner, scope)
        compose.setContent { Harness(controller) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("sync_action"))
        compose.onNodeWithTag("sync_action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings_list").assertIsDisplayed()
        compose.onNodeWithText("登录状态已失效").assertIsDisplayed()
        compose.onNodeWithText("已有课表不会受到影响").assertIsDisplayed()
        assertEquals(1, runner.calls)
    }

    @Test
    fun relogin_result_ok_retries_exactly_once() {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired), SyncResult.NoChange)
        val controller = ManualSyncController(runner, scope)
        compose.setContent { Harness(controller, onRelogin = controller::onReloginSuccess) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("sync_action"))
        compose.onNodeWithTag("sync_action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("auth_expired_relogin").performClick()
        compose.waitUntil(10_000) { runner.calls == 2 }
        compose.onNodeWithText("登录状态已失效").assertDoesNotExist()
        assertEquals(2, runner.calls)
    }

    @Test
    fun cancel_reauth_does_not_retry() {
        val runner = QueueRunner(SyncResult.Failed(SyncError.AuthenticationExpired))
        val controller = ManualSyncController(runner, scope)
        compose.setContent { Harness(controller) }
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("sync_action"))
        compose.onNodeWithTag("sync_action").assertIsDisplayed().performClick()
        compose.onNodeWithTag("auth_expired_cancel").performClick()
        compose.onNodeWithText("登录状态已失效").assertDoesNotExist()
        compose.onNodeWithTag("settings_list").assertIsDisplayed()
        assertEquals(1, runner.calls)
    }

    @Composable
    private fun Harness(
        controller: ManualSyncController,
        onRelogin: () -> Unit = controller::onReloginSuccess,
    ) {
        val syncState by controller.state.collectAsState()
        MaterialTheme {
            SettingsScreen(
                state = SettingsUiState(syncNowEnabled = true),
                callbacks = SettingsCallbacks(onSyncNow = controller::start),
                manualSyncState = syncState,
            )
            if (syncState == com.ustc.timetable.sync.ManualSyncState.AwaitingReauth) {
                AuthExpiredDialog(
                    onCancel = controller::onCancelAuthExpired,
                    onRelogin = onRelogin,
                )
            }
        }
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
