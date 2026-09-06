package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcEndpointId
import com.ustc.timetable.school.ustc.dto.UstcPortalPage
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.LocalTime

class UstcTimetablePageParser : TimetablePageParser {
    override fun parse(page: UstcPortalPage): List<UstcTimetableEntry> = parsePortalPayload {
        val layout = parseLayout(page)
        val datum = page.requiredJson(UstcEndpointId.TIMETABLE_DATUM).requiredObject().requiredResultObject()
        val lessons = parseLessons(datum.requiredArray("lessonList"))
        val groups = parseGroups(datum.requiredArray("scheduleGroupList"))
        val schedules = parseSchedules(datum.requiredArray("scheduleList"), layout, lessons, groups)
        if (schedules.isEmpty()) parseFailure()

        validateDigest(page, schedules)
        schedules.toCanonicalEntries()
    }

    private fun parseLayout(page: UstcPortalPage): Layout {
        val units = page.requiredJson(UstcEndpointId.TIMETABLE_LAYOUT)
            .requiredObject()
            .requiredResultObject()
            .requiredArray("courseUnitList")
        if (units.isEmpty()) parseFailure()

        val parsed = units.map { element ->
            val unit = element.requiredObject()
            LayoutUnit(
                index = unit.requiredInt("indexNo"),
                startTime = portalTime(unit.requiredInt("startTime")),
                endTime = unit.optionalPortalTime("endTime"),
            )
        }
        if (parsed.any { it.index !in 1..13 } || parsed.distinctBy(LayoutUnit::index).size != parsed.size) {
            parseFailure()
        }
        if (parsed.distinctBy(LayoutUnit::startTime).size != parsed.size ||
            parsed.any { it.endTime != null && it.endTime <= it.startTime }
        ) parseFailure()
        return Layout(
            byStartTime = parsed.associate { it.startTime to it.index },
            units = parsed,
            indices = parsed.mapTo(mutableSetOf(), LayoutUnit::index),
        )
    }

    private fun parseLessons(array: JsonArray): Map<Int, Lesson> {
        if (array.isEmpty()) parseFailure()
        val lessons = array.map { element ->
            val value = element.requiredObject()
            val teacherAssignments = (value["teacherAssignmentList"] as? JsonArray)
                ?.map { assignmentElement ->
                    val assignment = assignmentElement.requiredObject()
                    TeacherAssignment(
                        name = assignment.optionalString("name"),
                        weeks = (assignment["weekIndices"] as? JsonArray)
                            ?.mapTo(mutableSetOf()) { weekElement ->
                                val week = (weekElement as? JsonPrimitive)?.intOrNull ?: parseFailure()
                                if (week !in 1..63) parseFailure()
                                week
                            }
                            .orEmpty(),
                    )
                }
                .orEmpty()
            Lesson(
                id = value.requiredInt("id"),
                code = value.requiredString("code"),
                name = value.requiredString("courseName"),
                teacherAssignments = teacherAssignments,
            )
        }
        if (lessons.distinctBy(Lesson::id).size != lessons.size) parseFailure()
        return lessons.associateBy(Lesson::id)
    }

    private fun parseGroups(array: JsonArray): Map<Int, ScheduleGroup> {
        if (array.isEmpty()) parseFailure()
        val groups = array.map { element ->
            val value = element.requiredObject()
            ScheduleGroup(value.requiredInt("id"), value.requiredInt("lessonId"))
        }
        if (groups.distinctBy(ScheduleGroup::id).size != groups.size) parseFailure()
        return groups.associateBy(ScheduleGroup::id)
    }

