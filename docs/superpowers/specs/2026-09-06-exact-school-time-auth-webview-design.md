# Exact School Times, Account Recovery, and WebView Display Modes

Date: 2026-09-06  
Status: proposed for implementation  
Baseline: `7b67a63f99114aedff105f6a9e1b95bdcd925320`

## 1. Problem and evidence

A physical vivo device reproduced a first-import failure after successful CAS login. A sanitized, read-only probe established:

- the encrypted session exists;
- one current request context is available;
- the portal bundle and timetable requests succeed;
- eight selected courses parse successfully;
- 182 schedule occurrences are returned;
- 12 occurrences start at `16:10`, which is not one of the 13 layout start times;
- those occurrences have no usable `startUnit` or `endUnit`;
- all inspected course links, schedule-group links, weekdays, week indices, locations, and teachers are valid;
- `UstcTimetablePageParser` rejects the complete import at its exact `startTime` lookup.

This is portal-data variation, not a vivo, WebView, network, or cookie failure. The current school model stores only period indices while manual items and the timetable axis already support arbitrary minute times.

Two related login problems also exist:

- USTC CAS accepts both `gid` and `sc` identities, but only an eligible `sc` identity can supply normal student timetable data. A successful CAS login can therefore be followed by an import failure.
- the existing clear-login action clears the encrypted session but not this app's WebView/CAS cookies, so the same wrong identity can be selected automatically again.

On small screens, the unresponsive portal page can also hide the manual “进入选课” control. A user-selectable desktop rendering mode is needed without changing automatic DOM navigation.

## 2. Scope

This change will:

1. preserve exact portal start/end times for non-standard school meetings;
2. project those meetings through the existing minute-based `TimedBlock` layout;
3. show compact Material 3 boundary-time pills on non-standard school and manual blocks;
4. provide an explicit, non-destructive school-account reset and retry flow;
5. add an ephemeral mobile/desktop switch to the login WebView;
6. add a lossless Room v1-to-v2 migration.

This change will not:

- convert school meetings into manual items;
- delete local semesters, courses, manual items, appearance settings, or schedule profiles when logging out;
- change portal endpoints, current-turn DOM selectors, or automatic navigation gates;
- silently skip an invalid portal occurrence;
- redesign the segmented vertical axis;
- persist WebView display mode between login sessions.

## 3. Alternatives

### 3.1 Selected: optional exact-time overrides on school meetings

Keep the existing period range for compatibility and add an optional exact start/end pair. Standard meetings remain period-based. Non-standard meetings retain a conservative period anchor but render and format using exact times.

Benefits:

- exact `16:10` semantics;
- reuses the proven minute layout used by manual items;
- preserves school identity, sync, details, teachers, locations, and week patterns;
- old rows and standard imports retain existing behavior and stable identities.

Cost: Room v2 migration and coordinated domain/fingerprint/diff changes.

### 3.2 Rejected: snap non-standard time to a standard period

This avoids migration but displays false times, such as `15:55` instead of `16:10`.

### 3.3 Rejected: store portal rows as manual items

This loses school course identity, read-only semantics, sync replacement, teacher data, and course detail behavior.

### 3.4 Deferred: convert every school meeting to a pure minute-time model

This is conceptually uniform but unnecessarily changes all existing rows and fingerprints. Optional overrides are the smaller compatible evolution.

## 4. Exact school-time model

`CourseMeeting` gains:

```kotlin
val exactStartTime: LocalTime? = null
val exactEndTime: LocalTime? = null
```

Invariant:

- both values are null, or both are non-null;
- when non-null, `exactEndTime > exactStartTime`;
- `startPeriod/endPeriod` remain valid 1-to-13 anchors;
- source remains `SCHOOL`.

The effective range is a shared pure function:

```kotlin
fun CourseMeeting.effectiveTimeRange(profile: ScheduleProfile): LocalTimeRange
```

It returns the exact pair when present, otherwise `profile.timeRange(startPeriod, endPeriod)`. Timetable projection, detail formatting, accessibility text, overlap grouping, markers, and WeekOverview consume this effective range rather than independently resolving period indices.

Manual items keep their current persistence and domain type. They already enter layout as exact minute ranges; only the new boundary-time presentation is shared.

## 5. Portal parsing and normalization

