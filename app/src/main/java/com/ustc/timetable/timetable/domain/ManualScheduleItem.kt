package com.ustc.timetable.timetable.domain

import java.time.Instant
import java.time.LocalTime

@JvmInline value class ManualItemId(val value: String)

/**
 * 手动项目（canonical，SPEC §3.4）。分钟粒度任意时间（不要求节次）；
 * day-window 校验按冻结分层留在 D2 编辑器边界。只允许 MANUAL source。
 */
data class ManualScheduleItem(
    val id: ManualItemId,
    val semesterId: SemesterId,
    val title: String,
    val weekday: Int,              // 1..7 = 周一..周日
    val startTime: LocalTime,
    val endTime: LocalTime,
    val weekPattern: WeekPattern,
    val location: String?,
    val note: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val source: ItemSource = ItemSource.MANUAL,
) {
    init {
        require(source == ItemSource.MANUAL) { "ManualScheduleItem.source must be MANUAL: $source" }
        require(title.isNotBlank()) { "title required" }
        require(weekday in 1..7) { "weekday out of range: $weekday" }
        require(endTime > startTime) { "end must be after start: $startTime-$endTime" }
        require(weekPattern != WeekPattern.EMPTY) { "weekPattern must not be empty" }
    }
}
