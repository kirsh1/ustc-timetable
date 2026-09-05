package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.*
import java.time.Instant
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class OverlapDetailTest {
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
