# 子计划 02 — Timetable UI + Manual（B2, B3, C1–C4, D1–D3）

- 需求来源：[SPEC r3.1](../specs/2026-08-30-ustc-timetable-design.md)；路线图：[2026-08-30-ustc-timetable.md](2026-08-30-ustc-timetable.md)
- 前置：子计划 01 全部完成（domain、Room、仓库、布局纯函数就绪）。
- 本子计划的 Compose 组件测试全部跑在 Robolectric（`@Config(sdk=[36])`，`createComposeRule`）；不依赖真机。
- 路径约定：相对仓库根；命令在仓库根执行；测试过滤用完整 FQCN。

---

## Task B2 — WeeklyTimetableGrid Compose 组件

- SPEC §4.1–§4.7。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/CoursePalette.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGridTest.kt`。

接口：
```kotlin
@Composable
fun WeeklyTimetableGrid(
    weekDates: LocalDateRange,
    axis: TimelineAxis,
    periodStarts: List<LocalTime>,      // 时刻标签唯一来源 = 查看学期绑定 profile 的 13 个节次开始时刻（禁止硬编码）
    placedSchool: List<PlacedBlock>,
    placedManual: List<PlacedBlock>,
    showNonCurrentWeek: Boolean,
    viewedWeek: Int,
    nowLine: LocalTime?,
    today: LocalDate?,
    onSchoolBlockClick: (MeetingId) -> Unit,
    onManualBlockClick: (ManualItemId) -> Unit,
    onEmptyLongPress: (columnFraction: Float, yFraction: Float) -> Unit,   // 原始比例；换算在 D1 的 LongPressResolver
)

@Composable
private fun WeekHeaderRow(weekDates: LocalDateRange, today: LocalDate?, gutterWidth: Dp)
// 行首必须先放 Spacer(gutterWidth)（与 TimeGutter 同宽），随后 7 个星期格等分剩余宽度——保证星期列与课程七列严格对齐

@Composable
private fun TimeGutter(periodStarts: List<LocalTime>, axis: TimelineAxis, modifier: Modifier)
// 每个节次开始时刻一个小号时间标签（按 axis.fractionOf 定位），顶部/底部为 axis.start/axis.endInclusive

