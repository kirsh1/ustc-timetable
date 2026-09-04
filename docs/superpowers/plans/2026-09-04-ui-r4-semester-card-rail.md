# UI-R4 Semester, Card, and Rail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkboxes for tracking.

**Goal:** Add deterministic semester-end editing, optical week-bar alignment, card text priority, and measured horizontal rail width without changing timetable vertical geometry.

**Architecture:** `SemesterConfirmEditor` owns a pure AUTO/MANUAL model. Card text allocation and rail width are pure helpers consumed by Compose. The existing logical timetable coordinates and hit regions remain authoritative; only visual insets, text presentation, and horizontal width allocation change.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, JUnit 4, Robolectric Compose tests, Gradle.

**Spec:** `X:\schedule\docs\superpowers\specs\2026-09-04-ui-r4-addendum.md` §§2–4, §10, §12.

## Frozen boundary

- Do not edit `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\SegmentedTimelineAxis.kt`, `LongPressResolver.kt`, `TimeBoundaryMark.kt`, schedule profiles, or Room/domain persistence.
- `logicalTop = axis.fractionOf(start)` and `logicalBottom = axis.fractionOf(endInclusive)` remain unchanged.
- Time-label content, vertical anchors, compressed gaps, header height, and WeekOverview vertical geometry remain unchanged.
- `ConfirmedSemesterMeta` and the import transaction signature remain unchanged.

---

### Task 1: Add the pure AUTO/MANUAL semester-end model

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\semester\SemesterConfirmEditor.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\semester\SemesterConfirmEditorTest.kt`
- Test FQCN: `com.ustc.timetable.semester.SemesterConfirmEditorTest`

**Required production contract**

```kotlin
package com.ustc.timetable.semester

enum class SemesterEndDateMode { AUTO, MANUAL }

data class SemesterConfirmEditorState(
    val displayName: String,
    val academicYear: String,
    val term: Term?,
    val startDate: LocalDate?,
    val week1Start: LocalDate?,
    val totalWeeksText: String,
    val manualEndDate: LocalDate?,
    val endDateMode: SemesterEndDateMode,
)

object SemesterConfirmEditor {
    fun fromDraft(draft: SemesterImportDraft): SemesterConfirmEditorState
    fun automaticEndDate(week1Start: LocalDate?, totalWeeksText: String): LocalDate?
    fun effectiveEndDate(state: SemesterConfirmEditorState): LocalDate?
    fun withWeek1Start(state: SemesterConfirmEditorState, value: LocalDate): SemesterConfirmEditorState
    fun withTotalWeeksText(state: SemesterConfirmEditorState, value: String): SemesterConfirmEditorState
    fun withManualEndDate(state: SemesterConfirmEditorState, value: LocalDate): SemesterConfirmEditorState
    fun restoreAutomaticEndDate(state: SemesterConfirmEditorState): SemesterConfirmEditorState
    fun validate(state: SemesterConfirmEditorState): SemesterConfirmValidation
}
```

`automaticEndDate` returns null unless `week1Start` exists and trimmed `totalWeeksText` parses to `1..63`; otherwise it returns `week1Start.plusWeeks(totalWeeks.toLong()).minusDays(1)`. `fromDraft` stores a recognized `draft.endDate` in `manualEndDate` with `MANUAL`; a missing date produces null plus `AUTO`. `effectiveEndDate` returns the derived date in AUTO and the stored value in MANUAL. Validation and `ConfirmedSemesterMeta.endDate` use only the effective value. `startDate` is never the auto-end authority.

- [ ] **1. Write failing test.** Add named tests `recognized_end_date_starts_manual`, `missing_end_date_starts_auto`, `auto_end_date_uses_week1_monday_and_inclusive_last_sunday`, `manual_end_date_survives_week_or_week_count_changes`, and `restore_auto_recomputes_end_date` to `SemesterConfirmEditorTest`. Also assert that `2026-08-31` plus 20 teaching weeks yields `2027-01-17`, and that `validate` emits that date in `ConfirmedSemesterMeta`.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmEditorTest" --rerun-tasks --max-workers=1`. Expected RED: `SemesterEndDateMode`, `manualEndDate`, and the pure transition functions do not exist; current validation requires `state.endDate` directly.
- [ ] **3. Minimal production implementation.** Implement exactly the contract above in `SemesterConfirmEditor.kt`; update `ConfirmedSemesterMeta.isValidSemesterConfirmation` to construct MANUAL state from the already-confirmed end date. Do not alter `ConfirmedSemesterMeta`, `SemesterImportDraft`, or `ImportFlowViewModel` transaction calls.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmEditorTest" --rerun-tasks --max-workers=1`. Expected GREEN: all legacy validation cases and the five new mode/calculation cases pass.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.ImportFlowTest" --tests "com.ustc.timetable.semester.SemesterConfirmEditorTest" --rerun-tasks --max-workers=1`. Expected GREEN: final import confirmation still receives one complete `ConfirmedSemesterMeta`; no transaction API changes.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/semester/SemesterConfirmEditor.kt app/src/test/java/com/ustc/timetable/semester/SemesterConfirmEditorTest.kt` and `git commit -m "feat(ui-r4): model automatic semester end date"`.

