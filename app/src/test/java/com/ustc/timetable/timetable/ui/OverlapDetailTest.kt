package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.*
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class OverlapDetailTest {
    @Test fun alternate_school_anchor_prefers_current_week_even_on_another_day() {
        val course=Course(CourseId("s"),SemesterId("s"),"stable","CODE","school",3.0,null)
        val ghost=CourseMeeting(MeetingId("ghost"),course.id,1,3,5,WeekPattern.of(2),"room",emptyList())
        val current=ghost.copy(id=MeetingId("current"),weekday=2,weekPattern=WeekPattern.of(1))
        val candidate=OverlapEntry(UiSchoolTimedBlock("s:stable",ghost.id,1,LocalTime.of(10,0),LocalTime.of(12,0),ghost.weekPattern,course.name,ghost.location,ghost.teacherNames),Instant.EPOCH)
        val pages=OverlapDetail.build(entry("manual"),OverlapAttachment(crossWeek=listOf(candidate)),OverlapMarkerKind.CROSS_WEEK,1,
            buildCourseDetailsByMeetingId(listOf(course),listOf(ghost,current)),mapOf(item("manual").id to item("manual")))
        assertEquals(current,pages[1].school!!.selectedMeeting)
    }
    @Test fun mixed_details_deduplicate_school_identity_and_choose_earliest_resolvable_anchor() {
        val course=Course(CourseId("s"),SemesterId("s"),"stable","CODE","same name",3.0,null)
        fun meeting(id:String,week:Int)=CourseMeeting(MeetingId(id),course.id,1,3,5,WeekPattern.of(week),"room",listOf("teacher"))
        val early=meeting("z",2); val late=meeting("a",4)
        fun school(m:CourseMeeting)=OverlapEntry(UiSchoolTimedBlock("s:stable",m.id,1,LocalTime.of(10,0),LocalTime.of(12,0),m.weekPattern,course.name,m.location,m.teacherNames),Instant.EPOCH)
        val representative=entry("manual")
        val result=OverlapDetail.build(representative,OverlapAttachment(crossWeek=listOf(school(late),school(early))),
            OverlapMarkerKind.CROSS_WEEK,1,buildCourseDetailsByMeetingId(listOf(course),listOf(late,early)),mapOf(item("manual").id to item("manual")))
        assertEquals(listOf("manual:manual","school:s:stable"),result.map { it.identity })
        assertEquals(early,result[1].school!!.selectedMeeting)
        assertEquals(2,result[1].school!!.allMeetings.size)
    }
    @Test fun tiny_card_fallback_keeps_both_current_and_cross_week_candidates() {
        val a=entry("a"); val b=entry("b"); val c=entry("c")
        val all=listOf("a","b","c").map(::item).associateBy { it.id }
        val pages=OverlapDetail.build(a,OverlapAttachment(crossWeek=listOf(b),currentConflicts=listOf(c)),
            OverlapMarkerKind.ALL_CONTENT,1,emptyMap(),all)
        assertEquals(listOf("manual:a","manual:b","manual:c"),pages.map { it.identity })
    }
    private fun item(id: String) = ManualScheduleItem(ManualItemId(id), SemesterId("s"), "same name", 1,
        LocalTime.of(10,0), LocalTime.of(12,0), WeekPattern.of(1), "room", "note", Instant.EPOCH, Instant.EPOCH)
    private fun entry(id: String) = OverlapEntry(manualTimedBlock(item(id)), Instant.EPOCH)
    @Test fun representative_first_and_same_name_manuals_remain_independent() {
        val a = entry("z"); val b = entry("a")
        val result = OverlapDetail.build(a, OverlapAttachment(crossWeek = listOf(b, b)), OverlapMarkerKind.CROSS_WEEK,
            1, emptyMap(), listOf(item("z"), item("a")).associateBy { it.id })
        assertEquals(listOf("manual:z", "manual:a"), result.map { it.identity })
        assertEquals(ManualItemId("a"), result[1].manual!!.id)
    }
    @Test fun deleted_candidate_is_removed_and_missing_representative_closes_details() {
        val a = entry("a"); val b = entry("b")
        val attachment = OverlapAttachment(currentConflicts = listOf(b))
        assertEquals(1, OverlapDetail.build(a, attachment, OverlapMarkerKind.CURRENT_CONFLICT, 1, emptyMap(), mapOf(item("a").id to item("a"))).size)
        assertTrue(OverlapDetail.build(a, attachment, OverlapMarkerKind.CURRENT_CONFLICT, 1, emptyMap(), emptyMap()).isEmpty())
    }
    @Test fun current_and_cross_week_lists_are_not_mixed() {
        val a=entry("a"); val b=entry("b"); val c=entry("c")
        val all=listOf("a","b","c").map(::item).associateBy { it.id }
        val result=OverlapDetail.build(a, OverlapAttachment(crossWeek=listOf(b),currentConflicts=listOf(c)), OverlapMarkerKind.CURRENT_CONFLICT, 1, emptyMap(),all)
        assertEquals(listOf("manual:a","manual:c"),result.map { it.identity })
    }
    @Test fun variant_marker_does_not_create_pages() {
        val a=entry("a"); val b=entry("b")
        assertEquals(1,OverlapDetail.build(a,OverlapAttachment(sameCourseVariants=listOf(b)),OverlapMarkerKind.VARIANT,1,emptyMap(),listOf(item("a"),item("b")).associateBy { it.id }).size)
    }
}
