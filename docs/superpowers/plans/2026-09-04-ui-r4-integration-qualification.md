# UI-R4 Integration and Qualification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkboxes for tracking.

**Goal:** Connect the three feature subplans through the Activity/ViewModel/Compose graph, add current-build connected evidence, and qualify the final code tree.

**Architecture:** One final unit-level wiring task propagates applied settings into immutable timetable state and every renderer. One instrumentation task adds stable semantics and end-to-end checks without test-only behavior entering main/debug. Qualification then runs the full gates and audits the approved production boundary.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore Preferences, AndroidX instrumentation, JUnit 4, Gradle, adb, Android lint.

**Spec:** `X:\schedule\docs\superpowers\specs\2026-09-04-ui-r4-addendum.md` §§11–13.

**Approved application baseline:** `75b15822890e379b3c3382bdf95a76b539065f4b` (`f7cb33b^`). This immutable commit is the authority for production-boundary, persistence, Room, portal/runtime, MANUAL, and vertical-axis comparisons.

**Actual execution starting HEAD:** deliberately not assigned by this docs-only planning pass. On the future production-authorized run, capture `git rev-parse HEAD` before Task 1 as `$executionStartHead`, require that it contains the approved r1.1 plan commit, and record it separately from the approved application baseline. Never substitute the plan-authoring HEAD or `$executionStartHead` for the approved application baseline in scope audits.

## Entry gate

Before Task 1, set `$approvedAppBaseline = "75b15822890e379b3c3382bdf95a76b539065f4b"`, set `$executionStartHead = git rev-parse HEAD`, verify that the approved r1.1 plan commit is an ancestor of `$executionStartHead`, verify that every subplan completion command in the first three plans is green, and require `git status --short` empty. Record the actual execution starting HEAD and the three ending commits as distinct values. Do not use this subplan to repair portal/parser/session/runtime, Room, sync fingerprint/worker, manual persistence, or vertical-axis defects.

---

### Task 1: Wire applied palette state across ViewModel, grid, mini-map, and recreation

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableViewModel.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableScreen.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\WeekOverviewStrip.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableViewModelTest.kt`
- Modify test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\TimetableScreenTest.kt`
- Add test: `X:\schedule\app\src\test\java\com\ustc\timetable\timetable\ui\UiR4IntegrationTest.kt`
- Test FQCNs: `com.ustc.timetable.timetable.ui.TimetableViewModelTest`, `com.ustc.timetable.timetable.ui.TimetableScreenTest`, `com.ustc.timetable.timetable.ui.UiR4IntegrationTest`

**Required production contract**

```kotlin
data class TimetableUiState(
    val coursePaletteSeed: Long = DEFAULT_COURSE_PALETTE_SEED,
)

fun WeeklyTimetableGrid(
    coursePaletteSeed: Long,
)

internal fun WeekOverviewStrip(
    coursePaletteSeed: Long,
)
```

The excerpts add parameters while retaining every existing parameter. `TimetableViewModel.state` combines `settings.coursePaletteSeed` as a top-level presentation input and copies it into `TimetableUiState`; it does not rebuild domain data or persist a week. `TimetableScreen` passes the same applied seed to every pager page and WeekOverview. `WeeklyTimetableGrid` and `WeekOverviewStrip` call `CoursePalette.colorIndexFor(colorKey, coursePaletteSeed)`. SCHOOL and MANUAL use their existing stable `colorKey`. Marker colors come from `MaterialTheme`, not the palette seed.

- [ ] **1. Write failing test.** Add `UiR4IntegrationTest` cases proving default seed keeps baseline school/manual/overview indices, an applied nondefault seed updates all three renderers together, recomposition and week navigation do not generate a seed, recreation reads the applied seed, wallpaper visibility does not change card alpha, SCHOOL markers stay absent from MANUAL, and overview expansion remains non-persistent.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.UiR4IntegrationTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected RED: applied seed is present in Settings but is not yet collected by timetable state or passed to grid/overview rendering.
- [ ] **3. Minimal production implementation.** Add the setting to the ViewModel combine/state, thread the immutable value through screen/grid/overview signatures, and remove use of the default-only `colorIndexFor(colorKey)` overload from production call sites. Do not change Room/repository collection, raw meeting mapping, week projection, or vertical layout.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.ui.UiR4IntegrationTest" --tests "com.ustc.timetable.timetable.ui.TimetableViewModelTest" --tests "com.ustc.timetable.timetable.ui.TimetableScreenTest" --rerun-tasks --max-workers=1`. Expected GREEN: one persisted seed drives every course renderer and all isolation assertions pass.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.settings.SettingsViewModelTest" --tests "com.ustc.timetable.settings.SettingsScreenTest" --tests "com.ustc.timetable.manual.ManualItemFlowTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --tests "com.ustc.timetable.timetable.ui.WeekSwitchNavigationTest" --rerun-tasks --max-workers=1`. Expected GREEN: settings, MANUAL operations, overview, and week paging remain stable.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/timetable/ui/TimetableViewModel.kt app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/main/java/com/ustc/timetable/timetable/ui/WeekOverviewStrip.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableViewModelTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/TimetableScreenTest.kt app/src/test/java/com/ustc/timetable/timetable/ui/UiR4IntegrationTest.kt` and `git commit -m "feat(ui-r4): wire timetable appearance state"`.