---

### Task 2: Wire picker confirmation, cancellation, labels, and saveable state

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\semester\SemesterConfirmSheet.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\semester\SemesterConfirmSheetTest.kt`
- Test FQCN: `com.ustc.timetable.semester.SemesterConfirmSheetTest`

**Required production contract**

```kotlin
internal fun SemesterConfirmEditorState.dateForPicker(field: SemesterDateField): LocalDate? =
    when (field) {
        SemesterDateField.START_DATE -> startDate
        SemesterDateField.WEEK1_START -> week1Start
        SemesterDateField.END_DATE -> SemesterConfirmEditor.effectiveEndDate(this)
    }

internal fun SemesterConfirmEditorState.withConfirmedDate(
    field: SemesterDateField,
    date: LocalDate,
): SemesterConfirmEditorState
```

`withConfirmedDate(END_DATE, date)` calls `withManualEndDate`; START_DATE copies only `startDate`; WEEK1_START calls `withWeek1Start`. The week-count text field calls `withTotalWeeksText`. Picker open and cancel mutate only `openDateField`. The saveable payload includes `manualEndDate` and `endDateMode.name`. The end row displays effective date plus exactly one status label: `自动计算` or `已手动修改`; `semester_confirm_restore_auto_end` is visible only in MANUAL.

- [ ] **1. Write failing test.** Add `opening_and_canceling_end_picker_preserves_mode` and `confirming_end_picker_selection_enters_manual`; cover save/restore of both modes, live AUTO display after week-count changes, the `semester_confirm_end_date_mode` status, and `semester_confirm_restore_auto_end` action.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmSheetTest" --rerun-tasks --max-workers=1`. Expected RED: the screen still reads and writes `endDate` directly, has no mode label/action, and its saver has no mode field.
- [ ] **3. Minimal production implementation.** Replace direct date mutation with `dateForPicker` and `withConfirmedDate`, route week-one and week-count edits through Task 1 reducers, add mode/status UI and restore-auto action, and extend `SemesterConfirmEditorStateSaver`. Cancelling the picker must only set `openDateField = null`.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmSheetTest" --rerun-tasks --max-workers=1`. Expected GREEN: opening/cancelling is state-neutral, only confirmed end selection enters MANUAL, and saveable restoration preserves the exact mode.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmEditorTest" --tests "com.ustc.timetable.semester.SemesterConfirmSheetTest" --tests "com.ustc.timetable.semester.ImportFlowTest" --rerun-tasks --max-workers=1`. Expected GREEN: editor, sheet, and import confirmation agree on one effective end date.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/semester/SemesterConfirmSheet.kt app/src/test/java/com/ustc/timetable/semester/SemesterConfirmSheetTest.kt` and `git commit -m "feat(ui-r4): expose semester end date mode"`.

---

### Task 3: Make week number and range optically centered

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableScreen.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableScreenTest.kt`
- Test FQCN: `com.ustc.timetable.timetable.ui.TimetableScreenTest`

**Required production contract**

```kotlin
internal const val WEEK_BAR_CONTENT_HEIGHT_DP = 44
internal const val WEEK_NUMBER_FONT_SP = 16
internal const val WEEK_NUMBER_LINE_HEIGHT_SP = 20
internal const val WEEK_RANGE_FONT_SP = 12
internal const val WEEK_RANGE_LINE_HEIGHT_SP = 16
```

`PrimaryWeekBar` keeps week number and date range in the same `WEEK_BAR_CONTENT_HEIGHT_DP.dp` Row with `Alignment.CenterVertically`. Both `TextStyle` instances set explicit `fontSize`, `lineHeight`, and `PlatformTextStyle(includeFontPadding = false)`. No baseline alignment modifier is allowed. Existing arrow, overview, and settings vector controls and their 44dp hit targets remain.

- [ ] **1. Write failing test.** Extend `TimetableScreenTest` with parameterized Compose assertions for `week_number_and_range_text_visual_centers_match` at font scales 1.0 and 1.3 and viewport widths 320dp and 400dp. Capture actual text bounds and require `abs(centerYWeek - centerYRange) <= density` pixels, equivalent to 1dp.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected RED: current typography-derived line heights produce a text-bounds center difference greater than 1dp in at least one font-scale/width case.
- [ ] **3. Minimal production implementation.** Apply the constants and a single shared fixed-height parent in `PrimaryWeekBar`; retain existing horizontal spacing and control ordering. Do not change pager, overview, or week-selection state.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: all four configurations report at most 1dp actual text-center difference and existing top-bar height/navigation tests remain green.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: arrows, direct week selection, and overview strip positioning are unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableScreenTest.kt` and `git commit -m "fix(ui-r4): optically center week bar text"`.