    private fun parseSchedules(
        array: JsonArray,
        layout: Layout,
        lessons: Map<Int, Lesson>,
        groups: Map<Int, ScheduleGroup>,
    ): List<ScheduleOccurrence> = array.map { element ->
        val value = element.requiredObject()
        val lessonId = value.requiredInt("lessonId")
        val groupId = value.requiredInt("scheduleGroupId")
        val lesson = lessons[lessonId] ?: parseFailure()
        val group = groups[groupId] ?: parseFailure()
        if (group.lessonId != lessonId) parseFailure()

        val weekday = value.requiredInt("weekday")
        if (weekday !in 1..7) parseFailure()
        val actualStart = portalTime(value.requiredInt("startTime"))
        val standardStart = layout.byStartTime[actualStart]
        val actualEnd = if (standardStart == null) {
            portalTime(value.requiredInt("endTime")).also { if (it <= actualStart) parseFailure() }
        } else {
            null
        }
        val startPeriod = standardStart ?: layout.units
            .filter { unit -> unit.endTime?.let { actualStart >= unit.startTime && actualStart < it } == true }
            .singleOrNull()
            ?.index
            ?: parseFailure()
        val periodCount = value.requiredInt("periods")
        if (periodCount <= 0) parseFailure()
        val endPeriod = startPeriod + periodCount - 1
        if ((startPeriod..endPeriod).any { it !in layout.indices }) parseFailure()
        val week = value.requiredInt("weekIndex")
        if (week !in 1..63) parseFailure()

        val location = value.location()
        val teacher = value.optionalString("personName")
            ?: lesson.teacherAssignments
                .filter { assignment -> week in assignment.weeks && assignment.name != null }
                .singleOrNull()
                ?.name
            ?: parseFailure()

        ScheduleOccurrence(
            lessonId = lessonId,
            groupId = groupId,
            code = lesson.code,
            name = lesson.name,
            weekday = weekday,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            week = week,
            location = location,
            teacher = teacher,
            exactStartTime = actualStart.takeIf { standardStart == null },
            exactEndTime = actualEnd,
        )
    }

    private fun JsonObject.location(): String {
        val roomName = (get("room") as? JsonObject)?.optionalString("nameZh")
        return roomName ?: optionalString("customPlace") ?: parseFailure()
    }

    private fun List<ScheduleOccurrence>.toCanonicalEntries(): List<UstcTimetableEntry> {
        val teacherSeries = groupBy { occurrence -> occurrence.teacherSeriesKey() }
            .map { (key, rows) -> TeacherSeries(key, rows.mapTo(sortedSetOf(), ScheduleOccurrence::week)) }

        return teacherSeries
            .groupBy { it.assignmentKey() }
            .map { (key, rows) ->
                UstcTimetableEntry(
                    courseName = key.name,
                    courseCode = key.code,
                    weekdayText = "周${WEEKDAYS[key.weekday - 1]}",
                    periodText = "${key.startPeriod}-${key.endPeriod}",
                    weekText = formatWeeks(key.weeks),
                    locationText = key.location,
                    teacherText = rows.map { it.key.teacher }.distinct().sorted().joinToString("、"),
                    sourceAssignmentKey = "${key.lessonId}:${key.groupId}",
                    exactStartTime = key.exactStartTime,
                    exactEndTime = key.exactEndTime,
                )
            }
            .sortedWith(
                compareBy<UstcTimetableEntry>(
                    UstcTimetableEntry::courseCode,
                    { WEEKDAYS.indexOf(it.weekdayText.removePrefix("周")) },
                    { it.periodText.substringBefore('-').toInt() },
                    { it.periodText.substringAfter('-').toInt() },
                    { it.weekText.substringBefore('-').substringBefore(',').toInt() },
                    UstcTimetableEntry::locationText,
                    UstcTimetableEntry::sourceAssignmentKey,
                    UstcTimetableEntry::teacherText,
                    UstcTimetableEntry::exactStartTime,
                    UstcTimetableEntry::exactEndTime,
                ),
            )
    }

