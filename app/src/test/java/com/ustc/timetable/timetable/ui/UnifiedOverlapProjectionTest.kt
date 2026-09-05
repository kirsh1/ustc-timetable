package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class UnifiedOverlapProjectionTest {
    private fun manual(id: String, week: Int, start: Int = 10, end: Int = 12, added: Long = 10) = OverlapEntry(
        UiManualTimedBlock("manual:$id", ManualItemId(id), 1, LocalTime.of(start, 0), LocalTime.of(end, 0),
            WeekPattern.of(week), "same name", "room", emptyList()), Instant.ofEpochSecond(added))
    private fun school(id: String, week: Int, meeting: String = id, location: String = "room") = OverlapEntry(
        UiSchoolTimedBlock("semester:$id", MeetingId(meeting), 1, LocalTime.of(10, 0), LocalTime.of(12, 0),
            WeekPattern.of(week), "same name", location, listOf("teacher")), Instant.ofEpochSecond(1))
    private fun project(vararg entries: OverlapEntry, mode: ExactOverlapMode = ExactOverlapMode.SPLIT, show: Boolean = true) =
        UnifiedOverlapProjection.project(entries.toList(), 1, show, mode)

    @Test fun school_ghost_attaches_to_active_manual_without_narrowing() {
        val a = manual("a", 1)
        val result = project(a, school("b", 2))
        assertEquals(listOf("manual:a"), result.retained.map { it.block.colorKey })
        assertEquals(listOf("semester:b"), result.attachments.getValue(a.key).crossWeek.map { it.block.colorKey })
    }
    @Test fun manual_ghost_attaches_to_school_and_manual() {
        for (a in listOf(school("a", 1), manual("a", 1))) {
            val result = project(a, manual("b", 3))
            assertEquals(1, result.retained.size)
            assertEquals("manual:b", result.attachments.getValue(a.key).crossWeek.single().block.colorKey)
        }
    }
    @Test fun ghost_only_uses_earliest_week_not_input_order() {
        val result = project(manual("later", 5), manual("early", 2))
        assertEquals("manual:early", result.retained.single().block.colorKey)
        assertEquals("manual:later", result.attachments.values.single().crossWeek.single().block.colorKey)
    }
    @Test fun transitive_ghost_does_not_attach_without_direct_intersection() {
        val result = project(manual("a", 2, 10, 12), manual("b", 3, 11, 13), manual("c", 4, 12, 14))
        assertEquals(listOf("manual:a", "manual:c"), result.retained.map { it.block.colorKey })
    }
    @Test fun exact_active_conflicts_split_by_default_but_earliest_uses_timestamp() {
        val a = manual("a", 1, added = 20)
        val b = manual("b", 1, added = 10)
        assertEquals(2, project(a, b).retained.size)
        val result = project(a, b, mode = ExactOverlapMode.EARLIEST, show = false)
        assertEquals("manual:b", result.retained.single().block.colorKey)
        assertEquals("manual:a", result.attachments.getValue(b.key).currentConflicts.single().block.colorKey)
    }
    @Test fun partial_active_conflicts_never_hidden_by_earliest() {
        assertEquals(2, project(manual("a", 1), manual("b", 1, 11, 13), mode = ExactOverlapMode.EARLIEST).retained.size)
    }
    @Test fun school_uses_semester_import_time_for_mixed_exact_priority() {
        assertEquals("semester:b", project(manual("a", 1), school("b", 1), mode = ExactOverlapMode.EARLIEST).retained.single().block.colorKey)
    }
    @Test fun same_course_week_only_has_no_marker_but_location_variant_does() {
        assertTrue(project(school("a", 1), school("a", 2, "other")).attachments.isEmpty())
        assertEquals(1, project(school("a", 1), school("a", 2, "other", "elsewhere")).attachments.values.single().sameCourseVariants.size)
    }
    @Test fun local_meeting_ids_and_input_order_do_not_choose_representative() {
        val a = project(school("a", 2, "z"), school("b", 3, "a"))
        val b = project(school("b", 3, "zz"), school("a", 2, "aa"))
        assertEquals(a.retained.map { it.key }, b.retained.map { it.key })
        assertEquals(a.attachments.keys, b.attachments.keys)
    }
    @Test fun hidden_other_weeks_have_no_ghost_or_cross_marker() {
        val result = project(manual("a", 1), manual("b", 2), show = false)
        assertEquals(1, result.retained.size)
        assertTrue(result.attachments.isEmpty())
    }
    @Test fun touching_intervals_remain_separate() {
        assertEquals(2, project(manual("a", 2), manual("b", 3, 12, 14)).retained.size)
    }
}
