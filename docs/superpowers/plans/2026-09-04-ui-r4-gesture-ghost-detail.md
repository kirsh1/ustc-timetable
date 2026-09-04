# UI-R4 Gesture, Ghost, and Detail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkboxes for tracking.

**Goal:** Add deterministic WeekOverview gesture arbitration and SCHOOL-only non-current-week aggregation with stable marker/detail paging while preserving all MANUAL behavior.

**Architecture:** A platform-independent owner state machine decides one action per pointer sequence. A pure SCHOOL projection removes only attached SCHOOL ghosts before the existing joint layout call. Stable course identity and canonical meeting anchors build detail pages before Compose; the grid and sheet only render supplied models.

**Tech Stack:** Kotlin, Jetpack Compose pointer input and HorizontalPager, JUnit 4, Robolectric Compose tests, Gradle.

**Spec:** `X:\schedule\docs\superpowers\specs\2026-09-04-ui-r4-addendum.md` §§5–7, §11, §12.

## Frozen boundary

- Do not edit Room/domain persistence, `ManualItemRepository`, manual editor state, sync, portal code, or WeekOverview persistence.
- MANUAL active blocks, MANUAL non-current blocks, SCHOOL-to-MANUAL overlap, MANUAL-to-MANUAL overlap, manual click, empty-area create, and editor behavior remain baseline behavior.
- `WeeklyTimetableLayout.place` remains the sole column allocator and `SegmentedTimelineAxis` remains the sole vertical projection.
- WeekOverview active-only mini-map data is not passed through ghost aggregation.

---

### Task 1: Implement the pure gesture ownership state machine

**Files**

- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\OverviewGestureArbitrator.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\OverviewGestureArbitratorTest.kt`
- Test FQCN: `com.ustc.timetable.timetable.ui.OverviewGestureArbitratorTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

enum class GridGestureOwner {
    IDLE,
    PRESS_PENDING,
    CLICK_OWNED,
    LONG_PRESS_OWNED,
    HORIZONTAL_OWNED,
    VERTICAL_OWNED,
}

enum class VerticalOverviewAction { EXPAND, COLLAPSE }

sealed interface GridGestureDecision {
    data object None : GridGestureDecision
    data object Click : GridGestureDecision
    data object LongPress : GridGestureDecision
    data object YieldToHorizontalPager : GridGestureDecision
    data class ChangeOverview(val action: VerticalOverviewAction) : GridGestureDecision
}

class OverviewGestureArbitrator(private val touchSlopPx: Float) {
    val owner: GridGestureOwner
    fun onDown()
    fun onMove(totalDxPx: Float, totalDyPx: Float): GridGestureDecision
    fun onLongPressTimeout(emptyTarget: Boolean): GridGestureDecision
    fun onUp(): GridGestureDecision
    fun onCancel()
}
```

`onMove` stays pending until movement magnitude crosses platform slop and one absolute axis strictly dominates. Horizontal ownership returns `YieldToHorizontalPager` without a local action. Vertical ownership returns exactly one EXPAND for positive Y or COLLAPSE for negative Y and never returns a second action before reset. Long press can win only while pending and only on an empty target. Owner remains locked until `onUp` or `onCancel`; cancellation returns no action.

- [ ] **1. Write failing test.** Add exact named tests `click_wins_when_pointer_releases_within_slop`, `long_press_wins_after_timeout`, `horizontal_drag_before_timeout_yields_to_pager`, `vertical_drag_before_timeout_cancels_click_and_longpress`, and `one_vertical_drag_changes_overview_at_most_once`; also cover equal-axis pending, ineligible long press, owner lock, and cancellation.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.OverviewGestureArbitratorTest" --rerun-tasks --max-workers=1`. Expected RED: the owner, decisions, and arbitrator types do not exist.
- [ ] **3. Minimal production implementation.** Implement the state machine as a small Kotlin class with no Compose, Android, repository, or persistence dependency. Reject nonpositive slop in the constructor and reset all accumulated state only on up/cancel.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.OverviewGestureArbitratorTest" --rerun-tasks --max-workers=1`. Expected GREEN: every sequence selects at most one exclusive owner and vertical action.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest" --rerun-tasks --max-workers=1`. Expected GREEN: existing overview window motion and empty-area X/Y resolution are unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/OverviewGestureArbitrator.kt app/src/test/java/com/ustc/timetable/timetable/ui/OverviewGestureArbitratorTest.kt` and `git commit -m "feat(ui-r4): define timetable gesture ownership"`.

