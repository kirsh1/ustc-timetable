package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.school.ustc.portal.SchoolPortalSource
import com.ustc.timetable.timetable.domain.Term
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ParserContractsTest {
    @Test fun course_summary_preserves_raw_boundary_fields() {
        val summary = UstcCourseSummary(
            courseCode = "  CS1001  ",
            name = "  课程A  ",
            credits = 3.5,
            department = "  院系A  ",
            courseType = "  类型A  ",
            teacherSummary = "  教师A  ",
            weeksText = "  第1-3周  ",
        )

        assertEquals("  CS1001  ", summary.courseCode)
        assertEquals("  课程A  ", summary.name)
        assertEquals(3.5, summary.credits!!, 0.0)
        assertEquals("  院系A  ", summary.department)
        assertEquals("  类型A  ", summary.courseType)
        assertEquals("  教师A  ", summary.teacherSummary)
        assertEquals("  第1-3周  ", summary.weeksText)
    }

    @Test fun course_summary_allows_missing_credits() {
        val summary = UstcCourseSummary("C", "N", null, null, null, null, null)

        assertNull(summary.credits)
    }

    @Test fun timetable_entry_preserves_raw_school_strings() {
        val entry = UstcTimetableEntry(
            courseName = "  课程A  ",
            courseCode = "  CS1001  ",
            weekdayText = "  星期三  ",
            periodText = "  3-4节  ",
            weekText = "  1-8周  ",
            locationText = "  教室A  ",
            teacherText = "  教师A  ",
        )

        assertEquals("  课程A  ", entry.courseName)
        assertEquals("  CS1001  ", entry.courseCode)
        assertEquals("  星期三  ", entry.weekdayText)
        assertEquals("  3-4节  ", entry.periodText)
        assertEquals("  1-8周  ", entry.weekText)
        assertEquals("  教室A  ", entry.locationText)
        assertEquals("  教师A  ", entry.teacherText)
    }

    @Test fun timetable_entry_allows_missing_course_code() {
        val entry = UstcTimetableEntry("N", null, "W", "P", "K", "L", "T")

        assertNull(entry.courseCode)
    }

    @Test fun semester_meta_allows_partial_fields() {
        val meta = UstcSemesterMetaPartial(
            displayName = null,
            academicYear = "2026-2027",
            term = Term.AUTUMN,
            week1Start = null,
            totalWeeks = null,
            startDate = LocalDate.of(2026, 8, 30),
            endDate = null,
        )

        assertNull(meta.displayName)
        assertEquals("2026-2027", meta.academicYear)
        assertEquals(Term.AUTUMN, meta.term)
        assertNull(meta.week1Start)
        assertNull(meta.totalWeeks)
        assertEquals(LocalDate.of(2026, 8, 30), meta.startDate)
        assertNull(meta.endDate)
    }

    @Test fun parser_interfaces_accept_existing_UstcPortalPage() {
        val page = UstcPortalPage("<html></html>", "https://fixture.example/page")
        var received: UstcPortalPage? = null
        val parser = object : CourseSelectionPageParser {
            override fun parse(page: UstcPortalPage): List<UstcCourseSummary> {
                received = page
                return emptyList()
            }
        }

        parser.parse(page)

        assertSame(page, received)
    }

    @Test fun fake_selection_parser_can_implement_interface() {
        val expected = UstcCourseSummary("C", "N", null, null, null, null, null)
        val parser = object : CourseSelectionPageParser {
            override fun parse(page: UstcPortalPage) = listOf(expected)
        }

        assertEquals(listOf(expected), parser.parse(page("selection")))
    }

    @Test fun fake_timetable_parser_can_implement_interface() {
        val expected = UstcTimetableEntry("N", null, "W", "P", "K", "L", "T")
        val parser = object : TimetablePageParser {
            override fun parse(page: UstcPortalPage) = listOf(expected)
        }

        assertEquals(listOf(expected), parser.parse(page("timetable")))
    }

    @Test fun fake_meta_parser_can_return_confidence_false() {
        val expected = UstcSemesterMetaPartial(null, null, null, null, null, null, null)
        val parser = object : SemesterMetaParser {
            override fun parse(selection: UstcPortalPage, timetable: UstcPortalPage) =
                SemesterMetaResult(expected, isConfident = false)
        }

        val result = parser.parse(page("selection"), page("timetable"))

        assertEquals(expected, result.meta)
        assertFalse(result.isConfident)
    }

    @Test fun fake_school_portal_source_can_supply_both_pages() = runTest {
        val selection = page("selection")
        val timetable = page("timetable")
        val source = object : SchoolPortalSource {
            override suspend fun fetchCourseSelectionPage() = selection
            override suspend fun fetchTimetablePage() = timetable
        }

        assertSame(selection, source.fetchCourseSelectionPage())
        assertSame(timetable, source.fetchTimetablePage())
    }

    private fun page(name: String) = UstcPortalPage(
        html = "<$name></$name>",
        finalUrl = "https://fixture.example/$name",
    )
}
