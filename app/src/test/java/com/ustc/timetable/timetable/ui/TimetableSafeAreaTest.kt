package com.ustc.timetable.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TimetableSafeAreaTest {
    @get:Rule val rule = createComposeRule()

    private fun setSafeRoot(
        top: Dp = 0.dp,
        bottom: Dp = 0.dp,
        left: Dp = 0.dp,
        right: Dp = 0.dp,
    ) {
        rule.setContent {
            val density = LocalDensity.current
            Box(Modifier.requiredSize(400.dp, 800.dp).testTag("test_window")) {
                AppRoot(
                    gate = FirstLaunchGate.Ready,
                    safeDrawingInsets = WindowInsets(
                        left = with(density) { left.roundToPx() },
                        top = with(density) { top.roundToPx() },
                        right = with(density) { right.roundToPx() },
                        bottom = with(density) { bottom.roundToPx() },
                    ),
                    firstLaunchContent = {},
                    timetableContent = {
                        Column(Modifier.fillMaxSize()) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .testTag("timetable_top_header"),
                            )
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("timetable_body"),
                            )
                        }
                    },
                    settingsContent = { _, _ -> Text("settings") },
                    profileEditorContent = { Text("profile") },
                )
            }
        }
    }

    @Test fun top_inset_places_header_below_system_ui() {
        setSafeRoot(top = 40.dp)

        val window = rule.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        val safe = rule.onNodeWithTag("app_safe_content").fetchSemanticsNode().boundsInRoot
        val header = rule.onNodeWithTag("timetable_top_header").fetchSemanticsNode().boundsInRoot

        assertTrue(
            "window=$window safe=$safe header=$header",
            safe.top > window.top && header.top >= safe.top,
        )
    }

    @Test fun larger_caption_inset_moves_header_by_same_delta() {
        var top by mutableStateOf(40.dp)
        var expectedDeltaPx = 0f
        rule.setContent {
            val density = LocalDensity.current
            expectedDeltaPx = with(density) { 48.dp.toPx() }
            Box(Modifier.requiredSize(400.dp, 800.dp)) {
                AppRoot(
                    gate = FirstLaunchGate.Ready,
                    safeDrawingInsets = WindowInsets(top = with(density) { top.roundToPx() }),
                    firstLaunchContent = {},
                    timetableContent = {
                        Box(Modifier.fillMaxSize().testTag("timetable_top_header"))
                    },
                    settingsContent = { _, _ -> },
                    profileEditorContent = {},
                )
            }
        }
        val before = rule.onNodeWithTag("timetable_top_header").fetchSemanticsNode().boundsInRoot.top
        rule.runOnIdle { top = 88.dp }
        val after = rule.onNodeWithTag("timetable_top_header").fetchSemanticsNode().boundsInRoot.top

        assertEquals(expectedDeltaPx, after - before, 1f)
    }

    @Test fun bottom_inset_keeps_timetable_above_navigation_area() {
        setSafeRoot(bottom = 56.dp)

        val window = rule.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        val body = rule.onNodeWithTag("timetable_body").fetchSemanticsNode().boundsInRoot

        assertTrue("window=$window body=$body", body.bottom < window.bottom)
    }

    @Test fun safe_content_remains_inside_horizontal_cutout_insets() {
        setSafeRoot(left = 24.dp, right = 32.dp)

        val window = rule.onNodeWithTag("test_window").fetchSemanticsNode().boundsInRoot
        val safe = rule.onNodeWithTag("app_safe_content").fetchSemanticsNode().boundsInRoot

        assertTrue("window=$window safe=$safe", safe.left > window.left && safe.right < window.right)
    }
}
