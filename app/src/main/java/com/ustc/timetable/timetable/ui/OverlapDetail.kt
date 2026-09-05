package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId

data class OverlapDetailPage(val identity: String, val school: CourseDetailUiModel? = null, val manual: ManualScheduleItem? = null)
data class OverlapDetailSelection(val week: Int, val representativeKey: String, val kind: OverlapMarkerKind)

object OverlapDetail {
    fun build(
        representative: OverlapEntry,
        attachment: OverlapAttachment,
        kind: OverlapMarkerKind,
        week: Int,
        schools: Map<MeetingId, CourseDetailUiModel>,
        manuals: Map<ManualItemId, ManualScheduleItem>,
    ): List<OverlapDetailPage> {
        fun resolve(entry: OverlapEntry): OverlapDetailPage? {
            entry.block.manualItemId?.let { id -> return manuals[id]?.let { OverlapDetailPage(entry.identity, manual = it) } }
            return entry.block.meetingId?.let(schools::get)?.let { OverlapDetailPage(entry.identity, school = it) }
        }
        val first = resolve(representative) ?: return emptyList()
        val candidates = when (kind) {
            OverlapMarkerKind.CROSS_WEEK -> attachment.crossWeek
            OverlapMarkerKind.CURRENT_CONFLICT -> attachment.currentConflicts
            OverlapMarkerKind.VARIANT -> emptyList()
            OverlapMarkerKind.ALL_CONTENT -> attachment.crossWeek + attachment.currentConflicts
        }
        val alternatives = candidates.filter { it.identity != representative.identity }.groupBy { it.identity }.values.mapNotNull { group ->
            group.sortedWith(compareBy<OverlapEntry> { if (week in it.block.weeks) 0 else 1 }
                .then(UnifiedOverlapProjection.ghostOrder)).firstOrNull { resolve(it) != null }
        }.sortedWith(UnifiedOverlapProjection.order).mapNotNull(::resolve).map { page ->
            val detail = page.school ?: return@map page
            // Candidates attach by overlap, but their header represents the full course.
            // The clicked representative above deliberately keeps its original meeting.
            val anchor = detail.allMeetings.minWithOrNull(compareBy(
                { if (week in it.weekPattern) 0 else 1 },
                { meeting -> (1..63).firstOrNull { it in meeting.weekPattern } ?: 64 },
                { it.weekday }, { it.startPeriod }, { it.endPeriod }, { it.location.trim() },
                { it.teacherNames.map(String::trim).distinct().sorted().joinToString("\u0000") },
                { it.weekPattern.mask },
            )) ?: detail.selectedMeeting
            page.copy(school = detail.copy(selectedMeeting = anchor))
        }
        return listOf(first) + alternatives
    }
}
