package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.layout.TimedBlock
import java.time.Duration
import java.time.Instant

enum class ExactOverlapMode { SPLIT, EARLIEST }
enum class OverlapMarkerKind { CROSS_WEEK, VARIANT, CURRENT_CONFLICT }

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
        val active = unique.filter { week in it.block.weeks }
        val attachments = linkedMapOf<String, OverlapAttachment>()
        val retainedActive = if (mode == ExactOverlapMode.SPLIT) active else active
            .groupBy { Triple(it.block.weekday, it.block.start, it.block.endInclusive) }.values.map { group ->
                val sorted = group.sortedWith(order)
                sorted.first().also { representative ->
                    if (sorted.size > 1) attachments[representative.key] = OverlapAttachment(currentConflicts = sorted.drop(1))
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
        val ghosts = mutableListOf<OverlapEntry>()
        while (remaining.isNotEmpty()) {
            val representative = remaining.removeAt(0)
            ghosts += representative
            val attached = remaining.filter { overlapMinutes(it.block, representative.block) > 0 }
            attached.forEach { attach(representative, it) }
            remaining.removeAll(attached.toSet())
        }
        return OverlapProjection(retainedActive + ghosts, attachments)
    }

    fun overlapMinutes(a: TimedBlock, b: TimedBlock): Long =
        if (a.weekday != b.weekday) 0 else Duration.between(maxOf(a.start, b.start), minOf(a.endInclusive, b.endInclusive)).toMinutes().coerceAtLeast(0)
}