---

### Task 2: Add stable connected semantics and current-build device evidence

**Files**

- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\semester\SemesterConfirmSheet.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\settings\SettingsScreen.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\layout\WeeklyTimetableGrid.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\CourseDetailSheet.kt`
- Modify production: `X:\schedule\app\src\main\java\com\ustc\timetable\timetable\ui\TimetableScreen.kt`
- Add test: `X:\schedule\app\src\androidTest\java\com\ustc\timetable\ui\UiR4ConnectedTest.kt`
- Modify test harness: `X:\schedule\app\src\androidTest\java\com\ustc\timetable\ui\J1MainActivityHarness.kt`
- Test FQCN: `com.ustc.timetable.ui.UiR4ConnectedTest`

**Required production semantics**

```text
semester_confirm_end_date_mode
semester_confirm_restore_auto_end
course_palette
course_palette_sheet
palette_new_candidate
palette_apply
palette_restore_default
wallpaper_visibility_slider
wallpaper_visibility_preview
school_marker:different
school_marker:variant
course_detail_pager
course_detail_page:<zero-based-index>
course_detail_previous
course_detail_next
course_detail_indicator
```

Tags expose existing controls only; they do not add test-only branches. Marker identity remains in the supplied `SchoolCanonicalPresentationKey`; the semantics tag names only the marker kind and therefore does not expose or depend on local `MeetingId`. Marker semantics include a localized content description. The test harness may add test-owned seed fixtures and screenshot helpers only under `androidTest`; it must still launch the base `TimetableApp` through the existing J1 runner and must not enable `DebugSeed` automatically.

- [ ] **1. Write failing test.** Add device tests for downward expansion/upward collapse, horizontal page arbitration, empty long press, current-course-first detail carousel, same-course single detail, MANUAL isolation, default/applied palette persistence across Activity recreation, wallpaper 0/65/100 preview and finish/cancel, wallpaper course-card alpha isolation, and measured rail at font scale 1.3. Use test-owned local data only and close every `ActivityScenario` before resetting Room.
- [ ] **2. Run and observe expected RED.** Run `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ustc.timetable.ui.UiR4ConnectedTest" --rerun-tasks --max-workers=1`. Expected RED: stable semantics listed above are absent and the new connected class cannot locate/drive the complete UI-R4 workflow.
- [ ] **3. Minimal production implementation.** Add only the listed test tags/content descriptions and any accessibility click action required to expose already-implemented behavior. Add test-owned fixtures/helpers only to the two androidTest files. Do not introduce production test switches, debug seed activation, or network calls.
- [ ] **4. Run targeted GREEN.** Run `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ustc.timetable.ui.UiR4ConnectedTest" --rerun-tasks --max-workers=1`. Expected GREEN: all UI-R4 device cases pass with zero failures, errors, and skips.
- [ ] **5. Run targeted regression.** Run `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.ustc.timetable.ui.TimetableSmokeTest,com.ustc.timetable.ui.ManualItemUiTest,com.ustc.timetable.ui.WallpaperConnectedTest,com.ustc.timetable.ui.ReauthUiTest" --rerun-tasks --max-workers=1`. Expected GREEN: existing timetable, MANUAL, wallpaper, and reauth connected suites pass with zero failures, errors, and skips.
- [ ] **6. Commit.** Run `git add app/src/main/java/com/ustc/timetable/semester/SemesterConfirmSheet.kt app/src/main/java/com/ustc/timetable/settings/SettingsScreen.kt app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/main/java/com/ustc/timetable/timetable/ui/CourseDetailSheet.kt app/src/main/java/com/ustc/timetable/timetable/ui/TimetableScreen.kt app/src/androidTest/java/com/ustc/timetable/ui/UiR4ConnectedTest.kt app/src/androidTest/java/com/ustc/timetable/ui/J1MainActivityHarness.kt` and `git commit -m "test(ui-r4): qualify interaction addendum on device"`.

