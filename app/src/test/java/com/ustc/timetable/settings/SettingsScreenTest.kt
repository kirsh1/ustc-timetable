package com.ustc.timetable.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun all_required_settings_entries_present() {
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks()) }
        listOf("课表", "学校作息时间", "显示非当前周课程", "同步", "上次同步时间", "每周静默同步", "立即同步", "学校账户", "登录状态", "重新登录", "清除登录状态", "关于", "数据与版本", "App 版本").forEach {
            rule.onNodeWithTag("settings_list").performScrollToNode(hasText(it))
            rule.onAllNodesWithText(it)[0].assertExists()
        }
    }

    @Test fun forbidden_settings_entries_absent() {
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks()) }
        listOf("一周第一天", "周末", "日视图", "周视图", "五日", "七日").forEach { rule.onAllNodesWithText(it, substring = true).assertCountEquals(0) }
    }

    @Test fun denied_hint_shown_after_request() {
        rule.setContent { SettingsScreen(state = state().copy(showNotificationDeniedHint = true), callbacks = SettingsCallbacks()) }
        rule.onNodeWithTag("settings_list").performScrollToNode(hasText("通知权限未授予，课表变化将不会提醒"))
        rule.onAllNodesWithText("通知权限未授予，课表变化将不会提醒")[0].assertExists()
    }

    @Test fun settings_back_returns_timetable() {
        var calls = 0
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks(onBack = { calls++ })) }
        rule.onNodeWithTag("settings_back").performClick(); rule.waitForIdle(); assertEquals(1, calls)
    }

    @Test fun profile_row_opens_editor() {
        var calls = 0
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks(onOpenProfile = { calls++ })) }
        rule.onNodeWithTag("working_profile").performClick(); rule.waitForIdle(); assertEquals(1, calls)
    }

    private fun state() = SettingsUiState(lastSyncText = "—", loginText = "未登录", appVersion = "1.0-test")
}
