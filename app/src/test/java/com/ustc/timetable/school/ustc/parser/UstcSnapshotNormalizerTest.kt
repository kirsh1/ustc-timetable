package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.SyncFailure
import com.ustc.timetable.sync.syncErrorOrNull
import com.ustc.timetable.timetable.domain.ItemSource
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.Term
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UstcSnapshotNormalizerTest {
    @Test fun match_by_course_code() {
        val snapshot = normalize(
            selection = listOf(course(code = " C1 ", name = "课程A")),
            timetable = listOf(entry(code = " C1 ", name = "另一显示名")),
        )

        assertEquals("C1", snapshot.courses.single().sourceCourseKey)
        assertEquals(snapshot.courses.single().id, snapshot.meetings.single().courseId)
    }

    @Test fun missing_code_falls_back_to_exact_name() {
        val snapshot = normalize(
            selection = listOf(course(code = "", name = " 课程A ")),
            timetable = listOf(entry(code = null, name = " 课程A ")),
        )

        assertEquals("name:课程A", snapshot.courses.single().sourceCourseKey)
        assertEquals("课程A", snapshot.courses.single().name)
    }

    @Test fun provided_unknown_code_does_not_fallback_to_name() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(
                selection = listOf(course(code = "C1", name = "课程A")),
                timetable = listOf(entry(code = "UNKNOWN", name = "课程A")),
            )
        }
    }

    @Test fun ambiguous_name_fallback_is_validation_failure() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(
                selection = listOf(
                    course(code = "C1", name = "课程A"),
                    course(code = "C2", name = "课程A"),
                ),
                timetable = listOf(entry(code = null, name = "课程A")),
            )
        }
    }

    @Test fun duplicate_nonblank_course_code_is_validation_failure() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(
                selection = listOf(
                    course(code = " C1 ", name = "课程A"),
                    course(code = "C1", name = "课程B"),
                ),
                timetable = emptyList(),
            )
        }
    }

    @Test fun duplicate_source_key_is_validation_failure() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(
                selection = listOf(
                    course(code = "", name = " 课程A "),
                    course(code = " ", name = "课程A"),
                ),
                timetable = emptyList(),
            )
        }
    }

    @Test fun unmatched_timetable_course_is_validation_failure() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(
                selection = listOf(course(code = "C1", name = "课程A")),
                timetable = listOf(entry(code = null, name = "课程B")),
            )
        }
    }

    @Test fun source_course_key_uses_trimmed_code_or_name() {
        val snapshot = normalize(
            selection = listOf(
                course(code = " C2 ", name = "课程B"),
                course(code = "", name = " 课程A "),
            ),
            timetable = emptyList(),
        )

        assertEquals(listOf("C2", "name:课程A"), snapshot.courses.map { it.sourceCourseKey })
        assertEquals(listOf("C2", ""), snapshot.courses.map { it.courseCode })
    }

    @Test fun normalized_course_ids_are_unique() {
        val snapshot = normalize(
            selection = listOf(course("C1", "课程A"), course("C2", "课程B")),
            timetable = emptyList(),
        )

        assertEquals(2, snapshot.courses.map { it.id }.toSet().size)
        assertTrue(snapshot.courses.none { it.id.value == "pending" })
    }

    @Test fun normalized_meeting_ids_are_unique() {
        val snapshot = normalize(
            timetable = listOf(
                entry(location = "教室A", weeks = "2-6"),
                entry(location = "教室B", weeks = "7-12"),
            ),
        )

        assertEquals(2, snapshot.meetings.map { it.id }.toSet().size)
        assertTrue(snapshot.meetings.none { it.id.value == "pending" })
    }

    @Test fun every_meeting_references_existing_course() {
        val snapshot = normalize(
            selection = listOf(course("C1", "课程A"), course("C2", "课程B")),
            timetable = listOf(entry("C1", "课程A"), entry("C2", "课程B")),
        )

        val courseIds = snapshot.courses.map { it.id }.toSet()
        assertTrue(snapshot.meetings.all { it.courseId in courseIds })
    }

    @Test fun input_permutation_keeps_normalized_identity_stable() {
        val selection = listOf(course("C2", "课程B"), course("C1", "课程A"))
        val timetable = listOf(
            entry("C2", "课程B", weekday = "周二", location = "教室B"),
            entry("C1", "课程A", weekday = "周一", location = "教室A"),
        )

        val first = normalize(selection, timetable)
        val second = normalize(selection.reversed(), timetable.reversed())

        assertEquals(first, second)
    }

    @Test fun weekday_monday_to_sunday() {
        val forms = listOf("一", "周二", "星期三", "四", "周五", "星期六", "星期天")
        val snapshot = normalize(
            timetable = forms.mapIndexed { index, weekday ->
                entry(weekday = weekday, periods = (index + 1).toString())
            },
        )

        assertEquals((1..7).toList(), snapshot.meetings.map { it.weekday })
    }

    @Test fun malformed_weekday_is_parse_failed() {
        assertSyncError(SyncError.ParseFailed) {
            normalize(timetable = listOf(entry(weekday = "礼拜一")))
        }
    }

    @Test fun unapproved_weekday_composition_is_parse_failed() {
        assertSyncError(SyncError.ParseFailed) {
            normalize(timetable = listOf(entry(weekday = "星期周一")))
        }
    }

    @Test fun period_single_and_range() {
        val snapshot = normalize(
            timetable = listOf(
                entry(periods = "3", location = "A"),
                entry(periods = "第4节", location = "B"),
                entry(periods = "3-5", location = "C"),
                entry(periods = "第6–8节", location = "D"),
            ),
        )

        assertEquals(listOf(3 to 3, 3 to 5, 4 to 4, 6 to 8), snapshot.meetings.map { it.startPeriod to it.endPeriod })
    }

    @Test fun malformed_period_is_parse_failed() {
        assertSyncError(SyncError.ParseFailed) {
            normalize(timetable = listOf(entry(periods = "第X节")))
        }
    }

    @Test fun period_outside_1_13_is_validation_failed() {
        listOf("0", "13-14", "5-3").forEach { periods ->
            assertSyncError(SyncError.ValidationFailed) {
                normalize(timetable = listOf(entry(periods = periods)))
            }
        }
    }

    @Test fun numeric_and_decorated_week_forms_are_supported() {
        val forms = listOf("2-6", "2–6", "2-6,8,10-12", "第2-6周")
        val snapshot = normalize(
            timetable = forms.mapIndexed { index, weeks -> entry(weeks = weeks, location = "L$index") },
        )

        assertEquals(
            listOf("2-6", "2-6", "2-6", "2-6,8,10-12"),
            snapshot.meetings.map { it.weekPattern.format() },
        )
    }

    @Test fun bounded_odd_even_week_forms_are_supported() {
        val snapshot = normalize(
            timetable = listOf(
                entry(weeks = "1-16周(单)", location = "oddRange"),
                entry(weeks = "1-16周(双)", location = "evenRange"),
                entry(weeks = "单周", location = "oddBare"),
                entry(weeks = "双周", location = "evenBare"),
            ),
            meta = meta(totalWeeks = 18),
        )

        assertEquals("2,4,6,8,10,12,14,16", snapshot.meetings.single { it.location == "evenRange" }.weekPattern.format())
        assertEquals("1,3,5,7,9,11,13,15", snapshot.meetings.single { it.location == "oddRange" }.weekPattern.format())
        assertEquals("2,4,6,8,10,12,14,16,18", snapshot.meetings.single { it.location == "evenBare" }.weekPattern.format())
        assertEquals("1,3,5,7,9,11,13,15,17", snapshot.meetings.single { it.location == "oddBare" }.weekPattern.format())
    }

    @Test fun bare_parity_without_total_weeks_is_parse_failed() {
        listOf("单周", "双周").forEach { weeks ->
            assertSyncError(SyncError.ParseFailed) {
                normalize(timetable = listOf(entry(weeks = weeks)), meta = meta(totalWeeks = null))
            }
        }
    }

    @Test fun parsed_week_above_total_weeks_is_validation_failed() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(timetable = listOf(entry(weeks = "1-17")), meta = meta(totalWeeks = 16))
        }
    }

    @Test fun empty_parity_within_total_weeks_is_validation_failed() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(timetable = listOf(entry(weeks = "双周")), meta = meta(totalWeeks = 1))
        }
    }

    @Test fun invalid_total_weeks_is_validation_failed() {
        listOf(0, 64).forEach { totalWeeks ->
            assertSyncError(SyncError.ValidationFailed) {
                normalize(timetable = emptyList(), meta = meta(totalWeeks = totalWeeks))
            }
        }
    }

    @Test fun malformed_week_text_is_parse_failed() {
        assertSyncError(SyncError.ParseFailed) {
            normalize(timetable = listOf(entry(weeks = "2foo6")))
        }
    }

    @Test fun three_teacher_week_segments_create_three_assignments() {
        val snapshot = normalize(
            timetable = listOf(
                entry(
                    weeks = "2-18",
                    teachers = "吴长征 2-6周 / 刘斯 7-12周 / 郭宇桥 13-18周",
                ),
            ),
            meta = meta(totalWeeks = 18),
        )

        assertEquals(
            listOf(
                "2-6" to listOf("吴长征"),
                "7-12" to listOf("刘斯"),
                "13-18" to listOf("郭宇桥"),
            ),
            snapshot.meetings.map { it.weekPattern.format() to it.teacherNames },
        )
    }

    @Test fun teacher_segment_outside_base_weeks_is_rejected() {
        assertSyncError(SyncError.ValidationFailed) {
            normalize(
                timetable = listOf(entry(weeks = "2-6", teachers = "教师A 2-7周")),
                meta = meta(totalWeeks = 18),
            )
        }
    }

    @Test fun ordinary_teacher_names_are_trimmed_deduped_and_sorted() {
        val snapshot = normalize(
            timetable = listOf(entry(teachers = " 教师B / 教师A、教师B，教师C ; 教师D；教师A ")),
        )

        assertEquals(listOf("教师A", "教师B", "教师C", "教师D"), snapshot.meetings.single().teacherNames)
    }

    @Test fun unrecognized_teacher_segment_falls_back_to_plain_teacher_list() {
        val snapshot = normalize(timetable = listOf(entry(teachers = "教师A 实验班 / 教师B")))

        assertEquals(listOf("教师A 实验班", "教师B"), snapshot.meetings.single().teacherNames)
    }

    @Test fun unsupported_teacher_week_segmentation_is_not_partially_guessed() {
        val snapshot = normalize(timetable = listOf(entry(teachers = "教师A 2-6周，教师B 7-12周")))

        assertEquals(listOf("教师A 2-6周", "教师B 7-12周"), snapshot.meetings.single().teacherNames)
        assertEquals("2-6", snapshot.meetings.single().weekPattern.format())
    }

    @Test fun empty_teacher_text_is_allowed() {
        val snapshot = normalize(timetable = listOf(entry(teachers = "  ")))

        assertTrue(snapshot.meetings.single().teacherNames.isEmpty())
    }

    @Test fun location_split_uses_multiple_dto_entries() {
        val snapshot = normalize(
            timetable = listOf(
                entry(weeks = "2-6", location = "TH-A"),
                entry(weeks = "7-12", location = "TH-B"),
            ),
        )

        assertEquals(listOf("TH-A", "TH-B"), snapshot.meetings.map { it.location })
        assertEquals(listOf("2-6", "7-12"), snapshot.meetings.map { it.weekPattern.format() })
    }

    @Test fun duplicate_entries_are_canonicalized() {
        val duplicate = entry(teachers = "教师A")
        val snapshot = normalize(timetable = listOf(duplicate, duplicate))

        assertEquals(1, snapshot.meetings.size)
    }

    @Test fun same_assignment_week_slices_union() {
        val snapshot = normalize(
            timetable = listOf(
                entry(weeks = "2-6", teachers = "教师A"),
                entry(weeks = "8", teachers = "教师A"),
            ),
        )

        assertEquals(1, snapshot.meetings.size)
        assertEquals("2-6,8", snapshot.meetings.single().weekPattern.format())
    }

    @Test fun same_week_and_location_merge_teacher_names() {
        val snapshot = normalize(
            timetable = listOf(
                entry(weeks = "2-6", teachers = "教师B"),
                entry(weeks = "2-6", teachers = "教师A"),
            ),
        )

        assertEquals(1, snapshot.meetings.size)
        assertEquals(listOf("教师A", "教师B"), snapshot.meetings.single().teacherNames)
    }

    @Test fun different_teacher_assignments_remain_split() {
        val snapshot = normalize(
            timetable = listOf(
                entry(weeks = "2-6", teachers = "教师A"),
                entry(weeks = "7-12", teachers = "教师B"),
            ),
        )

        assertEquals(2, snapshot.meetings.size)
        assertEquals(listOf(listOf("教师A"), listOf("教师B")), snapshot.meetings.map { it.teacherNames })
    }

    @Test fun input_order_does_not_change_output() {
        val entries = listOf(
            entry(weeks = "8", teachers = "教师A", location = "A"),
            entry(weeks = "2-6", teachers = "教师A", location = "A"),
            entry(weeks = "7-12", teachers = "教师B", location = "B"),
        )

        assertEquals(normalize(timetable = entries), normalize(timetable = entries.reversed()))
    }

    @Test fun missing_credits_and_course_type_create_deterministic_warnings() {
        val courses = listOf(
            course("C2", "课程B", credits = null, courseType = " "),
            course("C1", "课程A", credits = null, courseType = null),
        )

        val first = normalize(selection = courses, timetable = emptyList())
        val second = normalize(selection = courses.reversed(), timetable = emptyList())

        assertEquals(first.issues, second.issues)
        assertEquals(4, first.issues.size)
        assertTrue(first.issues.all { it.severity == NormalizationIssue.Severity.WARNING })
        assertTrue(first.issues.all { !it.message.contains("教师") && !it.message.contains("教室") })
    }

    @Test fun successful_snapshot_uses_school_source() {
        val snapshot = normalize()

        assertEquals(ItemSource.SCHOOL, snapshot.courses.single().source)
        assertEquals(ItemSource.SCHOOL, snapshot.meetings.single().source)
    }

    @Test fun different_semesters_produce_different_temporary_course_ids() {
        val first = normalize(semesterId = SemesterId("semester-a"))
        val second = normalize(semesterId = SemesterId("semester-b"))

        assertNotEquals(first.courses.single().id, second.courses.single().id)
    }

    private fun normalize(
        selection: List<UstcCourseSummary> = listOf(course()),
        timetable: List<UstcTimetableEntry> = listOf(entry()),
        meta: UstcSemesterMetaPartial = meta(),
        semesterId: SemesterId = SemesterId("semester-a"),
    ): NormalizedSchoolSnapshot = UstcSnapshotNormalizer().normalize(selection, timetable, meta, semesterId)

    private fun course(
        code: String = "C1",
        name: String = "课程A",
        credits: Double? = 3.0,
        courseType: String? = "专业课",
    ) = UstcCourseSummary(
        courseCode = code,
        name = name,
        credits = credits,
        department = "院系A",
        courseType = courseType,
        teacherSummary = null,
        weeksText = null,
    )

    private fun entry(
        code: String? = "C1",
        name: String = "课程A",
        weekday: String = "周一",
        periods: String = "3-5",
        weeks: String = "2-6",
        location: String = "教室A",
        teachers: String = "教师A",
    ) = UstcTimetableEntry(
        courseName = name,
        courseCode = code,
        weekdayText = weekday,
        periodText = periods,
        weekText = weeks,
        locationText = location,
        teacherText = teachers,
    )

    private fun meta(totalWeeks: Int? = 20) = UstcSemesterMetaPartial(
        displayName = "2026-2027 秋季",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        week1Start = LocalDate.of(2026, 8, 31),
        totalWeeks = totalWeeks,
        startDate = LocalDate.of(2026, 8, 30),
        endDate = LocalDate.of(2027, 1, 15),
    )

    private fun assertSyncError(expected: SyncError, block: () -> Unit) {
        val error = try {
            block()
            fail("expected SyncFailure($expected)")
            return
        } catch (error: SyncFailure) {
            error
        }
        assertEquals(expected, error.syncErrorOrNull())
    }
}