object CoursePalette {
    fun colorIndexFor(colorKey: String): Int       // MD5(colorKey)[0] % 12，稳定跨启动
    fun containerColor(index: Int): Color          // 12 色 tonal 配对
    fun onContainerColor(index: Int): Color
    fun alphaFor(isCurrentWeek: Boolean, showNonCurrentWeek: Boolean): Float
    const val NON_CURRENT_WEEK_ALPHA: Float = 0.35f
}
```

块渲染规则（SPEC §4.3/§4.7）：名称（粗体）与地点必显，均为单行 ellipsis；块高超过 60dp 且实际卡片宽 `groupW` 超过 52dp 时追加教师行，块高超过 90dp 且同一 `groupW` 超过 52dp 时追加时间行。`groupW` 是 overlap 分列后的有效宽度，不是完整 weekday column；所有文字单行 ellipsis，卡片按 4dp 圆角边界 clip，无 elevation。当前周由 `block.weeks.contains(viewedWeek)` 判定；非当前周且 `showNonCurrentWeek==true` 时 `alpha=0.35`，`showNonCurrentWeek==false` 时不参与布局输入（上层过滤）。取色用 `block.colorKey`（学校块 = `"$semesterId:$sourceCourseKey"`，与本地 meeting UUID 无关，替换重建不影响颜色；手动块 = `"manual:$manualItemId"`）。

步骤：
- [ ] 1. 写 failing test（Robolectric compose；`TimedBlock` 用测试内 fake 实现 `FakeBlock`）：
  - `seven_columns_all_visible`：`onNodeWithText("一").assertExists()` 至 `onNodeWithText("日").assertExists()`；列头日期号 `onNodeWithText("7")` 存在（weekDates 从周一 2026-09-07）。
  - `weekend_columns_present_when_empty`：无任何块时 `六`、`日` 两列仍存在。
  - `seven_columns_use_entire_post_gutter_width`：在 gutter 右侧区域放置一块 weekday=1 的块，其右端位置断言为网格宽度（`BoxWithConstraints.maxWidth`）的比例 1/7，weekday=7 的块右端到达 7/7——证明块坐标以 gutter 之后的全部宽度为 100%，无二次扣减。
  - `header_columns_align_with_grid_columns`：头部 `一` 格的左边缘 x == gutterWidth，且七个头部格等分 `maxWidth - gutterWidth`（测量头行各格边界与网格列边界一致）。
  - `time_gutter_labels_come_from_profile`：`periodStarts` 传入自定义 profile（如首节 08:00）时，gutter 顶部标签为 08:00 而非任何内置常量；标签数量 == periodStarts.size。
  - `school_block_click_fires_meetingId`：放置一个带 meetingId 的块，`onNodeWithContentDescription` 命中后 `performClick()`，捕获 `onSchoolBlockClick` 参数。
  - `manual_block_click_fires_id`：同理 manual。
  - `empty_area_longpress_fires_fractions`：`onRoot().performTouchInput { longClick(center) }` → 捕获 `columnFraction` 在 0..1、`yFraction` 在 0..1。
  - `noncurrent_block_has_faded_alpha`：`showNonCurrentWeek=true`、viewedWeek 不在块周次内 → 断言 `CoursePalette.alphaFor(false, true) == 0.35f`，且当前周 `alphaFor(true, true) == 1f`。
  - `today_column_highlighted`：today=2026-09-09 → 列头 `三 9` 所在节点有 `testTag("today_header")`。
  - `nowline_drawn_only_when_provided`：nowLine 非空时存在 `testTag("now_line")` 节点；为空时不存在。
  - `a11y_description_format`：块的 `contentDescription == "高等无机化学，周五 09:45–12:10，第7–12周，TH-B301，刘斯"`（由 `BlockTexts.a11y(block, timeRange)` 纯函数生成并单测）。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableGridTest"` → RED。
