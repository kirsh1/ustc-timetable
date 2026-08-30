package com.ustc.timetable.scheduleprofile

import java.time.Duration
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 官方作息真实性的唯一验证点：从真实 APK asset（`assets/profile/official_2026autumn.json`）
 * 逐行断言官方 13 节表。production 中不存在第二份官方时间常量。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfficialProfileLoaderTest {

    private val ctx = RuntimeEnvironment.getApplication()

    @Test fun bundled_official_2026_autumn_periods_are_exact() {
        val p = OfficialProfileLoader.load(ctx)
        assertEquals("profile.bundled.ustc.2026-autumn", p.id)
        assertTrue(p.isBundledOfficial)
        assertEquals("USTC 2026 秋季官方作息", p.name)
        val expected = listOf(
            PeriodTime(1, LocalTime.of(7, 50), LocalTime.of(8, 35)),
            PeriodTime(2, LocalTime.of(8, 40), LocalTime.of(9, 25)),
            PeriodTime(3, LocalTime.of(9, 45), LocalTime.of(10, 30)),
            PeriodTime(4, LocalTime.of(10, 35), LocalTime.of(11, 20)),
            PeriodTime(5, LocalTime.of(11, 25), LocalTime.of(12, 10)),
            PeriodTime(6, LocalTime.of(14, 0), LocalTime.of(14, 45)),
            PeriodTime(7, LocalTime.of(14, 50), LocalTime.of(15, 35)),
            PeriodTime(8, LocalTime.of(15, 55), LocalTime.of(16, 40)),
            PeriodTime(9, LocalTime.of(16, 45), LocalTime.of(17, 30)),
            PeriodTime(10, LocalTime.of(17, 35), LocalTime.of(18, 20)),
            PeriodTime(11, LocalTime.of(19, 30), LocalTime.of(20, 15)),
            PeriodTime(12, LocalTime.of(20, 20), LocalTime.of(21, 5)),
            PeriodTime(13, LocalTime.of(21, 10), LocalTime.of(21, 55)),
        )
        assertEquals(expected, p.periods)
    }

    @Test fun dayWindow_is_0750_2155() {
        val w = OfficialProfileLoader.load(ctx).dayWindow()
        assertEquals(LocalTime.of(7, 50), w.start)
        assertEquals(LocalTime.of(21, 55), w.endInclusive)
    }

    @Test fun gap_after_period2_and_period7_is_20min() {
        val periods = OfficialProfileLoader.load(ctx).periods
        assertEquals(Duration.ofMinutes(20), Duration.between(periods[1].end, periods[2].start))  // 第2节→第3节
        assertEquals(Duration.ofMinutes(20), Duration.between(periods[6].end, periods[7].start))  // 第7节→第8节
    }

    @Test fun asset_periods_are_13_and_monotonic() {
        val periods = OfficialProfileLoader.load(ctx).periods
        assertEquals(13, periods.size)
        periods.zipWithNext().forEach { (a, b) ->
            assertTrue("period ${b.number} must start after period ${a.number} ends", b.start > a.end)
        }
    }
}
