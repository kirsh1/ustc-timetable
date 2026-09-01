package com.ustc.timetable.timetable.domain

class SnapshotDiffer {
    fun diff(
        old: FingerprintedSchoolContent,
        new: FingerprintedSchoolContent,
    ): List<ScheduleChange> {
        val oldCourses = old.courses.associateBy { it.sourceCourseKey }
        val newCourses = new.courses.associateBy { it.sourceCourseKey }
        val changes = mutableListOf<ScheduleChange>()

        (oldCourses.keys + newCourses.keys).sorted().forEach { sourceKey ->
            val oldCourse = oldCourses[sourceKey]
            val newCourse = newCourses[sourceKey]
            when {
                oldCourse == null -> changes += ScheduleChange.CourseAdded(requireNotNull(newCourse).name)
                newCourse == null -> changes += ScheduleChange.CourseRemoved(oldCourse.name)
                else -> changes += diffCourseMeetings(
                    courseName = newCourse.name,
                    oldMeetings = old.meetings.filter { it.sourceCourseKey == sourceKey },
                    newMeetings = new.meetings.filter { it.sourceCourseKey == sourceKey },
                )
            }
        }
        return changes
    }

    private fun diffCourseMeetings(
        courseName: String,
        oldMeetings: List<FingerprintedMeeting>,
        newMeetings: List<FingerprintedMeeting>,
    ): List<ScheduleChange> {
        val pairedChanges = mutableListOf<ScheduleChange>()
        val removals = mutableListOf<ScheduleChange>()
        val additions = mutableListOf<ScheduleChange>()
        val weekdays = (oldMeetings.map { it.weekday } + newMeetings.map { it.weekday }).toSortedSet()

        weekdays.forEach { weekday ->
            val bucket = diffBucket(
                oldMeetings.filter { it.weekday == weekday },
                newMeetings.filter { it.weekday == weekday },
            )
            bucket.pairs.forEach { pair -> pairedChanges += changesForPair(courseName, pair.old, pair.new) }
            bucket.unmatchedOld.forEach { meeting ->
                removals += ScheduleChange.MeetingRemoved(courseName, meeting.toSummary())
            }
            bucket.unmatchedNew.forEach { meeting ->
                additions += ScheduleChange.MeetingAdded(courseName, meeting.toSummary())
            }
        }
        return pairedChanges + removals + additions
    }

    private fun diffBucket(
        oldInput: List<FingerprintedMeeting>,
        newInput: List<FingerprintedMeeting>,
    ): BucketResult {
        val oldSorted = oldInput.sortedWith(fingerprintedMeetingComparator)
        val unmatchedNewAfterExact = newInput.sortedWith(fingerprintedMeetingComparator).toMutableList()
        val unmatchedOldAfterExact = mutableListOf<FingerprintedMeeting>()
        oldSorted.forEach { oldMeeting ->
            val exactIndex = unmatchedNewAfterExact.indexOf(oldMeeting)
            if (exactIndex >= 0) unmatchedNewAfterExact.removeAt(exactIndex)
            else unmatchedOldAfterExact += oldMeeting
        }

        val matching = minimumMatching(unmatchedOldAfterExact, unmatchedNewAfterExact)
        val matchedOld = matching.map { it.oldIndex }.toSet()
        val matchedNew = matching.map { it.newIndex }.toSet()
        return BucketResult(
            pairs = matching.map { Pairing(unmatchedOldAfterExact[it.oldIndex], unmatchedNewAfterExact[it.newIndex]) },
            unmatchedOld = unmatchedOldAfterExact.filterIndexed { index, _ -> index !in matchedOld },
            unmatchedNew = unmatchedNewAfterExact.filterIndexed { index, _ -> index !in matchedNew },
        )
    }

