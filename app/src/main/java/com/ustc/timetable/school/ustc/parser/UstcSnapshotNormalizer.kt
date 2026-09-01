package com.ustc.timetable.school.ustc.parser

import com.ustc.timetable.school.ustc.dto.UstcCourseSummary
import com.ustc.timetable.school.ustc.dto.UstcSemesterMetaPartial
import com.ustc.timetable.school.ustc.dto.UstcTimetableEntry
import com.ustc.timetable.sync.SyncError
import com.ustc.timetable.sync.asFailure
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterId
import com.ustc.timetable.timetable.domain.WeekPattern
import java.security.MessageDigest

class UstcSnapshotNormalizer {
    fun normalize(
        selection: List<UstcCourseSummary>,
        timetable: List<UstcTimetableEntry>,
        meta: UstcSemesterMetaPartial,
        semesterId: SemesterId,
    ): NormalizedSchoolSnapshot {
        validateTotalWeeks(meta.totalWeeks)
        val selected = selection.map { it.toSelectedCourse() }
        validateSelectionIdentity(selected)

        val courses = selected
            .sortedBy { it.sourceKey }
            .map { selectedCourse -> selectedCourse.toCourse(semesterId) }
        val courseByKey = courses.associateBy { it.sourceCourseKey }
        val selectedByCode = selected.filter { it.code.isNotEmpty() }.associateBy { it.code }
        val selectedByName = selected.groupBy { it.name }

        val rawAssignments = timetable.flatMap { entry ->
            val selectedCourse = match(entry, selectedByCode, selectedByName)
            val baseWeeks = parseWeeks(entry.weekText, meta.totalWeeks)
            val weekday = parseWeekday(entry.weekdayText)
            val periods = parsePeriods(entry.periodText)
            teacherAssignments(entry.teacherText, baseWeeks, meta.totalWeeks).map { teacherAssignment ->
                Assignment(
                    sourceKey = selectedCourse.sourceKey,
                    weekday = weekday,
                    startPeriod = periods.first,
                    endPeriod = periods.second,
                    weeks = teacherAssignment.weeks,
                    location = entry.locationText.trim(),
                    teacherNames = teacherAssignment.teacherNames,
                )
            }
        }

        val canonicalAssignments = canonicalize(rawAssignments)
        val meetings = canonicalAssignments.map { assignment ->
            val course = courseByKey.getValue(assignment.sourceKey)
            CourseMeeting(
                id = MeetingId(
                    stableId(
                        prefix = "normalized-meeting",
                        parts = listOf(
                            assignment.sourceKey,
                            assignment.weekday.toString(),
                            assignment.startPeriod.toString(),
                            assignment.endPeriod.toString(),
                            assignment.weeks.mask.toString(),
                            assignment.location,
                            assignment.teacherNames.joinToString("\u001f"),
                        ),
                    ),
                ),
                courseId = course.id,
                weekday = assignment.weekday,
                startPeriod = assignment.startPeriod,
                endPeriod = assignment.endPeriod,
                weekPattern = assignment.weeks,
                location = assignment.location,
                teacherNames = assignment.teacherNames,
            )
        }

        val issues = selected
            .sortedBy { it.sourceKey }
            .flatMap { course ->
                buildList {
                    if (course.dto.credits == null) {
                        add(warning(course.sourceKey, "credits missing"))
                    }
                    if (course.dto.courseType.isNullOrBlank()) {
                        add(warning(course.sourceKey, "courseType missing"))
                    }
                }
            }

        return NormalizedSchoolSnapshot(courses, meetings, issues)
    }

