package com.ustc.timetable.timetable.ui

import com.ustc.timetable.scheduleprofile.PeriodTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundaryTimePillTest {
    private val periods = listOf(
        PeriodTime(8, LocalTime.of(15, 55), LocalTime.of(16, 40)),
        PeriodTime(9, LocalTime.of(16, 45), LocalTime.of(17, 30)),
    )

    @Test fun only_nonstandard_endpoints_receive_labels() {
        assertEquals(BoundaryTimePills("16:10", "17:50"), boundaryTimePills(LocalTime.of(16, 10), LocalTime.of(17, 50), periods))
        assertEquals(BoundaryTimePills(null, "17:50"), boundaryTimePills(LocalTime.of(15, 55), LocalTime.of(17, 50), periods))
        assertEquals(BoundaryTimePills(null, null), boundaryTimePills(LocalTime.of(15, 55), LocalTime.of(16, 40), periods))
    }
}