- [ ] 3. 最小实现（关键代码；注意坐标系：`BoxWithConstraints` 已位于 `TimeGutter` 之后的剩余 Row 空间，块 offset 一律 `x = x`，**不得**再减/加 gutter；`gridW = maxWidth` 即 gutter 右侧全部宽度）：
```kotlin
@Composable
fun WeeklyTimetableGrid(
    weekDates: LocalDateRange, axis: TimelineAxis, periodStarts: List<LocalTime>,
    placedSchool: List<PlacedBlock>, placedManual: List<PlacedBlock>,
    showNonCurrentWeek: Boolean, viewedWeek: Int, nowLine: LocalTime?, today: LocalDate?,
    onSchoolBlockClick: (MeetingId) -> Unit, onManualBlockClick: (ManualItemId) -> Unit,
    onEmptyLongPress: (Float, Float) -> Unit,
) {
    val gutterWidth = 44.dp
    Column(Modifier.fillMaxSize()) {
        // 头部行：行首预留与 TimeGutter 同宽的 spacer，七个星期格等分剩余宽度，与网格七列严格对齐
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutterWidth))
            WeekHeaderCells(weekDates, today, Modifier.weight(7f))  // 一 7 / 二 8 …；today 列 testTag("today_header")，七格等分
        }
        Row(Modifier.fillMaxSize()) {
            TimeGutter(periodStarts, axis, Modifier.width(gutterWidth))  // 标签只来自 periodStarts（绑定 profile），顶/底为 axis 边界
            BoxWithConstraints(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTapGestures(onLongPress = { offset ->
                        onEmptyLongPress(
                            (offset.x / size.width.toFloat()).coerceIn(0f, 0.999f),
                            (offset.y / size.height.toFloat()).coerceIn(0f, 0.999f),
                        )
                    })
                },
            ) {
                val gridW = maxWidth   // 已经是 gutter 右侧的剩余宽度：七列以它为 100%
                val gridH = maxHeight
                fun place(pb: PlacedBlock, isManual: Boolean) {
                    val isCurrent = pb.block.weeks.contains(viewedWeek)
                    if (!isCurrent && !showNonCurrentWeek) return
                    val colW = gridW / 7f
                    val dayX = (pb.block.weekday - 1) * colW
                    val groupW = colW / pb.columnsInGroup
                    val hasTextWidth = groupW > 52.dp
                    val x = dayX + pb.column * groupW
                    val y = gridH * pb.topFraction
                    val h = gridH * pb.heightFraction
                    val colorIdx = CoursePalette.colorIndexFor(pb.block.colorKey)
                    val alpha = CoursePalette.alphaFor(isCurrent, showNonCurrentWeek)
                    Box(
                        Modifier
                            .offset(x = x, y = y)
                            .size(width = groupW, height = h)
                            .graphicsLayer { this.alpha = alpha }
                            .clip(RoundedCornerShape(4.dp))
                            .background(CoursePalette.containerColor(colorIdx), RoundedCornerShape(4.dp))
                            .clickable {
                                if (isManual) onManualBlockClick(pb.block.manualItemId!!) else onSchoolBlockClick(pb.block.meetingId!!)
                            }
                            .semantics { contentDescription = BlockTexts.a11y(pb.block, viewedWeek) },
                    ) {
                        BlockTexts.Content(
                            pb.block,
                            showTeacher = h > 60.dp && hasTextWidth,
                            showTime = h > 90.dp && hasTextWidth,
                        )
                    }
                }
                placedSchool.forEach { place(it, isManual = false) }
                placedManual.forEach { place(it, isManual = true) }
                if (nowLine != null) NowLine(axis, nowLine, Modifier.testTag("now_line"))
            }
        }
    }
}
```
（`place` 内点击回调取 `pb.block.meetingId!!` / `pb.block.manualItemId!!`（`TimedBlock` 字段，见 B1 接口）；`BlockTexts.a11y` 纯函数：`"高等无机化学，周五 09:45–12:10，第7–12周，TH-B301，刘斯"`，地点/教师为空时省略对应段；`CoursePalette.colorIndexFor` 用 `MessageDigest.getInstance("MD5").digest(colorKey.toByteArray())[0].toInt() and 0xFF` 对 12 取模。）
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.*"` → GREEN（含 B1 纯函数回归）。
- [ ] 6. `git add app/src && git commit -m "phaseB2: compose weekly grid with interactive blocks, faded non-current weeks, a11y"`。

---

## Task B3 — TimetableViewModel + TimetableScreen + DebugSeed

