package com.ustc.timetable.timetable.layout

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimelineAxisTest {

    private val axis by lazy { TimelineAxis(LocalTime.of(7, 50), LocalTime.of(21, 55)) }

    // ---- 不变量 ----

    @Test fun axis_equal_start_end_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineAxis(LocalTime.of(7, 50), LocalTime.of(7, 50))
        }
    }

    @Test fun axis_inverted_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            TimelineAxis(LocalTime.of(21, 55), LocalTime.of(7, 50))
        }
    }

    // ---- fractionOf ----

    @Test fun axis_fraction_boundaries_0750_2155() {
        assertEquals(0f, axis.fractionOf(LocalTime.of(7, 50)), 1e-6f)
        assertEquals(1f, axis.fractionOf(LocalTime.of(21, 55)), 1e-6f)
    }

    @Test fun axis_fraction_before_start_is_zero() {
        assertEquals(0f, axis.fractionOf(LocalTime.of(6, 0)), 1e-6f)
    }

    @Test fun axis_fraction_after_end_is_one() {
        assertEquals(1f, axis.fractionOf(LocalTime.of(22, 30)), 1e-6f)
    }

    @Test fun axis_timeAt_clamps_fraction_below_zero() {
        assertEquals(LocalTime.of(7, 50), axis.timeAt(-1f))
    }

    @Test fun axis_timeAt_clamps_fraction_above_one() {
        assertEquals(LocalTime.of(21, 55), axis.timeAt(2f))
    }

    @Test fun axis_timeAt_fractionOf_roundtrip_preserves_minute_points() {
        for (t in listOf(
            LocalTime.of(7, 50), LocalTime.of(9, 45), LocalTime.of(14, 20),
            LocalTime.of(15, 55), LocalTime.of(21, 55),
        )) {
            assertEquals(t, axis.timeAt(axis.fractionOf(t)))
        }
    }

    // ---- A3 bundled profile 自然得到该轴 ----
    @Test fun axisOf_delegates_to_profile_dayWindow() {
        // 合成 profile dayWindow 14:00–15:30，axisOf 必须忠实该窗口
        val p = com.ustc.timetable.scheduleprofile.ScheduleProfile(
            id = "p-test",
            name = "test",
            isBundledOfficial = false,
            periods = (1..13).map { i ->
                com.ustc.timetable.scheduleprofile.PeriodTime(
                    number = i,
                    start = LocalTime.of(6 + i, 0),
                    end = LocalTime.of(6 + i, 45),
                )
            },
        )
        val a = TimelineAxis.axisOf(p)
        assertEquals(LocalTime.of(7, 0), a.start)
        assertEquals(LocalTime.of(19, 45), a.endInclusive)
    }
}
