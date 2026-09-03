package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class UstcCourseSelectionPageParserTest {
    private val parser = UstcCourseSelectionPageParser()

    @Test fun parses_all_real_selected_courses_with_exact_authoritative_fields() {
        val expected = listOf(
            UstcCourseSummary("CHEM5012P.01", "电化学研究方法", 4.0, "化学物理系", "理论课", "李少锋、孔爽", "2~17"),
            UstcCourseSummary("CHEM5013P.02", "高等无机化学", 3.0, "化学系", "理论课", "吴长征、刘斯、郭宇桥", "2~18"),
            UstcCourseSummary("CHEM6001P.01", "结晶化学导论", 3.0, "化学系", "理论课", "朱永春", "2~18"),
            UstcCourseSummary("CHEM6016P.01", "应用物理化学II", 2.0, "化学物理系", "理论课", "葛君杰", "10~17"),
            UstcCourseSummary("CHEM6023P.02", "固体化学原理", 3.0, "应用化学系", "理论课", "张晓东、郭宇桥", "2~18"),
            UstcCourseSummary("FORL6102U.45", "日常交流英语", 2.0, "外语教学中心", "理论课", "Steve Masashi Musha", "2~16"),
            UstcCourseSummary("MARX6102U.50", "新时代中国特色社会主义理论与实践", 2.0, "马克思主义学院", "理论课", "韩笑", "8~18"),
            UstcCourseSummary("MSEN6406P.01", "无机新能源材料与应用", 2.0, "材料科学与工程系", "理论课", "陈立锋", "2~9"),
            UstcCourseSummary("PHIL6101U.10", "自然辩证法概论", 1.0, "马克思主义学院", "理论课", "吕凌峰", "2~7"),
        )

        assertEquals(expected, parser.parse(RealUstcFixturePages.selection()))
    }

    @Test fun missing_optional_course_fields_and_credits_remain_null() {
        val payload = """
            [{"id":1,"code":"TEST.01","course":{"nameZh":"可空字段测试"}}]
        """.trimIndent()

        val course = parser.parse(RealUstcFixturePages.selection(selectedLessons = payload)).single()

        assertEquals("TEST.01", course.courseCode)
        assertEquals("可空字段测试", course.name)
        assertNull(course.credits)
        assertNull(course.department)
        assertNull(course.courseType)
        assertNull(course.teacherSummary)
        assertNull(course.weeksText)
    }

    @Test fun explicit_null_optional_course_fields_remain_null() {
        val payload = """
            [{
              "id":1,
              "code":"TEST.02",
              "course":{"nameZh":"显式空字段测试","credits":null},
              "openDepartment":null,
              "courseType":null,
              "teachers":null,
              "weekText":null
            }]
        """.trimIndent()

        val course = parser.parse(RealUstcFixturePages.selection(selectedLessons = payload)).single()

        assertNull(course.credits)
        assertNull(course.department)
        assertNull(course.courseType)
        assertNull(course.teacherSummary)
        assertNull(course.weeksText)
    }

    @Test fun portal_page_keeps_document_and_evidenced_xhr_bodies_separate() {
        val page = UstcPortalPage(
            document = RealUstcFixturePages.response("document", "<html>document only</html>", "text/html"),
            xhr = UstcEndpointId.entries.associateWith { endpoint ->
                RealUstcFixturePages.response(endpoint.name.lowercase(), "{\"endpoint\":\"${endpoint.name}\"}", "application/json")
            },
        )

        assertEquals("<html>document only</html>", page.document?.body)
        assertEquals(
            setOf(
                UstcEndpointId.TIMETABLE_LAYOUT,
                UstcEndpointId.SELECTED_LESSONS,
                UstcEndpointId.TIMETABLE_DATUM,
                UstcEndpointId.WEEK_INDICES_DIGEST,
            ),
            page.xhr.keys,
        )
        assertFalse(page.document!!.body.contains(page.xhr.getValue(UstcEndpointId.SELECTED_LESSONS).body))
    }

    @Test fun malformed_selected_lessons_json_is_parse_failure() {
        assertParseFailed {
            parser.parse(RealUstcFixturePages.selection(selectedLessons = "not-json"))
        }
    }

    @Test fun unrelated_document_does_not_become_empty_success() {
        assertParseFailed {
            parser.parse(
                UstcPortalPage(
                    document = RealUstcFixturePages.response("unrelated", "<html><p>unrelated</p></html>", "text/html"),
                    xhr = emptyMap(),
                ),
            )
        }
    }

    @Test fun empty_selected_lessons_array_is_parse_failure() {
        assertParseFailed {
            parser.parse(RealUstcFixturePages.selection(selectedLessons = "[]"))
        }
    }
}