    private fun minimumMatching(
        old: List<FingerprintedMeeting>,
        new: List<FingerprintedMeeting>,
    ): List<IndexedPair> {
        var best: MatchingCandidate? = null
        val usedNew = BooleanArray(new.size)
        val pairs = mutableListOf<IndexedPair>()

        fun visit(oldIndex: Int, pairCost: Int, unmatchedOld: Int) {
            if (oldIndex == old.size) {
                val unmatchedNew = usedNew.count { !it }
                val candidate = MatchingCandidate(
                    score = pairCost + UNMATCHED_PENALTY * (unmatchedOld + unmatchedNew),
                    pairs = pairs.toList(),
                )
                if (best == null || compareCandidates(candidate, requireNotNull(best), old, new) < 0) best = candidate
                return
            }

            visit(oldIndex + 1, pairCost, unmatchedOld + 1)
            new.indices.forEach { newIndex ->
                if (!usedNew[newIndex]) {
                    val cost = pairCost(old[oldIndex], new[newIndex])
                    if (cost <= CONFIDENCE_THRESHOLD) {
                        usedNew[newIndex] = true
                        pairs += IndexedPair(oldIndex, newIndex)
                        visit(oldIndex + 1, pairCost + cost, unmatchedOld)
                        pairs.removeAt(pairs.lastIndex)
                        usedNew[newIndex] = false
                    }
                }
            }
        }

        visit(oldIndex = 0, pairCost = 0, unmatchedOld = 0)
        return requireNotNull(best).pairs.sortedWith(indexedPairComparator(old, new))
    }

    private fun compareCandidates(
        first: MatchingCandidate,
        second: MatchingCandidate,
        old: List<FingerprintedMeeting>,
        new: List<FingerprintedMeeting>,
    ): Int {
        val scoreComparison = first.score.compareTo(second.score)
        if (scoreComparison != 0) return scoreComparison
        val comparator = indexedPairComparator(old, new)
        val firstPairs = first.pairs.sortedWith(comparator)
        val secondPairs = second.pairs.sortedWith(comparator)
        for (index in 0 until minOf(firstPairs.size, secondPairs.size)) {
            val compared = comparator.compare(firstPairs[index], secondPairs[index])
            if (compared != 0) return compared
        }
        return firstPairs.size.compareTo(secondPairs.size)
    }

    private fun indexedPairComparator(
        old: List<FingerprintedMeeting>,
        new: List<FingerprintedMeeting>,
    ) = Comparator<IndexedPair> { first, second ->
        fingerprintedMeetingComparator.compare(old[first.oldIndex], old[second.oldIndex])
            .takeIf { it != 0 }
            ?: fingerprintedMeetingComparator.compare(new[first.newIndex], new[second.newIndex])
    }

    private fun pairCost(old: FingerprintedMeeting, new: FingerprintedMeeting): Int =
        (if (old.startPeriod != new.startPeriod || old.endPeriod != new.endPeriod) 1 else 0) +
            (if (old.weekPatternMask != new.weekPatternMask) 1 else 0) +
            (if (old.location != new.location) 1 else 0) +
            (if (old.teacherNames != new.teacherNames) 1 else 0)

    private fun changesForPair(
        courseName: String,
        old: FingerprintedMeeting,
        new: FingerprintedMeeting,
    ): List<ScheduleChange> = buildList {
        val newWeeks = WeekPattern(new.weekPatternMask)
        if (old.startPeriod != new.startPeriod || old.endPeriod != new.endPeriod) {
            add(ScheduleChange.TimeChanged(courseName, new.weekday, old.periodSpan(), new.periodSpan(), newWeeks))
        }
        if (old.weekPatternMask != new.weekPatternMask) {
            add(ScheduleChange.WeekPatternChanged(courseName, new.weekday, WeekPattern(old.weekPatternMask), newWeeks))
        }
        if (old.location != new.location) {
            add(ScheduleChange.LocationChanged(courseName, newWeeks, old.location, new.location))
        }
        if (old.teacherNames != new.teacherNames) {
            add(
                ScheduleChange.TeacherChanged(
                    courseName,
                    newWeeks,
                    old.teacherNames.joinToString("、"),
                    new.teacherNames.joinToString("、"),
                ),
            )
        }
    }

    private fun FingerprintedMeeting.periodSpan(): String =
        if (startPeriod == endPeriod) "$startPeriod" else "$startPeriod-$endPeriod"

    private fun FingerprintedMeeting.toSummary() = MeetingSummary(
        weekday = weekday,
        startPeriod = startPeriod,
        endPeriod = endPeriod,
        weeks = WeekPattern(weekPatternMask),
        location = location,
        teacherNames = teacherNames,
    )

    private data class IndexedPair(val oldIndex: Int, val newIndex: Int)
    private data class MatchingCandidate(val score: Int, val pairs: List<IndexedPair>)
    private data class Pairing(val old: FingerprintedMeeting, val new: FingerprintedMeeting)
    private data class BucketResult(
        val pairs: List<Pairing>,
        val unmatchedOld: List<FingerprintedMeeting>,
        val unmatchedNew: List<FingerprintedMeeting>,
    )

    private companion object {
        const val CONFIDENCE_THRESHOLD = 2
        const val UNMATCHED_PENALTY = 3
    }
}