- SPEC §5.1、§5.8、§3.4（手动项目属于 viewed 学期）。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/TimetableViewModel.kt`、`app/src/debug/java/com/ustc/timetable/DebugSeed.kt`、`app/src/main/java/com/ustc/timetable/timetable/ui/BlockTexts.kt`（B2 引用的纯文本函数落于此文件）；测试 `app/src/test/java/com/ustc/timetable/timetable/ui/TimetableViewModelTest.kt`。

接口：
```kotlin
data class TimetableUiState(
    val semester: Semester?,
    val viewedWeek: Int,
    val weekDates: LocalDateRange?,
    val profile: ScheduleProfile?,
    val schoolBlocks: List<TimedBlock>,     // 已按 viewedWeek 过滤或含全量由淡化开关决定
    val manualBlocks: List<TimedBlock>,
    val showNonCurrentWeek: Boolean,
    val nowLine: LocalTime?,
    val isAcademicCurrentViewed: Boolean,   // 控制 ↻ 是否显示
    val isLoading: Boolean,
)
class TimetableViewModel(
    private val semesters: SemesterRepository, private val timetable: TimetableRepository,
    private val manual: ManualItemRepository, private val profiles: ScheduleProfileRepository,
    private val settings: SettingsStore, private val today: LocalDate,
) : ViewModel() {
    val state: StateFlow<TimetableUiState>
    fun onWeekSelected(week: Int)          // clamp 1..totalWeeks
    fun onNextWeek(); fun onPrevWeek()
    fun onSemesterSelected(id: SemesterId) // C2 实现；B3 预留
}
```
- `weekFilter` 纯函数（VM 内顶层可测）：`showNonCurrentWeek==false` 时只保留 `weeks.contains(viewedWeek)` 的块；`true` 时全量保留交给网格淡化。
- `nowLine` 计算纯函数 `NowLinePolicy.line(semester, viewedWeek, today, now): LocalTime?`：semester 存在 && `isCurrentAcademicSemester && viewed 学期==该学期` && `WeekCalculator.weekNumberOn(today)==viewedWeek` && today ∈ weekDates → 返回 now，否则 null（SPEC §4.6）。
- `DebugSeed`（仅 `BuildConfig.DEBUG`，库空时插入，`main` source set 不编译）：插入 2026 秋季本地学期（`portalLinked=false`）+ 高等无机化学（`sourceCourseKey="name:高等无机化学"`，周五 3–5 节三条 meeting：吴长征 2–6 / 刘斯 7–12 / 郭宇桥 13–18）+ 线性代数（周二 3–4 节 1–20 周）+ 一条手动项目（周六 14:20–16:00 讲座，第 2 周）。

步骤：
- [ ] 1. 写 failing test（fake 仓库 + Turbine）：
  - `weekFilter_uses_contains`：viewedWeek=2 时第 2 周不在 pattern 的块被过滤（showNonCurrentWeek=false）。
  - `weekFilter_keeps_all_when_showNonCurrent`：开关打开时保留全量。
  - `viewedWeek_clamped`：`onWeekSelected(99)` → state.viewedWeek==totalWeeks；`onWeekSelected(0)` → 1。
  - `state_exposes_weekDates_monday_to_sunday`：weekDates.start.dayOfWeek==MONDAY、endInclusive==start+6。
  - `nowline_only_when_academic_current_and_viewed_matches_today`：构造历史学期 → null；academic-current + 查看周==自然周 → now。
  - `isAcademicCurrentViewed_false_for_history`。
  - `manual_items_belong_to_viewed_semester`：切换 viewed（fake 双学期）后 manualBlocks 来自 viewed 学期。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest"` → RED。
- [ ] 3. 最小实现：VM 用 `combine(semesters.viewedOrDefault(settings.viewedSemesterId), timetable.observeSchool, manual.observe, profiles.observeForSemester, settings.showNonCurrentWeek)` 构建 state；`MainActivity.setContent` 改为 `TimetableScreen(vm)`；网格以 `state.profile.periods.map { it.start }` 作为 `periodStarts` 传入 `WeeklyTimetableGrid`（时刻标签唯一来源，SPEC §4.1）；顶栏先渲染学期名/`‹›`/周标题与网格，`↻`/`⚙`/▼ 此时渲染为不可用状态（`enabled=false`，C/I 阶段接通）；`TimetableApp.onCreate` 在 `BuildConfig.DEBUG` 且库空时 `runBlocking { DebugSeed.seedIfEmpty(container) }`。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest` → GREEN；`./gradlew :app:installDebug` 装模拟器目检（七列、整周、真实时间轴、色块）。
- [ ] 6. `git add -A && git commit -m "phaseB3: timetable home renders seeded week via viewmodel"`。

---

## Task C1 — 周切换（Pager + 箭头 + 周选择器）

- SPEC §5.1、§5.2。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/ui/WeekSwitcherSheet.kt`；修改 `TimetableScreen.kt`、`TimetableViewModel.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/ui/WeekSwitchNavigationTest.kt`。

步骤：
- [ ] 1. 写 failing test：
  - `swipe_pager_changes_viewedWeek`：compose 测试对 pager 执行 `performTouchInput { swipeLeft() }` → state.viewedWeek+1。
  - `arrow_bounds_clamp_1_totalWeeks`：在第 1 周按 `‹` 不变；在第 20 周按 `›` 不变。
  - `week_sheet_marks_current_natural_week`：WeekSwitcherSheet 中自然周节点带 `testTag("natural_week")`，viewed 周带 `testTag("viewed_week")`。
  - `semester_switch_resets_week_clamped`（VM 层）：切到其他学期 → viewedWeek = defaultViewedWeek（教学周内→自然周；未来学期→1；历史学期→totalWeeks；C1 修正原反向表述）。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest"` → RED。
