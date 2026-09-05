package com.ustc.timetable.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperVisibilityEditorTest {
    @Test fun slider_finish_persists_candidate() {
        val preview = WallpaperVisibilityEditor.preview(WallpaperVisibilityEditor.open(65), 100)
        assertEquals(65, preview.persistedPercent)
        assertEquals(100, WallpaperVisibilityEditor.finish(preview).persistedPercent)
    }
    @Test fun slider_cancel_before_finish_restores_persisted_value() {
        val preview = WallpaperVisibilityEditor.preview(WallpaperVisibilityEditor.open(65), 0)
        assertEquals(65, WallpaperVisibilityEditor.cancel(preview).candidatePercent)
    }
    @Test fun cancel_after_finish_keeps_finished_value_and_all_inputs_are_bounded() {
        val finished = WallpaperVisibilityEditor.finish(WallpaperVisibilityEditor.preview(WallpaperVisibilityEditor.open(-10), 120))
        assertEquals(100, WallpaperVisibilityEditor.cancel(WallpaperVisibilityEditor.preview(finished, 20)).candidatePercent)
        assertEquals(0, WallpaperVisibilityEditor.open(-1).candidatePercent)
    }
}
