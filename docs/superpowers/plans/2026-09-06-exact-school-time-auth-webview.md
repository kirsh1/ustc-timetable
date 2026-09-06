# Exact School Times and Login Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import non-standard portal times exactly, preserve existing databases, show endpoint-time pills, provide a real school-account reset, and add an ephemeral desktop WebView mode.

**Architecture:** School meetings retain their existing period anchors and gain an optional exact-minute override persisted through a lossless Room v1-to-v2 migration. All presentation consumes one effective-time function, while account clearing owns both encrypted session and app-local WebView cookies and the login activity owns a non-persistent display-mode controller.

**Tech Stack:** Kotlin, Android Room, Jetpack Compose Material 3, Android WebView, kotlinx.serialization, Robolectric, AndroidJUnit4.

**Spec:** `docs/superpowers/specs/2026-09-06-exact-school-time-auth-webview-design.md`

## Global Constraints

- Existing v1 installations open their current timetable after upgrade without login or re-import.
- Never use destructive Room migration and never delete local timetable/manual/settings data during logout.
- Standard portal meetings keep current period behavior, stable IDs, and legacy fingerprint bytes.
- Exact time is an all-null or all-non-null pair with end strictly after start.
- Portal import remains atomic; malformed rows are never silently skipped.
- Automatic DOM navigation selectors and generation gates remain unchanged.
- Desktop/mobile mode is activity-local and defaults to mobile for every new login activity.
- No raw credentials, cookies, account IDs, portal bodies, or course content are logged.

---

### Task 1: Exact-time domain, persistence, and lossless Room migration

**Files:**
- Modify: `app/src/main/java/com/ustc/timetable/timetable/domain/CourseMeeting.kt`
- Create: `app/src/main/java/com/ustc/timetable/timetable/domain/CourseMeetingTime.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/data/db/entity/Entities.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/data/db/Mappers.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/data/db/TimetableDatabase.kt`
- Create: `app/src/main/java/com/ustc/timetable/timetable/data/db/TimetableMigrations.kt`
- Modify: `app/src/main/java/com/ustc/timetable/AppContainer.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/domain/DomainModelInvariantTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/data/RepositoriesTest.kt`
- Create: `app/src/test/java/com/ustc/timetable/timetable/data/db/TimetableMigrationTest.kt`

**Interfaces:**
- Produces: `CourseMeeting.exactStartTime: LocalTime?`, `CourseMeeting.exactEndTime: LocalTime?`.
- Produces: `fun CourseMeeting.effectiveTimeRange(profile: ScheduleProfile): LocalTimeRange`.
- Produces: `TimetableMigrations.MIGRATION_1_2: Migration`.

- [ ] **Step 1: Write failing domain, mapper, and migration tests**

Add assertions equivalent to:

```kotlin
@Test fun exact_time_pair_is_atomic_and_ordered() {
    assertThrows(IllegalArgumentException::class.java) {
        meeting(exactStartTime = LocalTime.of(16, 10), exactEndTime = null)
    }
    assertThrows(IllegalArgumentException::class.java) {
        meeting(exactStartTime = LocalTime.of(17, 50), exactEndTime = LocalTime.of(16, 10))
    }
}

@Test fun effective_range_prefers_exact_pair() {
    val result = meeting(
        exactStartTime = LocalTime.of(16, 10),
        exactEndTime = LocalTime.of(17, 50),
    ).effectiveTimeRange(profile())
    assertEquals(LocalTime.of(16, 10), result.start)
    assertEquals(LocalTime.of(17, 50), result.endInclusive)
}
```

The migration test creates a v1 SQLite database with one row in every table, executes `MIGRATION_1_2`, then asserts the existing values remain and both new columns are null.

- [ ] **Step 2: Run and observe RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.timetable.domain.DomainModelInvariantTest --tests com.ustc.timetable.timetable.data.RepositoriesTest --tests com.ustc.timetable.timetable.data.db.TimetableMigrationTest
```

Expected RED: exact properties, effective range, migration, and v2 columns do not exist.

- [ ] **Step 3: Implement the minimum compatible model and migration**

Use this invariant and migration shape:

```kotlin
require((exactStartTime == null) == (exactEndTime == null))
if (exactStartTime != null) require(exactEndTime!! > exactStartTime)