- [ ] 3. 最小实现：网格外套 `HorizontalPager(state = rememberPagerState(pageCount = { semester.totalWeeks }), pageCount 从 viewedWeek-1 初值)`，`pageCount` 为 totalWeeks，页 index=week-1；`LaunchedEffect(pagerState.currentPage)` 回写 VM `onWeekSelected(currentPage+1)`；`‹`/`›` 调 VM；点“第 N 周”弹 ModalBottomSheet：`totalWeeks` 网格按钮 + 自然周/viewed 标注。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseC1: week switching via pager, arrows, week picker sheet"`。

---

## Task C2 — 学期切换（只写 viewedSemesterId）

- SPEC §3.2、§5.3、不变量 §1-13。
- 文件：`app/src/main/java/com/ustc/timetable/semester/SemesterSwitcherSheet.kt`；VM 接 `onSemesterSelected`；测试 `app/src/test/java/com/ustc/timetable/semester/SemesterSwitcherTest.kt`。

步骤：
- [ ] 1. 写 failing test：
  - `list_sorted_by_startDate_desc`。
  - `sheet_marks_academic_current_and_portal_state`：academic-current 学期节点带 `testTag("academic_current")`；`portalLinked=false` 学期显示“本地”徽标。
  - `switch_writes_only_viewed_semester_id`：fake `SettingsStore` 记录写入；断言调用后仅 `setViewedSemesterId` 被写，且 fake 仓库暴露的 `isCurrentAcademicSemester` 字段无任何变更、无同步类依赖被调用（VM 构造函数不含任何 portal/sync 类型——编译期保证 + 运行期写计数断言）。
  - `switch_updates_header_and_blocks_to_viewed_semester`。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterSwitcherTest"` → RED。
- [ ] 3. 最小实现：Sheet 列出 `semesters.observeSemesters()` 排序结果；点击 → `settings.setViewedSemesterId(id)`（VM `onSemesterSelected` 内唯一写操作）。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.*" --tests "com.ustc.timetable.timetable.ui.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseC2: local semester switching writes only viewedSemesterId"`。

---

## Task C3 — 课程详情 Sheet + 格式化

- SPEC §4.4。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/ui/CourseDetailFormatter.kt`（先落纯格式化函数）、`app/src/main/java/com/ustc/timetable/timetable/ui/CourseDetailSheet.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/ui/CourseDetailFormatterTest.kt`、`app/src/test/java/com/ustc/timetable/timetable/ui/CourseDetailSheetTest.kt`。
- `ChangeFormatter` remains owned by H1 for `ScheduleChange` / notification prose；C3 不创建或复用它。

接口：
```kotlin
object CourseDetailFormatter {
    fun weekdayName(weekday: Int): String
    fun formatWeeks(pattern: WeekPattern): String
    fun formatMeetingTime(meeting: CourseMeeting, profile: ScheduleProfile): String
    fun formatTeachers(names: List<String>): String
    fun formatLocation(location: String): String
    fun formatCourseCode(courseCode: String): String
    fun formatCredits(credits: Double?): String
}
```
- 步骤：
- [ ] 1. 写 failing test：`formatWeeks_range_single_set`（"2-6,8,10-12" → "第 2–6,8,10–12 周"）、`formatWeeks_singleWeek`（of(10) → "第10周"）、`formatMeetingTime_uses_profile_conversion`（周五 3–5 节 → "周五 · 09:45–12:10"）、`detail_lists_all_meetings`（三条教师-周次行全部出现）、`detail_has_no_edit_action`（语义树无"编辑"/"删除"节点）、`detail_shows_dash_when_credits_null`（学分 "—"）。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.CourseDetailFormatterTest" --tests "com.ustc.timetable.timetable.ui.CourseDetailSheetTest"` → RED。
- [ ] 3. 最小实现：`formatWeeks` 复用 `WeekPattern.format()` 的段结构换中文；Sheet 内容顺序：名称、meeting 时间/周次/地点/教师、课程号、学分、完整安排列表。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.domain.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseC3: read-only course detail sheet with chinese formatters"`。

---

## Task C4 — 显示非当前周课程开关

