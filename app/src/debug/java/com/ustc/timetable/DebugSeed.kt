package com.ustc.timetable

import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.data.ManualItemRepository
import com.ustc.timetable.timetable.data.SemesterRepository
import com.ustc.timetable.timetable.data.db.TimetableDatabase
import com.ustc.timetable.timetable.data.db.applySchoolSnapshot
import com.ustc.timetable.timetable.domain.Course
import com.ustc.timetable.timetable.domain.CourseId
import com.ustc.timetable.timetable.domain.CourseMeeting
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.ManualScheduleItem
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.SemesterDefaults
import com.ustc.timetable.timetable.domain.WeekPattern
import java.time.Instant
import java.time.LocalTime

/**
 * Debug-only seed fixture（不进入 release）：2026 秋季纯手动学期（portalLinked=false，
 * 永不被学校同步选中、refresh 不显示）+ 学校样例（教师-周次拆分）+ 手动任意分钟项目。
 * 幂等：数据库已有任意学期即 no-op。复用 A5/A6 production persistence API。
 */
object DebugSeed {

    private const val SEMESTER_ID = "debug-seed-2026-autumn"
    private const val FINGERPRINT = "debug-seed-v1"

    suspend fun seedIfEmpty(
        semesters: SemesterRepository,
        manual: ManualItemRepository,
        db: TimetableDatabase,
        bundledProfile: ScheduleProfile,
        now: Instant = Instant.now(),
    ) {
        val semester = semesters.createInitialLocalSemesterIfEmpty(
            SemesterDefaults.AUTUMN_2026(id = SEMESTER_ID, profileId = "replaced-by-clone", now = now),
            bundledProfile,
        ) ?: return

        val chem = CourseId("debug-c-chem")
        val math = CourseId("debug-c-math")
        val courses = listOf(
            Course(chem, semester.id, "name:高等无机化学", "CHEM5013P", "高等无机化学", 3.0, null),
            Course(math, semester.id, "name:线性代数", "MATH1001P", "线性代数", 2.0, null),
        )
        // 高等无机化学 周五 3–5 节：教师-周次分段拆为 3 条 meeting（frozen §7）
        val meetings = listOf(
            CourseMeeting(MeetingId("debug-m-chem-1"), chem, 5, 3, 5, WeekPattern.range(2, 6), "TH-B301", listOf("吴长征")),
            CourseMeeting(MeetingId("debug-m-chem-2"), chem, 5, 3, 5, WeekPattern.range(7, 12), "TH-B301", listOf("刘斯")),
            CourseMeeting(MeetingId("debug-m-chem-3"), chem, 5, 3, 5, WeekPattern.range(13, 18), "TH-B301", listOf("郭宇桥")),
            // 线性代数 周二 3–4 节 1–20 周
            CourseMeeting(MeetingId("debug-m-math-1"), math, 2, 3, 4, WeekPattern.range(1, 20), "3C107", listOf("李雷")),
        )
        db.applySchoolSnapshot(semester.id, courses, meetings, FINGERPRINT, now)

        // 手动：周六 14:20–16:00 固态电池专题讲座（第 2 周）
        manual.add(
            ManualScheduleItem(
                id = ManualItemId("debug-i-lecture"),
                semesterId = semester.id,
                title = "固态电池专题讲座",
                weekday = 6,
                startTime = LocalTime.of(14, 20),
                endTime = LocalTime.of(16, 0),
                weekPattern = WeekPattern.of(2),
                location = null,
                note = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }
}
