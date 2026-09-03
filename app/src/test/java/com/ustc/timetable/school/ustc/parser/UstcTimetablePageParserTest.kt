package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.timetable.domain.SemesterId
import org.junit.Assert.assertEquals
import org.junit.Test

class UstcTimetablePageParserTest {
    private val parser = UstcTimetablePageParser()

    @Test fun parses_every_real_canonical_meeting_assignment() {
        val expected = listOf(
            entry("电化学研究方法", "CHEM5012P.01", 2, "8-10", "2-17", "TH-A101", "孔爽、李少锋", "181405:197551"),
            entry("电化学研究方法", "CHEM5012P.01", 5, "8-9", "2-17", "TH-A101", "孔爽、李少锋", "181405:197551"),
            entry("高等无机化学", "CHEM5013P.02", 5, "3-5", "2-6", "TH-B301", "吴长征", "180239:194641"),
            entry("高等无机化学", "CHEM5013P.02", 5, "3-5", "7-12", "TH-B301", "刘斯", "180239:194641"),
            entry("高等无机化学", "CHEM5013P.02", 5, "3-5", "13-18", "TH-B301", "郭宇桥", "180239:194641"),
            entry("结晶化学导论", "CHEM6001P.01", 4, "6-8", "2-18", "TH-B301", "朱永春", "180238:194640"),
            entry("应用物理化学II", "CHEM6016P.01", 1, "6-7", "10-17", "TH-A301", "葛君杰", "181406:196458"),
            entry("应用物理化学II", "CHEM6016P.01", 4, "3-5", "10-17", "TH-A301", "葛君杰", "181406:196458"),
            entry("固体化学原理", "CHEM6023P.02", 2, "3-5", "2-9", "TH-B301", "张晓东", "181357:196409"),
            entry("固体化学原理", "CHEM6023P.02", 2, "3-5", "10-18", "TH-B301", "郭宇桥", "181357:196409"),
            entry("日常交流英语", "FORL6102U.45", 3, "6-7", "2-16", "2204", "Steve Masashi Musha", "182318:197320"),
            entry("新时代中国特色社会主义理论与实践", "MARX6102U.50", 2, "11-13", "8-18", "TH-B201", "韩笑", "182125:197123"),
            entry("无机新能源材料与应用", "MSEN6406P.01", 1, "9-10", "2-9", "TH-C101", "陈立锋", "181255:196349"),
            entry("无机新能源材料与应用", "MSEN6406P.01", 1, "11-13", "2-9", "TH-C101", "陈立锋", "181255:196349"),
            entry("自然辩证法概论", "PHIL6101U.10", 3, "3-5", "2-7", "3B101", "吕凌峰", "182155:197153"),
        )

        assertEquals(expected, parser.parse(RealUstcFixturePages.timetable()))
    }

    @Test fun layout_start_unit_and_period_count_map_the_non_wall_clock_end_time() {
        val crystallography = parser.parse(RealUstcFixturePages.timetable())
            .single { it.courseCode == "CHEM6001P.01" }

        assertEquals("6-8", crystallography.periodText)
    }

    @Test fun teacher_week_segments_are_not_merged() {
        val segments = parser.parse(RealUstcFixturePages.timetable())
            .filter { it.courseCode == "CHEM5013P.02" }

        assertEquals(
            listOf("2-6" to "吴长征", "7-12" to "刘斯", "13-18" to "郭宇桥"),
            segments.map { it.weekText to it.teacherText },
        )
    }

    @Test fun digest_must_corroborate_the_schedule_slot_week_unions() {
        val mismatchedDigest = RealUstcFixturePages.fixture("xhr/response-04-week-indices-digest.json")
            .replaceFirst("\"c274\": \"2~18\"", "\"c274\": \"1~18\"")

        assertParseFailed {
            parser.parse(RealUstcFixturePages.timetable(digest = mismatchedDigest))
        }
    }

    @Test fun unresolved_lesson_link_is_parse_failure() {
        assertParseFailed {
            parser.parse(minimalTimetablePage(scheduleLessonId = 999))
        }
    }

    @Test fun unresolved_schedule_group_link_is_parse_failure() {
        assertParseFailed {
            parser.parse(minimalTimetablePage(scheduleGroupId = 999))
        }
    }

    @Test fun distinct_schedule_groups_are_not_merged_into_a_false_co_taught_assignment() {
        val page = distinctScheduleGroupsPage()

        val entries = parser.parse(page)

        assertEquals(2, entries.size)
        assertEquals(setOf("教师甲", "教师乙"), entries.map { it.teacherText }.toSet())
    }

    @Test fun distinct_schedule_groups_remain_distinct_through_normalization() {
        val parsed = parser.parse(distinctScheduleGroupsPage())

        val snapshot = UstcSnapshotNormalizer().normalize(
            selection = listOf(
                UstcCourseSummary("TEST.01", "测试课程", 2.0, "测试院系", "理论课", null, null),
            ),
            timetable = parsed,
            meta = UstcSemesterMetaPartial(null, null, null, null, null, null, null),
            semesterId = SemesterId("semester-test"),
        )

        assertEquals(2, snapshot.meetings.size)
        assertEquals(2, snapshot.meetings.map { it.id }.toSet().size)
        assertEquals(setOf(listOf("教师甲"), listOf("教师乙")), snapshot.meetings.map { it.teacherNames }.toSet())
    }

