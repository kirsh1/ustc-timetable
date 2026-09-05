package com.ustc.timetable.appearance

import com.ustc.timetable.timetable.ui.CoursePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableThemeTest {
    @Test fun default_seed_preserves_existing_palette_mapping() {
        listOf("school:CHEM", "manual:1", "中文", "", "semester:key").forEach { key ->
            val expected = (java.security.MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))[0].toInt() and 255) % 12
            assertEquals(expected, CoursePalette.colorIndexFor(key, 0L))
        }
    }

    @Test fun all_twenty_four_variants_are_unique_permutations_and_contrast_safe() {
        val previews = (0L..23L).map(CoursePalette::previewIndices)
        assertEquals(24, previews.toSet().size)
        previews.forEach { indices ->
            assertEquals((0..11).toList(), indices.sorted())
            indices.forEach { index ->
                assertTrue(CoursePalette.contrastRatio(index, false) >= 4.5)
                assertTrue(CoursePalette.contrastRatio(index, true) >= 4.5)
            }
        }
        assertEquals(CoursePalette.previewIndices(-1L), CoursePalette.previewIndices(23L))
    }
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