---

### Task 2: Connect the gesture owner to grid click, long press, and pager

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableScreen.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableGestureIntegrationTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableScreenTest.kt`
- Test FQCNs: `com.ustc.timetable.timetable.ui.TimetableGestureIntegrationTest`, `com.ustc.timetable.timetable.ui.TimetableScreenTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.layout

sealed interface GridHitTarget {
    data class School(val meetingId: MeetingId) : GridHitTarget
    data class Manual(val manualItemId: ManualItemId) : GridHitTarget
    data class Empty(val draft: LongPressDraft) : GridHitTarget
}

fun WeeklyTimetableGrid(
    weekDates: LocalDateRange,
    axis: TimelineAxis,
    periodStarts: List<LocalTime>,
    placedSchool: List<PlacedBlock>,
    placedManual: List<PlacedBlock>,
    showNonCurrentWeek: Boolean,
    viewedWeek: Int,
    nowLine: LocalTime?,
    today: LocalDate?,
    onSchoolBlockClick: (MeetingId) -> Unit,
    onManualBlockClick: (ManualItemId) -> Unit,
    onEmptyLongPress: (LongPressDraft) -> Unit,
    onVerticalOverviewAction: (VerticalOverviewAction) -> Unit,
    periods: List<PeriodTime> = emptyList(),
    segmentedAxis: SegmentedTimelineAxis? = null,
    showTimeRail: Boolean = true,
)
```

One `awaitEachGesture` dispatcher on the seven-day body obtains `LocalViewConfiguration.current.touchSlop` and `longPressTimeoutMillis`, instantiates/reset the arbitrator, and hit-tests the pointer against logical block bounds. It dispatches card click on pending-up, empty long press only after timeout, yields unconsumed movement to `HorizontalPager`, and consumes vertical-owned movement. Card semantics retain explicit accessibility `onClick`, but per-card pointer detectors are removed so two detectors cannot fire. The fixed rail is outside this modifier. `TimetableScreen` maps EXPAND/COLLAPSE to its existing semester-scoped `overviewExpanded` Boolean.

- [ ] **1. Write failing test.** Add Compose cases that drag down over empty grid to expand, drag up to collapse, horizontally swipe to change week without toggling overview, tap a card once, long-press empty area once, drag vertically before timeout without opening a card/editor, and prove the fixed rail does not respond to the overview gesture.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.TimetableGestureIntegrationTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected RED: `WeeklyTimetableGrid` lacks `onVerticalOverviewAction`, existing independent `detectTapGestures` cannot provide exclusive ownership, and a grid drag does not change overview state.
- [ ] **3. Minimal production implementation.** Add the single dispatcher and deterministic logical hit testing, remove per-card pointer detectors, retain semantic click actions, and connect the callback in `TimetableScreen`. Use the resolved existing axis for empty long-press mapping and do not change its Y calculation.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.OverviewGestureArbitratorTest" --tests "com.ustc.timetable.timetable.ui.TimetableGestureIntegrationTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: each gesture invokes exactly one owner action and horizontal paging remains fluid.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.manual.ManualItemFlowTest" --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --rerun-tasks --max-workers=1`. Expected GREEN: manual create/edit, week paging, coordinate inversion, and overview button/strip behavior remain unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableGestureIntegrationTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableScreenTest.kt` and `git commit -m "feat(ui-r4): arbitrate timetable overview gestures"`.

---

### Task 3: Build the pure SCHOOL-only ghost association projection

**Files**

- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\SchoolGhostProjection.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\SchoolGhostProjectionTest.kt`
- Test FQCN: `com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

interface SchoolTimedBlock : TimedBlock {
    override val meetingId: MeetingId
    override val manualItemId: ManualItemId? get() = null
}

data class SchoolPresentationSignature(
    val canonicalTeachers: List<String>,
    val location: String,
    val weekday: Int,
    val start: LocalTime,
    val endInclusive: LocalTime,
)

data class SchoolGhostAttachment(
    val representativeMeetingId: MeetingId,
    val sameCourseVariants: List<SchoolTimedBlock>,
    val differentCourses: List<SchoolTimedBlock>,
)

data class SchoolGhostAssociation(
    val retainedSchoolBlocks: List<SchoolTimedBlock>,
    val attachmentsByMeetingId: Map<MeetingId, SchoolGhostAttachment>,
)

object SchoolGhostProjection {
    fun presentationSignature(block: TimedBlock): SchoolPresentationSignature
    fun associate(
        schoolBlocks: List<SchoolTimedBlock>,
        viewedWeek: Int,
        showNonCurrentWeek: Boolean,
    ): SchoolGhostAssociation
}
```