---

### Task 4: Enforce card insets and mandatory text priority

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\CourseCardVisualBounds.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\BlockTexts.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\ui\theme\TimetableTheme.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\layout\CourseCardVisualLayoutTest.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\BlockTextsTest.kt`
- Test FQCNs: `com.ustc.timetable.timetable.layout.CourseCardVisualLayoutTest`, `com.ustc.timetable.timetable.ui.BlockTextsTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

data class CourseCardTextBudget(
    val titleMaxLines: Int,
    val showLocation: Boolean,
    val teacherMaxLines: Int,
    val showTime: Boolean,
)

data class CourseCardTextMetrics(
    val cardHeightDp: Float,
    val cardWidthDp: Float,
    val titleLineHeightDp: Float,
    val locationLineHeightDp: Float,
    val metadataLineHeightDp: Float,
)

object CourseCardTextTokens {
    const val CONTENT_VERTICAL_PADDING_DP = 4f
    const val MAX_TITLE_LINES = 3
    const val LOCATION_LINES = 1
    const val MAX_TEACHER_LINES = 2
    const val TEACHER_MIN_OPTIONAL_WIDTH_DP = 40f
    const val TIME_MIN_OPTIONAL_WIDTH_DP = 52f
    const val MARKER_DIAMETER_DP = 8f
    const val MARKER_GAP_DP = 2f
    const val MARKER_RIGHT_INSET_DP = 2f
}

object BlockTexts {
    fun budget(
        metrics: CourseCardTextMetrics,
        hasLocation: Boolean,
        hasTeachers: Boolean,
        markerCount: Int,
    ): CourseCardTextBudget
}
```

Compose resolves the three `TimetableTypography` line heights through the current `LocalDensity` and passes their dp values to the pure function. The algorithm is fixed:

```kotlin
require(metrics.titleLineHeightDp > 0f)
require(metrics.locationLineHeightDp > 0f)
require(metrics.metadataLineHeightDp > 0f)
require(markerCount in 0..2)
val usableHeight = (metrics.cardHeightDp - CourseCardTextTokens.CONTENT_VERTICAL_PADDING_DP)
    .coerceAtLeast(0f)
val locationHeight = if (
    hasLocation &&
    usableHeight >= metrics.titleLineHeightDp + metrics.locationLineHeightDp
) metrics.locationLineHeightDp else 0f
val titleMaxLines = floor(
    (usableHeight - locationHeight).coerceAtLeast(0f) / metrics.titleLineHeightDp
).toInt().coerceIn(1, CourseCardTextTokens.MAX_TITLE_LINES)
val afterMandatory = (
    usableHeight - titleMaxLines * metrics.titleLineHeightDp - locationHeight
).coerceAtLeast(0f)
val markerReservation = if (markerCount <= 0) 0f else {
    markerCount * CourseCardTextTokens.MARKER_DIAMETER_DP +
        (markerCount - 1) * CourseCardTextTokens.MARKER_GAP_DP +
        CourseCardTextTokens.MARKER_RIGHT_INSET_DP
}
val optionalWidth = (metrics.cardWidthDp - markerReservation).coerceAtLeast(0f)
val teacherMaxLines = when {
    !hasTeachers || optionalWidth < CourseCardTextTokens.TEACHER_MIN_OPTIONAL_WIDTH_DP -> 0
    afterMandatory >= 2f * metrics.metadataLineHeightDp -> 2
    afterMandatory >= metrics.metadataLineHeightDp -> 1
    else -> 0
}
val afterTeachers = afterMandatory - teacherMaxLines * metrics.metadataLineHeightDp
val showTime = optionalWidth >= CourseCardTextTokens.TIME_MIN_OPTIONAL_WIDTH_DP &&
    afterTeachers >= metrics.metadataLineHeightDp
