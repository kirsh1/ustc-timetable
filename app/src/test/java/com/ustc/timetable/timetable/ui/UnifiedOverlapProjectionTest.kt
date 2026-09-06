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
    private fun school(
        id: String,
        week: Int,
        meeting: String = id,
        location: String = "room",
        teacher: String = "teacher",
        start: Int = 10,
        end: Int = 12,
    ) = OverlapEntry(
        UiSchoolTimedBlock("semester:$id", MeetingId(meeting), 1, LocalTime.of(start, 0), LocalTime.of(end, 0),
            WeekPattern.of(week), "same name", location, listOf(teacher)), Instant.ofEpochSecond(1))
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
        assertEquals(listOf("manual:early", "manual:later"), result.retained.map { it.block.colorKey })
        assertTrue(result.attachments.isEmpty())
    }
    @Test fun transitive_ghost_does_not_attach_without_direct_intersection() {
        val result = project(manual("a", 2, 10, 12), manual("b", 3, 11, 13), manual("c", 4, 12, 14))
        assertEquals(listOf("manual:a", "manual:b", "manual:c"), result.retained.map { it.block.colorKey })
        assertTrue(result.attachments.isEmpty())
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

    @Test fun active_same_school_course_teacher_variants_use_one_full_card_and_variant_marker() {
        val first = school("CHEM6023P.01", 1, meeting = "teacher-a", teacher = "教师甲")
        val second = school("CHEM6023P.01", 1, meeting = "teacher-b", teacher = "教师乙")

        val result = project(first, second, show = false)

        assertEquals(1, result.retained.size)
        assertEquals(setOf(OverlapMarkerKind.VARIANT), result.attachments.values.single().kinds)
        assertEquals(1, result.attachments.values.single().sameCourseVariants.size)
    }

    @Test fun active_same_school_course_week_pattern_only_duplicate_has_no_marker() {
        val broad = school("CHEM6023P.01", 1, meeting = "broad")
        val overlap = broad.copy(
            block = (broad.block as UiSchoolTimedBlock).copy(
                meetingId = MeetingId("overlap"),
                weeks = WeekPattern.range(1, 3),
            ),
        )

        val result = project(broad, overlap, show = false)

        assertEquals(1, result.retained.size)
        assertTrue(result.attachments.isEmpty())
    }

    @Test fun active_different_school_courses_still_split() {
        val result = project(school("course-a", 1), school("course-b", 1))

        assertEquals(2, result.retained.size)
        assertTrue(result.attachments.isEmpty())
    }

    @Test fun ghost_only_different_courses_remain_visible_for_segmented_split() {
        val result = project(
            school("course-a", 2, start = 10, end = 12),
            school("course-b", 3, start = 11, end = 13),
        )

        assertEquals(listOf("school:semester:course-a", "school:semester:course-b"), result.retained.map { it.identity })
        assertTrue(result.attachments.isEmpty())
    }

    @Test fun overlapping_ghosts_attach_when_any_current_card_is_present() {
        val active = school("current", 1, start = 10, end = 13)
        val result = project(
            active,
            school("future-a", 2, start = 10, end = 12),
            school("future-b", 3, start = 11, end = 13),
        )

        assertEquals(listOf(active.key), result.retained.map { it.key })
        assertEquals(2, result.attachments.getValue(active.key).crossWeek.size)
    }
}
