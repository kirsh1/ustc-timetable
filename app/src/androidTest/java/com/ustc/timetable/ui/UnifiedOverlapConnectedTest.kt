package com.ustc.timetable.ui

import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ustc.timetable.appearance.AppearanceMode
import com.ustc.timetable.test.J1TestState
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.domain.*
import com.ustc.timetable.timetable.ui.ExactOverlapMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic emulator-only fixtures; never execute against the user's physical database. */
@RunWith(AndroidJUnit4::class)
class UnifiedOverlapConnectedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val activity = J1MainActivityHarness(compose)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val settings get() = J1TestState.app.container.settings

    @Before fun seed() = runBlocking {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        J1TestState.reset()
        settings.setAppearanceMode(AppearanceMode.LIGHT)
        settings.setCoursePaletteSeed(0L)
        settings.setExactOverlapMode(ExactOverlapMode.SPLIT)
        val c = J1TestState.app.container
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val semester = c.semesters.createLocalSemester(SemesterDefaults.AUTUMN_2026("overlap", "clone", Instant.EPOCH)
            .copy(displayName="重叠展示验收", week1Start=monday, startDate=monday, endDate=monday.plusWeeks(20).minusDays(1)),c.bundledOfficial)
        val course = Course(CourseId("school"),semester.id,"stable-school","S","学校课程",3.0,null)
        c.db.applySchoolSnapshot(semester.id,listOf(course),listOf(CourseMeeting(MeetingId("school-1"),course.id,1,6,8,WeekPattern.of(1),"TH-A301-ROOM",listOf("教师甲"))),"synthetic",Instant.EPOCH)
        suspend fun manual(id:String,day:Int,start:Int,end:Int,week:Int,created:Long) {
            c.manual.add(ManualScheduleItem(ManualItemId(id),semester.id,id,day,LocalTime.of(start,0),LocalTime.of(end,0),WeekPattern.of(week),"TH-B301","fixture",Instant.ofEpochSecond(created),Instant.ofEpochSecond(created)))
        }
        manual("future",1,14,16,2,1)
        manual("A",2,10,12,1,2)
        manual("B",2,11,13,1,3)
        manual("earlier",3,14,16,1,4)
        manual("later",3,14,16,1,5)
        settings.setViewedSemesterId(semester.id.value)
        settings.setShowNonCurrentWeek(true)
        Unit
    }
    @After fun close() { activity.close() }
    private fun await(tag:String) = compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag,true).fetchSemanticsNodes().isNotEmpty() }
    private fun launch() { activity.launch(); await("school_block:school-1") }
    private fun grid() = compose.onNode(hasTestTag("timetable_grid") and hasAnyAncestor(hasTestTag("week_page_1")))
    private fun shell(command:String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().toString(Charsets.UTF_8).trim() }
    private fun capture(name:String) {
        settleFrames()
        shell("screencap -p /sdcard/Pictures/overlap-$name.png")
    }
    private fun settleFrames() {
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        val frames=java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            var remaining=3
            val callback=object : android.view.Choreographer.FrameCallback {
                override fun doFrame(time:Long) {
                    if (--remaining==0) frames.countDown() else android.view.Choreographer.getInstance().postFrameCallback(this)
                }
            }
            android.view.Choreographer.getInstance().postFrameCallback(callback)
        }
        check(frames.await(5,java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.uiAutomation.waitForIdle(300,5_000)
    }
    private fun deviceImage(name:String):android.graphics.Bitmap {
        // Semantics can be ready while SurfaceFlinger still presents the loading frame.
        // Gate on an independent school-card fill before testing puzzle pixels or corners.
        val deadline=android.os.SystemClock.uptimeMillis()+10_000
        while (true) {
            capture(name)
            val bitmap=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "cat /sdcard/Pictures/overlap-$name.png")).use { requireNotNull(android.graphics.BitmapFactory.decodeStream(it)) }
            val bounds=compose.onNodeWithTag("school_block:school-1").fetchSemanticsNode().boundsInRoot
            val dark=runBlocking { settings.appearanceMode.first() }==AppearanceMode.DARK
            val expected=com.ustc.timetable.timetable.ui.CoursePalette.containerColor(
                com.ustc.timetable.timetable.ui.CoursePalette.colorIndexFor("overlap:stable-school",0L),dark).toArgb()
            if (bitmap.getPixel(bounds.center.x.toInt(),bounds.bottom.toInt()-15)==expected) return bitmap
            bitmap.recycle()
            check(android.os.SystemClock.uptimeMillis()<deadline) { "School card never reached its rendered target frame" }
        }
    }
    private fun awaitSystemBars(dark:Boolean) {
        compose.waitUntil(5_000) {
            var matches=false
            instrumentation.runOnMainSync {
                val active=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<com.ustc.timetable.MainActivity>().singleOrNull()
                if (active!=null) {
                    val bars=androidx.core.view.WindowInsetsControllerCompat(active.window,active.window.decorView)
                    matches=bars.isAppearanceLightStatusBars==!dark && bars.isAppearanceLightNavigationBars==!dark
                }
            }
            matches
        }
    }
    @Test fun physical_marker_opens_current_then_original_manual_editor() {
        launch()
        compose.onNodeWithTag("manual_block:future").assertDoesNotExist()
        compose.onNodeWithTag("overlap_marker:CROSS_WEEK",true).performClick()
        settleFrames()
        compose.onNodeWithTag("overlap_detail_indicator").assertTextEquals("1 / 2")
        compose.onNodeWithTag("overlap_detail_next").performClick()
        settleFrames()
        compose.onNodeWithTag("overlap_detail_indicator").assertTextEquals("2 / 2")
        compose.onNodeWithTag("overlap_edit_manual").performClick()
        settleFrames()
        compose.onNodeWithTag("editor_title").assertTextContains("future")
        compose.onNodeWithTag("editor_title").performTextReplacement("future edited")
        capture("editor-before-save")
        compose.onNodeWithTag("editor_save").performScrollTo()
        compose.onNodeWithTag("editor_save").assertIsEnabled()
        compose.onNodeWithTag("editor_save").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntil(10_000) {
            runBlocking { J1TestState.app.container.db.manualItemDao().itemsForSemester("overlap") }
                .any { it.id=="future" && it.title=="future edited" }
        }
        val items=runBlocking { J1TestState.app.container.db.manualItemDao().itemsForSemester("overlap") }
        assertEquals("future edited",items.single { it.id=="future" }.title)
        assertEquals("A",items.single { it.id=="A" }.title)
    }
    @Test fun partial_shapes_own_exclusive_and_shared_pixels_and_clicks() {
        launch()
        // Map wall-clock positions via visible rail labels, not a replacement axis formula.
        settleFrames()
        val body=grid().fetchSemanticsNode().boundsInRoot
        val image=deviceImage("partial-pixels")
        fun row(tag:String):Int {
            val label=compose.onNodeWithTag("time_boundary:$tag").fetchSemanticsNode().boundsInRoot
            return ((label.top+label.bottom)/2).toInt()
        }
        val xLeft=(body.left+body.width/7f*1.2f).toInt(); val xRight=(body.left+body.width/7f*1.8f).toInt()
        val exclusive=row("10:30")+5
        val shared=row("11:25")+5
        assertEquals(image.getPixel(xLeft,exclusive),image.getPixel(xRight,exclusive))
        assertNotEquals(image.getPixel(xLeft,shared),image.getPixel(xRight,shared))
        image.recycle()
        grid().performTouchInput { click(Offset(xRight-body.left,shared-body.top)) }
        compose.onNodeWithTag("editor_title").assertTextContains("B")
    }
    @Test fun card_outer_corner_is_rounded_not_square() {
        launch(); settleFrames()
        val bounds=compose.onNodeWithTag("school_block:school-1").fetchSemanticsNode().boundsInRoot
        val image=deviceImage("rounded-corner")
        val edge=image.getPixel(bounds.left.toInt()+1,bounds.top.toInt()+1)
        val inner=image.getPixel(bounds.center.x.toInt(),bounds.top.toInt()+1)
        image.recycle()
        assertNotEquals("Outer corner must reveal the background, not a square fill",inner,edge)
    }
    @Test fun exact_preference_themes_overview_and_large_font_keep_geometry() {
        launch()
        compose.onNodeWithTag("manual_block:later").assertExists()
        runBlocking { settings.setExactOverlapMode(ExactOverlapMode.EARLIEST) }
        await("overlap_marker:CURRENT_CONFLICT")
        compose.onNodeWithTag("manual_block:later").assertDoesNotExist()
        deviceImage("light-collapsed").recycle()
        compose.onNodeWithTag("time_boundary:21:55").assertIsDisplayed()
        compose.onNodeWithTag("week_overview_toggle").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("time_boundary:21:55").assertIsDisplayed()
        deviceImage("light-expanded").recycle()
        runBlocking { settings.setAppearanceMode(AppearanceMode.DARK) }
        awaitSystemBars(dark=true)
        deviceImage("dark-expanded").recycle()
        compose.onNodeWithTag("week_overview_toggle").performClick()
        compose.onNodeWithTag("time_boundary:21:55").assertIsDisplayed()
        deviceImage("dark-collapsed").recycle()
        val before=compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot().let { it.right-it.left }
        val old=shell("settings get system font_scale")
        try {
            shell("settings put system font_scale 1.3")
            compose.waitUntil(5_000) { kotlin.math.abs(activity.fontScale()-1.3f)<.001f }
            activity.recreate(); await("school_block:school-1")
            compose.waitUntil(5_000) { compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot().let { it.right-it.left }>before }
            compose.onNodeWithText("周日").assertIsDisplayed()
            val rail=compose.onNodeWithTag("fixed_time_rail").getUnclippedBoundsInRoot()
            val label=compose.onNodeWithTag("time_boundary:21:55").getUnclippedBoundsInRoot()
            assertTrue(label.left>=rail.left && label.right<=rail.right)
            compose.onNodeWithTag("time_boundary:21:55").assertIsDisplayed()
            deviceImage("font-1-3").recycle()
        } finally { shell("settings put system font_scale $old") }
    }
}
