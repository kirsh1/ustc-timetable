package com.ustc.timetable.timetable.ui

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ustc.timetable.ui.theme.TimetableTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockTextsTest {
    private fun metrics(height: Float, width: Float = 80f) = CourseCardTextMetrics(
        cardHeightDp = height,
        cardWidthDp = width,
        titleLineHeightDp = 12.5f,
        locationLineHeightDp = 11.5f,
        metadataLineHeightDp = 10.5f,
    )

    @Test fun location_threshold_does_not_depend_on_teacher_presence() {
        val belowWithoutTeacher = BlockTexts.budget(metrics(27.99f), hasLocation = true, hasTeachers = false, markerCount = 0)
        val belowWithTeacher = BlockTexts.budget(metrics(27.99f), hasLocation = true, hasTeachers = true, markerCount = 0)
        val atWithoutTeacher = BlockTexts.budget(metrics(28f), hasLocation = true, hasTeachers = false, markerCount = 0)
        val atWithTeacher = BlockTexts.budget(metrics(28f), hasLocation = true, hasTeachers = true, markerCount = 0)

        assertFalse(belowWithoutTeacher.showLocation)
        assertFalse(belowWithTeacher.showLocation)
        assertTrue(atWithoutTeacher.showLocation)
        assertTrue(atWithTeacher.showLocation)
    }

    @Test fun teacher_two_to_one_line_boundary_is_deterministic() {
        assertEquals(0, BlockTexts.budget(metrics(63.49f), true, true, 0).teacherMaxLines)
        assertEquals(1, BlockTexts.budget(metrics(63.5f), true, true, 0).teacherMaxLines)
        assertEquals(1, BlockTexts.budget(metrics(73.99f), true, true, 0).teacherMaxLines)
        assertEquals(2, BlockTexts.budget(metrics(74f), true, true, 0).teacherMaxLines)
        assertFalse(BlockTexts.budget(metrics(84.49f), true, true, 0).showTime)
        assertTrue(BlockTexts.budget(metrics(84.5f), true, true, 0).showTime)
    }

    @Test fun marker_reservation_does_not_reduce_all_title_lines() {
        val budgets = (0..2).map { markerCount ->
            BlockTexts.budget(metrics(height = 84.5f, width = 80f), true, true, markerCount)
        }
        assertEquals(listOf(3, 3, 3), budgets.map { it.titleMaxLines })
        assertTrue(budgets.all { it.showLocation })
    }

    @Test fun optional_width_thresholds_are_inclusive_after_marker_reservation() {
        assertEquals(0, BlockTexts.budget(metrics(84.5f, 39.99f), true, true, 0).teacherMaxLines)
        assertEquals(2, BlockTexts.budget(metrics(84.5f, 40f), true, true, 0).teacherMaxLines)
        assertFalse(BlockTexts.budget(metrics(84.49f, 51.99f), true, false, 0).showTime)
        assertTrue(BlockTexts.budget(metrics(84.5f, 52f), true, false, 0).showTime)
    }

    @Test fun mandatory_title_and_location_precede_optional_metadata() {
        val short = BlockTexts.budget(metrics(28f, 20f), hasLocation = true, hasTeachers = true, markerCount = 2)
        assertEquals(1, short.titleMaxLines)
        assertTrue(short.showLocation)
        assertEquals(0, short.teacherMaxLines)
        assertFalse(short.showTime)
    }

    @Test fun location_codes_use_monospace_semibold_typography() {
        assertEquals(FontFamily.Monospace, TimetableTypography.courseLocation.fontFamily)
        assertEquals(FontWeight.SemiBold, TimetableTypography.courseLocation.fontWeight)
    }

    @Test fun exact_course_card_tokens_are_stable() {
        assertEquals(4f, CourseCardTextTokens.CONTENT_VERTICAL_PADDING_DP)
        assertEquals(3, CourseCardTextTokens.MAX_TITLE_LINES)
        assertEquals(1, CourseCardTextTokens.LOCATION_LINES)
        assertEquals(2, CourseCardTextTokens.MAX_TEACHER_LINES)
        assertEquals(40f, CourseCardTextTokens.TEACHER_MIN_OPTIONAL_WIDTH_DP)
        assertEquals(52f, CourseCardTextTokens.TIME_MIN_OPTIONAL_WIDTH_DP)
        assertEquals(8f, CourseCardTextTokens.MARKER_DIAMETER_DP)
        assertEquals(2f, CourseCardTextTokens.MARKER_GAP_DP)
        assertEquals(2f, CourseCardTextTokens.MARKER_RIGHT_INSET_DP)
    }
}