```

The returned `showLocation` is `locationHeight > 0f`. Default-font metrics are exact tokens from existing typography: title 12.5dp, location 11.5dp, metadata 10.5dp. With 4dp vertical content padding, the first location threshold is 28dp; once the three-title-line budget is active, teacher zero-to-one is 63.5dp, teacher one-to-two is 74dp, and time after two teacher lines is 84.5dp. Optional-width thresholds are exactly 40dp for teachers and 52dp for time after marker reservation. Comparisons use inclusive `>=`. Boundary tests pair 27.99/28dp, 63.49/63.5dp, 73.99/74dp, 84.49/84.5dp, 39.99/40dp, and 51.99/52dp. Location calculation occurs before and independently of `hasTeachers`. Marker reservation changes only `optionalWidth`; it never changes `titleMaxLines`, `showLocation`, or their full-width measurement.

Teacher names are joined by `、`; `teacherMaxLines` is 2, 1, or 0. Time is omitted first. `TimetableTypography.courseLocation` uses `FontFamily.Monospace` and `FontWeight.SemiBold`. Title uses one to three lines and final-line ellipsis only when constrained. Location uses exactly one line with ellipsis only as a last resort. Blank values render no placeholders.

The existing `courseCardVisualBounds` remains the vertical primitive and is called with `insetDp = 1f`. Horizontal inset stays uniform at 1dp. The visual card is inside the logical hit region; a separate outer node retains the full logical size and owns input semantics.

- [ ] **1. Write failing test.** In `CourseCardVisualLayoutTest`, assert exact 1dp top/bottom inset, non-negative short-card height, and unchanged logical hit bounds. In `BlockTextsTest`, add exact named cases `location_threshold_does_not_depend_on_teacher_presence`, `teacher_two_to_one_line_boundary_is_deterministic`, and `marker_reservation_does_not_reduce_all_title_lines`. Assert every paired boundary—27.99/28dp location, 63.49/63.5dp first teacher line, 73.99/74dp second teacher line, 84.49/84.5dp time, 39.99/40dp teacher width, and 51.99/52dp time width—with default metrics; assert unchanged title/location for marker counts 0/1/2, full “电化学研究方法”, monospaced semibold `TH-A301`/`TH-B301`, two-line `Steve Masashi Musha、教师乙`, one-line ellipsis, teacher omission before location, no unknown placeholders, and complete `BlockTexts.a11y`.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.CourseCardVisualLayoutTest" --tests "com.ustc.timetable.timetable.ui.BlockTextsTest" --rerun-tasks --max-workers=1`. Expected RED: `CourseCardTextBudget` and `budget` do not exist, location is proportional normal-weight and multi-line, teachers are fixed to one line, and the visible node currently owns the click region.
- [ ] **3. Minimal production implementation.** Implement the exact token object and formula above, resolve actual typography line heights before calling the pure budget, then implement one-line location, adaptive teacher lines, title ellipsis, and the two-layer logical-hit/visual-card structure. Apply the same renderer to SCHOOL and MANUAL nodes. Keep full accessibility strings and all existing IDs/callbacks.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.CourseCardVisualLayoutTest" --tests "com.ustc.timetable.timetable.ui.BlockTextsTest" --rerun-tasks --max-workers=1`. Expected GREEN: visual and logical bounds are distinct, exact threshold-minus-0.01dp cases select the lower budget, exact thresholds select the higher budget, and markers never reduce mandatory title/location lines.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --tests "com.ustc.timetable.manual.ManualItemFlowTest" --rerun-tasks --max-workers=1`. Expected GREEN: school/manual clicks, manual editor flow, overlap geometry, and full a11y remain intact.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/layout/CourseCardVisualBounds.kt app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/main/java/com/ustc/timetable/timetable/ui/BlockTexts.kt app/src/main/java/com/ustc/timetable/ui/theme/TimetableTheme.kt app/src/test/java/com/ustc/timetable/timetable/layout/CourseCardVisualLayoutTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/BlockTextsTest.kt` and `git commit -m "fix(ui-r4): preserve timetable card identity text"`.

---

### Task 5: Replace the fixed 44dp rail with measured rendered width

**Files**

- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\TimeRailWidth.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\layout\TimeRailWidthTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\layout\WeeklyTimetableViewportTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\layout\TimeBoundaryRailTest.kt`
- Test FQCNs: `com.ustc.timetable.timetable.layout.TimeRailWidthTest`, `com.ustc.timetable.timetable.layout.WeeklyTimetableViewportTest`, `com.ustc.timetable.timetable.layout.TimeBoundaryRailTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.layout

