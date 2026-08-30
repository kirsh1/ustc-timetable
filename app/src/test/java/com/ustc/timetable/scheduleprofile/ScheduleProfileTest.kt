package com.ustc.timetable.scheduleprofile

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleProfileTest {

    /**
     * 测试专用合成 13 节 fixture（非官方时间）。官方作息的唯一权威数据是
     * `assets/profile/official_2026autumn.json`，由 OfficialProfileLoaderTest 验证；
     * 纯函数测试不复制官方表。
     */
    private fun syntheticPeriods(): List<PeriodTime> {
        val times = listOf(
            "08:00" to "08:45", "08:55" to "09:40", "09:50" to "10:35", "10:45" to "11:30", "11:40" to "12:25",
            "13:30" to "14:15", "14:25" to "15:10", "15:20" to "16:05", "16:15" to "17:00", "17:10" to "17:55",
            "19:00" to "19:45", "19:55" to "20:40", "20:50" to "21:35",
        )
        return times.mapIndexed { i, (s, e) -> PeriodTime(i + 1, LocalTime.parse(s), LocalTime.parse(e)) }
    }

    private fun testProfile(): ScheduleProfile = ScheduleProfile(
        id = "profile.test.synthetic", name = "test synthetic", isBundledOfficial = false, periods = syntheticPeriods(),
    )

    // ---- LocalTimeRange（ClosedRange<LocalTime> 自带 contains 语义，不自行 shadow） ----

    @Test fun localTimeRange_contains_boundaries() {
        val r = LocalTimeRange(LocalTime.of(8, 0), LocalTime.of(8, 45))
        assertTrue(r.contains(LocalTime.of(8, 0)))
        assertTrue(r.contains(LocalTime.of(8, 45)))
        assertTrue(r.contains(LocalTime.of(8, 20)))
    }

    @Test fun localTimeRange_outside_false() {
        val r = LocalTimeRange(LocalTime.of(8, 0), LocalTime.of(8, 45))
        assertFalse(r.contains(LocalTime.of(7, 59)))
        assertFalse(r.contains(LocalTime.of(8, 46)))
    }

    @Test fun localTimeRange_inverted_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalTimeRange(LocalTime.of(10, 0), LocalTime.of(9, 0))
        }
    }

    // ---- timeRange：节次 → 真实时间 ----

    @Test fun timeRange_maps_period_boundaries() {
        val r = testProfile().timeRange(1, 2)
        assertEquals(LocalTime.of(8, 0), r.start)
        assertEquals(LocalTime.of(9, 40), r.endInclusive)
    }

    @Test fun timeRange_single_period() {
        val r = testProfile().timeRange(3, 3)
        assertEquals(LocalTime.of(9, 50), r.start)
        assertEquals(LocalTime.of(10, 35), r.endInclusive)
    }

    @Test fun timeRange_period_zero_throws() {
        assertThrows(IllegalArgumentException::class.java) { testProfile().timeRange(0, 2) }
    }

    @Test fun timeRange_period_fourteen_throws() {
        assertThrows(IllegalArgumentException::class.java) { testProfile().timeRange(12, 14) }
    }

    @Test fun timeRange_inverted_throws() {
        assertThrows(IllegalArgumentException::class.java) { testProfile().timeRange(5, 3) }
    }

    @Test fun dayWindow_equals_min_start_max_end() {
        val w = testProfile().dayWindow()
        assertEquals(LocalTime.of(8, 0), w.start)
        assertEquals(LocalTime.of(21, 35), w.endInclusive)
    }

    // ---- ScheduleProfile 构造不变量 ----

    @Test fun profile_requires_exactly_13_periods() {
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleProfile("id", "n", false, syntheticPeriods().dropLast(1))
        }
    }

    @Test fun profile_requires_numbers_1_to_13_in_order() {
        val swapped = syntheticPeriods().toMutableList()
        val tmp = swapped[0]
        swapped[0] = swapped[1]
        swapped[1] = tmp
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleProfile("id", "n", false, swapped)
        }
    }

    @Test fun periodTime_end_after_start_enforced() {
        // end > start 由 PeriodTime 构造器保证（ScheduleProfile 因此结构性满足该不变量）
        assertThrows(IllegalArgumentException::class.java) {
            PeriodTime(3, LocalTime.of(10, 35), LocalTime.of(9, 50))
        }
    }

    @Test fun profile_requires_next_start_after_previous_end() {
        val touching = syntheticPeriods().toMutableList()
        touching[1] = PeriodTime(2, LocalTime.of(8, 45), LocalTime.of(9, 30))  // 起点恰等于第1节结束
        assertThrows(IllegalArgumentException::class.java) {
            ScheduleProfile("id", "n", false, touching)
        }
    }
}
