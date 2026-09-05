package com.ustc.timetable.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.sync.ManualSyncState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun seed_source_is_called_only_by_explicit_new_candidate() {
        var samples = 0
        val applied = mutableListOf<Long>()
        rule.setContent {
            SettingsScreen(state = state().copy(coursePaletteSeed = 5L),
                callbacks = SettingsCallbacks(onApplyCoursePaletteSeed = { applied += it }),
                paletteSeedSource = PaletteSeedSource { samples++; 7L })
        }
        rule.onNodeWithTag("course_palette").performClick()
        rule.onAllNodesWithTag("palette_color", useUnmergedTree = true).assertCountEquals(12)
        assertEquals(0, samples)
        rule.onNodeWithTag("palette_new_candidate").performClick()
        assertEquals(1, samples)
        assertEquals(emptyList<Long>(), applied)
        rule.onNodeWithTag("palette_restore_default").performClick()
        assertEquals(1, samples)
        assertEquals(emptyList<Long>(), applied)
        rule.onNodeWithTag("palette_apply").performClick()
        assertEquals(listOf(0L), applied)
        assertEquals(1, samples)
    }

    @Test fun palette_cancel_discards_candidate_and_reopening_uses_persisted_seed() {
        val applied = mutableListOf<Long>()
        rule.setContent { SettingsScreen(state().copy(coursePaletteSeed = 5L), SettingsCallbacks(onApplyCoursePaletteSeed = { applied += it }), paletteSeedSource = PaletteSeedSource { 7L }) }
        rule.onNodeWithTag("course_palette").performClick()
        rule.onNodeWithTag("palette_new_candidate").performClick()
        rule.onNodeWithTag("palette_cancel").performClick()
        assertEquals(emptyList<Long>(), applied)
        rule.onNodeWithTag("course_palette").performClick()
        rule.onNodeWithTag("palette_apply").performClick()
        assertEquals(listOf(5L), applied)
    }

    @Test fun all_required_settings_entries_present() {
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks()) }
        listOf("课表", "当前查看学期", "2026-2027 秋季", "学校作息时间", "显示非当前周课程", "同步", "上次同步时间", "每周静默同步", "立即同步", "学校账户", "登录状态", "登录并导入", "清除登录状态", "关于", "数据与版本", "App 版本").forEach {
            rule.onNodeWithTag("settings_list").performScrollToNode(hasText(it))
            rule.onAllNodesWithText(it)[0].assertExists()
        }
    }

    @Test fun portal_linked_current_semester_keeps_relogin_action() {
        rule.setContent {
            SettingsScreen(
                state = state().copy(hasPortalLinkedCurrentSemester = true),
                callbacks = SettingsCallbacks(),
            )
        }
        rule.onNodeWithTag("settings_list").performScrollToNode(hasText("重新登录"))
        rule.onAllNodesWithText("重新登录").assertCountEquals(1)
        rule.onAllNodesWithText("登录并导入").assertCountEquals(0)
    }

    @Test fun portal_linked_current_semester_reports_school_data_source() {
        rule.setContent {
            SettingsScreen(
                state = state().copy(hasPortalLinkedCurrentSemester = true),
                callbacks = SettingsCallbacks(),
            )
        }
        rule.onNodeWithTag("settings_list").performScrollToNode(hasText("学校课表数据"))
        rule.onAllNodesWithText("学校课表数据").assertCountEquals(1)
        rule.onAllNodesWithText("本地课表数据").assertCountEquals(0)
    }

    @Test fun appearance_group_exposes_theme_and_wallpaper_rows() {
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks()) }
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_group:appearance"))
        rule.onNodeWithTag("appearance_theme").assertExists()
        rule.onNodeWithTag("timetable_wallpaper").assertExists()
        rule.onAllNodesWithText("浅色").assertCountEquals(1)
        rule.onAllNodesWithText("未设置").assertCountEquals(1)
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
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("working_profile"))
        rule.onNodeWithTag("working_profile").performClick(); rule.waitForIdle(); assertEquals(1, calls)
    }

    @Test fun semester_row_opens_existing_switcher_boundary() {
        var calls = 0
        rule.setContent {
            SettingsScreen(
                state = state(),
                callbacks = SettingsCallbacks(onOpenSemesterSwitcher = { calls++ }),
            )
        }
        rule.onNodeWithTag("viewed_semester").performClick()
        rule.waitForIdle()
        assertEquals(1, calls)
    }

    @Test fun back_and_sync_use_vector_icon_semantics() {
        rule.setContent {
            SettingsScreen(
                state = state().copy(syncNowEnabled = true),
                callbacks = SettingsCallbacks(),
            )
        }
        rule.onNodeWithContentDescription("返回").assertExists()
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("sync_action"))
        rule.onNodeWithContentDescription("立即同步").assertExists()
        rule.onAllNodesWithText("‹").assertCountEquals(0)
        rule.onAllNodesWithText("↻").assertCountEquals(0)
    }

    @Test fun syncing_state_replaces_refresh_action_with_progress() {
        rule.setContent {
            SettingsScreen(
                state = state().copy(syncNowEnabled = true),
                callbacks = SettingsCallbacks(),
                manualSyncState = ManualSyncState.Syncing,
            )
        }
        rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("sync_progress"))
        rule.onNodeWithTag("sync_progress").assertExists()
        rule.onAllNodesWithTag("sync_action").assertCountEquals(0)
    }

    @Test fun settings_sections_use_compact_group_cards() {
        rule.setContent { SettingsScreen(state = state(), callbacks = SettingsCallbacks()) }
        listOf("timetable", "sync", "account", "about").forEach {
            rule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_group:$it"))
            rule.onNodeWithTag("settings_group:$it").assertExists()
        }
    }

    private fun state() = SettingsUiState(
        lastSyncText = "—",
        loginText = "未登录",
        appVersion = "1.0-test",
        availableSemesters = listOf(SemesterDefaults.AUTUMN_2026(id = "semester", profileId = "profile")),
        viewedSemesterId = SemesterId("semester"),
    )
}
