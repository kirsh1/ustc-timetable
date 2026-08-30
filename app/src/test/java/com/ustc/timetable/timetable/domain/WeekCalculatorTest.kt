package com.ustc.timetable.timetable.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class WeekCalculatorTest {

    private val sem = SemesterDefaults.AUTUMN_2026(id = "s1", profileId = "p1")

    /** 内部自洽的春季学期 fixture（仅用于周计算语义，不宣称为学校官方日期）。 */
    private fun spring() = Semester(
        id = SemesterId("s-spring"),
        displayName = "2026-2027 春季",
        academicYear = "2026-2027",
        term = Term.SPRING,
        week1Start = LocalDate.of(2027, 2, 22),  // Monday
        totalWeeks = 18,
        startDate = LocalDate.of(2027, 2, 20),
        endDate = LocalDate.of(2027, 6, 27),
        importedAt = Instant.EPOCH,
        lastSyncedAt = null,
        isCurrentAcademicSemester = false,
        portalLinked = true,
        profileId = ProfileId("p1"),
        sourceFingerprint = null,
    )

    // ---- A2 correction 1: 官方基准四值 ----

    @Test fun autumn2026_official_dates_are_exact() {
        assertEquals(LocalDate.of(2026, 8, 30), sem.startDate)   // 开学注册
        assertEquals(LocalDate.of(2026, 8, 31), sem.week1Start)  // 第1教学周周一
        assertEquals(LocalDate.of(2027, 1, 15), sem.endDate)
        assertEquals(20, sem.totalWeeks)
    }

    // ---- 周计算 ----

    @Test fun week1_on_2026_08_31() {
        assertEquals(1, WeekCalculator.weekNumberOn(LocalDate.of(2026, 8, 31), sem))
    }

    @Test fun week2_span_2026_09_07_to_09_13() {
        assertEquals(2, WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 7), sem))
        assertEquals(2, WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 13), sem))
    }

    @Test fun sunday_2026_09_06_still_week1() {
        assertEquals(1, WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 6), sem))
    }

    @Test fun before_week1_null() {
        // 注册日 2026-08-30 早于 week1Start：不是教学周（week1Start 是唯一 authority）
        assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2026, 8, 30), sem))
    }

    @Test fun beyond_totalWeeks_null() {
        assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2027, 1, 18), sem))
    }

    @Test fun end_date_is_still_week20_not_null() {
        // endDate 2027-01-15 是周五，落在第 20 教学周内
        assertEquals(20, WeekCalculator.weekNumberOn(LocalDate.of(2027, 1, 15), sem))
    }

    @Test fun weekStart_mondayInvariant() {
        assertEquals(LocalDate.of(2026, 9, 7), WeekCalculator.weekStart(sem, 2))
        assertEquals(DayOfWeek.MONDAY, WeekCalculator.weekStart(sem, 10).dayOfWeek)
    }

    @Test fun weekRange_monday_to_sunday() {
        val r = WeekCalculator.weekRange(sem, 2)
        assertEquals(LocalDate.of(2026, 9, 7), r.start)
        assertEquals(LocalDate.of(2026, 9, 13), r.endInclusive)
    }

    @Test fun weekStart_zero_throws() {
        assertThrows(IllegalArgumentException::class.java) { WeekCalculator.weekStart(sem, 0) }
    }

    @Test fun weekStart_after_totalWeeks_throws() {
        assertThrows(IllegalArgumentException::class.java) { WeekCalculator.weekStart(sem, 21) }
    }

    @Test fun naturalWeekToday_delegates_to_weekNumberOn() {
        assertEquals(
            WeekCalculator.weekNumberOn(LocalDate.of(2026, 9, 7), sem),
            WeekCalculator.naturalWeekToday(sem, LocalDate.of(2026, 9, 7)),
        )
        assertNull(WeekCalculator.naturalWeekToday(sem, LocalDate.of(2026, 8, 30)))
    }

    @Test fun semesterTransition_noOverlap() {
        val spring = spring()
        assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2027, 1, 10), spring))
        assertNull(WeekCalculator.weekNumberOn(LocalDate.of(2027, 3, 1), sem))
        assertEquals(1, WeekCalculator.weekNumberOn(LocalDate.of(2027, 2, 22), spring))
        assertEquals(18, WeekCalculator.weekNumberOn(LocalDate.of(2027, 6, 27), spring))
    }

    // ---- A2 correction 2: Semester 构造不变量 ----

    @Test fun semester_nonMonday_week1Start_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            sem.copy(week1Start = LocalDate.of(2026, 9, 1))  // Tuesday
        }
    }

    @Test fun semester_start_after_end_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            sem.copy(startDate = LocalDate.of(2027, 1, 16))
        }
    }

    @Test fun semester_week1Start_outside_semester_dates_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            sem.copy(startDate = LocalDate.of(2026, 9, 5))  // startDate 晚于 week1Start
        }
    }

    @Test fun semester_totalWeeks_zero_or_over63_throws() {
        assertThrows(IllegalArgumentException::class.java) { sem.copy(totalWeeks = 0) }
        assertThrows(IllegalArgumentException::class.java) { sem.copy(totalWeeks = 64) }
    }

    @Test fun localDateRange_inverted_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalDateRange(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1))
        }
    }
}