`UstcTimetableEntry` carries the optional exact pair. Portal HHmm integers are parsed with strict validation: valid 24-hour time, same-day positive interval, and no partial pair.

For every occurrence:

1. If `startTime` exactly matches a layout unit, retain current `startPeriod + periods - 1` behavior and leave the exact pair null. Existing fixtures and identities remain unchanged.
2. Otherwise, accept the occurrence only when its actual start falls inside one layout period and `startPeriod + periods - 1` remains within the 13-unit layout. Persist the portal `startTime/endTime` as the exact pair.
3. If no safe period anchor exists, the pair is invalid, or the calculated range exceeds the layout, return `ParseFailed`; do not drop the row or approximate it silently.

Canonicalization and teacher/week merging include the exact pair. A non-standard and a standard assignment cannot merge merely because their anchors match.

Stable meeting IDs include exact minutes only when an override exists. Standard meetings therefore retain their current IDs.

## 6. Persistence and migration

Room database version becomes 2. `course_meetings` gains two nullable integer columns:

```sql
exactStartMinutes INTEGER NULL
exactEndMinutes INTEGER NULL
```

Migration 1-to-2 executes exactly:

```sql
ALTER TABLE course_meetings ADD COLUMN exactStartMinutes INTEGER DEFAULT NULL;
ALTER TABLE course_meetings ADD COLUMN exactEndMinutes INTEGER DEFAULT NULL;
```

Existing rows remain period-based and all other tables are untouched. `AppContainer` registers this migration; destructive fallback is forbidden.

An existing installation must remain immediately usable after an in-place APK upgrade. It must not require login or timetable re-import merely because the database moved from v1 to v2. Existing semester selection, portal linkage, school courses, meetings, manual items, profiles, sync timestamps, session storage, and user settings remain intact.

Entity/domain mappers preserve the pair and reject partial or invalid values.

Migration verification must create representative v1 data containing a portal-linked semester, school meetings, and manual items, open it as v2, and prove all original rows remain while exact-time columns are null.

## 7. Fingerprints, identity, and sync

Legacy compatibility is required. Adding nullable fields directly to the legacy serialized fingerprint would change every old hash because fingerprint JSON encodes defaults and nulls.

Therefore:

- snapshots with no exact-time override continue to use the byte-for-byte legacy fingerprint representation;
- snapshots containing at least one override use an explicit v2 fingerprint representation containing exact minutes;
- fingerprint ordering includes effective time and remains deterministic;
- standard-only existing data does not produce a false change after upgrade;
- adding, removing, or changing an exact override changes the fingerprint;
- `SnapshotDiffer` reports an effective time change, while unchanged standard rows behave as before.

No sync fingerprint, worker, or portal row may silently ignore the override.

## 8. Boundary-time pills

The timetable derives the set of standard axis boundaries from every bound profile period start and end. Each placed school or manual block independently evaluates:

```text
show start pill = effective start is not a standard boundary
show end pill   = effective end is not a standard boundary
```

Presentation:

- compact `HH:mm` text;
- horizontally centered on the card;
- half embedded across the top or bottom card edge;
- Material 3 extra-large pill shape;
- `surfaceContainerHigh`-family container, readable content color, subtle outline/tonal elevation;
- theme-aware in light, dark, and wallpaper states;
- decorative pill does not change the logical time bounds or hit target;
- card text receives only the minimum top/bottom inset needed for a visible pill;
- pills and existing lower-right markers must not overlap;
- accessibility description includes the exact range once, without reading decorative pills as separate controls.

If one endpoint is standard and the other is not, only the non-standard endpoint receives a pill. A fully standard school meeting receives none. Manual items use the same rule.

## 9. Wrong-account recovery

Successful CAS authentication is not treated as proof that the selected identity can supply timetable data. Import failure remains non-destructive.

The first-launch error state presents:

- the existing retry action;
- a concise hint that USTC timetable import requires the `sc` student identity when multiple identities are available;
- an explicit “清除学校登录并重新登录” action.

The action clears only:

1. the encrypted `SessionStore` blob;
2. all cookies in this app's WebView `CookieManager`, followed by `flush()`;
3. transient import error/pending state.

After completion it launches `WebViewLoginActivity` again. It never clears Room or DataStore user data.

