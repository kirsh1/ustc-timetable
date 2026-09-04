package com.ustc.timetable.timetable.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRailWidthTest {
    @Test fun time_rail_uses_measured_label_width_not_fixed_44dp() {
        val width = measuredTimeRailWidthPx(listOf(25.2f, 31.1f), density = 2f)
        assertEquals(32, width.widestLabelPx)
        assertEquals(49, width.totalPx)
        assertNotEquals(88, width.totalPx)
    }

    @Test fun time_rail_left_and_right_text_padding_are_symmetric() {
        val width = measuredTimeRailWidthPx(listOf(30f), density = 2.75f)
        assertEquals(width.leftPaddingPx, width.rightPaddingPx)
    }

    @Test fun actual_rail_padding_difference_within_1dp() {
        val density = 2.75f
        val width = measuredTimeRailWidthPx(listOf(30.2f), density)
        val remainingRight = width.totalPx - width.dividerPx - width.leftPaddingPx - width.widestLabelPx
        assertTrue(kotlin.math.abs(remainingRight - width.leftPaddingPx) <= density)
    }

    @Test fun longest_time_label_is_not_clipped_at_font_scale_1_3() {
        val renderedAtFontScale13 = 42.25f
        val width = measuredTimeRailWidthPx(listOf(38f, renderedAtFontScale13), density = 3f)
        assertTrue(width.widestLabelPx >= kotlin.math.ceil(renderedAtFontScale13).toInt())
        assertTrue(width.totalPx >= width.widestLabelPx + width.leftPaddingPx + width.rightPaddingPx)
    }
}
