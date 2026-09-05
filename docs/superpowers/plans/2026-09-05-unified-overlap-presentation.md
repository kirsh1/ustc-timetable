# Unified Overlap Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Cross-week SCHOOL/MANUAL aggregation, segmented active conflicts, and an exact-conflict preference with safe detail/edit navigation.

**Architecture:** A pure typed projection owns representatives and attachments. A separate pure geometry stage produces per-event slices and content rectangles; Compose renders and hit-tests those slices. Repositories keep their current ownership and persistence contracts.

**Tech Stack:** Kotlin, Compose, Room (unchanged), Preferences DataStore, JUnit/Robolectric, Android instrumentation.

**Spec:** `X:/schedule/docs/superpowers/specs/2026-09-05-unified-overlap-presentation-design.md`

## Global Constraints

- Execution starts at `cd05ca334aa947d6628455e2b5c800f0adb45b85`; approved production baseline is `1b66e5f073065536e1fbce64736586faf5959ba1`.
- Work in the user-selected `X:/schedule`; preserve and never stage `icon/`.
- No Room/domain identity/manual persistence/portal/parser/session/sync/Worker changes.
- Freeze SegmentedTimelineAxis, TimeBoundaryMark, LongPressResolver and WeekOverview active-only projection.
- New preference only: `exact_overlap_display_mode`, SPLIT default and invalid fallback, EARLIEST alternative.
- School order uses semester importedAt, not invented course insertion time; manual order uses createdAt.
- Do not run destructive fixture reset tests on the physical phone's real state. Use emulator for automated UI work; physical manual acceptance remains explicitly pending where not observed.
- PowerShell commands run from `X:/schedule` with TEMP/TMP=`C:/jtmp`, GRADLE_OPTS=`-Djava.net.preferIPv4Stack=true`, `--max-workers=1`.

## Task 1 — Typed projection and preference

Files (all package paths below are under `X:/schedule/app/src`):

- Create `main/java/com/ustc/timetable/timetable/ui/UnifiedOverlapProjection.kt`.
- Modify `main/java/com/ustc/timetable/timetable/data/SettingsStore.kt`.
- Create `test/java/com/ustc/timetable/timetable/ui/UnifiedOverlapProjectionTest.kt`.
- Extend `test/java/com/ustc/timetable/timetable/data/SettingsStoreTest.kt`.

Contract in `com.ustc.timetable.timetable.ui`: `ExactOverlapMode { SPLIT, EARLIEST }`; `OverlapEntry(block: TimedBlock, addedAt: Instant)`; `OverlapAttachment(crossWeek: List<OverlapEntry>, sameCourseVariants: List<OverlapEntry>, currentConflicts: List<OverlapEntry>)`; `OverlapProjection(retained: List<OverlapEntry>, attachments: Map<String, OverlapAttachment>)`.
`UnifiedOverlapProjection.project(entries: List<OverlapEntry>, week: Int, showOtherWeeks: Boolean, mode: ExactOverlapMode): OverlapProjection`.
Expose canonical key and identity functions; no MeetingId in ordering. Deduplicate school presentation-equivalent rows before choosing representatives.

- [ ] Write tests using actual `UiManualTimedBlock` and `UiSchoolTimedBlock` fixtures. Literal case: active manual A week 1 10–12 plus school B week 2 11–13 retains A only and attaches B; a week-2-only pair retains earliest-week representative; active exact pairs stay two in SPLIT and one in EARLIEST. Test mixed types, ties, adjacency, direct versus transitive ghost relation, manual edit timestamp stability and MeetingId replacement.
- [ ] RED: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.ui.UnifiedOverlapProjectionTest' --tests 'com.ustc.timetable.timetable.data.SettingsStoreTest' --max-workers=1`. Missing projection is the initial expected compilation RED; after API exists each new behavior must yield assertion RED before its implementation.
- [ ] Implement canonical identities as type-qualified colorKey/manual ID; compare addedAt then identity then normalized presentation fields. Active exact groups use `(weekday,start,end)` only in EARLIEST. Attach ghosts by maximum positive overlap, stable tie-break; greedily retain earliest remaining unattached ghost with direct-overlap candidates. Settings stores enum.name and reads `ExactOverlapMode.entries.firstOrNull { it.name == stored } ?: ExactOverlapMode.SPLIT`.
- [ ] GREEN: rerun the same two FQCN filters; assert literal retained IDs and candidate identities, not helper-computed expected output.
- [ ] Regression: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest' --tests 'com.ustc.timetable.timetable.data.*' --max-workers=1`.
- [ ] Commit only task files: `feat(timetable): project mixed cross-week and exact conflicts`.