## Qualification phase

The following gates are mandatory but are not implementation Tasks and therefore do not create additional task checkboxes or production commits.

### 1. Clean and environment capture

```powershell
Set-Location "X:\schedule"
git status --short
git stash list
git rev-parse HEAD
java -version
./gradlew --version
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
```

Require a clean tree, empty stash, expected implementation HEAD, one authorized emulator/device, and the configured JDK/Gradle environment.

Also record both authorities explicitly:

```powershell
$approvedAppBaseline = "75b15822890e379b3c3382bdf95a76b539065f4b"
$executionStartHead = git rev-parse HEAD
$planFiles = @(
    "docs/superpowers/plans/2026-09-04-ui-r4.md",
    "docs/superpowers/plans/2026-09-04-ui-r4-semester-card-rail.md",
    "docs/superpowers/plans/2026-09-04-ui-r4-gesture-ghost-detail.md",
    "docs/superpowers/plans/2026-09-04-ui-r4-palette-wallpaper.md",
    "docs/superpowers/plans/2026-09-04-ui-r4-integration-qualification.md"
)
$reviewedPlanCommit = git log -1 --format=%H -- $planFiles
git merge-base --is-ancestor $reviewedPlanCommit $executionStartHead
if ($LASTEXITCODE -ne 0) { throw "Execution HEAD does not contain the reviewed plan history" }
"Approved app baseline: $approvedAppBaseline"
"Reviewed plan commit: $reviewedPlanCommit"
"Actual execution starting HEAD: $executionStartHead"
```

Because the five plan paths are docs-only and committed together, `$reviewedPlanCommit` resolves to the r1.1 correction commit without hard-coding a self-referential hash. `6944d11768662c02e6a475e02fcc3c77be865348` is only the parent at which this correction pass started and is never reported as the production baseline.

### 2. Targeted unit gate

```powershell
./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.semester.SemesterConfirmEditorTest" --tests "com.ustc.timetable.semester.SemesterConfirmSheetTest" --tests "com.ustc.timetable.timetable.layout.CourseCardVisualLayoutTest" --tests "com.ustc.timetable.timetable.layout.TimeRailWidthTest" --tests "com.ustc.timetable.timetable.ui.OverviewGestureArbitratorTest" --tests "com.ustc.timetable.timetable.ui.TimetableGestureIntegrationTest" --tests "com.ustc.timetable.timetable.ui.SchoolGhostProjectionTest" --tests "com.ustc.timetable.timetable.ui.CourseDetailPagerTest" --tests "com.ustc.timetable.settings.PaletteCandidateEditorTest" --tests "com.ustc.timetable.settings.WallpaperVisibilityEditorTest" --tests "com.ustc.timetable.timetable.ui.UiR4IntegrationTest" --rerun-tasks --max-workers=1
```

Require zero failures, errors, and skips.

### 3. Vertical-axis freeze gate

```powershell
./gradlew :app:testDebugUnitTest --tests "com.ustc.timetable.timetable.layout.SegmentedTimelineAxisTest" --tests "com.ustc.timetable.timetable.layout.SegmentedTimelineAxisV2Test" --tests "com.ustc.timetable.timetable.layout.TimeBoundaryRailTest" --tests "com.ustc.timetable.timetable.layout.LongPressResolverTest" --tests "com.ustc.timetable.timetable.ui.WeekOverviewStripTest" --rerun-tasks --max-workers=1
```

Require `existing_time_boundary_positions_remain_unchanged` and `existing_segmented_axis_roundtrip_remains_unchanged` plus all baseline vertical tests to pass. Any required production change to vertical geometry blocks UI-R4.

### 4. Full unit, lint, connected, and build gates

```powershell
$env:TEMP = "C:\jtmp"
$env:TMP = "C:\jtmp"
./gradlew --stop
./gradlew :app:testDebugUnitTest --rerun-tasks --max-workers=1
./gradlew :app:lintDebug --rerun-tasks --max-workers=1
./gradlew :app:connectedDebugAndroidTest --rerun-tasks --max-workers=1
./gradlew :app:assembleDebug :app:assembleRelease --rerun-tasks --max-workers=1
```

Require every unit and connected test green with zero failures, errors, and skips; lint errors zero; both APK builds green.

