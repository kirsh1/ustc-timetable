package com.ustc.timetable.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperStateTest {
    @Test fun slider_preview_does_not_change_course_card_alpha() {
        for (percent in listOf(0, 65, 100)) {
            assertEquals(percent / 100f, wallpaperImageAlpha(percent), 0.001f)
            for (appearance in ResolvedAppearance.entries) {
                org.junit.Assert.assertTrue(wallpaperScrim(appearance, percent).alpha in 0f..wallpaperScrim(appearance).alpha)
            }
            assertEquals(1f, com.ustc.timetable.timetable.ui.CoursePalette.alphaFor(true, true))
            assertEquals(0.35f, com.ustc.timetable.timetable.ui.CoursePalette.alphaFor(false, true))
        }
        assertEquals(0f, wallpaperImageAlpha(-1), 0f)
        assertEquals(1f, wallpaperImageAlpha(101), 0f)
    }
    @Test fun wallpaper_contribution_is_monotonic_and_maximal_at_100_percent() {
        for (appearance in ResolvedAppearance.entries) {
            var previous = -1f
            for (percent in 0..100) {
                val weight = wallpaperImageAlpha(percent) * (1f - wallpaperScrimAlpha(appearance, percent))
                org.junit.Assert.assertTrue("$appearance $percent: $previous -> $weight", weight >= previous)
                previous = weight
            }
            assertEquals(0f, wallpaperScrim(appearance, 0).alpha, .001f)
            assertEquals(wallpaperScrim(appearance).alpha, wallpaperScrim(appearance, 100).alpha, .001f)
        }
    }
    @Test fun picker_cancel_keeps_existing_wallpaper() {
        val grants = FakeGrants()
        assertEquals("content://old", WallpaperSelection.resolve("content://old", null, grants))
        assertEquals(emptyList<String>(), grants.persisted)
        assertEquals(emptyList<String>(), grants.released)
    }

    @Test fun replacing_wallpaper_persists_new_and_releases_old_grant() {
        val grants = FakeGrants()
        assertEquals("content://new", WallpaperSelection.resolve("content://old", "content://new", grants))
        assertEquals(listOf("content://new"), grants.persisted)
        assertEquals(listOf("content://old"), grants.released)
    }

    @Test fun clearing_wallpaper_releases_grant() {
        val grants = FakeGrants()
        WallpaperSelection.clear("content://old", grants)
        assertEquals(listOf("content://old"), grants.released)
    }

    private class FakeGrants : WallpaperUriGrants {
        val persisted = mutableListOf<String>()
        val released = mutableListOf<String>()
        override fun persist(uri: String) { persisted += uri }
        override fun release(uri: String) { released += uri }
    }
}