Settings uses the same account-clear authority. The current clear-login row therefore becomes a real school logout rather than a session-blob-only operation. Cookie clearing is asynchronous; UI prevents duplicate actions until completion and reports failure without claiming logout succeeded.

Ordinary `NetworkFailed`, `ParseFailed`, or `ValidationFailed` never automatically clears a valid session. The user explicitly chooses account reset.

## 10. WebView mobile/desktop mode

`WebViewLoginActivity` gains an app-owned top control with a vector desktop/mobile icon and a state-specific content description. It is outside portal DOM and remains reachable regardless of portal layout.

Behavior:

- every activity starts in mobile/default WebView mode;
- desktop mode applies a desktop user agent, `useWideViewPort = true`, and `loadWithOverviewMode = true`;
- returning to mobile restores the exact captured default user agent and initial viewport settings;
- switching reloads the current HTTP(S) page and preserves this app's WebView cookies/history;
- mode is activity-local and not persisted;
- automatic module bootstrap, current-turn DOM evaluation, completion detection, manual fallback, navigation-generation protection, and endpoint allowlisting are unchanged;
- the control uses Material 3 sizing, touch target, theme colors, and system-bar insets.

The switch is a manual escape hatch only. The known occasional DOM timing miss remains recoverable by tapping the portal control and is not changed in this scope.

## 11. Error handling and privacy

- No raw cookie, URL query, account identifier, course name, teacher, room, or portal body is logged.
- User-visible import errors distinguish authentication/account recovery guidance from retryable failure without exposing server payloads.
- Invalid time rows still fail atomically; partial school imports remain forbidden.
- Logout does not imply local timetable deletion.

## 12. Test strategy

Tests are written and observed failing before production changes.

### Parser and domain

- a `16:10` occurrence with a valid actual end imports with exact overrides;
- exact standard occurrences retain null overrides and their old IDs;
- malformed HHmm, inverted ranges, unsafe anchors, and out-of-layout period counts fail;
- canonical merging and ordering distinguish exact ranges;
- effective range selects override before profile range.

### Persistence and sync

- Room 1-to-2 migration preserves every existing table row;
- an upgraded v1 installation opens directly on its existing timetable without login or re-import;
- mapper round trips null and non-null pairs and rejects partial pairs;
- legacy standard-only fingerprint remains byte-for-byte unchanged;
- exact-time changes affect fingerprint and `SnapshotDiffer`;
- import and subsequent sync preserve exact ranges.

### Timetable presentation

- non-standard start/end independently show centered boundary pills;
- standard endpoints suppress their corresponding pill;
- school and manual blocks share the rule;
- pills do not change placement bounds or block click/long-press behavior;
- pills coexist with course text, ghost/variant markers, overlap splitting, themes, wallpaper, and font scale 1.3.

### Account recovery

- clear-login removes encrypted session and WebView cookies but preserves local database/settings;
- first-launch error offers clear-and-retry;
- cancel/failure does not falsely report success;
- an ordinary import error does not auto-clear credentials.

### WebView mode

- default mode retains current mobile settings;
- desktop toggle changes UA/viewport and reloads current page;
- toggling back restores captured defaults;
- mode is not persisted across activity recreation/new launch;
- DOM navigation and login completion tests remain green.

### Qualification

- targeted parser/domain/database/UI/WebView tests;
- full unit suite and lint;
- debug and release assembly;
- migration test against v1 data;
- physical-device import on the diagnosed account, verifying all eight courses and the non-standard meeting render;
- mobile/desktop switching on the small-screen portal page;
- logout/relogin with `gid` then `sc`, proving local timetable data is not deleted.

## 13. Acceptance criteria

The work is accepted when:

- the diagnosed 182-row payload imports atomically rather than failing on the 12 `16:10` rows;
- non-standard school blocks use their actual portal times in layout and details;
- non-standard school/manual endpoints show only the required Material 3 pills;
- installed v1 data migrates without loss and standard-only fingerprints remain compatible;
- an already working phone remains usable immediately after an in-place upgrade, with no forced re-import;
- a user can clear a wrong school identity and reauthenticate without system-level app-data clearing;
- desktop mode exposes otherwise hidden portal controls without changing automatic navigation logic;
- no local timetable or manual-item data is deleted during account recovery;
- the production and test suites pass and the real device completes import.