- SPEC §4.5、§9.2。
- 文件：修改 `TimetableViewModel.kt`、`SettingsStore.kt`（`show_non_current_week` 已在 A6）；测试并入 `app/src/test/java/com/ustc/timetable/timetable/ui/TimetableViewModelTest.kt`。

步骤：
- [ ] 1. 写 failing test：`toggle_persists_across_vm_recreation`：写 true → 新建 VM（同一 DataStore）读到 true，且 `weekFilter_keeps_all_when_showNonCurrent` 生效。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest"` → RED（新用例）。
- [ ] 3. 最小实现：VM 暴露 `fun onToggleShowNonCurrentWeek(v: Boolean)` 写 `settings.setShowNonCurrentWeek`；组合进 state。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseC4: persist non-current-week display toggle"`。

---

## Task D1 — LongPressResolver

- SPEC §3.4、§4.1。
- 文件：`app/src/main/java/com/ustc/timetable/timetable/layout/LongPressResolver.kt`；测试 `app/src/test/java/com/ustc/timetable/timetable/layout/LongPressResolverTest.kt`。

接口：
```kotlin
data class LongPressDraft(val weekday: Int, val snappedStart: LocalTime)
object LongPressResolver {
    fun resolve(columnFraction: Float, yFraction: Float, axis: TimelineAxis): LongPressDraft
    // 两个 fraction 都必须 finite；X defensive clamp 到 [0,1)，再做七列映射
    // Y clamp 到 [0,1] → axis.timeAt → floor5 → 最终 clamp 回 axis day window
    // 默认 45 分钟 endTime 仍由 D2 负责，不属于 D1 geometry resolver
}
```
- 步骤：
- [ ] 1. 写 failing test：`column_fraction_seven_way_split`（0.0→周一、0.5→周四、0.999→周日）、`snap_floors_to_5min`（14:23→14:20、14:25→14:25）、`clamp_into_axis`（yFraction 0→axis.start、1→axis.endInclusive）、`draft_within_dayWindow`（对 bundled profile，任意输入产出都在 07:50–21:55）。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest"` → RED。
- [ ] 3. 最小实现（完整如上接口，8 行逻辑）。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseD1: long-press resolver with seven-way column split and 5-minute snap"`。

---

## Task D2 — ManualItemEditorSheet（三周次模式 + day window 校验）

- SPEC §3.4、§5.6。
- 文件：`app/src/main/java/com/ustc/timetable/manual/ManualItemEditorSheet.kt`、`app/src/main/java/com/ustc/timetable/manual/ManualItemEditorViewModel.kt`；测试 `app/src/test/java/com/ustc/timetable/manual/ManualItemEditorViewModelTest.kt`。

