package com.ustc.timetable.appearance

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LocalContentColor
import com.ustc.timetable.ui.theme.AppBackgroundLayer
import com.ustc.timetable.ui.theme.TimetableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WallpaperImageLoaderTest {
    @get:Rule val rule = createComposeRule()

    @Test fun unreadable_or_revoked_uri_falls_back_to_solid() {
        WallpaperRuntimeState.clear()
        rule.setContent {
            AppBackgroundLayer(
                wallpaperUri = "content://missing/wallpaper",
                onWallpaperUnavailable = { WallpaperRuntimeState.reportUnavailable("content://missing/wallpaper") },
            ) {}
        }
        rule.waitUntil(5_000) { WallpaperRuntimeState.unavailableUri.value != null }
        rule.onNodeWithTag("solid_timetable_background").assertExists()
        rule.onAllNodesWithTag("timetable_wallpaper_image").assertCountEquals(0)
        assertEquals("content://missing/wallpaper", WallpaperRuntimeState.unavailableUri.value)
    }

    @Test fun dark_background_provides_dark_theme_content_color_to_timetable_controls() {
        var inherited = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            TimetableTheme(AppearanceMode.DARK) {
                expected = MaterialTheme.colorScheme.onSurface
                AppBackgroundLayer {
                    inherited = LocalContentColor.current
                }
            }
        }
        rule.runOnIdle { assertEquals(expected, inherited) }
    }

    @Test fun dark_theme_provides_content_color_to_non_timetable_screens() {
        var inherited = Color.Unspecified
        var expected = Color.Unspecified
        rule.setContent {
            TimetableTheme(AppearanceMode.DARK) {
                expected = MaterialTheme.colorScheme.onSurface
                inherited = LocalContentColor.current
            }
        }
        rule.runOnIdle { assertEquals(expected, inherited) }
    }
}
