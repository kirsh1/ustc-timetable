package com.ustc.timetable.ui

import android.Manifest
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.appearance.AppearanceMode
import com.ustc.timetable.test.ConnectedTestState
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.domain.*
import com.ustc.timetable.timetable.ui.CoursePalette
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppearanceAndLayoutConnectedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val activity = MainActivityTestHarness(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val settings get() = ConnectedTestState.app.container.settings
    private val wallpaper = "content://com.ustc.timetable.test.wallpaper/wallpaper.png"

    @Before fun seed() = runBlocking {
        ConnectedTestState.reset()
        settings.setCoursePaletteSeed(0L)
        settings.setWallpaperVisibilityPercent(65)
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
        val c = ConnectedTestState.app.container
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val semester = c.semesters.createLocalSemester(
            SemesterDefaults.AUTUMN_2026("appearance-test", "clone", Instant.now()).copy(
                displayName = "界面测试学期", week1Start = monday, startDate = monday,
                endDate = monday.plusWeeks(20).minusDays(1)), c.bundledOfficial)
        val courses = listOf(
            Course(CourseId("a"), semester.id, "key:a", "A", "电化学研究方法", 3.0, null),
            Course(CourseId("b"), semester.id, "key:b", "B", "应用物理化学", 3.0, null),
            Course(CourseId("c"), semester.id, "key:c", "C", "高等无机化学", 3.0, null))
        val meetings = listOf(
            CourseMeeting(MeetingId("a1"), CourseId("a"), 2, 3, 5, WeekPattern.of(1), "TH-A301-ROOM", listOf("教师甲", "教师乙")),
            CourseMeeting(MeetingId("b2"), CourseId("b"), 2, 3, 5, WeekPattern.of(2), "TH-B301", listOf("教师丙")),
            CourseMeeting(MeetingId("b3"), CourseId("b"), 2, 3, 5, WeekPattern.of(3), "TH-B301", listOf("教师丁")),
            CourseMeeting(MeetingId("c1"), CourseId("c"), 5, 3, 5, WeekPattern.of(1), "TH-B301", listOf("教师甲")),
            CourseMeeting(MeetingId("c2"), CourseId("c"), 5, 3, 5, WeekPattern.of(2), "TH-A301", listOf("教师乙")))
        c.db.applySchoolSnapshot(semester.id, courses, meetings, "appearance-test", Instant.now())
        c.manual.add(ManualScheduleItem(ManualItemId("appearance-manual"), semester.id, "本地讲座", 6,
            LocalTime.of(14, 20), LocalTime.of(16, 0), WeekPattern.range(1, 20), "3C107", null, Instant.now(), Instant.now()))
        settings.setViewedSemesterId(semester.id.value)
        settings.setShowNonCurrentWeek(true)
        Unit
    }

    @After fun close() {
        activity.close()
        runBlocking { settings.setCoursePaletteSeed(0L); settings.setWallpaperVisibilityPercent(65) }
    }

    private fun launch() {
        activity.launch()
        awaitTag("school_block:a1")
    }

    private fun awaitTag(tag: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun grid(week: Int = 1) = compose.onNode(hasTestTag("timetable_grid") and hasAnyAncestor(hasTestTag("week_page_$week")))

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("uiR4Screenshots") != "true") return
        compose.waitForIdle()
        // Compose semantics can settle before the window compositor presents that frame.
        // Wait for window-event quiescence before taking the device-side screenshot.
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        shell("screencap -p /sdcard/Pictures/appearance-$name.png")
    }

    @Test fun gesture_arbitration_and_card_long_press_preserve_manual_editor_authority() {
        launch()
        grid().performTouchInput { down(center); moveBy(Offset(0f, 100f)); up() }
        compose.onNodeWithTag("week_overview_strip").assertIsDisplayed()
        grid().performTouchInput { down(center); moveBy(Offset(0f, -100f)); up() }
        compose.onNodeWithTag("week_overview_strip").assertDoesNotExist()
        compose.onNodeWithTag("school_block:a1").performTouchInput { down(center); advanceEventTime(1000); up() }
        compose.onNodeWithTag("course_detail_pager").assertDoesNotExist()
        compose.onNodeWithTag("editor_content").assertDoesNotExist()
        grid().performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { compose.onNodeWithTag("viewed_week").fetchSemanticsNode().config.toString().contains("第 2 周") }
        compose.onNodeWithTag("week_overview_strip").assertDoesNotExist()
        grid(2).performTouchInput { down(Offset(width * .94f, height * .6f)); advanceEventTime(1000); up() }
        compose.onNodeWithTag("editor_content").assertIsDisplayed()
    }

    @Test fun different_course_carousel_starts_current_and_deduplicates_course_identity() {
        launch()
        compose.onNodeWithTag("overlap_marker:CROSS_WEEK", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("overlap_detail_indicator").assertTextEquals("1 / 2")
        compose.onNode(hasText("电化学研究方法") and hasAnyAncestor(hasTestTag("course_detail"))).assertIsDisplayed()
        capture("different-course-page-1")
        compose.onNodeWithTag("overlap_detail_next").performClick()
        compose.onNodeWithTag("overlap_detail_indicator").assertTextEquals("2 / 2")
        compose.onNode(hasText("应用物理化学") and hasAnyAncestor(hasTestTag("course_detail"))).assertIsDisplayed()
        capture("different-course-page-2")
    }

    @Test fun long_room_wraps_and_does_not_intersect_marker() {
        launch()
        val room = compose.onNode(hasText("TH-A301-ROOM") and hasAnyAncestor(hasTestTag("school_block:a1")), useUnmergedTree = true)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        room.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount >= 2)
        assertTrue((0 until layouts.single().lineCount).none { layouts.single().isLineEllipsized(it) })
        val textBounds = room.getUnclippedBoundsInRoot()
        val markerBounds = compose.onNodeWithTag("overlap_marker:CROSS_WEEK", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(textBounds.bottom <= markerBounds.top)
    }

    @Test fun same_course_variants_are_single_page_and_manual_has_no_school_marker() {
        launch()
        compose.onNode(hasTestTag("overlap_marker:CROSS_WEEK") and hasAnyAncestor(hasTestTag("manual_block:appearance-manual")), useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("overlap_marker:VARIANT", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("course_detail").assertIsDisplayed()
        compose.onNodeWithTag("overlap_detail_next").assertDoesNotExist()
        compose.onNodeWithText("完整安排").assertIsDisplayed()
        capture("same-course-single-page")
    }

    private fun openPalette() {
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("course_palette"))
        compose.onNodeWithTag("course_palette").performClick()
    }

    @Test fun palette_candidate_cancel_default_and_apply_persist_only_explicitly() {
        launch()
        openPalette()
        compose.onNodeWithTag("palette_new_candidate").performClick()
        assertEquals(0L, runBlocking { settings.coursePaletteSeed.first() })
        capture("palette-candidate-sheet")
        compose.onNodeWithTag("palette_cancel").performClick()
        assertEquals(0L, runBlocking { settings.coursePaletteSeed.first() })
        compose.onNodeWithTag("course_palette").performClick()
        compose.onNodeWithTag("palette_new_candidate").performClick()
        compose.onNodeWithTag("palette_apply").performClick()
        compose.waitUntil(10_000) { runBlocking { settings.coursePaletteSeed.first() } != 0L }
        val applied = runBlocking { settings.coursePaletteSeed.first() }
        activity.recreate()
        assertEquals(applied, runBlocking { settings.coursePaletteSeed.first() })
        awaitTag("settings")
        openPalette()
        compose.onNodeWithTag("palette_restore_default").performClick()
        assertEquals(applied, runBlocking { settings.coursePaletteSeed.first() })
        compose.onNodeWithTag("palette_apply").performClick()
        compose.waitUntil(10_000) { runBlocking { settings.coursePaletteSeed.first() } == 0L }
    }

    private fun sample(tag: String): Color {
        val image = compose.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().toPixelMap()
        return image[image.width / 2, image.height * 3 / 4]
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, .012f)
        assertEquals(expected.green, actual.green, .012f)
        assertEquals(expected.blue, actual.blue, .012f)
    }

    @Test fun applied_seed_changes_all_renderers_and_wallpaper_never_changes_card_color() {
        runBlocking { settings.setTimetableWallpaperUri(wallpaper) }
        launch()
        awaitTag("timetable_wallpaper_image")
        compose.onNodeWithTag("week_overview_toggle").performClick()
        for (seed in listOf(0L, 13L)) {
            runBlocking { settings.setCoursePaletteSeed(seed) }
            compose.waitForIdle()
            val schoolColor = CoursePalette.containerColor(CoursePalette.colorIndexFor("r4:key:a", seed), false)
            compose.waitUntil(10_000) { runCatching { sample("school_block:a1") == schoolColor }.getOrDefault(false) }
            for (percent in listOf(0, 65, 100)) {
                runBlocking { settings.setWallpaperVisibilityPercent(percent) }
                compose.waitForIdle()
                assertColor(schoolColor, sample("school_block:a1"))
                assertColor(schoolColor, sample("week_overview_block:1:school:a1"))
                val manualColor = CoursePalette.containerColor(CoursePalette.colorIndexFor("manual:appearance-manual", seed), false)
                assertColor(manualColor, sample("manual_block:appearance-manual"))
                assertColor(manualColor, sample("week_overview_block:1:manual:appearance-manual"))
            }
        }
        activity.recreate()
        awaitTag("school_block:a1")
        compose.onNodeWithTag("week_overview_strip").assertDoesNotExist()
    }

    @Test fun wallpaper_preview_finish_and_pointer_cancel_obey_persistence_boundary() {
        runBlocking { settings.setTimetableWallpaperUri(wallpaper) }
        launch()
        compose.onNodeWithTag("settings").performClick()
        compose.onNodeWithTag("timetable_wallpaper").performClick()
        compose.onNodeWithTag("wallpaper_visibility_preview").assertIsDisplayed()
        for (percent in listOf(0, 65, 100)) {
            compose.onNodeWithTag("wallpaper_visibility_slider").performSemanticsAction(SemanticsActions.SetProgress) { it(percent.toFloat()) }
            compose.waitUntil(10_000) { runBlocking { settings.wallpaperVisibilityPercent.first() } == percent }
            compose.onNodeWithTag("wallpaper_close").performClick()
            compose.onNodeWithTag("settings_back").performClick()
            awaitTag("school_block:a1")
            capture("wallpaper-$percent-percent")
            compose.onNodeWithTag("settings").performClick()
            compose.onNodeWithTag("timetable_wallpaper").performClick()
        }
        compose.onNodeWithTag("wallpaper_visibility_slider").performTouchInput {
            down(Offset(width * .9f, center.y)); moveTo(Offset(width * .3f, center.y))
        }
        assertEquals(100, runBlocking { settings.wallpaperVisibilityPercent.first() })
        compose.onNodeWithTag("wallpaper_visibility_slider").performTouchInput { cancel() }
        compose.onNodeWithTag("wallpaper_close").performClick()
        assertEquals(100, runBlocking { settings.wallpaperVisibilityPercent.first() })
    }

    @Test fun light_dark_overview_and_font_scale_keep_rail_and_seven_columns_visible() {
        launch()
        capture("light-collapsed")
        compose.onNodeWithTag("week_overview_toggle").performClick()
        capture("light-overview-expanded")
        runBlocking { settings.setAppearanceMode(AppearanceMode.DARK) }
        compose.waitForIdle()
        capture("dark-overview-expanded")
        compose.onNodeWithTag("week_overview_toggle").performClick()
        capture("dark-collapsed")
        val normalRailWidth = compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot().let { it.right - it.left }
        val oldScale = shell("settings get system font_scale")
        try {
            shell("settings put system font_scale 1.3")
            compose.waitUntil(5_000) { kotlin.math.abs(activity.fontScale() - 1.3f) < 0.001f }
            activity.recreate()
            awaitTag("school_block:a1")
            compose.waitUntil(5_000) {
                compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot().let { it.right - it.left } > normalRailWidth
            }
            val rail = compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot()
            val label = compose.onNodeWithTag("time_boundary:21:55").getUnclippedBoundsInRoot()
            val body = grid().getUnclippedBoundsInRoot()
            assertTrue(label.left >= rail.left && label.right <= rail.right)
            assertTrue(kotlin.math.abs((body.left - rail.right).value) <= 1f)
            compose.onNodeWithText("周日").assertIsDisplayed()
            capture("font-scale-1-3-measured-rail")
        } finally {
            shell("settings put system font_scale $oldScale")
        }
    }
}