The stable school identity is `TimedBlock.colorKey`. Signature teachers are trimmed, blank-filtered, deduplicated, and sorted; location is trimmed; WeekPattern is excluded. Attach only non-current SCHOOL blocks that time-overlap a current SCHOOL block on the same weekday. Choose the representative with maximum overlap minutes, then stable business key `meetingId.value`, then `colorKey`. Active SCHOOL blocks all remain. Unattached ghosts remain. Attached ghosts are removed from `retainedSchoolBlocks` so they cannot consume a layout column. `SchoolTimedBlock` requires a nonnull meeting ID and null manual-item ID in its implementations. This helper accepts no MANUAL list, making SCHOOL/MANUAL mixing structurally impossible.

- [ ] **1. Write failing test.** Add exact named tests `week_pattern_only_difference_has_no_variant_marker`, `presentation_signature_difference_has_variant_marker`, `manual_blocks_never_enter_school_aggregation`, `active_school_conflicts_remain_side_by_side`, `unattached_school_ghost_remains_gray_card`, and `ghost_uses_maximum_overlap_then_stable_key`. The MANUAL test must prove the public input type/API never accepts or returns a manual block and that callers keep manual lists separate.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest" --rerun-tasks --max-workers=1`. Expected RED: the projection and signature types do not exist and current filtering sends every ghost into the overlap allocator.
- [ ] **3. Minimal production implementation.** Implement canonical signature, half-open time-overlap minutes, deterministic representative selection, attachment classification, and retained list order sorted by weekday/start/end/stable key. Do not call repositories or mutate blocks.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest" --rerun-tasks --max-workers=1`. Expected GREEN: WeekPattern-only differences produce neither variant marker nor different-course classification; teacher/location/time changes on the same identity produce only same-course variants.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableLayoutTest" --tests "com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest" --tests "com.ustc.timetable.manual.ManualItemFlowTest" --rerun-tasks --max-workers=1`. Expected GREEN: base overlap placement and all manual behavior remain baseline pending view-model wiring.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/SchoolGhostProjection.kt app/src/test/java/com/ustc/timetable/timetable/ui/SchoolGhostProjectionTest.kt` and `git commit -m "feat(ui-r4): project school ghost associations"`.

---

### Task 4: Wire SCHOOL projection into week pages and marker models

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableViewModel.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\ShowNonCurrentWeekTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableViewModelTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\WeekSwitchNavigationTest.kt`
- Test FQCNs: `com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest`, `com.ustc.timetable.timetable.ui.TimetableViewModelTest`, `com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

enum class SchoolMarkerKind { DIFFERENT_COURSE, SAME_COURSE_VARIANT }

data class SchoolCardMarkers(val kinds: Set<SchoolMarkerKind>)

data class TimetableWeekPageUiState(
    val week: Int,
    val weekDates: LocalDateRange,
    val placedSchool: List<PlacedBlock>,
    val placedManual: List<PlacedBlock>,
    val schoolMarkersByMeetingId: Map<MeetingId, SchoolCardMarkers>,
    val attachedSchoolGhostsByMeetingId: Map<MeetingId, SchoolGhostAttachment>,
    val nowLine: LocalTime?,
)

internal data class UiSchoolTimedBlock(
    override val colorKey: String,
    override val meetingId: MeetingId,
    override val weekday: Int,
    override val start: LocalTime,
    override val endInclusive: LocalTime,
    override val weeks: WeekPattern,
    override val title: String,
    override val location: String,
    override val teacherNames: List<String>,
) : SchoolTimedBlock