    private fun validateDigest(page: UstcPortalPage, schedules: List<ScheduleOccurrence>) {
        val digest = page.requiredJson(UstcEndpointId.WEEK_INDICES_DIGEST)
            .requiredObject()
            .requiredResultObject()
        if (digest.isEmpty()) parseFailure()

        val expected = schedules.groupBy(ScheduleOccurrence::slotKey)
            .values
            .map { rows -> formatWeeks(rows.mapTo(sortedSetOf(), ScheduleOccurrence::week)) }
            .groupingBy { it }
            .eachCount()
        val actual = digest.values.map { element ->
            val raw = (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: parseFailure()
            formatWeeks(parseDigestWeeks(raw))
        }.groupingBy { it }.eachCount()
        if (actual != expected) parseFailure()
    }

    private fun parseDigestWeeks(raw: String): Set<Int> {
        val weeks = sortedSetOf<Int>()
        raw.trim().split(',', '，', '、').forEach { segment ->
            val bounds = segment.trim().replace('~', '-').split('-')
            val start = bounds.getOrNull(0)?.toIntOrNull() ?: parseFailure()
            val end = bounds.getOrNull(1)?.toIntOrNull() ?: start
            if (bounds.size > 2 || start !in 1..63 || end !in start..63) parseFailure()
            weeks.addAll(start..end)
        }
        if (weeks.isEmpty()) parseFailure()
        return weeks
    }

    private fun formatWeeks(weeks: Set<Int>): String {
        if (weeks.isEmpty()) parseFailure()
        val sorted = weeks.sorted()
        return buildList {
            var index = 0
            while (index < sorted.size) {
                val start = sorted[index]
                var end = start
                while (index + 1 < sorted.size && sorted[index + 1] == end + 1) {
                    index++
                    end = sorted[index]
                }
                add(if (start == end) "$start" else "$start-$end")
                index++
            }
        }.joinToString(",")
    }

    private data class LayoutUnit(val index: Int, val startTime: LocalTime, val endTime: LocalTime?)

    private data class Layout(
        val byStartTime: Map<LocalTime, Int>,
        val units: List<LayoutUnit>,
        val indices: Set<Int>,
    )

    private data class Lesson(
        val id: Int,
        val code: String,
        val name: String,
        val teacherAssignments: List<TeacherAssignment>,
    )

    private data class TeacherAssignment(val name: String?, val weeks: Set<Int>)

    private data class ScheduleGroup(val id: Int, val lessonId: Int)

    private data class ScheduleOccurrence(
        val lessonId: Int,
        val groupId: Int,
        val code: String,
        val name: String,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val week: Int,
        val location: String,
        val teacher: String,
        val exactStartTime: LocalTime?,
        val exactEndTime: LocalTime?,
    ) {
        fun teacherSeriesKey() = TeacherSeriesKey(
            lessonId,
            groupId,
            code,
            name,
            weekday,
            startPeriod,
            endPeriod,
            location,
            teacher,
            exactStartTime,
            exactEndTime,
        )

        fun slotKey() = SlotKey(
            lessonId, groupId, weekday, startPeriod, endPeriod, location, exactStartTime, exactEndTime,
        )
    }

    private data class TeacherSeriesKey(
        val lessonId: Int,
        val groupId: Int,
        val code: String,
        val name: String,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val location: String,
        val teacher: String,
        val exactStartTime: LocalTime?,
        val exactEndTime: LocalTime?,
    )

    private data class TeacherSeries(val key: TeacherSeriesKey, val weeks: Set<Int>) {
        fun assignmentKey() = AssignmentKey(
            key.lessonId,
            key.groupId,
            key.code,
            key.name,
            key.weekday,
            key.startPeriod,
            key.endPeriod,
            weeks,
            key.location,
            key.exactStartTime,
            key.exactEndTime,
        )
    }

    private data class AssignmentKey(
        val lessonId: Int,
        val groupId: Int,
        val code: String,
        val name: String,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeks: Set<Int>,
        val location: String,
        val exactStartTime: LocalTime?,
        val exactEndTime: LocalTime?,
    )

    private data class SlotKey(
        val lessonId: Int,
        val groupId: Int,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val location: String,
        val exactStartTime: LocalTime?,
        val exactEndTime: LocalTime?,
    )

    private fun portalTime(value: Int): LocalTime {
        val hour = value / 100
        val minute = value % 100
        if (hour !in 0..23 || minute !in 0..59) parseFailure()
        return LocalTime.of(hour, minute)
    }

    private fun JsonObject.optionalPortalTime(name: String): LocalTime? =
        (get(name) as? JsonPrimitive)?.intOrNull?.let(::portalTime)

    private companion object {
        val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")
    }
}