fun CourseMeeting.effectiveTimeRange(profile: ScheduleProfile): LocalTimeRange =
    exactStartTime?.let { LocalTimeRange(it, requireNotNull(exactEndTime)) }
        ?: profile.timeRange(startPeriod, endPeriod)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE course_meetings ADD COLUMN exactStartMinutes INTEGER DEFAULT NULL")
        db.execSQL("ALTER TABLE course_meetings ADD COLUMN exactEndMinutes INTEGER DEFAULT NULL")
    }
}
```

Set `TimetableDatabase.version = 2`, add nullable entity columns, map minutes with `LocalTime`, and register `.addMigrations(TimetableMigrations.MIGRATION_1_2)` in `AppContainer`.

- [ ] **Step 4: Run targeted GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Run persistence regression**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.timetable.data.db.TimetableDatabaseTest --tests com.ustc.timetable.timetable.data.db.ImportSchoolSnapshotTest --tests com.ustc.timetable.manual.ManualItemFlowTest
```

Expected: existing database and manual-item behavior remain green.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ustc/timetable/timetable/domain app/src/main/java/com/ustc/timetable/timetable/data/db app/src/main/java/com/ustc/timetable/AppContainer.kt app/src/test/java/com/ustc/timetable/timetable/domain/DomainModelInvariantTest.kt app/src/test/java/com/ustc/timetable/timetable/data/RepositoriesTest.kt app/src/test/java/com/ustc/timetable/timetable/data/db/TimetableMigrationTest.kt
git commit -m "feat(data): preserve exact school meeting times"
```

### Task 2: Parse and normalize non-standard portal times

**Files:**
- Modify: `app/src/main/java/com/ustc/timetable/school/ustc/dto/Dtos.kt`
- Modify: `app/src/main/java/com/ustc/timetable/school/ustc/parser/UstcTimetablePageParser.kt`
- Modify: `app/src/main/java/com/ustc/timetable/school/ustc/parser/UstcSnapshotNormalizer.kt`
- Modify: `app/src/test/java/com/ustc/timetable/school/ustc/parser/UstcTimetablePageParserTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/school/ustc/parser/UstcSnapshotNormalizerTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/semester/ImportFlowTest.kt`

**Interfaces:**
- Consumes: Task 1 exact pair.
- Produces: `UstcTimetableEntry.exactStartTime: LocalTime?` and `exactEndTime: LocalTime?`.
- Produces: strict `portalTime(hhmm: Int): LocalTime` parsing internal to `UstcTimetablePageParser`.

- [ ] **Step 1: Write failing parser and import tests**

Add a fixture occurrence containing `startTime:1610`, `endTime:1750`, `periods:2`, `startUnit:0`, and `endUnit:0`. Assert parser output anchors at period 8, preserves `16:10–17:50`, normalization preserves the pair, and import completes. Add malformed `1660`, inverted range, start-in-gap, and period-overflow rejection tests. Assert a standard `15:55` row retains null overrides and its previous canonical output.

- [ ] **Step 2: Run and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.parser.UstcTimetablePageParserTest --tests com.ustc.timetable.school.ustc.parser.UstcSnapshotNormalizerTest --tests com.ustc.timetable.semester.ImportFlowTest
```

Expected RED: `16:10` still raises `ParseFailed` and DTO/domain fields are absent.

- [ ] **Step 3: Implement strict fallback and canonical propagation**

Extend layout units with end time. Preserve exact-match logic. For a non-match, parse the actual pair and select exactly one layout unit satisfying `unit.start <= actualStart && actualStart < unit.end`; calculate `endPeriod = startPeriod + periods - 1`, reject overflow, and store the exact pair. Include the pair in parser assignment keys, normalizer merge keys, deterministic ordering, and stable ID parts only when non-null.

- [ ] **Step 4: Run targeted GREEN**

Run the Step 2 command. Expected: all parser/normalizer/import cases pass.

- [ ] **Step 5: Run portal regressions**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.parser.ParserContractsTest --tests com.ustc.timetable.school.ustc.portal.UstcHttpPortalSourceTest --tests com.ustc.timetable.sync.SyncEngineTest
```

Expected: existing sanitized portal evidence and sync behavior pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ustc/timetable/school/ustc app/src/test/java/com/ustc/timetable/school/ustc app/src/test/java/com/ustc/timetable/semester/ImportFlowTest.kt
git commit -m "fix(import): accept exact nonstandard portal times"
```