internal data class UiManualTimedBlock(
    override val colorKey: String,
    override val manualItemId: ManualItemId,
    override val weekday: Int,
    override val start: LocalTime,
    override val endInclusive: LocalTime,
    override val weeks: WeekPattern,
    override val title: String,
    override val location: String,
    override val teacherNames: List<String>,
) : TimedBlock {
    override val meetingId: MeetingId? = null
}
```

The excerpts show identity-bearing fields; both concrete classes retain all `TimedBlock` fields. `schoolTimedBlock` returns `UiSchoolTimedBlock`, `manualTimedBlock` returns `UiManualTimedBlock`, and each constructor enforces the opposite ID is absent. For each week, call `SchoolGhostProjection.associate(rawSchool, week, showNonCurrentWeek)`. Then call `WeeklyTimetableLayout.place(association.retainedSchoolBlocks + weekFilter(rawManual, week, showNonCurrentWeek), axis)` exactly once and split results by IDs as before. This retains SCHOOL/MANUAL joint layout and leaves MANUAL filtering untouched. Build markers only for placed active SCHOOL representatives. `WeeklyTimetableGrid` draws compact theme-colored vector markers in a reserved bottom-right region and never emits Unicode marker glyphs.

- [ ] **1. Write failing test.** Update the old ghost-column expectation and assert attached SCHOOL ghosts do not consume active columns, active SCHOOL conflicts still do, unattached SCHOOL ghosts remain gray cards, SCHOOL/MANUAL overlaps still share the joint group, MANUAL blocks produce no marker, and the two marker kinds can coexist without obscuring the mandatory location semantics.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --rerun-tasks --max-workers=1`. Expected RED: `TimetableWeekPageUiState` has no attachment/marker models and current `weekFilter(rawSchool + rawManual)` gives overlapping ghosts their own narrow columns.
- [ ] **3. Minimal production implementation.** Wire the pure association before the one joint placement, expose immutable marker/attachment maps, and render the two vector shapes. Do not alter `weekOverviewPages = buildWeekOverviewPages(semester, rawSchool + rawManual, axis)`.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest" --tests "com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --rerun-tasks --max-workers=1`. Expected GREEN: projected SCHOOL markers match attachments and all active/manual placement remains deterministic.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.manual.ManualItemFlowTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableLayoutTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --rerun-tasks --max-workers=1`. Expected GREEN: manual flow, shared allocator, and active-only WeekOverview are unchanged.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/TimetableViewModel.kt app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/test/java/com/ustc/timetable/timetable/ui/ShowNonCurrentWeekTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableViewModelTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/WeekSwitchNavigationTest.kt` and `git commit -m "feat(ui-r4): aggregate overlapping school ghosts"`.

---

### Task 5: Build stable one-page-per-course detail projections

**Files**

- Add production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\CourseDetailPager.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\CourseDetailPagerTest.kt`
- Test FQCN: `com.ustc.timetable.timetable.ui.CourseDetailPagerTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

data class SchoolMeetingAnchor(
    val earliestWeek: Int,
    val weekday: Int,
    val start: LocalTime,
    val endInclusive: LocalTime,
    val location: String,
    val canonicalTeachers: List<String>,
    val canonicalWeekPattern: String,
)

data class CourseDetailPage(
    val stableCourseIdentity: String,
    val anchorMeetingId: MeetingId,
    val detail: CourseDetailUiModel,
)

data class CourseDetailPagerModel(val pages: List<CourseDetailPage>)

object CourseDetailPager {
    fun build(
        representative: SchoolTimedBlock,
        attachment: SchoolGhostAttachment?,
        detailsByMeetingId: Map<MeetingId, CourseDetailUiModel>,
    ): CourseDetailPagerModel?
}
```

Page 1 always uses the representative active meeting. Alternative blocks are grouped by `colorKey`, excluding the representative identity. Each group selects the minimum `SchoolMeetingAnchor`: earliest set week, weekday, start, end, trimmed location, canonical teacher set, canonical WeekPattern text. Exact duplicate anchors collapse. Alternative pages sort by anchor then stable identity. `MeetingId` never sorts pages. Same-course variants remain in page 1's existing `allMeetings` and never add a page.

