package com.ustc.timetable.timetable.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseCardVisualLayoutTest {
    @Test fun course_visual_rect_is_inside_logical_time_rect() {
        val rect = courseCardVisualBounds(logicalTopDp = 100f, logicalBottomDp = 180f, insetDp = 1f)
        assertEquals(101f, rect.topDp, 0f)
        assertEquals(179f, rect.bottomDp, 0f)
    }

    @Test fun short_block_inset_never_inverts_height() {
        val rect = courseCardVisualBounds(logicalTopDp = 100f, logicalBottomDp = 101f, insetDp = 1f)
        assertTrue(rect.topDp <= rect.bottomDp)
        assertTrue(rect.topDp >= 100f && rect.bottomDp <= 101f)
    }

    @Test fun one_dp_visual_inset_does_not_change_logical_hit_bounds() {
        val logicalTop = 42f
        val logicalBottom = 96f
        val visual = courseCardVisualBounds(logicalTop, logicalBottom, insetDp = 1f)

        assertEquals(logicalTop + 1f, visual.topDp, 0f)
        assertEquals(logicalBottom - 1f, visual.bottomDp, 0f)
        assertEquals(42f, logicalTop, 0f)
        assertEquals(96f, logicalBottom, 0f)
    }

    @Test fun zero_height_logical_card_stays_non_negative() {
        val visual = courseCardVisualBounds(12f, 12f, insetDp = 1f)
        assertEquals(12f, visual.topDp, 0f)
        assertEquals(12f, visual.bottomDp, 0f)
    }
}
