package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CourseDetailPagerTest {
    private fun block(
        identity: String,
        meetingId: String,
        week: Int,
        teacher: String = "教师",
    ) = UiSchoolTimedBlock(
        colorKey = identity,
        meetingId = MeetingId(meetingId),
        weekday = 2,
        start = LocalTime.of(9, 45),
        endInclusive = LocalTime.of(11, 20),
        weeks = WeekPattern.of(week),
        title = identity,
        location = "TH-B301",
        teacherNames = listOf(teacher),
    )

    private fun detail(block: SchoolTimedBlock): CourseDetailUiModel {
        val courseId = CourseId("course-${block.colorKey}")
        val course = Course(courseId, SemesterId("semester"), block.colorKey, "CODE", block.title, 3.0, null)
        val meeting = CourseMeeting(
            id = block.meetingId,
            courseId = courseId,
            weekday = block.weekday,
            startPeriod = 3,
            endPeriod = 4,
            weekPattern = block.weeks,
            location = block.location,
            teacherNames = block.teacherNames,
        )
        return CourseDetailUiModel(course, meeting, listOf(meeting))
    }

    @Test fun one_detail_page_per_stable_course_identity() {
        val active = block("A", "a", 2)
        val bLate = block("B", "b-late", 6)
        val bEarly = block("B", "b-early", 3)
        val c = block("C", "c", 4)
        val attachment = SchoolGhostAttachment(
            SchoolGhostProjection.canonicalPresentationKey(active),
            sameCourseVariants = emptyList(),
            differentCourses = listOf(c, bLate, bEarly, bEarly),
        )
        val all = listOf(active, bLate, bEarly, c).associate { it.meetingId to detail(it) }

        val pager = CourseDetailPager.build(active, attachment, all)
        assertNotNull(pager)

        assertEquals(listOf("A", "B", "C"), pager!!.pages.map { it.stableCourseIdentity })
        assertEquals("b-early", pager.pages[1].anchorMeetingId.value)
    }

    @Test fun representative_active_course_is_first_detail_page() {
        val active = block("Z", "active", 8)
        val alternative = block("A", "other", 1)
        val attachment = SchoolGhostAttachment(
            SchoolGhostProjection.canonicalPresentationKey(active),
            sameCourseVariants = emptyList(),
            differentCourses = listOf(alternative),
        )
        val all = listOf(active, alternative).associate { it.meetingId to detail(it) }

        val pager = CourseDetailPager.build(active, attachment, all)
        assertNotNull(pager)

        assertEquals("active", pager!!.pages.first().anchorMeetingId.value)
        assertEquals("Z", pager.pages.first().stableCourseIdentity)
    }

    @Test fun same_course_variants_use_single_detail_page() {
        val active = block("A", "active", 2)
        val teacherVariant = block("A", "variant", 4, teacher = "另一位教师")
        val attachment = SchoolGhostAttachment(
            SchoolGhostProjection.canonicalPresentationKey(active),
            sameCourseVariants = listOf(teacherVariant),
            differentCourses = emptyList(),
        )
        val all = listOf(active, teacherVariant).associate { it.meetingId to detail(it) }

        val pager = CourseDetailPager.build(active, attachment, all)
        assertNotNull(pager)

        assertEquals(1, pager!!.pages.size)
        assertEquals("active", pager.pages.single().anchorMeetingId.value)
    }

    @Test fun alternative_order_is_stable_across_input_and_local_id_changes() {
        fun identities(bId: String, cId: String, reverse: Boolean): List<String> {
            val active = block("A", "active-$bId", 2)
            val alternatives = listOf(block("C", cId, 4), block("B", bId, 3)).let { if (reverse) it.reversed() else it }
            val attachment = SchoolGhostAttachment(
                SchoolGhostProjection.canonicalPresentationKey(active),
                emptyList(),
                alternatives,
            )
            val all = (listOf(active) + alternatives).associate { it.meetingId to detail(it) }
            val pager = CourseDetailPager.build(active, attachment, all)
            assertNotNull(pager)
            return pager!!.pages.map { it.stableCourseIdentity }
        }

        assertEquals(identities("b-1", "c-1", false), identities("b-99", "c-99", true))
    }
}
