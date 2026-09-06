package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.layout.TimedBlock
import java.time.Duration
import java.time.Instant

enum class ExactOverlapMode { SPLIT, EARLIEST }
enum class OverlapMarkerKind { CROSS_WEEK, VARIANT, CURRENT_CONFLICT, ALL_CONTENT }

data class OverlapEntry(val block: TimedBlock, val addedAt: Instant) {
    val identity: String get() = block.manualItemId?.let { "manual:${it.value}" } ?: "school:${block.colorKey}"
    val signature: String get() = listOf(block.weekday.toString(), block.start.toString(), block.endInclusive.toString(),
        block.location.trim(), block.teacherNames.map(String::trim).filter(String::isNotEmpty).distinct().sorted().joinToString("\u0000"))
        .joinToString("") { "${it.length}:$it" }
    val key: String get() = listOf(identity, signature, block.weeks.format()).joinToString("") { "${it.length}:$it" }
    val earliestWeek: Int get() = (1..63).firstOrNull { it in block.weeks } ?: 64
}

data class OverlapAttachment(
    val crossWeek: List<OverlapEntry> = emptyList(),
    val sameCourseVariants: List<OverlapEntry> = emptyList(),
    val currentConflicts: List<OverlapEntry> = emptyList(),
) {
    val kinds: Set<OverlapMarkerKind> get() = buildSet {
        if (crossWeek.isNotEmpty()) add(OverlapMarkerKind.CROSS_WEEK)
        if (sameCourseVariants.isNotEmpty()) add(OverlapMarkerKind.VARIANT)
        if (currentConflicts.isNotEmpty()) add(OverlapMarkerKind.CURRENT_CONFLICT)
    }
}

data class OverlapProjection(val retained: List<OverlapEntry>, val attachments: Map<String, OverlapAttachment>)

object UnifiedOverlapProjection {
    val order: Comparator<OverlapEntry> = compareBy({ it.addedAt }, { it.identity }, { it.signature }, { it.block.weeks.format() })
    val ghostOrder: Comparator<OverlapEntry> = compareBy<OverlapEntry> { it.earliestWeek }.then(order)

    fun project(entries: List<OverlapEntry>, week: Int, showOtherWeeks: Boolean, mode: ExactOverlapMode): OverlapProjection {
        val unique = entries.sortedWith(order).distinctBy { it.key }
        val attachments = linkedMapOf<String, OverlapAttachment>()
        val active = collapseSameSchoolCourse(unique.filter { week in it.block.weeks }, attachments)
        val retainedActive = if (mode == ExactOverlapMode.SPLIT) active else active
            .groupBy { Triple(it.block.weekday, it.block.start, it.block.endInclusive) }.values.map { group ->
                val sorted = group.sortedWith(order)
                sorted.first().also { representative ->
                    if (sorted.size > 1) {
                        val old = attachments[representative.key] ?: OverlapAttachment()
                        attachments[representative.key] = old.copy(currentConflicts = old.currentConflicts + sorted.drop(1))
                    }
                }
            }.sortedWith(order)
        if (!showOtherWeeks) return OverlapProjection(retainedActive, attachments)

        fun attach(representative: OverlapEntry, ghost: OverlapEntry) {
            val old = attachments[representative.key] ?: OverlapAttachment()
            val next = when {
                ghost.identity != representative.identity -> old.copy(crossWeek = old.crossWeek + ghost)
                ghost.signature != representative.signature -> old.copy(sameCourseVariants = old.sameCourseVariants + ghost)
                else -> old
            }
            if (next.kinds.isNotEmpty()) attachments[representative.key] = next
        }
        val remaining = mutableListOf<OverlapEntry>()
        unique.filterNot { week in it.block.weeks }.sortedWith(ghostOrder).forEach { ghost ->
            val representative = retainedActive.filter { overlapMinutes(it.block, ghost.block) > 0 }
                .sortedWith(compareByDescending<OverlapEntry> { overlapMinutes(it.block, ghost.block) }.then(order)).firstOrNull()
            if (representative == null) remaining += ghost else attach(representative, ghost)
        }
        // A time region with no current-week card remains a truthful ghost-only layout:
        // different stable identities participate in the segmented split, while duplicate
        // presentations of the same school course collapse into one representative.
        val ghosts = collapseSameSchoolCourse(remaining, attachments).sortedWith(ghostOrder)
        return OverlapProjection(retainedActive + ghosts, attachments)
    }

    private fun collapseSameSchoolCourse(
        entries: List<OverlapEntry>,
        attachments: MutableMap<String, OverlapAttachment>,
    ): List<OverlapEntry> {
        val retained = mutableListOf<OverlapEntry>()
        entries.sortedWith(order).groupBy { it.identity }.values.forEach { identityGroup ->
            if (identityGroup.first().block.manualItemId != null) {
                retained += identityGroup
                return@forEach
            }
            val remaining = identityGroup.sortedWith(order).toMutableList()
            while (remaining.isNotEmpty()) {
                val representative = remaining.removeAt(0)
                val component = linkedSetOf(representative)
                do {
                    val next = remaining.filter { candidate ->
                        component.any { overlapMinutes(it.block, candidate.block) > 0 }
                    }
                    component += next
                    remaining.removeAll(next.toSet())
                } while (next.isNotEmpty())
                retained += representative
                val variants = component.drop(1).filter { it.signature != representative.signature }
                if (variants.isNotEmpty()) {
                    val old = attachments[representative.key] ?: OverlapAttachment()
                    attachments[representative.key] = old.copy(
                        sameCourseVariants = (old.sameCourseVariants + variants).distinctBy { it.key }.sortedWith(order),
                    )
                }
            }
        }
        return retained.sortedWith(order)
    }

    fun overlapMinutes(a: TimedBlock, b: TimedBlock): Long =
        if (a.weekday != b.weekday) 0 else Duration.between(maxOf(a.start, b.start), minOf(a.endInclusive, b.endInclusive)).toMinutes().coerceAtLeast(0)
}