## Task 2 — Pure segmented geometry

Create `X:/schedule/app/src/main/java/com/ustc/timetable/timetable/layout/SegmentedOverlapLayout.kt` and `X:/schedule/app/src/test/java/com/ustc/timetable/timetable/layout/SegmentedOverlapLayoutTest.kt`.
Contract: `OverlapSlice(start: LocalTime, end: LocalTime, left: Float, right: Float)`; `SegmentedBlock(block: TimedBlock, slices: List<OverlapSlice>)`; `SegmentedOverlapLayout.place(blocks: List<TimedBlock>): List<SegmentedBlock>`; fractions are local to one day, not the viewport. `contentRect(axis: ReversibleTimelineAxis): SliceRect` selects the largest contained rectangle over contiguous slices, tie top then left. `contains(dayFraction: Float,time: LocalTime): Boolean` uses half-open slices.

- [ ] Write literal 10–12 A / 11–13 B expectations: A `[10,11,0,1],[11,12,0,.5]`; B `[11,12,.5,1],[12,13,0,1]`. Add touching, nested, triple and shuffled-input cases, contained content rectangle and shape hit-test exclusion.
- [ ] RED: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.layout.SegmentedOverlapLayoutTest' --max-workers=1`; missing class first, then incorrect segment/rectangle assertions when extending behavior.
- [ ] Sweep unique sorted start/end times per day. For each adjacent boundary pair select blocks satisfying `block.start < end && block.endInclusive > start`, stably sort by canonical business/presentation key, and assign `[index/count,(index+1)/count]`. Merge vertically adjacent equal-width slices. Find content rectangle by intersecting horizontal intervals across each contiguous slice run and comparing area after existing axis mapping; no Y mapping changes.
- [ ] GREEN: rerun the focused layout class.
- [ ] Regression: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.layout.*' --max-workers=1`.
- [ ] Commit: `feat(timetable): allocate overlap width per time segment`.

## Task 3 — Typed detail candidates

Create `X:/schedule/app/src/main/java/com/ustc/timetable/timetable/ui/OverlapDetail.kt` and `X:/schedule/app/src/test/java/com/ustc/timetable/timetable/ui/OverlapDetailTest.kt`.
Contract: `OverlapMarkerKind { CROSS_WEEK, VARIANT, CURRENT_CONFLICT }`; detail selection stores page week, representative canonical key and marker kind. A page carries stable typed identity and either CourseDetailUiModel or ManualScheduleItem. Builder receives current retained entries, attachment, current school detail map and manual map; returns representative first and one page per stable identity.

- [ ] Write tests asserting current-first literal identities, variant-only single page, independent same-name manuals, mixed candidates, original manual ID, deleted candidate removal, and empty result when representative disappears.
- [ ] RED: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.ui.OverlapDetailTest' --max-workers=1`.
- [ ] Resolve maps by IDs only after deterministic ordering. CROSS_WEEK uses crossWeek; CURRENT_CONFLICT uses currentConflicts; VARIANT uses representative only. School alternatives prefer viewed-week anchor then earliest week then canonical presentation. Return no page for a deleted record, never rebind by index.
- [ ] GREEN: rerun the focused class.
- [ ] Regression: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.ui.CourseDetail*' --max-workers=1`.
- [ ] Commit: `feat(timetable): resolve mixed overlap details safely`.

## Task 4 — ViewModel, segmented cards, markers and settings wiring

