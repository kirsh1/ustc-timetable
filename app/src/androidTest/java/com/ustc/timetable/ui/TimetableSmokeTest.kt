package com.ustc.timetable.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.test.J1TestState
import com.ustc.timetable.appearance.AppearanceMode
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimetableSmokeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val activity = J1MainActivityHarness(compose)

    @Before fun reset() { J1TestState.reset() }
    @After fun close() { activity.close() }

    @Test
    fun empty_database_manual_fallback_enters_blank_seven_day_timetable() {
        activity.launch()
        compose.onNodeWithTag("first_launch_screen").assertIsDisplayed()
        compose.onNodeWithText("课表数据保存在本机。", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("first_launch_import").assertIsDisplayed()
        compose.onNodeWithTag("first_launch_manual").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable_grid").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithText("周一").assertIsDisplayed()
        compose.onNodeWithText("8-31").assertIsDisplayed()
        compose.onNodeWithText("周六").assertIsDisplayed()
        compose.onNodeWithText("9-05").assertIsDisplayed()
        compose.onNodeWithText("周日").assertIsDisplayed()
        compose.onNodeWithText("9-06").assertIsDisplayed()
        compose.onNodeWithTag("settings").assertIsDisplayed()
        compose.onNodeWithTag("refresh").assertDoesNotExist()
        compose.onNodeWithTag("school_block:debug-m-math-1").assertDoesNotExist()

        val semesters = J1TestState.semesters()
        assertEquals(1, semesters.size)
        assertFalse(semesters.single().portalLinked)
        assertTrue(semesters.single().isCurrentAcademicSemester)
        assertTrue(runBlocking { J1TestState.app.container.db.courseDao().coursesForSemester(semesters.single().id) }.isEmpty())
    }

    @Test
    fun explicitly_seeded_timetable_renders_switches_weeks_and_keeps_ui_r1_geometry() {
        J1TestState.seedDebug()
        activity.launch()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("school_block:debug-m-math-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("school_block:debug-m-math-1").assertIsDisplayed()
        compose.onNodeWithTag("next_week").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("viewed_week").fetchSemanticsNode().config.toString().contains("第 2 周") }
        compose.onNodeWithTag("manual_block:debug-i-lecture").assertIsDisplayed()

        compose.onNodeWithTag("week_overview_toggle").performClick()
        compose.onNodeWithTag("week_overview_strip").assertIsDisplayed()
        compose.onNodeWithTag("week_overview_card:2").assertIsDisplayed()
        val weekTwoCard = compose.onNodeWithTag("week_overview_card:2").getUnclippedBoundsInRoot()
        val weekTwoManualNode = compose.onNodeWithTag("week_overview_block:2:manual:debug-i-lecture", useUnmergedTree = true)
        weekTwoManualNode.assertExists()
        val weekTwoManual = weekTwoManualNode.getUnclippedBoundsInRoot()
        assertTrue(weekTwoManual.right > weekTwoManual.left && weekTwoManual.bottom > weekTwoManual.top)
        assertTrue(weekTwoManual.left >= weekTwoCard.left && weekTwoManual.right <= weekTwoCard.right)
        compose.onNodeWithTag("week_overview_strip").performScrollToNode(hasTestTag("week_overview_card:20"))
        compose.onNodeWithTag("week_overview_card:20").assertIsDisplayed()
        compose.onNodeWithTag("week_overview_card:21").assertDoesNotExist()
        compose.onNodeWithTag("week_overview_strip").performScrollToNode(hasTestTag("week_overview_card:3"))
        compose.onNodeWithTag("week_overview_block:3:manual:debug-i-lecture", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("week_overview_block:3:school:debug-m-chem-1", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("week_overview_card:3").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("viewed_week").fetchSemanticsNode().config.toString().contains("第 3 周") }
        compose.onNodeWithTag("week_overview_strip").assertIsDisplayed()

        val fixedRailBefore = compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("week_pager").performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { compose.onNodeWithTag("viewed_week").fetchSemanticsNode().config.toString().contains("第 4 周") }
        val fixedRailAfter = compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot()
        assertEquals(fixedRailBefore, fixedRailAfter)

        val safe = compose.onNodeWithTag("app_safe_content").getUnclippedBoundsInRoot()
        val header = compose.onNodeWithTag("timetable_top_bar").getUnclippedBoundsInRoot()
        val currentPage = hasAnyAncestor(hasTestTag("week_page_4"))
        val body = compose.onNode(hasTestTag("timetable_body") and currentPage).getUnclippedBoundsInRoot()
        val grid = compose.onNode(hasTestTag("timetable_grid") and currentPage).getUnclippedBoundsInRoot()
        assertTrue(header.top >= safe.top)
        assertTrue(body.bottom <= safe.bottom + 1.dp)
        val gridWidth = grid.right - grid.left
        val gridHeight = grid.bottom - grid.top
        assertTrue(gridWidth > 7.dp && gridHeight > 0.dp)
        val time0945 = compose.onNodeWithTag("time_boundary:09:45").getUnclippedBoundsInRoot()
        val time2155 = compose.onNodeWithTag("time_boundary:21:55").getUnclippedBoundsInRoot()
        val y0945 = (time0945.top + time0945.bottom) / 2f
        val y2155 = (time2155.top + time2155.bottom) / 2f
        assertTrue(y0945 in grid.top..grid.bottom)
        assertTrue(y2155 <= grid.bottom + 1.dp)
        assertTrue(gridWidth / 7f > 0.dp)
    }

    @Test
    fun semester_switch_changes_viewed_only() {
        val (a, b) = J1TestState.seedTwoSemesters()
        val before = J1TestState.semesters()
        grantNotificationPermissionIfNeeded()
        activity.launch()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("viewed_semester").assertIsDisplayed()
        compose.onNodeWithTag("viewed_semester").performClick()
        compose.onNodeWithTag("semester_item:${b.id.value}").performClick()
        compose.waitUntil(10_000) { J1TestState.viewedSemesterId() == b.id.value }
        compose.waitUntil(10_000) { compose.onNodeWithTag("viewed_semester").fetchSemanticsNode().config.toString().contains("J1 学期 B") }
        compose.onNodeWithTag("viewed_semester").assertIsDisplayed()
        assertEquals(before, J1TestState.semesters())
        assertTrue(J1TestState.semesters().single { it.id == a.id.value }.isCurrentAcademicSemester)
    }

    @Test
    fun settings_and_profile_editor_navigation_smoke() {
        J1TestState.seedDebug()
        grantNotificationPermissionIfNeeded()
        activity.launch()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings_list").assertIsDisplayed()
        compose.onNodeWithTag("working_profile").performClick()
        compose.onNodeWithTag("profile_period_list").assertIsDisplayed()
        compose.onNodeWithTag("period_1_start").assertIsDisplayed()
        compose.onNodeWithTag("profile_editor_back").performClick()
        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("timetable_grid").assertIsDisplayed()
    }

    @Test
    fun appearance_mode_changes_persist_from_settings() {
        J1TestState.seedDebug()
        grantNotificationPermissionIfNeeded()
        activity.launch()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("appearance_theme"))
        compose.onNodeWithTag("appearance_theme").performClick()
        compose.onNodeWithTag("appearance_option:DARK").performClick()
        compose.waitUntil(10_000) { J1TestState.appearanceMode() == AppearanceMode.DARK }
        compose.onNodeWithTag("settings_list").assertIsDisplayed()
    }

    private fun grantNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(
                instrumentation.targetContext.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
    }
}
