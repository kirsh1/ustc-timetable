package com.ustc.timetable.appearance

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperStateTest {
    @Test fun slider_preview_does_not_change_course_card_alpha() {
        for (percent in listOf(0, 65, 100)) {
            assertEquals(percent / 100f, wallpaperImageAlpha(percent), 0.001f)
            for (appearance in ResolvedAppearance.entries) {
                assertEquals(wallpaperScrim(appearance).alpha * percent / 100f, wallpaperScrim(appearance, percent).alpha, 0.005f)
            }
            assertEquals(1f, com.ustc.timetable.timetable.ui.CoursePalette.alphaFor(true, true))
            assertEquals(0.35f, com.ustc.timetable.timetable.ui.CoursePalette.alphaFor(false, true))
        }
        assertEquals(0f, wallpaperImageAlpha(-1), 0f)
        assertEquals(1f, wallpaperImageAlpha(101), 0f)
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
