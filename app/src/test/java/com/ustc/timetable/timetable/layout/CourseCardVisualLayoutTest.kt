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
}
