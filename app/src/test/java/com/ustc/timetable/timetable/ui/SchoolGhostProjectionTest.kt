package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolGhostProjectionTest {
    private data class School(
        override val colorKey: String,
        override val meetingId: MeetingId,
        override val weekday: Int = 1,
        override val start: LocalTime = LocalTime.of(9, 0),
        override val endInclusive: LocalTime = LocalTime.of(10, 0),
        override val weeks: WeekPattern,
        override val title: String = colorKey,
        override val location: String = "A101",
        override val teacherNames: List<String> = listOf("教师甲"),
    ) : SchoolTimedBlock

    @Test fun week_pattern_only_difference_has_no_variant_marker() {
        val active = school("course-a", "active", WeekPattern.of(2))
        val ghost = active.copy(meetingId = MeetingId("ghost"), weeks = WeekPattern.of(3))
        val result = SchoolGhostProjection.associate(listOf(ghost, active), 2, true)

        assertEquals(listOf(active), result.retainedSchoolBlocks)
        assertTrue(result.attachmentsByRepresentativeKey.isEmpty())
    }

    @Test fun presentation_signature_difference_has_variant_marker() {
        val active = school("course-a", "active", WeekPattern.of(2))
        val ghost = active.copy(
            meetingId = MeetingId("ghost"),
            weeks = WeekPattern.of(3),
            teacherNames = listOf(" 教师乙 "),
        )
        val result = SchoolGhostProjection.associate(listOf(ghost, active), 2, true)
        val attachment = result.attachmentsByRepresentativeKey.values.single()

        assertEquals(listOf(ghost), attachment.sameCourseVariants)
        assertTrue(attachment.differentCourses.isEmpty())
    }

    @Test fun manual_blocks_never_enter_school_aggregation() {
        val block: SchoolTimedBlock = school("course-a", "active", WeekPattern.of(2))
        val result = SchoolGhostProjection.associate(listOf(block), 2, true)
        assertTrue(result.retainedSchoolBlocks.all { it.manualItemId == null })
        assertEquals(null, block.manualItemId)
    }

    @Test fun active_school_conflicts_remain_side_by_side() {
        val a = school("course-a", "a", WeekPattern.of(2))
        val b = school("course-b", "b", WeekPattern.of(2))
        val result = SchoolGhostProjection.associate(listOf(b, a), 2, true)
        assertEquals(setOf(a, b), result.retainedSchoolBlocks.toSet())
    }

    @Test fun unattached_school_ghost_remains_gray_card() {
        val active = school("course-a", "active", WeekPattern.of(2))
        val ghost = school("course-b", "ghost", WeekPattern.of(3)).copy(weekday = 2)
        val result = SchoolGhostProjection.associate(listOf(active, ghost), 2, true)
        assertEquals(setOf(active, ghost), result.retainedSchoolBlocks.toSet())
    }

    @Test fun ghost_uses_maximum_overlap_then_stable_key() {
        val shorter = school("course-b", "b", WeekPattern.of(2)).copy(
            start = LocalTime.of(9, 0), endInclusive = LocalTime.of(9, 30), location = "B",
        )
        val longer = school("course-a", "a", WeekPattern.of(2)).copy(
            start = LocalTime.of(9, 0), endInclusive = LocalTime.of(10, 0), location = "A",
        )
        val ghost = school("course-c", "g", WeekPattern.of(3)).copy(
            start = LocalTime.of(9, 20), endInclusive = LocalTime.of(9, 50),
        )
        val result = SchoolGhostProjection.associate(listOf(shorter, ghost, longer), 2, true)
        val expectedKey = SchoolGhostProjection.canonicalPresentationKey(longer)
        assertEquals(listOf(ghost), result.attachmentsByRepresentativeKey.getValue(expectedKey).differentCourses)
    }

    @Test fun representative_is_stable_when_local_meeting_ids_change() {
        val activeA = school("course-a", "id-a", WeekPattern.of(2)).copy(location = "A")
        val activeB = school("course-b", "id-b", WeekPattern.of(2)).copy(location = "B")
        val ghost = school("course-c", "id-g", WeekPattern.of(3))
        val first = SchoolGhostProjection.associate(listOf(activeB, ghost, activeA), 2, true)
        val second = SchoolGhostProjection.associate(
            listOf(
                activeA.copy(meetingId = MeetingId("local-99")),
                ghost.copy(meetingId = MeetingId("local-42")),
                activeB.copy(meetingId = MeetingId("local-01")),
            ),
            2,
            true,
        )
        assertEquals(first.attachmentsByRepresentativeKey.keys, second.attachmentsByRepresentativeKey.keys)
    }

    @Test fun local_meeting_id_never_participates_in_attachment_order() {
        val active = school("course-a", "active", WeekPattern.of(2))
        val ghostA = school("course-b", "z-local", WeekPattern.of(3)).copy(location = "A")
        val ghostB = school("course-c", "a-local", WeekPattern.of(4)).copy(location = "B")
        val first = SchoolGhostProjection.associate(listOf(ghostB, active, ghostA), 2, true)
        val second = SchoolGhostProjection.associate(
            listOf(
                ghostA.copy(meetingId = MeetingId("000")),
                ghostB.copy(meetingId = MeetingId("999")),
                active.copy(meetingId = MeetingId("changed")),
            ),
            2,
            true,
        )
        fun keys(result: SchoolGhostAssociation) = result.attachmentsByRepresentativeKey.values
            .single().differentCourses.map(SchoolGhostProjection::canonicalPresentationKey)
        assertEquals(keys(first), keys(second))
    }

    private fun school(course: String, id: String, weeks: WeekPattern) = School(
        colorKey = course,
        meetingId = MeetingId(id),
        weeks = weeks,
    )
}