### Task 3: Preserve fingerprint compatibility and detect exact-time changes

**Files:**
- Modify: `app/src/main/java/com/ustc/timetable/timetable/domain/FingerprintedSchoolContent.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/domain/SchoolSnapshotFingerprint.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/domain/SnapshotDiffer.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/domain/ScheduleChange.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/domain/SchoolSnapshotFingerprintTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/domain/SnapshotDifferTest.kt`

**Interfaces:**
- Consumes: Task 1 exact pair.
- Produces: legacy fingerprint path for all-null overrides and versioned v2 content for any exact override.

- [ ] **Step 1: Write failing compatibility and change tests**

Pin an existing standard-only fingerprint string before modifying serialization. Assert the same content after model extension hashes identically. Assert `16:10–17:50` hashes differently from `16:15–17:50`, and `SnapshotDiffer` emits one time change for that pair.

- [ ] **Step 2: Run and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.timetable.domain.SchoolSnapshotFingerprintTest --tests com.ustc.timetable.timetable.domain.SnapshotDifferTest
```

Expected RED: exact minutes are not represented and no exact-time change is detected.

- [ ] **Step 3: Implement dual fingerprint representation**

Keep the current serializable `FingerprintedSchoolContent` byte shape as the legacy path. Add a private versioned v2 representation containing nullable exact-minute pairs; select it only when any meeting has an override. Extend deterministic comparison and differ costs/summaries with effective exact-minute identity while leaving standard period-only output unchanged.

- [ ] **Step 4: Run targeted GREEN**

Run Step 2. Expected: pinned legacy hash and exact-time sensitivity pass.

- [ ] **Step 5: Run sync regressions**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.sync.SyncEngineTest --tests com.ustc.timetable.timetable.domain.ChangeFormatterTest --tests com.ustc.timetable.notification.SyncNotificationTest
```

Expected: notification and sync diff tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ustc/timetable/timetable/domain app/src/test/java/com/ustc/timetable/timetable/domain
git commit -m "feat(sync): fingerprint exact school times compatibly"
```

### Task 4: Use exact ranges and render Material 3 boundary-time pills

**Files:**
- Modify: `app/src/main/java/com/ustc/timetable/timetable/ui/TimetableViewModel.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/ui/CourseDetailFormatter.kt`
- Create: `app/src/main/java/com/ustc/timetable/timetable/ui/BoundaryTimePill.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/layout/SegmentedCourseCard.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/ui/TimetableViewModelTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/ui/CourseDetailFormatterTest.kt`
- Create: `app/src/test/java/com/ustc/timetable/timetable/ui/BoundaryTimePillTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGridTest.kt`

**Interfaces:**
- Consumes: `CourseMeeting.effectiveTimeRange`.
- Produces: `data class BoundaryTimePills(val start: String?, val end: String?)`.
- Produces: `fun boundaryTimePills(start: LocalTime, end: LocalTime, periods: List<PeriodTime>): BoundaryTimePills`.

- [ ] **Step 1: Locate the single production card owner and write failing projection/UI tests**

The pure test asserts `16:10–17:50` produces both strings, `15:55–17:50` produces only the end, and `15:55–16:40` produces neither. Compose tests assert centered tagged pills, theme-aware content, unchanged placed bounds, click behavior, marker coexistence, and no pill clipping at font scale 1.3.

- [ ] **Step 2: Run and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.timetable.ui.TimetableViewModelTest --tests com.ustc.timetable.timetable.ui.CourseDetailFormatterTest --tests com.ustc.timetable.timetable.ui.BoundaryTimePillTest
```

Expected RED: school projection still resolves only profile periods and pill API is absent.

- [ ] **Step 3: Implement shared effective projection and pills**

Use effective times in `schoolTimedBlock` and detail formatting. Derive boundaries with `periods.flatMap { listOf(it.start, it.end) }.toSet()`. Render only non-null labels as non-clickable Material 3 `Surface` pills centered and half-overlapping their card edge; reserve minimal content inset and keep lower-right markers unobstructed.

- [ ] **Step 4: Run targeted GREEN**

Run Step 2. Expected: projection, formatter, and pill tests pass.

