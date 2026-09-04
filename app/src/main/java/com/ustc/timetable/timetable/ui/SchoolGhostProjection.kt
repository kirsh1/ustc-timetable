package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.layout.TimedBlock
import java.time.Duration
import java.time.LocalTime

interface SchoolTimedBlock : TimedBlock {
    override val meetingId: MeetingId
    override val manualItemId: ManualItemId? get() = null
}

data class SchoolPresentationSignature(
    val canonicalTeachers: List<String>,
    val location: String,
    val weekday: Int,
    val start: LocalTime,
    val endInclusive: LocalTime,
)

data class SchoolCanonicalPresentationKey(
    val earliestWeek: Int,
    val weekday: Int,
    val start: LocalTime,
    val endInclusive: LocalTime,
    val location: String,
    val canonicalTeachers: List<String>,
    val canonicalWeekPattern: String,
    val stableCourseIdentity: String,
)

data class SchoolGhostAttachment(
    val representativeKey: SchoolCanonicalPresentationKey,
    val sameCourseVariants: List<SchoolTimedBlock>,
    val differentCourses: List<SchoolTimedBlock>,
)

data class SchoolGhostAssociation(
    val retainedSchoolBlocks: List<SchoolTimedBlock>,
    val attachmentsByRepresentativeKey: Map<SchoolCanonicalPresentationKey, SchoolGhostAttachment>,
)

object SchoolGhostProjection {
    fun presentationSignature(block: SchoolTimedBlock): SchoolPresentationSignature =
        SchoolPresentationSignature(
            canonicalTeachers = block.teacherNames.map(String::trim).filter(String::isNotEmpty).distinct().sorted(),
            location = block.location.trim(),
            weekday = block.weekday,
            start = block.start,
            endInclusive = block.endInclusive,
        )

    fun canonicalPresentationKey(block: SchoolTimedBlock): SchoolCanonicalPresentationKey =
        SchoolCanonicalPresentationKey(
            earliestWeek = (1..63).firstOrNull { it in block.weeks } ?: 64,
            weekday = block.weekday,
            start = block.start,
            endInclusive = block.endInclusive,
            location = block.location.trim(),
            canonicalTeachers = presentationSignature(block).canonicalTeachers,
            canonicalWeekPattern = block.weeks.format(),
            stableCourseIdentity = block.colorKey,
        )

    val canonicalPresentationOrder: Comparator<SchoolCanonicalPresentationKey> = Comparator { left, right ->
        compareValuesBy(
            left,
            right,
            SchoolCanonicalPresentationKey::earliestWeek,
            SchoolCanonicalPresentationKey::weekday,
            SchoolCanonicalPresentationKey::start,
            SchoolCanonicalPresentationKey::endInclusive,
            SchoolCanonicalPresentationKey::location,
        ).takeIf { it != 0 }
            ?: compareStringLists(left.canonicalTeachers, right.canonicalTeachers).takeIf { it != 0 }
            ?: compareValuesBy(
                left,
                right,
                SchoolCanonicalPresentationKey::canonicalWeekPattern,
                SchoolCanonicalPresentationKey::stableCourseIdentity,
            )
    }

    fun associate(
        schoolBlocks: List<SchoolTimedBlock>,
        viewedWeek: Int,
        showNonCurrentWeek: Boolean,
    ): SchoolGhostAssociation {
        val active = schoolBlocks.filter { viewedWeek in it.weeks }
        if (!showNonCurrentWeek) {
            return SchoolGhostAssociation(active.sortedByCanonicalKey(), emptyMap())
        }

        data class MutableAttachment(
            val sameCourse: MutableList<SchoolTimedBlock> = mutableListOf(),
            val differentCourses: MutableList<SchoolTimedBlock> = mutableListOf(),
        )

        val unattachedGhosts = mutableListOf<SchoolTimedBlock>()
        val attachments = linkedMapOf<SchoolCanonicalPresentationKey, MutableAttachment>()
        schoolBlocks.filterNot { viewedWeek in it.weeks }.sortedByCanonicalKey().forEach { ghost ->
            val candidates = active.mapNotNull { representative ->
                overlapMinutes(representative, ghost).takeIf { it > 0L }?.let { overlap -> representative to overlap }
            }
            val representative = candidates.sortedWith(
                compareByDescending<Pair<SchoolTimedBlock, Long>> { it.second }
                    .thenComparator { left, right ->
                        canonicalPresentationOrder.compare(
                            canonicalPresentationKey(left.first),
                            canonicalPresentationKey(right.first),
                        )
                    },
            ).firstOrNull()?.first

            if (representative == null) {
                unattachedGhosts += ghost
            } else {
                val key = canonicalPresentationKey(representative)
                val bucket = attachments.getOrPut(key, ::MutableAttachment)
                when {
                    ghost.colorKey != representative.colorKey -> bucket.differentCourses += ghost
                    presentationSignature(ghost) != presentationSignature(representative) -> bucket.sameCourse += ghost
                    else -> Unit
                }
            }
        }

        val immutableAttachments = attachments.entries
            .filter { (_, value) -> value.sameCourse.isNotEmpty() || value.differentCourses.isNotEmpty() }
            .sortedWith { left, right -> canonicalPresentationOrder.compare(left.key, right.key) }
            .associate { (key, value) ->
                key to SchoolGhostAttachment(
                    representativeKey = key,
                    sameCourseVariants = value.sameCourse.sortedByCanonicalKey(),
                    differentCourses = value.differentCourses.sortedByCanonicalKey(),
                )
            }
        return SchoolGhostAssociation(
            retainedSchoolBlocks = (active + unattachedGhosts).sortedByCanonicalKey(),
            attachmentsByRepresentativeKey = immutableAttachments,
        )
    }

    private fun overlapMinutes(first: SchoolTimedBlock, second: SchoolTimedBlock): Long {
        if (first.weekday != second.weekday) return 0L
        val start = maxOf(first.start, second.start)
        val end = minOf(first.endInclusive, second.endInclusive)
        return if (end > start) Duration.between(start, end).toMinutes() else 0L
    }

    private fun List<SchoolTimedBlock>.sortedByCanonicalKey(): List<SchoolTimedBlock> =
        sortedWith { left, right ->
            canonicalPresentationOrder.compare(canonicalPresentationKey(left), canonicalPresentationKey(right))
        }

    private fun compareStringLists(left: List<String>, right: List<String>): Int {
        val sharedSize = minOf(left.size, right.size)
        for (index in 0 until sharedSize) {
            val comparison = left[index].compareTo(right[index])
            if (comparison != 0) return comparison
        }
        return left.size.compareTo(right.size)
    }
}