Read exact result counts rather than copying earlier totals:

```powershell
$unitFiles = Get-ChildItem "app\build\test-results\testDebugUnitTest" -Filter "TEST-*.xml"
$unitSuites = foreach ($file in $unitFiles) { ([xml](Get-Content -Raw $file.FullName)).testsuite }
"Unit tests: $((($unitSuites | Measure-Object tests -Sum).Sum))"
"Unit failures: $((($unitSuites | Measure-Object failures -Sum).Sum))"
"Unit errors: $((($unitSuites | Measure-Object errors -Sum).Sum))"
"Unit skipped: $((($unitSuites | Measure-Object skipped -Sum).Sum))"

$connectedFiles = Get-ChildItem "app\build\outputs\androidTest-results\connected" -Recurse -Filter "TEST-*.xml"
$connectedSuites = foreach ($file in $connectedFiles) { ([xml](Get-Content -Raw $file.FullName)).testsuite }
"Connected tests: $((($connectedSuites | Measure-Object tests -Sum).Sum))"
"Connected failures: $((($connectedSuites | Measure-Object failures -Sum).Sum))"
"Connected errors: $((($connectedSuites | Measure-Object errors -Sum).Sum))"
"Connected skipped: $((($connectedSuites | Measure-Object skipped -Sum).Sum))"

[xml]$lint = Get-Content "app\build\reports\lint-results-debug.xml"
$lintErrors = @($lint.issues.issue | Where-Object { $_.severity -eq "Error" })
$lintWarnings = @($lint.issues.issue | Where-Object { $_.severity -eq "Warning" })
"Lint errors: $($lintErrors.Count)"
"Lint warnings: $($lintWarnings.Count)"
$lintWarnings | Group-Object id | Sort-Object Name | ForEach-Object { "$($_.Name): $($_.Count)" }
```

Reports:

- `X:\schedule\app\build\reports\tests\testDebugUnitTest\index.html`
- `X:\schedule\app\build\reports\lint-results-debug.xml`
- `X:\schedule\app\build\reports\lint-results-debug.html`
- `X:\schedule\app\build\reports\androidTests\connected\debug\index.html`

### 5. Emulator screenshot matrix

Install the exact final debug build:

```powershell
./gradlew :app:installDebug
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$serial = ((& $adb devices | Select-String '\tdevice$' | Select-Object -First 1).Line -split "`t")[0]
New-Item -ItemType Directory -Force "docs\superpowers\qualification\2026-09-04-ui-r4-screens" | Out-Null
```

Capture these user-observed states after navigating the emulator to each named state. Each capture is first written by Android to a device file and then pulled byte-for-byte; do not pipe `adb exec-out screencap` into PowerShell redirection, because host text redirection can corrupt PNG bytes. Define this exact helper:

```powershell
function Capture-UiR4Screenshot([string]$screenName) {
    $deviceScreen = "/sdcard/Pictures/ui-r4-$screenName"
    $hostScreen = Join-Path "docs\superpowers\qualification\2026-09-04-ui-r4-screens" $screenName
    & $adb -s $serial shell screencap -p $deviceScreen
    if ($LASTEXITCODE -ne 0) { throw "Device screenshot failed: $screenName" }
    & $adb -s $serial pull $deviceScreen $hostScreen
    if ($LASTEXITCODE -ne 0) { throw "Screenshot pull failed: $screenName" }
    & $adb -s $serial shell rm $deviceScreen
    if (-not (Test-Path $hostScreen) -or (Get-Item $hostScreen).Length -eq 0) {
        throw "Empty screenshot: $hostScreen"
    }
}
```

After navigating to each state, invoke exactly the corresponding command before navigating onward:

```powershell
Capture-UiR4Screenshot "light-collapsed.png"
Capture-UiR4Screenshot "light-overview-expanded.png"
Capture-UiR4Screenshot "dark-collapsed.png"
Capture-UiR4Screenshot "dark-overview-expanded.png"
Capture-UiR4Screenshot "different-course-page-1.png"
Capture-UiR4Screenshot "different-course-page-2.png"
Capture-UiR4Screenshot "same-course-single-page.png"
Capture-UiR4Screenshot "palette-candidate-sheet.png"
Capture-UiR4Screenshot "wallpaper-0-percent.png"
Capture-UiR4Screenshot "wallpaper-65-percent.png"
Capture-UiR4Screenshot "wallpaper-100-percent.png"
Capture-UiR4Screenshot "font-scale-1-3-measured-rail.png"
```

Verify course title/location readability, teacher wrapping, marker clearance, Monday-to-Sunday width equality, fixed time rail, bar alignment, gesture results, theme contrast, and wallpaper-only strength changes. Restore the user's original font scale and theme after capture.

### 6. Preference and database compatibility audit

```powershell
git diff --unified=0 "$approvedAppBaseline..HEAD" -- app/src/main/java/com/ustc/timetable/timetable/data/SettingsStore.kt
git diff --exit-code "$approvedAppBaseline..HEAD" -- app/src/main/java/com/ustc/timetable/timetable/data/db app/src/main/java/com/ustc/timetable/timetable/domain app/schemas
git grep -n "course_palette_seed\|wallpaper_visibility_percent" -- app/src/main
git grep -n "appearance_mode\|timetable_wallpaper_uri" -- app/src/main/java/com/ustc/timetable/timetable/data/SettingsStore.kt
```

Require exactly two new preference keys, no Room/schema/domain persistence change, missing defaults 0L/65, read/write visibility clamps, and intact appearance/URI keys.

### 7. Production-boundary audit

```powershell
git diff --exit-code "$approvedAppBaseline..HEAD" -- app/src/main/java/com/ustc/timetable/school app/src/main/java/com/ustc/timetable/sync app/src/main/java/com/ustc/timetable/manual app/src/main/java/com/ustc/timetable/scheduleprofile
git diff --exit-code "$approvedAppBaseline..HEAD" -- app/src/main/AndroidManifest.xml app/src/debug app/src/release
git diff --name-only "$approvedAppBaseline..HEAD" -- app/src/main app/src/test app/src/androidTest
```

The first two commands must exit 0. Review the final name list against only the exact files named by Tasks 1–18 across the four plans. Verify test fakes exist only under `app/src/test` or `app/src/androidTest`; no `DebugSeed` auto-start is introduced.

### 8. Release isolation and APK evidence

```powershell
Get-ChildItem "app\build\intermediates" -Recurse -File | Where-Object {
    $_.FullName -match "release" -and $_.Name -match "UiR4ConnectedTest|J1MainActivityHarness"
}
$debugApk = (Resolve-Path "app\build\outputs\apk\debug\app-debug.apk").Path
$releaseApk = (Resolve-Path "app\build\outputs\apk\release\app-release.apk").Path
$apkanalyzer = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools" -Recurse -Filter "apkanalyzer.bat" |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $apkanalyzer) { throw "apkanalyzer.bat is required for release dex inspection" }
$releaseDexSymbols = & $apkanalyzer dex packages --defined-only $releaseApk
if ($LASTEXITCODE -ne 0) { throw "Release APK dex scan failed" }
$forbiddenReleaseSymbols = @(
    $releaseDexSymbols | Select-String -Pattern "com\.ustc\.timetable\.ui\.UiR4ConnectedTest|com\.ustc\.timetable\.ui\.J1MainActivityHarness"
)
if ($forbiddenReleaseSymbols.Count -ne 0) {
    $forbiddenReleaseSymbols
    throw "androidTest symbol found in release APK dex"
}
Get-Item $debugApk, $releaseApk | Select-Object FullName, Length
```

Require zero release-intermediate matches, a successful scan of the actual `app-release.apk` `classes*.dex` content, zero forbidden androidTest class matches, and recorded APK paths/sizes. Directory-name inspection alone does not satisfy release isolation.

### 9. Qualification record and final clean tree

Create `X:\schedule\docs\superpowers\qualification\2026-09-04-ui-r4.md` containing the approved application baseline, actual execution starting HEAD, every implementation commit, JDK/Gradle/AGP/SDK values, emulator serial/model/API/ABI, targeted/full counts, lint warning IDs, APK paths/sizes, screenshot paths, preference defaults, vertical-axis freeze result, SCHOOL/MANUAL isolation, production-boundary result, release-APK dex-scan result, and deviations. Commit only qualification docs and screenshots with:

```powershell
git add docs/superpowers/qualification/2026-09-04-ui-r4.md docs/superpowers/qualification/2026-09-04-ui-r4-screens
git commit -m "docs(ui-r4): record timetable interaction qualification"
$qualifiedCodeCommit = git rev-parse HEAD^
git diff --exit-code "$qualifiedCodeCommit..HEAD" -- app gradle
git status --short
git stash list
```

Require app/gradle diff zero across the docs commit, clean status, and empty stash. Stop and report rather than repair any production defect outside the named UI-R4 files.