    private fun UstcCourseSummary.toSelectedCourse(): SelectedCourse {
        val code = courseCode.trim()
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) parseFailure()
        return SelectedCourse(
            dto = this,
            code = code,
            name = normalizedName,
            sourceKey = if (code.isNotEmpty()) code else "name:$normalizedName",
        )
    }

    private fun SelectedCourse.toCourse(semesterId: SemesterId): Course = Course(
        id = CourseId(stableId("normalized-course", listOf(semesterId.value, sourceKey))),
        semesterId = semesterId,
        sourceCourseKey = sourceKey,
        courseCode = code,
        name = name,
        credits = dto.credits,
        courseType = dto.courseType?.trim()?.ifEmpty { null },
    )

    private fun validateSelectionIdentity(selected: List<SelectedCourse>) {
        if (selected.groupingBy { it.sourceKey }.eachCount().any { it.value > 1 }) validationFailure()
        if (selected.filter { it.code.isNotEmpty() }.groupingBy { it.code }.eachCount().any { it.value > 1 }) {
            validationFailure()
        }
    }

    private fun match(
        entry: UstcTimetableEntry,
        selectedByCode: Map<String, SelectedCourse>,
        selectedByName: Map<String, List<SelectedCourse>>,
    ): SelectedCourse {
        val code = entry.courseCode?.trim().orEmpty()
        if (code.isNotEmpty()) return selectedByCode[code] ?: validationFailure()

        val name = entry.courseName.trim()
        if (name.isEmpty()) parseFailure()
        val candidates = selectedByName[name].orEmpty()
        if (candidates.size != 1) validationFailure()
        return candidates.single()
    }

    private fun parseWeekday(raw: String): Int {
        return when (raw.trim()) {
            "一", "周一", "星期一" -> 1
            "二", "周二", "星期二" -> 2
            "三", "周三", "星期三" -> 3
            "四", "周四", "星期四" -> 4
            "五", "周五", "星期五" -> 5
            "六", "周六", "星期六" -> 6
            "日", "周日", "星期日", "星期天" -> 7
            else -> parseFailure()
        }
    }

    private fun parsePeriods(raw: String): Pair<Int, Int> {
        val match = PERIOD_PATTERN.matchEntire(raw.trim()) ?: parseFailure()
        val start = match.groupValues[1].toIntOrNull() ?: parseFailure()
        val end = match.groupValues[2].ifEmpty { match.groupValues[1] }.toIntOrNull() ?: parseFailure()
        if (start !in 1..13 || end !in start..13) validationFailure()
        return start to end
    }

    private fun parseWeeks(raw: String, totalWeeks: Int?): WeekPattern {
        val compact = raw.filterNot(Char::isWhitespace)
        val pattern = when (compact) {
            "单周" -> {
                val end = totalWeeks ?: parseFailure()
                parityWithin(1, end, odd = true)
            }
            "双周" -> {
                val end = totalWeeks ?: parseFailure()
                parityWithin(1, end, odd = false)
            }
            else -> parseBoundedOrNumericWeeks(compact)
        }
        if (totalWeeks != null && highestWeek(pattern) > totalWeeks) validationFailure()
        return pattern
    }

    private fun parseBoundedOrNumericWeeks(compact: String): WeekPattern {
        val bounded = BOUNDED_PARITY_PATTERN.matchEntire(compact)
        if (bounded == null) return parseNumericWeeks(compact)

        val numeric = parseNumericWeeks(bounded.groupValues[1])
        val parity = when (bounded.groupValues[2]) {
            "单" -> WeekPattern.oddWithin(1, 63)
            "双" -> WeekPattern.evenWithin(1, 63)
            else -> parseFailure()
        }
        val result = WeekPattern(numeric.mask and parity.mask)
        if (result == WeekPattern.EMPTY) validationFailure()
        return result
    }

    private fun parityWithin(start: Int, endInclusive: Int, odd: Boolean): WeekPattern = try {
        if (odd) WeekPattern.oddWithin(start, endInclusive) else WeekPattern.evenWithin(start, endInclusive)
    } catch (_: IllegalArgumentException) {
        validationFailure()
    }

    private fun parseNumericWeeks(raw: String): WeekPattern = try {
        WeekPattern.parse(raw)
    } catch (_: IllegalArgumentException) {
        if (looksLikeNumericWeekSyntax(raw)) validationFailure() else parseFailure()
    }

    private fun looksLikeNumericWeekSyntax(raw: String): Boolean {
        val cleaned = raw
            .replace('，', ',')
            .replace('、', ',')
            .replace('–', '-')
            .replace('—', '-')
            .replace("至", "-")
            .replace("到", "-")
            .replace("第", "")
            .replace("周", "")
        return NUMERIC_WEEK_SHAPE.matches(cleaned)
    }

    private fun teacherAssignments(
        raw: String,
        baseWeeks: WeekPattern,
        totalWeeks: Int?,
    ): List<TeacherAssignment> {
        val explicit = raw.split('/').map { segment ->
            TEACHER_WEEK_SEGMENT.matchEntire(segment.trim())
        }.takeIf { matches ->
            matches.isNotEmpty() && matches.all { match ->
                match != null && !EMBEDDED_WEEK_MARKER.containsMatchIn(match.groupValues[1])
            }
        }
        if (explicit == null) {
            return listOf(TeacherAssignment(baseWeeks, parseTeacherNames(raw)))
        }

        return explicit.map { nullableMatch ->
            val match = nullableMatch ?: parseFailure()
            val segmentWeeks = parseWeeks(match.groupValues[2], totalWeeks)
            if (segmentWeeks.mask and baseWeeks.mask != segmentWeeks.mask) validationFailure()
            TeacherAssignment(segmentWeeks, parseTeacherNames(match.groupValues[1]))
        }
    }

    private fun parseTeacherNames(raw: String): List<String> = raw
        .split(TEACHER_SEPARATOR)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sorted()

    private fun canonicalize(assignments: List<Assignment>): List<Assignment> {
        val teachersMerged = assignments
            .groupBy { it.teacherMergeKey() }
            .map { (key, group) ->
                Assignment(
                    sourceKey = key.sourceKey,
                    weekday = key.weekday,
                    startPeriod = key.startPeriod,
                    endPeriod = key.endPeriod,
                    weeks = WeekPattern(key.weekMask),
                    location = key.location,
                    teacherNames = group.flatMap { it.teacherNames }.distinct().sorted(),
                )
            }

        return teachersMerged
            .groupBy { it.weekMergeKey() }
            .map { (key, group) ->
                Assignment(
                    sourceKey = key.sourceKey,
                    weekday = key.weekday,
                    startPeriod = key.startPeriod,
                    endPeriod = key.endPeriod,
                    weeks = group.map { it.weeks }.reduce(WeekPattern::union),
                    location = key.location,
                    teacherNames = key.teacherNames,
                )
            }
            .sortedWith(ASSIGNMENT_ORDER)
    }

    private fun validateTotalWeeks(totalWeeks: Int?) {
        if (totalWeeks != null && totalWeeks !in 1..63) validationFailure()
    }

    private fun highestWeek(pattern: WeekPattern): Int = (63 downTo 1).first { it in pattern }

    private fun warning(sourceKey: String, category: String) = NormalizationIssue(
        severity = NormalizationIssue.Severity.WARNING,
        message = "$sourceKey: $category",
    )

    private fun parseFailure(): Nothing = throw SyncError.ParseFailed.asFailure()

    private fun validationFailure(): Nothing = throw SyncError.ValidationFailed.asFailure()

    private fun stableId(prefix: String, parts: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateLengthPrefixed(prefix)
        parts.forEach { part -> digest.updateLengthPrefixed(part) }
        return "$prefix-${digest.digest().joinToString("") { "%02x".format(it) }}"
    }

    private fun MessageDigest.updateLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        update(byteArrayOf(
            (bytes.size ushr 24).toByte(),
            (bytes.size ushr 16).toByte(),
            (bytes.size ushr 8).toByte(),
            bytes.size.toByte(),
        ))
        update(bytes)
    }

    private data class SelectedCourse(
        val dto: UstcCourseSummary,
        val code: String,
        val name: String,
        val sourceKey: String,
    )

    private data class TeacherAssignment(
        val weeks: WeekPattern,
        val teacherNames: List<String>,
    )

    private data class Assignment(
        val sourceKey: String,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weeks: WeekPattern,
        val location: String,
        val teacherNames: List<String>,
    ) {
        fun teacherMergeKey() = TeacherMergeKey(
            sourceKey,
            weekday,
            startPeriod,
            endPeriod,
            weeks.mask,
            location,
        )

        fun weekMergeKey() = WeekMergeKey(
            sourceKey,
            weekday,
            startPeriod,
            endPeriod,
            location,
            teacherNames,
        )
    }

    private data class TeacherMergeKey(
        val sourceKey: String,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val weekMask: Long,
        val location: String,
    )

    private data class WeekMergeKey(
        val sourceKey: String,
        val weekday: Int,
        val startPeriod: Int,
        val endPeriod: Int,
        val location: String,
        val teacherNames: List<String>,
    )

    private companion object {
        val PERIOD_PATTERN = Regex("""(?:第)?(\d+)(?:[-–](\d+))?(?:节)?""")
        val BOUNDED_PARITY_PATTERN = Regex("""(.+?)周?\(([单双])\)""")
        val NUMERIC_WEEK_SHAPE = Regex("""\d+(?:-\d+)?(?:,\d+(?:-\d+)?)*""")
        val TEACHER_WEEK_SEGMENT = Regex(
            """(.+?)\s+((?:第)?\d+(?:[-–]\d+)?(?:[,，、]\d+(?:[-–]\d+)?)*周(?:\([单双]\))?|[单双]周)""",
        )
        val EMBEDDED_WEEK_MARKER = Regex(
            """(?:第)?\d+(?:[-–]\d+)?(?:[,，、]\d+(?:[-–]\d+)?)*周|[单双]周""",
        )
        val TEACHER_SEPARATOR = Regex("""[/、,，;；]""")
        val ASSIGNMENT_ORDER = compareBy<Assignment>(
            { it.sourceKey },
            { it.weekday },
            { it.startPeriod },
            { it.endPeriod },
            { it.weeks.mask },
            { it.location },
            { it.teacherNames.joinToString("\u001f") },
        )
    }
}
