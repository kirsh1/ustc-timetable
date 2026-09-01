package com.ustc.timetable.timetable.layout

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPressResolverTest {

    private val bundledAxis = TimelineAxis(LocalTime.of(7, 50), LocalTime.of(21, 55))
    private val customAxis = TimelineAxis(LocalTime.of(7, 53), LocalTime.of(21, 57))

    private fun resolve(
        columnFraction: Float = 0f,
        yFraction: Float = 0f,
        axis: TimelineAxis = bundledAxis,
    ): LongPressDraft = LongPressResolver.resolve(columnFraction, yFraction, axis)

    @Test fun column_zero_is_monday() {
        assertEquals(1, resolve(columnFraction = 0f).weekday)
    }

    @Test fun column_half_is_thursday() {
        assertEquals(4, resolve(columnFraction = 0.5f).weekday)
    }

    @Test fun column_near_one_is_sunday() {
        assertEquals(7, resolve(columnFraction = Math.nextDown(1f)).weekday)
    }

    @Test fun seven_columns_cover_entire_fraction_domain() {
        for (weekday in 1..7) {
            val midpoint = (weekday - 0.5f) / 7f
            assertEquals(weekday, resolve(columnFraction = midpoint).weekday)
        }
    }

    @Test fun exact_column_boundary_belongs_to_next_day() {
        for (i in 1..6) {
            val boundary = i / 7f
            assertEquals("boundary $i/7", i + 1, resolve(columnFraction = boundary).weekday)
        }
    }

    @Test fun just_before_boundary_belongs_to_previous_day() {
        for (i in 1..6) {
            val boundary = i / 7f
            assertEquals("nextDown($i/7)", i, resolve(columnFraction = Math.nextDown(boundary)).weekday)
        }
    }

    @Test fun column_below_zero_clamps_to_monday() {
        assertEquals(1, resolve(columnFraction = -2f).weekday)
    }

    @Test fun column_one_defensively_maps_to_sunday() {
        assertEquals(7, resolve(columnFraction = 1f).weekday)
    }

    @Test fun column_above_one_clamps_to_sunday() {
        assertEquals(7, resolve(columnFraction = 2f).weekday)
    }

    @Test fun nonfinite_column_fraction_throws() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                resolve(columnFraction = value)
            }
        }
    }

    @Test fun nonfinite_y_fraction_throws() {
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                resolve(yFraction = value)
            }
        }
    }

    @Test fun y_zero_maps_to_axis_start() {
        assertEquals(bundledAxis.start, resolve(yFraction = 0f).snappedStart)
    }

    @Test fun y_below_zero_clamps_to_start() {
        assertEquals(bundledAxis.start, resolve(yFraction = -1f).snappedStart)
    }

    @Test fun y_above_one_clamps_to_end() {
        assertEquals(bundledAxis.endInclusive, resolve(yFraction = 2f).snappedStart)
    }

    @Test fun snap_1423_to_1420() {
        val y = bundledAxis.fractionOf(LocalTime.of(14, 23))
        assertEquals(LocalTime.of(14, 20), resolve(yFraction = y).snappedStart)
    }

    @Test fun snap_exact_1425_stays_1425() {
        val y = bundledAxis.fractionOf(LocalTime.of(14, 25))
        assertEquals(LocalTime.of(14, 25), resolve(yFraction = y).snappedStart)
    }

    @Test fun snap_1429_to_1425() {
        val y = bundledAxis.fractionOf(LocalTime.of(14, 29))
        assertEquals(LocalTime.of(14, 25), resolve(yFraction = y).snappedStart)
    }

    @Test fun bundled_axis_top_bottom_are_valid() {
        assertEquals(LocalTime.of(7, 50), resolve(yFraction = 0f).snappedStart)
        assertEquals(LocalTime.of(21, 55), resolve(yFraction = 1f).snappedStart)
    }

    @Test fun non_five_minute_axis_start_clamps_back_inside_window() {
        assertEquals(LocalTime.of(7, 53), resolve(yFraction = 0f, axis = customAxis).snappedStart)
    }

    @Test fun non_five_minute_axis_end_preserves_floor5_semantics() {
        assertEquals(LocalTime.of(21, 55), resolve(yFraction = 1f, axis = customAxis).snappedStart)
    }

    @Test fun y_one_never_exceeds_axis_end_and_preserves_floor5_semantics() {
        for (axis in listOf(bundledAxis, customAxis)) {
            val result = resolve(yFraction = 1f, axis = axis).snappedStart
            assertTrue(result <= axis.endInclusive)
            assertEquals(0, result.minute % 5)
        }
    }

    @Test fun resolved_start_always_inside_axis() {
        val samples = listOf(-1f, 0f, .001f, .1f, .25f, .5f, .75f, .999f, 1f, 2f)
        for (axis in listOf(bundledAxis, customAxis)) {
            for (y in samples) {
                val result = resolve(yFraction = y, axis = axis).snappedStart
                assertTrue("$result precedes ${axis.start} for y=$y", result >= axis.start)
                assertTrue("$result exceeds ${axis.endInclusive} for y=$y", result <= axis.endInclusive)
            }
        }
    }
}