    @Test fun missing_required_weekday_is_parse_failure() {
        assertParseFailed {
            parser.parse(minimalTimetablePage(includeWeekday = false))
        }
    }

    @Test fun null_schedule_teacher_uses_the_unique_week_scoped_assignment() {
        val entry = parser.parse(
            minimalTimetablePage(
                scheduleWeek = 1,
                personNameJson = "null",
                teacherAssignmentsJson = """
                    [
                      {"name":"教师乙","weekIndices":[2]},
                      {"name":"教师甲","weekIndices":[1]}
                    ]
                """.trimIndent(),
            ),
        ).single()

        assertEquals("教师甲", entry.teacherText)
    }

    @Test fun missing_schedule_teacher_without_a_week_scoped_assignment_is_parse_failure() {
        assertParseFailed {
            parser.parse(
                minimalTimetablePage(
                    scheduleWeek = 1,
                    personNameJson = null,
                    teacherAssignmentsJson = """[{"name":"教师甲","weekIndices":[2]}]""",
                ),
            )
        }
    }

    @Test fun null_schedule_teacher_without_a_week_scoped_assignment_is_parse_failure() {
        assertParseFailed {
            parser.parse(
                minimalTimetablePage(
                    scheduleWeek = 1,
                    personNameJson = "null",
                    teacherAssignmentsJson = """[{"name":"教师甲","weekIndices":[2]}]""",
                ),
            )
        }
    }

    @Test fun blank_schedule_teacher_without_a_week_scoped_assignment_is_parse_failure() {
        assertParseFailed {
            parser.parse(
                minimalTimetablePage(
                    scheduleWeek = 1,
                    personNameJson = "\"   \"",
                    teacherAssignmentsJson = """[{"name":"教师甲","weekIndices":[2]}]""",
                ),
            )
        }
    }

    @Test fun null_schedule_teacher_with_ambiguous_week_scoped_assignments_is_parse_failure() {
        assertParseFailed {
            parser.parse(
                minimalTimetablePage(
                    scheduleWeek = 1,
                    personNameJson = "null",
                    teacherAssignmentsJson = """
                        [
                          {"name":"教师甲","weekIndices":[1]},
                          {"name":"教师乙","weekIndices":[1]}
                        ]
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test fun malformed_datum_json_is_parse_failure() {
        assertParseFailed {
            parser.parse(RealUstcFixturePages.timetable(datum = "{"))
        }
    }

    private fun entry(
        name: String,
        code: String,
        weekday: Int,
        periods: String,
        weeks: String,
        location: String,
        teacher: String,
        sourceAssignmentKey: String,
    ) = UstcTimetableEntry(
        name,
        code,
        "周${WEEKDAYS[weekday - 1]}",
        periods,
        weeks,
        location,
        teacher,
        sourceAssignmentKey,
    )

    private fun distinctScheduleGroupsPage() = RealUstcFixturePages.timetable(
        layout = """{"result":{"courseUnitList":[{"indexNo":1,"startTime":750}]}}""",
        datum = """
            {"result":{
              "lessonList":[{"id":1,"code":"TEST.01","courseName":"测试课程","teacherAssignmentList":[]}],
              "scheduleGroupList":[{"id":10,"lessonId":1},{"id":11,"lessonId":1}],
              "scheduleList":[
                {"lessonId":1,"scheduleGroupId":10,"weekday":1,"startTime":750,"periods":1,"weekIndex":1,"room":{"nameZh":"测试教室"},"personName":"教师甲"},
                {"lessonId":1,"scheduleGroupId":11,"weekday":1,"startTime":750,"periods":1,"weekIndex":1,"room":{"nameZh":"测试教室"},"personName":"教师乙"}
              ]
            }}
        """.trimIndent(),
        digest = """{"result":{"cardA":"1","cardB":"1"}}""",
    )

    private fun minimalTimetablePage(
        scheduleLessonId: Int = 1,
        scheduleGroupId: Int = 10,
        includeWeekday: Boolean = true,
        scheduleWeek: Int = 1,
        personNameJson: String? = "\"教师甲\"",
        teacherAssignmentsJson: String = """[{"name":"教师甲","weekIndices":[1]}]""",
    ) = RealUstcFixturePages.timetable(
        layout = """
            {"result":{"courseUnitList":[
              {"indexNo":1,"startTime":750,"endTime":835},
              {"indexNo":2,"startTime":840,"endTime":925}
            ]}}
        """.trimIndent(),
        datum = """
            {"result":{
              "lessonList":[{"id":1,"code":"TEST.01","courseName":"测试课程","teacherAssignmentList":$teacherAssignmentsJson}],
              "scheduleGroupList":[{"id":10,"lessonId":1}],
              "scheduleList":[{
                "lessonId":$scheduleLessonId,
                "scheduleGroupId":$scheduleGroupId,
                ${if (includeWeekday) "\"weekday\":1," else ""}
                "startTime":750,
                "periods":2,
                "weekIndex":$scheduleWeek,
                "room":{"nameZh":"测试教室"}
                ${personNameJson?.let { ",\n\"personName\":$it" }.orEmpty()}
              }]
            }}
        """.trimIndent(),
        digest = """{"result":{"card":"1"}}""",
    )

    private companion object {
        val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}