Modify these exact existing files under `X:/schedule/app/src/main/java/com/ustc/timetable/`:
`timetable/ui/TimetableViewModel.kt`, `timetable/ui/TimetableScreen.kt`, `timetable/layout/WeeklyTimetableGrid.kt`, `settings/SettingsViewModel.kt`, `settings/SettingsScreen.kt`.
Create `timetable/ui/OverlapDetailSheet.kt` and `timetable/layout/SegmentedCourseCard.kt` to keep drawing/detail logic separate.
Tests: extend `X:/schedule/app/src/test/java/com/ustc/timetable/timetable/ui/TimetableViewModelTest.kt`; create `X:/schedule/app/src/test/java/com/ustc/timetable/timetable/ui/UnifiedOverlapUiTest.kt` (Robolectric Compose), plus existing Settings screen tests.

- [ ] Write ViewModel mixed ghost integration and mode-change tests, and Compose tests for marker detail routing, manual edit callback identity, content once, shape-only hit testing, setting cancellation and saved preference behavior.
- [ ] RED: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.ui.TimetableViewModelTest' --tests 'com.ustc.timetable.timetable.ui.UnifiedOverlapUiTest' --tests 'com.ustc.timetable.settings.*' --max-workers=1`. Expected missing mixed attachment/current setting or incorrect rendered bounds, not mock assertions.
- [ ] Build entries from rawSchool with semester.importedAt and rawManual with original createdAt; combine SettingsStore mode into page projection. Keep overview built from unfiltered raw active data. Render each event as union of slice rectangles with outer-only inset and one contained text region using existing BlockTexts budget. Route physical and accessibility marker activation through the same typed callback; preserve gesture ownership and manual body edit. Use latest maps for detail edit and deletion. Add exact-overlap selection sheet with non-persistent open state and confirmed enum callback.
- [ ] GREEN: rerun the focused filters; keep legacy semantic tags where they still express unchanged behavior.
- [ ] Regression: `./gradlew :app:testDebugUnitTest --tests 'com.ustc.timetable.timetable.*' --tests 'com.ustc.timetable.settings.*' --tests 'com.ustc.timetable.manual.*' --max-workers=1`. Replace old SCHOOL-only expectation only where the approved design explicitly changed it; never weaken data invariants.
- [ ] Commit: `feat(ui): render unified overlap shapes and candidate navigation`.

## Task 5 — Qualification

Create `X:/schedule/app/src/androidTest/java/com/ustc/timetable/ui/UnifiedOverlapConnectedTest.kt` with synthetic test-only fixtures; record results in `X:/schedule/docs/superpowers/qualification/2026-09-05-unified-overlap.md`.

- [ ] Write device checks for partial overlap step shapes, mixed cross-week markers, manual candidate editor, exact mode, LIGHT/DARK and 1.3 font, expanded/collapsed 21:55 and all seven days. A wrong marker route or missing slice must fail, not merely a source-text scan.
- [ ] Run on emulator only: `$env:ANDROID_SERIAL='emulator-5554'; ./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.ustc.timetable.ui.UnifiedOverlapConnectedTest' --max-workers=1`. Observe any focused regression before UI fixes.
- [ ] Fix only owning presentation files for observed failures; never patch frozen axis or production authentication to satisfy fixtures.
- [ ] Run focused GREEN then `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease --max-workers=1`. Count actual XML failures/errors/skips and lint errors. Capture settled device PNG files and retrieve with adb pull; no raw user data in docs.
- [ ] Audit `git diff cd05ca3 -- app/src/main/java/com/ustc/timetable/school app/src/main/java/com/ustc/timetable/sync app/src/main/java/com/ustc/timetable/timetable/data/db app/src/main/java/com/ustc/timetable/timetable/layout/SegmentedTimelineAxis.kt app/src/main/java/com/ustc/timetable/timetable/layout/TimeBoundaryMark.kt app/src/main/java/com/ustc/timetable/timetable/layout/LongPressResolver.kt`; require empty. Confirm icon remains unstaged. Physical J2/J3 remains separate until genuinely completed.
- [ ] Commit: `test(ui): qualify unified overlap presentation` and report task commits, observed RED/GREEN and remaining physical gates.

## Plan self-review

Spec sections 4/5/7 map to Task 1, section 6 to Task 2, section 8 to Tasks 3/4, sections 9/10 to Tasks 4/5. Canonical identity and Instant ordering are shared; no missing per-course timestamp assumed. Five tasks, thirty execution checkboxes. User requested execution in this session, so execute inline without spawning agents or adding intermediate product gates.