- [ ] **1. Write failing test.** Add exact named tests `one_detail_page_per_stable_course_identity`, `representative_active_course_is_first_detail_page`, and `same_course_variants_use_single_detail_page`; also prove input permutation and locally changed MeetingIds do not affect alternative ordering, exact duplicate anchors collapse, and MANUAL cannot appear because both representative and candidates require nonnull meeting IDs.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.CourseDetailPagerTest" --rerun-tasks --max-workers=1`. Expected RED: page/anchor types and deterministic builder do not exist.
- [ ] **3. Minimal production implementation.** Implement canonical anchor extraction, stable grouping, deduplication, and page construction from the existing unfiltered `courseDetailsByMeetingId`. Return null for stale/missing representative detail rather than constructing fake data.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.CourseDetailPagerTest" --rerun-tasks --max-workers=1`. Expected GREEN: representative context is first, every alternative identity occurs once, and ordering is stable without MeetingId.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.CourseDetailSheetTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --rerun-tasks --max-workers=1`. Expected GREEN: existing selected-meeting and complete-arrangement details remain intact.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/CourseDetailPager.kt app/src/test/java/com/ustc/timetable/timetable/ui/CourseDetailPagerTest.kt` and `git commit -m "feat(ui-r4): model stable course detail pages"`.

---

### Task 6: Render marker-aware horizontal course details

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\CourseDetailSheet.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableScreen.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\CourseDetailSheetTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableScreenTest.kt`
- Test FQCNs: `com.ustc.timetable.timetable.ui.CourseDetailSheetTest`, `com.ustc.timetable.timetable.ui.TimetableScreenTest`

**Required production contract**

```kotlin
package com.ustc.timetable.timetable.ui

data class SchoolDetailSelection(
    val pageWeek: Int,
    val representativeMeetingId: MeetingId,
)

fun CourseDetailSheet(
    pager: CourseDetailPagerModel,
    profile: ScheduleProfile,
    onDismiss: () -> Unit,
)

internal fun resolveSchoolCourseDetailPager(
    selection: SchoolDetailSelection?,
    state: TimetableUiState,
): CourseDetailPagerModel?
```

`TimetableRoute` owns `SchoolDetailSelection?` as saveable UI overlay state separate from `ManualOverlayState`; semester changes clear it. `TimetableScreen` passes `(MeetingId, pageWeek)` on school-card/marker click. Resolution reads that exact page's `attachedSchoolGhostsByMeetingId` and the global unfiltered detail map. `CourseDetailSheet` reuses `CourseDetailContent` for each page in a `HorizontalPager`, starts at page 0, shows vector previous/next buttons plus a page indicator only when page count exceeds one, and keeps one-page details free of paging chrome.

- [ ] **1. Write failing test.** Add tests that different-course marker/detail opens on current course page 1, swipes to each distinct alternative, vector controls stay in bounds, page indicator matches count, same-course marker opens a single page with full arrangements, ordinary active card uses the same page-1 context, stale selection dismisses, and a gray unattached ghost retains its independent single detail.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.CourseDetailSheetTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected RED: the sheet accepts only one `CourseDetailUiModel`, selection has no page-week context, and markers have no pager resolver.
- [ ] **3. Minimal production implementation.** Add page-aware selection/resolution, reuse `CourseDetailContent` inside a keyed pager, render vector navigation and indicator, and connect marker/card callbacks. Keep manual overlay state and manual editor code unchanged.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.CourseDetailPagerTest" --tests "com.ustc.timetable.timetable.ui.CourseDetailSheetTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: multi-course details retain current-course context and same-course variants remain single-page.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.manual.ManualItemFlowTest" --tests "com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest" --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --rerun-tasks --max-workers=1`. Expected GREEN: MANUAL edit/click, ghost projection, week navigation, and overview behavior remain intact.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/CourseDetailSheet.kt app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/test/java/com/ustc/timetable/timetable/ui/CourseDetailSheetTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableScreenTest.kt` and `git commit -m "feat(ui-r4): page overlapping school course details"`.

## Subplan completion command

Run:

```powershell
./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.OverviewGestureArbitratorTest" --tests "com.ustc.timetable.timetable.ui.TimetableGestureIntegrationTest" --tests "com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest" --tests "com.ustc.timetable.timetable.ui.CourseDetailPagerTest" --tests "com.ustc.timetable.timetable.ui.CourseDetailSheetTest" --tests "com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --tests "com.ustc.timetable.manual.ManualItemFlowTest" --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest" --tests "com.ustc.timetable.timetable.layout.WeeklyTimetableLayoutTest" --rerun-tasks --max-workers=1
```

Expected result: all named classes pass with zero failures, errors, and skips. A MANUAL marker, MANUAL carousel page, duplicate stable-course page, or changed vertical-axis assertion blocks the subplan.
