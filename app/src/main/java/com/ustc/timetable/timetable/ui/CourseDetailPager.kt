package com.ustc.timetable.timetable.ui

import com.ustc.timetable.timetable.domain.MeetingId

data class CourseDetailPage(
    val stableCourseIdentity: String,
    val anchorKey: SchoolCanonicalPresentationKey,
    val anchorMeetingId: MeetingId,
    val detail: CourseDetailUiModel,
)

data class CourseDetailPagerModel(val pages: List<CourseDetailPage>)

object CourseDetailPager {
    fun build(
        representative: SchoolTimedBlock,
        attachment: SchoolGhostAttachment?,
        detailsByMeetingId: Map<MeetingId, CourseDetailUiModel>,
    ): CourseDetailPagerModel? {
        val representativeDetail = detailsByMeetingId[representative.meetingId] ?: return null
        val representativePage = representative.toPage(representativeDetail)

        val alternatives = attachment
            ?.differentCourses
            .orEmpty()
            .filterNot { it.colorKey == representative.colorKey }
            .groupBy(SchoolTimedBlock::colorKey)
            .mapNotNull { (_, blocks) ->
                val candidatesByKey = blocks.groupBy(SchoolGhostProjection::canonicalPresentationKey)
                val anchorKey = candidatesByKey.keys.minWithOrNull(SchoolGhostProjection.canonicalPresentationOrder)
                    ?: return@mapNotNull null
                val anchor = candidatesByKey.getValue(anchorKey).first()
                detailsByMeetingId[anchor.meetingId]?.let { detail -> anchor.toPage(detail) }
            }
            .sortedWith { left, right ->
                SchoolGhostProjection.canonicalPresentationOrder.compare(left.anchorKey, right.anchorKey)
            }

        return CourseDetailPagerModel(listOf(representativePage) + alternatives)
    }

    private fun SchoolTimedBlock.toPage(detail: CourseDetailUiModel): CourseDetailPage = CourseDetailPage(
        stableCourseIdentity = colorKey,
        anchorKey = SchoolGhostProjection.canonicalPresentationKey(this),
        anchorMeetingId = meetingId,
        detail = detail,
    )
}
