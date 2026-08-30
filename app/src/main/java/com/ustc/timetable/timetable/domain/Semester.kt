package com.ustc.timetable.timetable.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

@JvmInline value class SemesterId(val value: String)

@JvmInline value class ProfileId(val value: String)

enum class Term { AUTUMN, SPRING, SUMMER }


data class LocalDateRange(
    override val start: LocalDate,
    override val endInclusive: LocalDate,
) : ClosedRange<LocalDate> {
    init {
        require(start <= endInclusive) { "inverted range: $start..$endInclusive" }
    }
}

/**
 * 学期（SPEC §3.2）。`week1Start` 是教学周计算唯一 authority；
 * `startDate/endDate` 是学期 metadata（含注册日等非教学日），不参与周推导。
 * `isCurrentAcademicSemester` 仅由导入/同步流程置位；`portalLinked=false` 的纯手动学期永不参与学校同步。
 */
data class Semester(
    val id: SemesterId,
    val displayName: String,
    val academicYear: String,
    val term: Term,
    val week1Start: LocalDate,
    val totalWeeks: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val importedAt: Instant,
    val lastSyncedAt: Instant?,
    val isCurrentAcademicSemester: Boolean,
    val portalLinked: Boolean,
    val profileId: ProfileId,
    val sourceFingerprint: String?,
) {
    init {
        require(startDate <= endDate) { "startDate after endDate: $startDate > $endDate" }
        require(!week1Start.isBefore(startDate) && !week1Start.isAfter(endDate)) {
            "week1Start $week1Start outside semester dates [$startDate, $endDate]"
        }
        require(week1Start.dayOfWeek == DayOfWeek.MONDAY) { "week1Start must be Monday: $week1Start" }
        require(totalWeeks in 1..63) { "totalWeeks out of range: $totalWeeks" }
    }
}

object SemesterDefaults {
    /** 2026 秋季官方基准（SPEC §3.2）：注册 08-30，上课 08-31，结束 2027-01-15，共 20 教学周。 */
    fun AUTUMN_2026(id: String, profileId: String, now: Instant = Instant.now()): Semester = Semester(
        id = SemesterId(id),
        displayName = "2026-2027 秋季",
        academicYear = "2026-2027",
        term = Term.AUTUMN,
        week1Start = LocalDate.of(2026, 8, 31),
        totalWeeks = 20,
        startDate = LocalDate.of(2026, 8, 30),
        endDate = LocalDate.of(2027, 1, 15),
        importedAt = now,
        lastSyncedAt = null,
        isCurrentAcademicSemester = true,
        portalLinked = false,
        profileId = ProfileId(profileId),
        sourceFingerprint = null,
    )
}
