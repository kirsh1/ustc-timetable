package com.ustc.timetable.appearance

import com.ustc.timetable.timetable.ui.CoursePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableThemeTest {
    @Test fun system_mode_tracks_system_night_state() {
        assertEquals(ResolvedAppearance.DARK, resolveAppearance(AppearanceMode.SYSTEM, systemDark = true))
        assertEquals(ResolvedAppearance.LIGHT, resolveAppearance(AppearanceMode.SYSTEM, systemDark = false))
    }

    @Test fun course_identity_index_is_stable_across_themes() {
        assertEquals(CoursePalette.colorIndexFor("course:stable"), CoursePalette.colorIndexFor("course:stable"))
    }

    @Test fun dark_palette_has_readable_contrast() {
        repeat(CoursePalette.PALETTE_SIZE) { assertTrue(CoursePalette.contrastRatio(it, dark = true) >= 4.5) }
    }

    @Test fun light_and_dark_scrims_are_theme_specific() {
        assertTrue(wallpaperScrim(ResolvedAppearance.LIGHT).alpha > wallpaperScrim(ResolvedAppearance.DARK).alpha)
    }
}
