package com.ustc.timetable.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotDifferTest {
    private val differ = SnapshotDiffer()

    @Test fun identical_snapshots_empty_diff() {
        val snapshot = content(meetings = listOf(meeting()))

        assertTrue(differ.diff(snapshot, snapshot).isEmpty())
    }

    @Test fun insert_earlier_meeting_does_not_shift_existing_pairing() {
        val a = meeting(start = 3, end = 3, location = "A")
        val b = meeting(start = 5, end = 5, location = "B")
        val inserted = meeting(start = 1, end = 1, location = "C")

        assertEquals(
            listOf(ScheduleChange.MeetingAdded("课程A", inserted.summary())),
            differ.diff(content(meetings = listOf(a, b)), content(meetings = listOf(inserted, a, b))),
        )
    }

    @Test fun remove_middle_meeting_does_not_shift_existing_pairing() {
        val a = meeting(start = 1, end = 1, location = "A")
        val b = meeting(start = 3, end = 3, location = "B")
        val c = meeting(start = 5, end = 5, location = "C")

        assertEquals(
            listOf(ScheduleChange.MeetingRemoved("课程A", b.summary())),
            differ.diff(content(meetings = listOf(a, b, c)), content(meetings = listOf(a, c))),
        )
    }

    @Test fun time_change_with_other_unchanged_meetings_pairs_correctly() {
        val unchangedA = meeting(start = 1, end = 1, location = "A")
        val changedOld = meeting(start = 3, end = 3, location = "B")
        val unchangedC = meeting(start = 5, end = 5, location = "C")
        val changedNew = changedOld.copy(startPeriod = 4, endPeriod = 4)

        assertEquals(
            listOf(ScheduleChange.TimeChanged("课程A", 1, "3", "4", WeekPattern.of(1))),
            differ.diff(
                content(meetings = listOf(unchangedA, changedOld, unchangedC)),
                content(meetings = listOf(unchangedA, changedNew, unchangedC)),
            ),
        )
    }

    @Test fun minimum_cost_does_not_choose_empty_matching() {
        val old = meeting(location = "A")
        val new = meeting(location = "B")

        assertEquals(
            listOf(ScheduleChange.LocationChanged("课程A", WeekPattern.of(1), "A", "B")),
            differ.diff(content(meetings = listOf(old)), content(meetings = listOf(new))),
        )
    }

    @Test fun global_minimum_avoids_greedy_trap() {
        val oldA = meeting(teachers = listOf("T1"), weeks = WeekPattern.of(1), location = "L1", start = 1, end = 1)
        val oldB = meeting(teachers = listOf("T2"), weeks = WeekPattern.of(2), location = "L1", start = 1, end = 1)
        val newX = meeting(teachers = listOf("T2"), weeks = WeekPattern.of(1), location = "L1", start = 1, end = 1)
        val newY = meeting(teachers = listOf("T1"), weeks = WeekPattern.of(1), location = "L1", start = 2, end = 2)

        assertEquals(
            listOf(
                ScheduleChange.TimeChanged("课程A", 1, "1", "2", WeekPattern.of(1)),
                ScheduleChange.WeekPatternChanged("课程A", 1, WeekPattern.of(2), WeekPattern.of(1)),
            ),
            differ.diff(content(meetings = listOf(oldA, oldB)), content(meetings = listOf(newX, newY))),
        )
    }

    @Test fun cost3_candidate_is_removed_plus_added() {
        val old = meeting(start = 1, end = 1, weeks = WeekPattern.of(1), location = "A", teachers = listOf("T"))
        val new = meeting(start = 2, end = 2, weeks = WeekPattern.of(2), location = "B", teachers = listOf("T"))

        assertEquals(
            listOf(
                ScheduleChange.MeetingRemoved("课程A", old.summary()),
                ScheduleChange.MeetingAdded("课程A", new.summary()),
            ),
            differ.diff(content(meetings = listOf(old)), content(meetings = listOf(new))),
        )
    }

    @Test fun cost4_candidate_is_removed_plus_added() {
        val old = meeting(start = 1, end = 1, weeks = WeekPattern.of(1), location = "A", teachers = listOf("T1"))
        val new = meeting(start = 2, end = 2, weeks = WeekPattern.of(2), location = "B", teachers = listOf("T2"))

        assertEquals(
            listOf(
                ScheduleChange.MeetingRemoved("课程A", old.summary()),
                ScheduleChange.MeetingAdded("课程A", new.summary()),
            ),
            differ.diff(content(meetings = listOf(old)), content(meetings = listOf(new))),
        )
    }

    @Test fun exact_location_change() {
        val changes = diffOne(meeting(location = "A"), meeting(location = "B"))
        assertEquals(listOf(ScheduleChange.LocationChanged("课程A", WeekPattern.of(1), "A", "B")), changes)
    }

    @Test fun exact_teacher_change() {
        val changes = diffOne(meeting(teachers = listOf("T1")), meeting(teachers = listOf("T2")))
        assertEquals(listOf(ScheduleChange.TeacherChanged("课程A", WeekPattern.of(1), "T1", "T2")), changes)
    }

    @Test fun exact_weekpattern_change() {
        val changes = diffOne(meeting(weeks = WeekPattern.of(1)), meeting(weeks = WeekPattern.of(2)))
        assertEquals(
            listOf(ScheduleChange.WeekPatternChanged("课程A", 1, WeekPattern.of(1), WeekPattern.of(2))),
            changes,
        )
    }

    @Test fun exact_time_change() {
        val changes = diffOne(meeting(start = 3, end = 5), meeting(start = 6, end = 7))
        assertEquals(
            listOf(ScheduleChange.TimeChanged("课程A", 1, "3-5", "6-7", WeekPattern.of(1))),
            changes,
        )
    }

    @Test fun exact_minute_change_emits_one_time_change() {
        val changes = diffOne(
            meeting(exactStartMinutes = 16 * 60 + 10, exactEndMinutes = 17 * 60 + 50),
            meeting(exactStartMinutes = 16 * 60 + 15, exactEndMinutes = 17 * 60 + 50),
        )
        assertEquals(
            listOf(ScheduleChange.TimeChanged("课程A", 1, "16:10-17:50", "16:15-17:50", WeekPattern.of(1))),
            changes,
        )
    }

    @Test fun time_change_uses_canonical_period_span() {
        val changes = diffOne(meeting(start = 3, end = 3), meeting(start = 4, end = 6))
        assertEquals(
            listOf(ScheduleChange.TimeChanged("课程A", 1, "3", "4-6", WeekPattern.of(1))),
            changes,
        )
    }

    @Test fun two_field_change_emits_exactly_two_changes() {
        val changes = diffOne(
            meeting(weeks = WeekPattern.of(1), location = "A"),
            meeting(weeks = WeekPattern.of(2), location = "B"),
        )

        assertEquals(
            listOf(
                ScheduleChange.WeekPatternChanged("课程A", 1, WeekPattern.of(1), WeekPattern.of(2)),
                ScheduleChange.LocationChanged("课程A", WeekPattern.of(2), "A", "B"),
            ),
            changes,
        )
    }

    @Test fun multi_field_changes_use_new_weeks_for_field_change_context() {
        val changes = diffOne(
            meeting(weeks = WeekPattern.of(1), teachers = listOf("T1")),
            meeting(weeks = WeekPattern.of(2), teachers = listOf("T2")),
        )

        assertEquals(WeekPattern.of(2), (changes[1] as ScheduleChange.TeacherChanged).weeks)
    }

    @Test fun meeting_added() {
        val added = meeting()
        assertEquals(
            listOf(ScheduleChange.MeetingAdded("课程A", added.summary())),
            differ.diff(content(meetings = emptyList()), content(meetings = listOf(added))),
        )
    }

    @Test fun meeting_removed() {
        val removed = meeting()
        assertEquals(
            listOf(ScheduleChange.MeetingRemoved("课程A", removed.summary())),
            differ.diff(content(meetings = listOf(removed)), content(meetings = emptyList())),
        )
    }

    @Test fun weekday_move_is_removed_and_added() {
        val old = meeting(weekday = 5)
        val new = meeting(weekday = 4)

        assertEquals(
            listOf(
                ScheduleChange.MeetingRemoved("课程A", old.summary()),
                ScheduleChange.MeetingAdded("课程A", new.summary()),
            ),
            differ.diff(content(meetings = listOf(old)), content(meetings = listOf(new))),
        )
    }

    @Test fun course_added_removed_by_source_key() {
        val old = content(courses = listOf(course("C1", "课程A")), meetings = emptyList())
        val new = content(courses = listOf(course("C2", "课程B")), meetings = emptyList())

        assertEquals(
            listOf(ScheduleChange.CourseRemoved("课程A"), ScheduleChange.CourseAdded("课程B")),
            differ.diff(old, new),
        )
    }

    @Test fun credits_and_course_type_only_drift_is_silent() {
        val old = content(courses = listOf(course(credits = 3.0, courseType = "A")), meetings = emptyList())
        val new = content(courses = listOf(course(credits = 4.0, courseType = "B")), meetings = emptyList())

        assertTrue(differ.diff(old, new).isEmpty())
    }

    @Test fun duplicate_identical_meeting_count_change_is_single_add_or_remove() {
        val duplicate = meeting()
        val removal = differ.diff(content(meetings = listOf(duplicate, duplicate)), content(meetings = listOf(duplicate)))
        val addition = differ.diff(content(meetings = listOf(duplicate)), content(meetings = listOf(duplicate, duplicate)))

        assertEquals(listOf(ScheduleChange.MeetingRemoved("课程A", duplicate.summary())), removal)
        assertEquals(listOf(ScheduleChange.MeetingAdded("课程A", duplicate.summary())), addition)
    }

    @Test fun equal_cost_matching_has_deterministic_tiebreak() {
        val oldA = meeting(location = "L1", teachers = listOf("A"))
        val oldB = meeting(location = "L2", teachers = listOf("B"))
        val newX = meeting(location = "L2", teachers = listOf("A"))
        val newY = meeting(location = "L1", teachers = listOf("B"))

        assertEquals(
            listOf(
                ScheduleChange.TeacherChanged("课程A", WeekPattern.of(1), "A", "B"),
                ScheduleChange.TeacherChanged("课程A", WeekPattern.of(1), "B", "A"),
            ),
            differ.diff(content(meetings = listOf(oldA, oldB)), content(meetings = listOf(newX, newY))),
        )
    }

    @Test fun input_permutation_produces_identical_diff() {
        val old = listOf(
            meeting(start = 1, end = 1, location = "A"),
            meeting(start = 3, end = 3, location = "B"),
        )
        val new = listOf(
            meeting(start = 1, end = 1, location = "C"),
            meeting(start = 4, end = 4, location = "B"),
        )

        assertEquals(
            differ.diff(content(meetings = old), content(meetings = new)),
            differ.diff(content(meetings = old.reversed()), content(meetings = new.reversed())),
        )
    }

    private fun diffOne(old: FingerprintedMeeting, new: FingerprintedMeeting) =
        differ.diff(content(meetings = listOf(old)), content(meetings = listOf(new)))

    private fun content(
        courses: List<FingerprintedCourse> = listOf(course()),
        meetings: List<FingerprintedMeeting> = emptyList(),
    ) = FingerprintedSchoolContent(meta(), courses, meetings)

    private fun meta() = FingerprintedSemesterMeta(
        "2026-2027 秋季", "2026-2027", "AUTUMN", 20696, 20, 20695, 20833,
    )

    private fun course(
        key: String = "C1",
        name: String = "课程A",
        credits: Double? = 3.0,
        courseType: String? = "专业课",
    ) = FingerprintedCourse(key, key, name, credits, courseType)

    private fun meeting(
        key: String = "C1",
        weekday: Int = 1,
        start: Int = 3,
        end: Int = 5,
        weeks: WeekPattern = WeekPattern.of(1),
        location: String = "L1",
        teachers: List<String> = listOf("T1"),
        exactStartMinutes: Int? = null,
        exactEndMinutes: Int? = null,
    ) = FingerprintedMeeting(
        key, weekday, start, end, weeks.mask, location, teachers, exactStartMinutes, exactEndMinutes,
    )

    private fun FingerprintedMeeting.summary() = MeetingSummary(
        weekday, startPeriod, endPeriod, WeekPattern(weekPatternMask), location, teacherNames,
    )
}
