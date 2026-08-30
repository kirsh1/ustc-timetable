package com.ustc.timetable.timetable.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 教学周纯函数（SPEC §3.2）。`week1Start` 是当前自然周的唯一 authority；
 * `startDate/endDate` 不参与第几周的推导。
 */
object WeekCalculator {

    /** date 相对 [semester.week1Start] 的教学周序号（Monday-first）；早于第 1 周或超出 [1, totalWeeks] 返回 null。 */
    fun weekNumberOn(date: LocalDate, semester: Semester): Int? {
        if (date.isBefore(semester.week1Start)) return null
        val w = ChronoUnit.WEEKS.between(semester.week1Start, date).toInt() + 1
        return if (w in 1..semester.totalWeeks) w else null
    }

    /** 第 [week] 周的周一；week 超出 1..totalWeeks 抛 IllegalArgumentException。 */
    fun weekStart(semester: Semester, week: Int): LocalDate {
        require(week in 1..semester.totalWeeks) { "week out of range: $week" }
        return semester.week1Start.plusWeeks((week - 1).toLong())
    }

    /** 第 [week] 周的周一到周日区间。 */
    fun weekRange(semester: Semester, week: Int): LocalDateRange {
        val s = weekStart(semester, week)
        return LocalDateRange(s, s.plusDays(6))
    }

    fun naturalWeekToday(semester: Semester, today: LocalDate): Int? = weekNumberOn(today, semester)
}
