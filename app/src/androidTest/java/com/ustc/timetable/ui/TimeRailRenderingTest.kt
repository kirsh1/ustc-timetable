package com.ustc.timetable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ustc.timetable.scheduleprofile.PeriodTime
import com.ustc.timetable.scheduleprofile.ScheduleProfile
import com.ustc.timetable.timetable.layout.FixedTimeRail
import com.ustc.timetable.timetable.layout.SegmentedTimelineAxis
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeRailRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun right_edge_has_no_separator_stroke() {
        val periods = (1..13).map { number ->
            val start = LocalTime.of(7, 50).plusMinutes((number - 1) * 50L)
            PeriodTime(number, start, start.plusMinutes(45))
        }
        val axis = SegmentedTimelineAxis.from(ScheduleProfile("rail-test", "rail-test", false, periods))
        compose.setContent {
            MaterialTheme {
                Surface {
                    Box {
                        FixedTimeRail(periods, axis, Modifier.height(300.dp))
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("fixed_time_rail").captureToImage().toPixelMap()
        // The header is blank: its rightmost pixels must match the adjacent background.
        // A rail-owned vertical stroke changes these pixels even without any course grid.
        for (y in 4..12) {
            val background = pixels[pixels.width - 4, y]
            assertEquals(background, pixels[pixels.width - 1, y])
        }
    }
}
