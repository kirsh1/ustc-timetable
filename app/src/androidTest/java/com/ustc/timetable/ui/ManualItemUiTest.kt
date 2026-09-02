package com.ustc.timetable.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustc.timetable.test.J1TestState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManualItemUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val activity = J1MainActivityHarness(compose)

    @Before fun resetAndSeed() { J1TestState.reset(); J1TestState.seedDebug() }
    @After fun close() { activity.close() }

    @Test
    fun long_press_creates_manual_item_for_viewed_week() {
        launchWeekTwo()
        val schoolBefore = schoolRows()
        compose.onNode(
            hasTestTag("timetable_grid") and hasAnyAncestor(hasTestTag("week_page_2")),
        ).performTouchInput {
            val p = Offset(center.x * 1.75f, center.y)
            down(p); advanceEventTime(1_000); up()
        }
        compose.onNodeWithTag("editor_content").assertIsDisplayed()
        compose.onNodeWithTag("weekday_7").performScrollTo().assertIsDisplayed()
        assertTrue(compose.onNodeWithTag("start_time").fetchSemanticsNode().config.toString().contains("开始时间"))
        replaceText("editor_title", "J1 手动项目")
        saveEditor()
        compose.waitUntil(10_000) { manualRows().any { it.title == "J1 手动项目" } }
        val created = manualRows().single { it.title == "J1 手动项目" }
        assertEquals(7, created.weekday)
        assertTrue(created.weekPatternMask and (1L shl 1) != 0L)
        assertTrue(created.startMinutes in (7 * 60 + 50)..(21 * 60 + 55))
        assertEquals(0, created.startMinutes % 5)
        compose.onNodeWithTag("manual_block:${created.id}").assertIsDisplayed()
        assertEquals(schoolBefore, schoolRows())
    }

    @Test
    fun manual_block_can_be_edited_without_replacing_id() {
        launchWeekTwo()
        val schoolBefore = schoolRows()
        val before = manualRows().single { it.id == "debug-i-lecture" }
        compose.onNodeWithTag("manual_block:${before.id}").performClick()
        replaceText("editor_title", "修改后的讲座")
        saveEditor()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("editor_content").fetchSemanticsNodes().isEmpty() }

        val afterTitle = manualRows().single { it.id == before.id }
        assertEquals("修改后的讲座", afterTitle.title)
        compose.onNodeWithTag("manual_block:${before.id}").performClick()
        replaceText("editor_location", "J1-ROOM")
        saveEditor()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("editor_content").fetchSemanticsNodes().isEmpty() }

        val after = manualRows().single { it.id == before.id }
        assertEquals("修改后的讲座", after.title)
        assertEquals(before.id, after.id)
        assertEquals(before.createdAtEpochMilli, after.createdAtEpochMilli)
        assertTrue(after.updatedAtEpochMilli >= before.updatedAtEpochMilli)
        assertEquals("J1-ROOM", after.location)
        compose.onNodeWithTag("manual_block:${after.id}").assertIsDisplayed()
        assertEquals(schoolBefore, schoolRows())
    }

    @Test
    fun manual_delete_requires_confirmation() {
        launchWeekTwo()
        compose.onNodeWithTag("manual_block:debug-i-lecture").performClick()
        compose.onNodeWithTag("editor_delete").performClick()
        compose.onNodeWithTag("delete_cancel").performClick()
        assertTrue(manualRows().any { it.id == "debug-i-lecture" })
        compose.onNodeWithTag("editor_content").assertIsDisplayed()
    }

    @Test
    fun manual_delete_removes_only_manual_item_and_school_rows_survive() {
        launchWeekTwo()
        val schoolBefore = schoolRows()
        compose.onNodeWithTag("manual_block:debug-i-lecture").performClick()
        compose.onNodeWithTag("editor_delete").performClick()
        compose.onNodeWithTag("delete_confirm").performClick()
        compose.waitUntil(10_000) { manualRows().none { it.id == "debug-i-lecture" } }
        compose.onNodeWithTag("manual_block:debug-i-lecture").assertDoesNotExist()
        assertEquals(schoolBefore, schoolRows())
    }

    @Test
    fun school_rows_survive_manual_crud() {
        val before = schoolRows()
        launchWeekTwo()
        compose.onNodeWithTag("manual_block:debug-i-lecture").performClick()
        replaceText("editor_title", "学校行不变")
        saveEditor()
        compose.waitUntil(10_000) { manualRows().single().title == "学校行不变" }
        assertEquals(before, schoolRows())
    }

    private fun launchWeekTwo() {
        activity.launch()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable_grid").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("next_week").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("manual_block:debug-i-lecture").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun manualRows() = runBlocking {
        J1TestState.app.container.db.manualItemDao().itemsForSemester("debug-seed-2026-autumn")
    }

    private fun replaceText(tag: String, text: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetText) { setText ->
            setText(AnnotatedString(text))
        }
        compose.waitUntil(10_000) {
            compose.onNodeWithTag(tag).fetchSemanticsNode().config.toString().contains(text)
        }
    }

    private fun saveEditor() {
        compose.onNodeWithTag("editor_save")
            .performSemanticsAction(SemanticsActions.OnClick) { onClick -> onClick() }
    }

    private fun schoolRows() = runBlocking {
        val dao = J1TestState.app.container.db.courseDao()
        dao.coursesForSemester("debug-seed-2026-autumn") to dao.meetingsForSemester("debug-seed-2026-autumn")
    }
}
