package com.ustc.timetable.timetable.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import com.ustc.timetable.timetable.domain.LocalDateRange
import com.ustc.timetable.timetable.domain.MeetingId
import com.ustc.timetable.timetable.domain.ManualItemId
import com.ustc.timetable.timetable.domain.WeekPattern
import com.ustc.timetable.timetable.ui.BlockTexts
import com.ustc.timetable.timetable.ui.CoursePalette
import com.ustc.timetable.scheduleprofile.PeriodTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeeklyTimetableGridTest {

    private val axis = TimelineAxis(LocalTime.of(7, 50), LocalTime.of(21, 55))
    private val periodStarts = listOf(
        LocalTime.of(7, 50), LocalTime.of(8, 40), LocalTime.of(9, 45), LocalTime.of(10, 35), LocalTime.of(11, 25),
        LocalTime.of(14, 0), LocalTime.of(14, 50), LocalTime.of(15, 55), LocalTime.of(16, 45), LocalTime.of(17, 35),
        LocalTime.of(19, 30), LocalTime.of(20, 20), LocalTime.of(21, 10),
    )
    private val periods = periodStarts.mapIndexed { index, start ->
        PeriodTime(index + 1, start, start.plusMinutes(45))
    }
    // weekDates 周一 = 2026-09-07 → 周日 09-13；header 为中文星期 + M-dd 两行。
    private fun weekDatesMonday(start: LocalDate = LocalDate.of(2026, 9, 7)) =
        LocalDateRange(start, start.plusDays(6))

    private fun schoolBlock(
        meetingId: String, weekday: Int, start: LocalTime, end: LocalTime,
        showWeek: WeekPattern = WeekPattern.range(1, 20),
        teacher: String = "刘斯", title: String = "高等无机化学", location: String = "TH-B301",
    ): TimedBlock = object : TimedBlock {
        override val colorKey: String = "c:$meetingId"
        override val meetingId: MeetingId = MeetingId(meetingId)
        override val manualItemId: ManualItemId? = null
        override val weekday: Int = weekday
        override val start: LocalTime = start
        override val endInclusive: LocalTime = end
        override val weeks: WeekPattern = showWeek
        override val title: String = title
        override val location: String = location
        override val teacherNames: List<String> = listOf(teacher)
    }

    private fun manualBlock(
        manualId: String, weekday: Int, start: LocalTime, end: LocalTime,
        showWeek: WeekPattern = WeekPattern.range(1, 20),
    ): TimedBlock = object : TimedBlock {
        override val colorKey: String = "m:$manualId"
        override val meetingId: MeetingId? = null
        override val manualItemId: ManualItemId = ManualItemId(manualId)
        override val weekday: Int = weekday
        override val start: LocalTime = start
        override val endInclusive: LocalTime = end
        override val weeks: WeekPattern = showWeek
        override val title: String = "组会"
        override val location: String = "教室A"
        override val teacherNames: List<String> = emptyList()
    }

    private fun placed(vararg blocks: TimedBlock): List<PlacedBlock> = WeeklyTimetableLayout.place(blocks.toList(), axis)

    @get:Rule val rule = createComposeRule()

    private fun setContentGrid(
        school: List<PlacedBlock> = emptyList(),
        manual: List<PlacedBlock> = emptyList(),
        showNonCurrentWeek: Boolean = false,
        viewedWeek: Int = 2,
        nowLine: LocalTime? = null,
        today: LocalDate? = null,
        weekDates: LocalDateRange = weekDatesMonday(),
        width: androidx.compose.ui.unit.Dp? = null,
        height: androidx.compose.ui.unit.Dp? = null,
        onSchool: (MeetingId) -> Unit = {},
        onManual: (ManualItemId) -> Unit = {},
        onEmpty: (LongPressDraft) -> Unit = {},
        segmentedAxis: SegmentedTimelineAxis? = null,
        showTimeRail: Boolean = true,
    ) {
        rule.setContent {
            val grid: @Composable () -> Unit = {
                WeeklyTimetableGrid(
                    weekDates, axis, periodStarts, school, manual, showNonCurrentWeek,
                    viewedWeek, nowLine, today, onSchool, onManual, onEmpty,
                    segmentedAxis = segmentedAxis,
                    periods = periods,
                    showTimeRail = showTimeRail,
                )
            }
            if (width != null && height != null) {
                Box(Modifier.requiredSize(width, height)) { grid() }
            } else {
                grid()
            }
        }
    }

    // ---- 列 / header ----

    @Test fun seven_columns_all_visible() {
        setContentGrid()
        rule.onAllNodesWithText("周一").assertCountEquals(1)
        rule.onAllNodesWithText("9-07").assertCountEquals(1)
        rule.onAllNodesWithText("周日").assertCountEquals(1)
        rule.onAllNodesWithText("9-13").assertCountEquals(1)
    }

    @Test fun weekend_columns_present_when_empty() {
        setContentGrid()
        rule.onAllNodesWithText("周六").assertCountEquals(1)
        rule.onAllNodesWithText("9-12").assertCountEquals(1)
        rule.onAllNodesWithText("周日").assertCountEquals(1)
        rule.onAllNodesWithText("9-13").assertCountEquals(1)
    }

    @Test fun seven_columns_use_entire_post_gutter_width() {
        val p = placed(schoolBlock("m1", 1, LocalTime.of(9, 45), LocalTime.of(10, 30))).first()
        assertEquals(0, p.column)
        assertEquals(1, p.columnsInGroup)  // 单块占满整列宽（无 gutter 二次扣减的语义由 Grid 坐标公式保证）
    }

    @Test fun header_columns_align_with_grid_columns() {
        setContentGrid()
        for (dow in 0..6) rule.onAllNodesWithTag("header_$dow").assertCountEquals(1)
    }

    // ---- TimeGutter label authority（correction 4） ----

    @Test fun time_gutter_labels_come_from_profile() {
        val labels = (periodStarts + axis.start + axis.endInclusive).toSet().sorted()
        assertEquals(periodStarts.first(), labels.first())   // 顶部标签来自 profile 首节，而非 hardcode
        assertEquals(axis.endInclusive, labels.last())
    }

    @Test fun time_gutter_dedupes_axis_start_with_first_period() {
        val labels = (periodStarts + axis.start + axis.endInclusive).toSet().sorted()
        assertEquals(13 + 1, labels.size)  // 13 个节次开始 + 底部边界；axis.start 与首节去重
    }

    @Test fun time_gutter_includes_axis_end_boundary() {
        val labels = (periodStarts + axis.start + axis.endInclusive).toSet().sorted()
        assertTrue(labels.contains(axis.endInclusive))
    }

    // ---- click / provenance guard ----

    @Test fun school_block_click_fires_meetingId() {
        var got: MeetingId? = null
        setContentGrid(school = placed(schoolBlock("m2", 5, LocalTime.of(9, 45), LocalTime.of(10, 30))), onSchool = { got = it })
        rule.onNodeWithTag("school_block:m2").performClick()
        assertEquals(MeetingId("m2"), got)
    }

    @Test fun manual_block_click_fires_id() {
        var got: ManualItemId? = null
        setContentGrid(manual = placed(manualBlock("i2", 6, LocalTime.of(14, 20), LocalTime.of(16, 0))), onManual = { got = it })
        rule.onNodeWithTag("manual_block:i2").performClick()
        assertEquals(ManualItemId("i2"), got)
    }

    @Test fun school_list_rejects_manual_identity() {
        val bad = object : TimedBlock {
            override val colorKey = "bad"
            override val meetingId: MeetingId? = null
            override val manualItemId: ManualItemId = ManualItemId("i-x")
            override val weekday = 1; override val start = LocalTime.of(9, 0)
            override val endInclusive = LocalTime.of(10, 0); override val weeks = WeekPattern.range(1, 20)
            override val title = "错"; override val location = "A"; override val teacherNames = emptyList<String>()
        }
        assertThrows(Exception::class.java) {
            setContentGrid(school = placed(bad))
            rule.waitForIdle()
        }
    }

    @Test fun manual_list_rejects_school_identity() {
        val bad = object : TimedBlock {
            override val colorKey = "bad"
            override val meetingId: MeetingId = MeetingId("m-x")
            override val manualItemId: ManualItemId? = null
            override val weekday = 1; override val start = LocalTime.of(9, 0)
            override val endInclusive = LocalTime.of(10, 0); override val weeks = WeekPattern.range(1, 20)
            override val title = "错"; override val location = "A"; override val teacherNames = emptyList<String>()
        }
        assertThrows(Exception::class.java) {
            setContentGrid(manual = placed(bad))
            rule.waitForIdle()
        }
    }

    // ---- long press（correction 7） ----

    @Test fun empty_area_longpress_resolves_bounded_draft() {
        var draft: LongPressDraft? = null
        setContentGrid(onEmpty = { draft = it })
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            advanceEventTime(1_000L)  // 超过 long-press timeout
            up()
        }
        rule.waitForIdle()
        assertTrue(draft!!.weekday in 1..7)
        assertTrue(draft!!.snappedStart in axis.start..axis.endInclusive)
    }

    @Test fun longpress_on_block_does_not_fire_empty_area_callback() {
        var fired = false
        setContentGrid(
            school = placed(schoolBlock("m3", 5, LocalTime.of(9, 45), LocalTime.of(10, 30))),
            onEmpty = { fired = true },
        )
        rule.onNodeWithTag("school_block:m3").performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }
        rule.waitForIdle()
        assertTrue(!fired)
    }

    // ---- 内容可见性（correction 8：宽度同时约束教师与时间） ----

    @Test fun narrow_overlap_hides_teacher_and_time() {
        // 320dp 宽容器：重叠两块 → 块宽 ≈ (320-44)/7/2 ≈ 19dp < 52dp
        setContentGrid(
            school = placed(
                schoolBlock("n1", 1, LocalTime.of(9, 0), LocalTime.of(12, 0)),
                schoolBlock("n2", 1, LocalTime.of(9, 30), LocalTime.of(12, 30)),
            ),
            width = 320.dp, height = 700.dp,
        )
        // 未合并树：块的 contentDescription 会覆盖合并语义中的 Text，须查未合并子 Text
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("09:00–12:00", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun tall_wide_block_shows_teacher_and_time() {
        // 800dp 宽容器：单块整列 ≈ 108dp > 52dp；块高 ≈ 850dp > 90dp
        setContentGrid(
            school = placed(schoolBlock("w1", 1, LocalTime.of(8, 0), LocalTime.of(20, 0))),
            width = 800.dp, height = 1000.dp,
        )
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("08:00–20:00", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun narrow_tall_block_hides_teacher_and_time() {
        setContentGrid(
            school = placed(
                schoolBlock("narrow", 2, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                schoolBlock("overlap", 2, LocalTime.of(8, 30), LocalTime.of(20, 30), teacher = "王老师"),
            ),
            width = 600.dp,
            height = 1000.dp,
        )
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("08:00–20:00", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun wide_tall_block_can_show_teacher_and_time() {
        setContentGrid(
            school = placed(schoolBlock("wide", 2, LocalTime.of(8, 0), LocalTime.of(20, 0))),
            width = 800.dp,
            height = 1000.dp,
        )
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("08:00–20:00", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test fun standard_portrait_tall_card_shows_teacher_but_keeps_time_width_gated() {
        // 360dp viewport minus the fixed 44dp rail gives a normal single-day card width of about 45dp.
        setContentGrid(
            school = placed(schoolBlock("portrait", 5, LocalTime.of(9, 45), LocalTime.of(12, 10))),
            width = 316.dp,
            height = 650.dp,
            showTimeRail = false,
        )

        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("09:45–12:10", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun narrow_block_keeps_title_and_location() {
        val title = "名字很长必须在窄卡中截断的课程"
        val location = "很长的教学楼房间名称"
        setContentGrid(
            school = placed(
                schoolBlock("content", 3, LocalTime.of(9, 0), LocalTime.of(12, 0), title = title, location = location),
                schoolBlock("content-overlap", 3, LocalTime.of(9, 30), LocalTime.of(12, 30)),
            ),
            width = 600.dp,
            height = 700.dp,
        )

        val card = rule.onNodeWithTag("school_block:content").fetchSemanticsNode().boundsInRoot
        val titleBounds = rule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val locationBounds = rule.onNodeWithText(location, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("title may use multiple lines before ellipsis: $titleBounds", titleBounds.height > 0f)
        assertTrue("title and location must not overlap: $titleBounds vs $locationBounds", titleBounds.bottom <= locationBounds.top)
        assertTrue("title must stay inside card: $titleBounds vs $card", titleBounds.left >= card.left && titleBounds.right <= card.right)
        assertTrue("location must stay inside card: $locationBounds vs $card", locationBounds.left >= card.left && locationBounds.right <= card.right)
    }

    @Test fun course_name_and_location_have_priority_over_teacher() {
        setContentGrid(
            school = placed(schoolBlock("priority", 2, LocalTime.of(9, 0), LocalTime.of(10, 30))),
            width = 600.dp,
            height = 500.dp,
        )
        rule.onAllNodesWithText("高等无机化学", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("TH-B301", useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun course_title_uses_available_height_before_optional_metadata() {
        assertEquals(5, BlockTexts.titleMaxLinesFor(cardHeightDp = 120f, hasLocation = true))
        assertEquals(3, BlockTexts.titleMaxLinesFor(cardHeightDp = 72f, hasLocation = true))
    }

    @Test fun location_is_rendered_as_separate_line() {
        setContentGrid(
            school = placed(schoolBlock("separate", 1, LocalTime.of(8, 0), LocalTime.of(14, 0))),
            width = 800.dp,
            height = 900.dp,
        )
        val title = rule.onNodeWithText("高等无机化学", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val location = rule.onNodeWithText("TH-B301", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(title.bottom <= location.top)
    }

    @Test fun tall_card_wraps_complete_course_name_and_location_before_optional_metadata() {
        val title = "新时代 中国特色 社会主义 理论与实践"
        val location = "第五教学楼 东区 研讨教室"
        setContentGrid(
            school = placed(schoolBlock("complete", 1, LocalTime.of(9, 45), LocalTime.of(17, 30), title = title, location = location)),
            width = 360.dp,
            height = 900.dp,
        )

        val card = rule.onNodeWithTag("school_block:complete").fetchSemanticsNode().boundsInRoot
        val titleBounds = rule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val locationBounds = rule.onNodeWithText(location, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(BlockTexts.titleMaxLinesFor(card.height, hasLocation = true) > 3)
        assertTrue(BlockTexts.locationMaxLinesFor(card.height) > 1)
        assertTrue("mandatory title precedes mandatory location: $titleBounds / $locationBounds", titleBounds.bottom <= locationBounds.top)
        assertTrue("mandatory text stays inside the card: $titleBounds / $locationBounds / $card", locationBounds.bottom <= card.bottom)
    }

    @Test fun short_card_preserves_course_identity() {
        setContentGrid(
            school = placed(schoolBlock("short-id", 4, LocalTime.of(9, 45), LocalTime.of(10, 0), title = "高等无机化学")),
            width = 600.dp,
            height = 700.dp,
        )
        rule.onAllNodesWithText("高等无机化学", useUnmergedTree = true).assertCountEquals(1)
        assertEquals(1, BlockTexts.titleMaxLinesFor(cardHeightDp = 20f, hasLocation = true))
    }

    @Test fun accessibility_still_contains_hidden_teacher_and_time() {
        val block = schoolBlock("a11y-hidden", 4, LocalTime.of(8, 0), LocalTime.of(20, 0))
        setContentGrid(
            school = placed(
                block,
                schoolBlock("a11y-overlap", 4, LocalTime.of(8, 30), LocalTime.of(20, 30)),
            ),
            width = 600.dp,
            height = 1000.dp,
        )
        rule.onNodeWithTag("school_block:a11y-hidden")
            .assertContentDescriptionEquals("高等无机化学，周四 08:00–20:00，第1–20周，TH-B301，刘斯")
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("08:00–20:00", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun overlapped_half_width_block_hides_optional_text() {
        setContentGrid(
            school = placed(
                schoolBlock("half-a", 5, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                schoolBlock("half-b", 5, LocalTime.of(8, 30), LocalTime.of(20, 30), teacher = "王老师"),
            ),
            width = 700.dp,
            height = 1000.dp,
        )
        rule.onAllNodesWithText("刘斯", useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithText("08:00–20:00", useUnmergedTree = true).assertCountEquals(0)
    }

    // ---- a11y ----

    @Test fun a11y_description_format() {
        val block = schoolBlock("m4", 5, LocalTime.of(9, 45), LocalTime.of(10, 30), showWeek = WeekPattern.range(7, 12))
        assertEquals("高等无机化学，周五 09:45–10:30，第7–12周，TH-B301，刘斯", BlockTexts.a11y(block))
    }

    @Test fun a11y_omits_empty_segments() {
        val bare = object : TimedBlock {
            override val colorKey = "b"; override val meetingId = MeetingId("b1")
            override val manualItemId: ManualItemId? = null
            override val weekday = 1; override val start = LocalTime.of(9, 0)
            override val endInclusive = LocalTime.of(10, 0); override val weeks = WeekPattern.of(2)
            override val title = "讲座"; override val location = ""; override val teacherNames = emptyList<String>()
        }
        assertEquals("讲座，周一 09:00–10:00，第2周", BlockTexts.a11y(bare))
    }

    // ---- 非当前周策略（correction 9） ----

    @Test fun noncurrent_block_hidden_when_toggle_false() {
        setContentGrid(school = placed(schoolBlock("h1", 2, LocalTime.of(9, 0), LocalTime.of(10, 0), showWeek = WeekPattern.range(3, 5))), showNonCurrentWeek = false, viewedWeek = 2)
        rule.onAllNodesWithTag("school_block:h1").assertCountEquals(0)
    }

    @Test fun noncurrent_block_faded_when_toggle_true() {
        assertEquals(CoursePalette.NON_CURRENT_WEEK_ALPHA, CoursePalette.alphaFor(false, true), 1e-6f)
    }

    @Test fun current_block_full_alpha() {
        assertEquals(1f, CoursePalette.alphaFor(true, true), 1e-6f)
        assertEquals(1f, CoursePalette.alphaFor(true, false), 1e-6f)
    }

    // ---- CoursePalette（correction 10） ----

    @Test fun palette_index_is_stable() {
        assertEquals(CoursePalette.colorIndexFor("s1:k1"), CoursePalette.colorIndexFor("s1:k1"))
    }

    @Test fun palette_uses_unsigned_first_md5_byte() {
        for (key in listOf("a", "s1:CHEM5013P", "manual:i-42")) {
            val first = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))[0].toInt() and 0xFF
            assertEquals(first % 12, CoursePalette.colorIndexFor(key))
        }
    }

    @Test fun palette_index_always_0_to_11() {
        for (i in 0..200) assertTrue(CoursePalette.colorIndexFor("k$i") in 0..11)
    }

    @Test fun palette_has_12_container_onContainer_pairs() {
        val containers = (0..11).map { CoursePalette.containerColor(it) }
        assertEquals(12, containers.toSet().size)  // 12 对互不相同的配色
        for (i in 0..11) assertTrue(CoursePalette.containerColor(i) != CoursePalette.onContainerColor(i))
    }

    @Test fun palette_pairs_have_readable_contrast() {
        for (index in 0 until CoursePalette.PALETTE_SIZE) {
            assertTrue("palette[$index] contrast", CoursePalette.contrastRatio(index) >= 4.5)
        }
    }

    @Test fun existing_stable_color_identity_is_unchanged() {
        assertEquals(7, CoursePalette.colorIndexFor("s1:k1"))
        assertEquals(10, CoursePalette.colorIndexFor("A:name:高等无机化学"))
        assertEquals(8, CoursePalette.colorIndexFor("manual:debug-i-lecture"))
    }

    @Test fun teaching_groups_are_derived_from_profile_breaks() {
        assertEquals(
            listOf(
                LocalTime.of(7, 50) to LocalTime.of(9, 25),
                LocalTime.of(9, 45) to LocalTime.of(12, 10),
                LocalTime.of(14, 0) to LocalTime.of(15, 35),
                LocalTime.of(15, 55) to LocalTime.of(18, 20),
                LocalTime.of(19, 30) to LocalTime.of(21, 55),
            ),
            teachingTimeGroups(periods).map { it.start to it.endInclusive },
        )
    }

    @Test fun gutter_renders_every_period_boundary_once() {
        setContentGrid(segmentedAxis = SegmentedTimelineAxis.from(
            com.ustc.timetable.scheduleprofile.ScheduleProfile("segmented", "segmented", false, periods),
        ))
        periods.flatMap { listOf(it.start, it.end) }.distinct().forEach { time ->
            rule.onAllNodesWithTag("time_boundary:$time").assertCountEquals(1)
        }
    }

    // ---- today / now line ----

    @Test fun today_column_highlighted() {
        val today = LocalDate.of(2026, 9, 9)  // 周三
        val weekDatesState = androidx.compose.runtime.mutableStateOf(weekDatesMonday())
        rule.setContent {
            WeeklyTimetableGrid(weekDatesState.value, axis, periodStarts, emptyList(), emptyList(), false, 2, null, today, {}, {}, {})
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("today_header").assertCountEquals(1)
        // 状态重组切换到不含 today 的周
        weekDatesState.value = weekDatesMonday(LocalDate.of(2026, 9, 14))
        rule.waitForIdle()
        rule.onAllNodesWithTag("today_header").assertCountEquals(0)
    }

    @Test fun homepage_never_draws_now_line() {
        val nowState = androidx.compose.runtime.mutableStateOf<LocalTime?>(LocalTime.of(10, 0))
        rule.setContent {
            WeeklyTimetableGrid(weekDatesMonday(), axis, periodStarts, emptyList(), emptyList(), false, 2, nowState.value, null, {}, {}, {})
        }
        rule.waitForIdle()
        rule.onAllNodesWithTag("now_line").assertCountEquals(0)
        nowState.value = null
        rule.waitForIdle()
        rule.onAllNodesWithTag("now_line").assertCountEquals(0)
    }

    @Test fun long_press_uses_resolved_segmented_axis() {
        val periods = periodStarts.mapIndexed { index, start ->
            PeriodTime(index + 1, start, start.plusMinutes(45))
        }
        val profile = com.ustc.timetable.scheduleprofile.ScheduleProfile(
            "segmented", "segmented", false, periods,
        )
        var draft: LongPressDraft? = null
        setContentGrid(
            width = 400.dp,
            height = 800.dp,
            segmentedAxis = SegmentedTimelineAxis.from(profile),
            onEmpty = { draft = it },
        )
        rule.onNodeWithTag("timetable_grid").performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }
        rule.waitForIdle()
        assertEquals(4, draft?.weekday)
        assertTrue(draft!!.snappedStart in axis.start..axis.endInclusive)
    }
}
