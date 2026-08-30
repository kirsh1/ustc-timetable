package com.ustc.timetable.timetable.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPatternTest {
    @Test fun contains_outOfRange_false() {
        val p = WeekPattern.range(1, 20)
        assertFalse(p.contains(0))
        assertFalse(p.contains(21))
        assertFalse(WeekPattern.EMPTY.contains(1))
    }

    @Test fun parse_1_20() { assertEquals(WeekPattern.range(1, 20), WeekPattern.parse("1-20周")) }

    @Test fun parse_2_6() { assertEquals(WeekPattern.range(2, 6), WeekPattern.parse("2-6")) }

    @Test fun parse_2_4_6_8() { assertEquals(WeekPattern.of(2, 4, 6, 8), WeekPattern.parse("2,4,6,8")) }

    @Test fun parse_2_6_8_10_12() {
        assertEquals(
            WeekPattern.range(2, 6).union(WeekPattern.of(8)).union(WeekPattern.range(10, 12)),
            WeekPattern.parse("2-6,8,10-12"),
        )
    }

    @Test fun parse_中文逗号与装饰字符() { assertEquals(WeekPattern.range(3, 5), WeekPattern.parse("第3–5周，5周")) }

    @Test fun parse_invertedRange_throws() {
        assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("5-3") }
    }

    @Test fun parse_empty_throws() {
        assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("第周") }
    }

    @Test fun parse_weekOver63_throws() {
        assertThrows(IllegalArgumentException::class.java) { WeekPattern.parse("64") }
    }

    @Test fun parse_zero_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WeekPattern.parse("0")
        }
    }

    @Test fun parse_unknown_text_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            WeekPattern.parse("2foo")
        }
    }

    @Test fun format_canonicalRanges() {
        assertEquals("2-6,8,10-12", WeekPattern.parse("2-6,8,10-12").format())
        assertEquals("1", WeekPattern.of(1).format())
        assertEquals("1-20", WeekPattern.range(1, 20).format())
    }

    @Test fun roundtrip_parse_format() {
        val raw = "2-6,8,10-12"
        assertEquals(raw, WeekPattern.parse(raw).format())
    }

    @Test fun oddWithin_单周语义() { assertEquals(WeekPattern.of(1, 3, 5, 7), WeekPattern.oddWithin(1, 7)) }

    @Test fun evenWithin_双周语义() { assertEquals(WeekPattern.of(2, 4, 6), WeekPattern.evenWithin(1, 6)) }

    @Test fun of_duplicate_weeks_naturally_dedupe() {
        assertEquals(WeekPattern.of(2, 3), WeekPattern.of(2, 2, 3))
    }

    @Test fun union_and_contains() {
        val p = WeekPattern.range(2, 6).union(WeekPattern.of(8))
        assertTrue(p.contains(2)); assertTrue(p.contains(6)); assertTrue(p.contains(8))
        assertFalse(p.contains(7)); assertFalse(p.contains(1))
    }
}
