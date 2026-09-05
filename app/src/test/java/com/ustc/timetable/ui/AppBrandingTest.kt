package com.ustc.timetable.ui

import android.content.ComponentName
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class AppBrandingTest {
    @Test fun launcher_and_starting_window_use_vector_branding() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val logo = context.resources.getIdentifier("ic_app_icon", "drawable", context.packageName)
        assertTrue("SVG-derived vector must be packaged", logo != 0)
        assertEquals(logo, context.applicationInfo.icon)
        val info = context.packageManager.getActivityInfo(ComponentName(context, com.ustc.timetable.MainActivity::class.java), 0)
        val theme = context.resources.newTheme().apply { applyStyle(info.theme, true) }
        val value = TypedValue()
        assertTrue(theme.resolveAttribute(android.R.attr.windowBackground, value, true))
        assertEquals("startup_background", context.resources.getResourceEntryName(value.resourceId))
        if (Build.VERSION.SDK_INT >= 31) {
            assertTrue(theme.resolveAttribute(android.R.attr.windowSplashScreenAnimatedIcon, value, true))
            assertEquals("ic_splash_logo", context.resources.getResourceEntryName(value.resourceId))
        }
    }

    @Test fun startup_background_has_distinct_light_and_dark_colors() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun color(mode: Int): Int {
            val config = Configuration(context.resources.configuration).apply { uiMode = mode }
            val themed = context.createConfigurationContext(config)
            val id = themed.resources.getIdentifier("startup_background_color", "color", context.packageName)
            assertTrue(id != 0)
            return themed.getColor(id)
        }
        assertNotEquals(color(Configuration.UI_MODE_NIGHT_NO), color(Configuration.UI_MODE_NIGHT_YES))
    }
}
