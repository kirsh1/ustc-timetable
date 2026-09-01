package com.ustc.timetable.timetable.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FirstLaunchScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun first_launch_contains_required_copy() {
        rule.setContent { FirstLaunchScreen(FirstLaunchUiState(gate = FirstLaunchGate.Empty), {}, {}) }
        listOf(
            "课表",
            "从学校教务系统导入课表",
            "登录仅用于读取你的课表。\nApp 不保存学校账户密码，\n课表数据保存在本机。",
        ).forEach {
            rule.onAllNodesWithText(it).assertCountEquals(1)
        }
    }

    @Test fun first_launch_contains_exactly_two_primary_actions() {
        rule.setContent { FirstLaunchScreen(FirstLaunchUiState(gate = FirstLaunchGate.Empty), {}, {}) }
        rule.onAllNodes(hasClickAction()).assertCountEquals(2)
        rule.onAllNodesWithTag("first_launch_import").assertCountEquals(1)
        rule.onAllNodesWithTag("first_launch_manual").assertCountEquals(1)
    }

    @Test fun first_launch_has_no_password_input_or_dashboard_navigation() {
        rule.setContent { FirstLaunchScreen(FirstLaunchUiState(gate = FirstLaunchGate.Empty), {}, {}) }
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        rule.onAllNodesWithText("Dashboard", substring = true).assertCountEquals(0)
        rule.onAllNodesWithTag("bottom_navigation").assertCountEquals(0)
    }

    @Test fun creating_state_disables_both_actions_and_shows_manual_progress() {
        rule.setContent { FirstLaunchScreen(FirstLaunchUiState(FirstLaunchGate.Empty, isCreatingManualSemester = true), {}, {}) }
        rule.onNodeWithTag("first_launch_import").assertIsNotEnabled()
        rule.onNodeWithTag("first_launch_manual").assertIsNotEnabled()
        rule.onAllNodesWithTag("first_launch_manual_progress").assertCountEquals(1)
    }

    @Test fun actions_forward_once() {
        var imports = 0; var manual = 0
        rule.setContent { FirstLaunchScreen(FirstLaunchUiState(FirstLaunchGate.Empty), { imports++ }, { manual++ }) }
        rule.onNodeWithTag("first_launch_import").performClick()
        rule.onNodeWithTag("first_launch_manual").performClick()
        rule.waitForIdle()
        assertEquals(1, imports); assertEquals(1, manual)
    }
}
