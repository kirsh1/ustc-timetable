package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppRootFirstLaunchTest {
    @get:Rule val rule = createComposeRule()

    @Test fun first_launch_to_timetable_transition_preserves_single_root() {
        var gate by mutableStateOf<FirstLaunchGate>(FirstLaunchGate.Empty)
        rule.setContent { root(gate) }
        rule.onAllNodesWithText("FIRST").assertCountEquals(1)
        rule.runOnIdle { gate = FirstLaunchGate.Ready }
        rule.onAllNodesWithText("FIRST").assertCountEquals(0)
        rule.onAllNodesWithText("TIMETABLE").assertCountEquals(1)
    }

    @Test fun existing_settings_navigation_regression_remains_green() {
        rule.setContent { root(FirstLaunchGate.Ready) }
        rule.onAllNodesWithText("TIMETABLE")[0].performClick()
        rule.onAllNodesWithText("SETTINGS").assertCountEquals(1)
        rule.onAllNodesWithText("SETTINGS")[0].performClick()
        rule.onAllNodesWithText("PROFILE").assertCountEquals(1)
    }

    @Composable
    private fun root(gate: FirstLaunchGate) = AppRoot(
        gate = gate,
        firstLaunchContent = { Text("FIRST") },
        timetableContent = { openSettings -> Text("TIMETABLE", modifier = androidx.compose.ui.Modifier.clickable(onClick = openSettings)) },
        settingsContent = { _, openProfile -> Text("SETTINGS", modifier = androidx.compose.ui.Modifier.clickable(onClick = openProfile)) },
        profileEditorContent = { _ -> Text("PROFILE") },
    )
}
