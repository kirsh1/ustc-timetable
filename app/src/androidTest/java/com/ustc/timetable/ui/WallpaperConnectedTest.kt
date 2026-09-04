package com.ustc.timetable.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustc.timetable.test.J1TestState
import com.ustc.timetable.appearance.WallpaperRuntimeState
import com.ustc.timetable.appearance.WallpaperImageLoader
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNotNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WallpaperConnectedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val activity = J1MainActivityHarness(compose)

    @Before fun reset() = J1TestState.reset()
    @After fun close() = activity.close()

    @Test fun test_owned_wallpaper_renders_only_on_timetable_destination() {
        J1TestState.seedDebug()
        grantNotificationPermissionIfNeeded()
        val uri = "content://com.ustc.timetable.test.ui-r4-wallpaper/wallpaper.png"
        val decoded = runBlocking {
            withContext(Dispatchers.IO) {
                WallpaperImageLoader.load(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    uri,
                    100,
                    100,
                )
            }
        }
        assertNotNull("test-owned content URI must decode before Activity launch", decoded)
        runBlocking {
            J1TestState.app.container.settings.setTimetableWallpaperUri(uri)
        }
        activity.launch()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("timetable_wallpaper_image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("timetable_wallpaper_image").assertIsDisplayed()
        compose.onNodeWithTag("settings").performClick()
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithTag("settings_list").fetchSemanticsNodes().isNotEmpty() }
                .getOrDefault(false)
        }
        compose.onNodeWithTag("settings_list").assertIsDisplayed()
        compose.onAllNodesWithTag("timetable_wallpaper_image").assertCountEquals(0)
    }

    @Test fun unreadable_wallpaper_keeps_solid_timetable_fallback() {
        J1TestState.seedDebug()
        runBlocking {
            J1TestState.app.container.settings.setTimetableWallpaperUri(
                "content://com.ustc.timetable.test.ui-r4-wallpaper/missing.png",
            )
        }
        activity.launch()
        compose.waitUntil(10_000) { WallpaperRuntimeState.unavailableUri.value != null }
        compose.onNodeWithTag("solid_timetable_background").assertIsDisplayed()
        compose.onAllNodesWithTag("timetable_wallpaper_image").assertCountEquals(0)
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