接口：
```kotlin
enum class WeekMode { CURRENT_ONLY, CONTINUOUS, CUSTOM }

data class EditorDraft(
    val title: String = "", val location: String = "", val note: String = "",
    val weekday: Int, val startTime: LocalTime, val endTime: LocalTime,
    val mode: WeekMode = WeekMode.CURRENT_ONLY,
    val continuousStart: Int, val continuousEnd: Int,
    val customWeeks: Set<Int> = emptySet(),
)

class ManualItemEditorViewModel(
    private val manual: ManualItemRepository,
    private val semester: Semester,            // viewed 学期；决定周次上界与 day window
    private val profile: ScheduleProfile,      // 学期绑定 profile；day window 校验依据
    private val initial: ManualEditorInitial,   // New/Edit session 在创建时冻结 viewedWeek
    private val clock: Clock,
) : ViewModel() {
    val draft: StateFlow<EditorDraft>
    fun update(transform: (EditorDraft) -> EditorDraft)
    fun validationError(): String?             // null=可保存
    fun buildItem(): ManualScheduleItem
    suspend fun save(); suspend fun delete()
}
```
- `ManualEditorInitial` 在 editor session 创建时冻结 `viewedWeek`，`buildItem()` / `save()` 不再接受调用方传入的周次。
- Edit 初始化必须无损推断 week mode：单周且等于 frozen viewedWeek → CURRENT_ONLY；连续多周 → CONTINUOUS；其余 → CUSTOM。特别地，single non-viewed week 必须是 CUSTOM singleton，避免无改动保存时被替换成当前查看周。
- 校验顺序（`validationError`）：标题空 → "请填写标题"；`endTime <= startTime` → "结束需晚于开始"；`!profile.dayWindow().contains(startTime) || !contains(endTime)` → "时间需在作息窗口内"（SPEC §3.4，轴外拒绝）；CUSTOM 且 `customWeeks.isEmpty()` → "请选择周次"。
- 周次构建：CURRENT_ONLY → `WeekPattern.of(viewedWeek)`；CONTINUOUS → `WeekPattern.range(clamp(start,1,totalWeeks), clamp(end,start..totalWeeks))`；CUSTOM → `WeekPattern.of(*customWeeks.sorted().toIntArray())`。
- 步骤：
- [ ] 1. 写 failing test：`default_duration_45min`（草稿 end = start+45，clamp 到 dayWindow.end）、`currentOnly_builds_single_week_pattern`、`continuous_3_to_12`、`custom_2_6_8_10_12`、`validation_title_required`、`validation_end_after_start`、`validation_rejects_outside_day_window`（06:00 起被拒）、`validation_rejects_empty_custom`、`save_persists_with_source_manual`（fake repo 捕获对象断言 source==MANUAL）、`existing_loads_into_draft_and_delete_removes`。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.manual.ManualItemEditorViewModelTest"` → RED。
- [ ] 3. 最小实现：按接口；UI Sheet 用 Material3 `TimePicker`、星期行 7 个 FilterChip、周次模式 3 个 RadioButton + 自定义周网格（1..totalWeeks）。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向回归：`./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.manual.*"` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseD2: manual item editor with three week modes and day-window validation"`。

---

## Task D3 — 编辑/删除接线

- SPEC §5.6。
- 文件：修改 `TimetableScreen.kt`（长按空白→D2 编辑器、手动块点击→编辑器含删除、学校块点击→C3 详情）；新增 `ManualEditorHost.kt`；测试 `app/src/test/java/com/ustc/timetable/manual/ManualItemFlowTest.kt`（compose + in-memory repository）。
- 路由目标只保存 identity/context：`semesterId`、`profileId`、触发时的 `viewedWeek`，以及 New 的坐标解析结果或 Edit 的 `ManualItemId`；不得保存完整 draft/item。`ManualEditorInitial` 只在创建全新 editor session 的边界，从当前未过滤 `manualItemsById` projection 重新解析。
- `manualItemsById` 必须来自同一 repository Flow 的未过滤原始 snapshot。保存/删除成功后只由 repository emission 重投影并关闭；失败保持编辑器打开，不命令式 patch `TimetableUiState`。
- 学期/profile 改变、目标条目外部删除或跨学期 identity 均视为 stale target 并关闭。学校详情和手动编辑器互斥；每次关闭后重开必须创建 fresh session。

步骤：
- [ ] 1. 写 failing test：`empty_longpress_opens_editor_prefilled`（长按周六下午区域 → 编辑器星期=周六、开始=吸附值）、`manual_block_click_opens_editor_with_existing`、`delete_requires_confirmation_then_removes`（点删除→确认对话框→确认后块消失）、`school_block_click_opens_readonly_detail_not_editor`。
- [ ] 2. `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.manual.ManualItemFlowTest"` → RED。
- [ ] 3. 最小实现：Route 持 identity/context-only `ManualEditorTarget`；空白长按用 D1 resolver + 当前 page week 创建 New target，手动块点击用 id + 当前 page week 创建 Edit target；Host 在 session 边界解析当前 item。
- [ ] 4. 同命令 → GREEN。
- [ ] 5. 定向 manual/UI/semester/layout 回归与 `./gradlew :app:testDebugUnitTest :app:assembleDebug` → GREEN。
- [ ] 6. `git add app/src && git commit -m "phaseD3: wire manual create edit delete into timetable grid"`。

---

## 本子计划完成判定

- `./gradlew :app:assembleDebug :app:testDebugUnitTest` 全绿。
- 模拟器目检：七列整周、真实时间轴、周切换三方式、学期切换（viewed only）、详情只读、长按新建/编辑/删除手动项目、非本周淡化。