- [ ] **Step 5: Run layout/gesture regressions**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.timetable.ui.ShowNonCurrentWeekTest --tests com.ustc.timetable.timetable.ui.TimetableGestureIntegrationTest --tests com.ustc.timetable.timetable.layout.WeeklyTimetableGridTest
```

Expected: overlap, ghost, gesture, and vertical-axis behavior remain unchanged.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ustc/timetable/timetable/ui app/src/main/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGrid.kt app/src/main/java/com/ustc/timetable/timetable/layout/SegmentedCourseCard.kt app/src/test/java/com/ustc/timetable/timetable/ui app/src/test/java/com/ustc/timetable/timetable/layout/WeeklyTimetableGridTest.kt
git commit -m "feat(ui): show exact boundary times on timetable cards"
```

### Task 5: Clear both school session layers and recover from wrong identity

**Files:**
- Modify: `app/src/main/java/com/ustc/timetable/school/ustc/auth/CookieRetriever.kt`
- Modify: `app/src/main/java/com/ustc/timetable/school/ustc/auth/UstcSessionManager.kt`
- Modify: `app/src/main/java/com/ustc/timetable/settings/SettingsModels.kt`
- Modify: `app/src/main/java/com/ustc/timetable/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/ustc/timetable/timetable/ui/FirstLaunchScreen.kt`
- Modify: `app/src/main/java/com/ustc/timetable/semester/ImportFlowViewModel.kt`
- Modify: `app/src/main/java/com/ustc/timetable/MainActivity.kt`
- Modify: `app/src/test/java/com/ustc/timetable/school/ustc/auth/UstcSessionManagerTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/settings/SettingsViewModelTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/timetable/ui/FirstLaunchScreenTest.kt`
- Modify: `app/src/test/java/com/ustc/timetable/semester/ImportFlowTest.kt`

**Interfaces:**
- Replace the read-only cookie function interface with `WebViewCookieAccess`, exposing `cookieHeaderFor(url: String): String?` and `suspend fun clearAll()`.
- `UstcSessionManager.clear()` clears `SessionStore` and WebView cookies, then flushes.
- First-launch route receives `onClearSchoolLoginAndRetry: () -> Unit`.

- [ ] **Step 1: Write failing logout and recovery tests**

Assert session and cookies are both cleared, local Room rows/settings are unchanged, duplicate clear taps are suppressed, an import error shows the `sc` identity hint plus “清除学校登录并重新登录”, and successful clearing relaunches login. Assert network/parse errors alone never invoke clear.

- [ ] **Step 2: Run and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.auth.UstcSessionManagerTest --tests com.ustc.timetable.settings.SettingsViewModelTest --tests com.ustc.timetable.timetable.ui.FirstLaunchScreenTest --tests com.ustc.timetable.semester.ImportFlowTest
```

Expected RED: cookies survive clear and first-launch recovery UI/callbacks are absent.

- [ ] **Step 3: Implement one account-clear authority**

Wrap `CookieManager.removeAllCookies` in a suspending implementation, call `flush()`, and have both Settings and first-launch recovery use `UstcSessionManager.clear()`. Clear only transient import error after the account layers finish, then launch WebView login. Keep database and DataStore untouched.

- [ ] **Step 4: Run targeted GREEN**

Run Step 2. Expected: all account recovery tests pass.

- [ ] **Step 5: Run login/sync regressions**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.auth.SessionStoreTest --tests com.ustc.timetable.sync.ManualSyncFlowTest --tests com.ustc.timetable.MainActivityLifecycleTest
```

Expected: session encryption, manual reauth, and activity lifecycle pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ustc/timetable/school/ustc/auth app/src/main/java/com/ustc/timetable/settings app/src/main/java/com/ustc/timetable/timetable/ui/FirstLaunchScreen.kt app/src/main/java/com/ustc/timetable/semester/ImportFlowViewModel.kt app/src/main/java/com/ustc/timetable/MainActivity.kt app/src/test/java/com/ustc/timetable
git commit -m "fix(auth): allow non-destructive school account reset"
```

### Task 6: Add ephemeral mobile/desktop WebView display mode

**Files:**
- Create: `app/src/main/java/com/ustc/timetable/school/ustc/auth/WebViewDisplayMode.kt`
- Modify: `app/src/main/java/com/ustc/timetable/school/ustc/auth/WebViewLoginActivity.kt`
- Modify: `app/src/main/java/com/ustc/timetable/ui/AppIcons.kt`
- Modify: `app/src/test/java/com/ustc/timetable/school/ustc/auth/WebViewLoginActivityTest.kt`
- Create: `app/src/test/java/com/ustc/timetable/school/ustc/auth/WebViewDisplayModeTest.kt`

**Interfaces:**
- Produces: `enum class WebViewDisplayMode { MOBILE, DESKTOP }`.
- Produces: a pure settings projection that retains captured mobile UA/viewport values and derives desktop values.

- [ ] **Step 1: Write failing mode and activity tests**

Assert new activity defaults to mobile, desktop changes UA and enables wide/overview settings, the icon has “切换到桌面版”/“切换到手机版” descriptions, switching reloads the current HTTP(S) URL, toggling back restores captured defaults, and a new activity starts mobile. Existing automatic landing-DOM and completion tests must remain unchanged.

- [ ] **Step 2: Run and observe RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.auth.WebViewDisplayModeTest --tests com.ustc.timetable.school.ustc.auth.WebViewLoginActivityTest
```