const val TIME_RAIL_TEXT_PADDING_DP: Float = 4f
const val TIME_RAIL_DIVIDER_DP: Float = 0.5f

data class TimeRailWidthPx(
    val widestLabelPx: Int,
    val leftPaddingPx: Int,
    val rightPaddingPx: Int,
    val dividerPx: Int,
) {
    val totalPx: Int
}

fun measuredTimeRailWidthPx(
    renderedLabelWidthsPx: List<Float>,
    density: Float,
): TimeRailWidthPx
```

The function takes real rendered widths from Compose `rememberTextMeasurer` using the same `MaterialTheme.typography.labelSmall`, density, and font scale as `PositionedGutterMark`. It applies `ceil` to the widest label and each physical width before summing. Left and right are each exactly 4dp after density conversion; divider is at most 0.5dp. `FixedTimeRail` uses the returned total width, and the sibling `HorizontalPager(Modifier.weight(1f))` automatically receives the rest. Remove `GUTTER_WIDTH_DP` from production and leave no Spacer between rail and pager. `WeeklyTimetableGrid(showTimeRail = false)` remains the pager-page contract.

- [ ] **1. Write failing test.** Add the exact named cases `time_rail_uses_measured_label_width_not_fixed_44dp`, `time_rail_left_and_right_text_padding_are_symmetric`, `actual_rail_padding_difference_within_1dp`, `no_extra_spacer_exists_between_time_rail_and_monday`, `released_width_is_distributed_equally_to_seven_days`, and `longest_time_label_is_not_clipped_at_font_scale_1_3`. Retain and explicitly invoke `existing_time_boundary_positions_remain_unchanged` and `existing_segmented_axis_roundtrip_remains_unchanged` in the existing rail/axis tests.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.TimeRailWidthTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableViewportTest" --tests "com.ustc.timetable.timetable.layout.TimeBoundaryRailTest" --rerun-tasks --max-workers=1`. Expected RED: the width model is absent and `FixedTimeRail` still forces `GUTTER_WIDTH_DP = 44`.
- [ ] **3. Minimal production implementation.** Implement `TimeRailWidthPx`, measure the unique rendered `TimeBoundaryMark` text with the actual text style, use the calculated width in `FixedTimeRail`, place label content at the 4dp text inset, and remove the fixed constant/spacer. Do not change mark derivation, vertical positioning, axis resolution, body height, header height, or long-press coordinate code.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.TimeRailWidthTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableViewportTest" --tests "com.ustc.timetable.timetable.layout.TimeBoundaryRailTest" --rerun-tasks --max-workers=1`. Expected GREEN: rail derives from rendered text, widest label is not clipped at 1.3 font scale, actual side-padding difference is at most 1dp, and seven columns share all released width equally.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.SegmentedTimelineAxisTest" --tests "com.ustc.timetable.timetable.layout.SegmentedTimelineAxisV2Test" --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableLayoutTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --rerun-tasks --max-workers=1`. Expected GREEN: every existing vertical boundary, round trip, gap, inverse Y mapping, and WeekOverview projection remains byte-for-behavior unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/layout/TimeRailWidth.kt app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/test/java/com/ustc/timetable/timetable/layout/TimeRailWidthTest.kt app/src/test/java/com/ustc/timetable/timetable/layout/WeeklyTimetableViewportTest.kt app/src/test/java/com/ustc/timetable/timetable/layout/TimeBoundaryRailTest.kt` and `git commit -m "fix(ui-r4): measure timetable time rail width"`.

## Subplan completion command

Run:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmEditorTest" --tests "com.ustc.timetable.semester.SemesterConfirmSheetTest" --tests "com.ustc.timetable.semester.ImportFlowTest" --tests "com.ustc.timetable.timetable.layout.CourseCardVisualLayoutTest" --tests "com.ustc.timetable.timetable.ui.BlockTextsTest" --tests "com.ustc.timetable.timetable.layout.TimeRailWidthTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableViewportTest" --tests "com.ustc.timetable.timetable.layout.TimeBoundaryRailTest" --tests "com.ustc.timetable.timetable.layout.SegmentedTimelineAxisTest" --tests "com.ustc.timetable.timetable.layout.SegmentedTimelineAxisV2Test" --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest" --rerun-tasks --max-workers=1
```

Expected result: all named classes pass with zero failures, errors, and skips. Do not proceed if a vertical-axis assertion changes.
