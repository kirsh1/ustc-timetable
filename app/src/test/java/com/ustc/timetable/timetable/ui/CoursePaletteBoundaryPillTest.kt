package com.ustc.timetable.timetable.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoursePaletteBoundaryPillTest {

    @Test
    fun boundary_pill_container_is_preset_for_every_course_color() {
        listOf(false, true).forEach { dark ->
            val colors = (0 until CoursePalette.PALETTE_SIZE)
                .map { CoursePalette.boundaryPillContainerColor(it, dark) }

            assertEquals(CoursePalette.PALETTE_SIZE, colors.distinct().size)
        }
    }

    @Test
    fun boundary_pill_text_retains_readable_contrast_for_every_preset() {
        listOf(false, true).forEach { dark ->
            (0 until CoursePalette.PALETTE_SIZE).forEach { index ->
                assertTrue(
                    "index=$index dark=$dark",
                    CoursePalette.boundaryPillContrastRatio(index, dark) >= 4.5,
                )
            }
        }
    }
}