Expected RED: display-mode type and app-owned toggle do not exist.

- [ ] **Step 3: Implement Material 3 top control and mode application**

Add an app-owned Compose top control above the WebView using a vector desktop/mobile icon and 48dp touch target. Capture initial UA, `useWideViewPort`, and `loadWithOverviewMode`; apply desktop projection and reload only an HTTP(S) current URL; restore captured settings for mobile. Keep `ModuleBootstrapGate`, `DomNavigationGate`, discovery JavaScript, completion coordinator, and fallback button logic unchanged.

- [ ] **Step 4: Run targeted GREEN**

Run Step 2. Expected: display mode and all existing WebView tests pass.

- [ ] **Step 5: Run authentication regressions**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.auth.LoginCompletionCoordinatorTest --tests com.ustc.timetable.school.ustc.portal.UstcCurrentTurnDiscoveryTest --tests com.ustc.timetable.RuntimeCompositionTest
```

Expected: automatic recognition and runtime wiring remain green.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/ustc/timetable/school/ustc/auth app/src/main/java/com/ustc/timetable/ui/AppIcons.kt app/src/test/java/com/ustc/timetable/school/ustc/auth
git commit -m "feat(auth): add desktop portal display mode"
```

### Task 7: Full qualification and physical-device verification

**Files:**
- Create: `app/src/androidTest/java/com/ustc/timetable/test/J2ExactTimeQualificationTest.kt`
- Create: `docs/superpowers/acceptance/2026-09-06-exact-time-login-recovery.md`

**Interfaces:**
- Consumes all prior tasks; produces no new product interface.

- [ ] **Step 1: Add a sanitized opt-in exact-time device assertion**

Create `J2ExactTimeQualificationTest.report_sanitized_exact_time_state`. It reports database version, semester count, school course/meeting counts, exact-override count, and whether a portal-linked current semester exists. It must not emit cookie values, URLs with query parameters, names, teachers, rooms, or raw portal bodies.

- [ ] **Step 2: Run the targeted qualification wave**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.ustc.timetable.school.ustc.parser.UstcTimetablePageParserTest --tests com.ustc.timetable.timetable.data.db.TimetableMigrationTest --tests com.ustc.timetable.timetable.ui.BoundaryTimePillTest --tests com.ustc.timetable.school.ustc.auth.UstcSessionManagerTest --tests com.ustc.timetable.school.ustc.auth.WebViewLoginActivityTest
```

Expected: all targeted tests pass.

- [ ] **Step 3: Run full automated qualification**

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Expected: unit suite, lint, debug APK, and release APK all succeed.

- [ ] **Step 4: Verify upgrade and real import on devices**

Use `adb install -r app/build/outputs/apk/release/app-release.apk` so package data is retained. On one already-working phone verify the existing timetable opens without re-import. On the diagnosed phone import the same account and verify eight courses, at least one exact-time meeting, visible `16:10` boundary pill, and successful sync availability. Exercise wrong-identity clear/retry and desktop toggle without clearing app data.

- [ ] **Step 5: Record evidence and audit boundaries**

Record only pass/fail, counts, build hashes, device model/API, and sanitized screenshots. Run `git diff --check`, confirm no destructive migration, confirm no portal selector change, and confirm local data remains after account reset.

- [ ] **Step 6: Commit**

```powershell
git add app/src/test docs/superpowers/acceptance/2026-09-06-exact-time-login-recovery.md
git commit -m "test: qualify exact portal times and account recovery"
```
